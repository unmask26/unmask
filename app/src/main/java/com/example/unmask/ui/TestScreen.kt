package com.example.unmask.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.unmask.data.DataRepository
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions


import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.Dispatchers

@Composable
fun TestScreen(
    repository: DataRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var activeFilter by remember { mutableStateOf("gozluk") }
    val latestFaceData = remember { java.util.concurrent.atomic.AtomicReference<TestFaceData?>(null) }
    val latestRotation = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val faceLandmarkerState = remember { mutableStateOf<FaceLandmarker?>(null) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }
    val lastDetectedTime = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var isFaceDetected by remember { mutableStateOf(false) }
    var isTalking by remember { mutableStateOf(false) }
    
    var sincapKapali by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sincapAcik by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var kurtKapali by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var kurtAcik by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val kapaliS = loadAndCleanBitmap(context, com.example.unmask.R.drawable.sincap_kapali)
            val acikS = loadAndCleanBitmap(context, com.example.unmask.R.drawable.sincap_acik)
            val kapaliK = loadAndCleanBitmap(context, com.example.unmask.R.drawable.kurt_kapali)
            val acikK = loadAndCleanBitmap(context, com.example.unmask.R.drawable.kurt_acik)
            
            sincapKapali = kapaliS
            sincapAcik = acikS
            kurtKapali = kapaliK
            kurtAcik = acikK
        }
    }
    
    val permissions = arrayOf(Manifest.permission.CAMERA)
    var permissionsGranted by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        permissionsGranted = map.values.all { it }
    }
    
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            launcher.launch(permissions)
        }
    }



    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) {
            faceLandmarkerState.value?.close()
            faceLandmarkerState.value = null
            return@LaunchedEffect
        }
        
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .setDelegate(Delegate.GPU)
                    .build()
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, mpImage ->
                        val landmarksList = result.faceLandmarks()
                        val faceLandmarks = landmarksList?.firstOrNull()
                        val rotation = latestRotation.get()
                        if (faceLandmarks != null) {
                            val rawLandmarks = faceLandmarks.map { lm ->
                                val raw = when (rotation) {
                                    90 -> Pair(lm.y(), 1f - lm.x())
                                    180 -> Pair(1f - lm.x(), 1f - lm.y())
                                    270 -> Pair(1f - lm.y(), lm.x())
                                    else -> Pair(lm.x(), lm.y())
                                }
                                Keypoint(raw.first, raw.second)
                            }
                            val newFaceData = TestFaceData(
                                landmarks = rawLandmarks,
                                imageWidth = mpImage.width,
                                imageHeight = mpImage.height
                            )
                            latestFaceData.set(newFaceData)
                            lastDetectedTime.set(System.currentTimeMillis())
                            isFaceDetected = true
                        } else {
                            if (System.currentTimeMillis() - lastDetectedTime.get() > 350L) {
                                latestFaceData.set(null)
                                isFaceDetected = false
                            }
                        }
                    }
                    .setErrorListener { error ->
                        error.printStackTrace()
                    }
                    .build()
                val landmarker = FaceLandmarker.createFromOptions(context, options)
                faceLandmarkerState.value = landmarker
            } catch (e: Exception) {
                e.printStackTrace()
                // CPU Fallback
                try {
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .setDelegate(Delegate.CPU)
                        .build()
                    val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setMinFaceDetectionConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
                        .setMinFacePresenceConfidence(0.5f)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener { result, mpImage ->
                            val landmarksList = result.faceLandmarks()
                            val faceLandmarks = landmarksList?.firstOrNull()
                            val rotation = latestRotation.get()
                            if (faceLandmarks != null) {
                                val rawLandmarks = faceLandmarks.map { lm ->
                                    val raw = when (rotation) {
                                        90 -> Pair(lm.y(), 1f - lm.x())
                                        180 -> Pair(1f - lm.x(), 1f - lm.y())
                                        270 -> Pair(1f - lm.y(), lm.x())
                                        else -> Pair(lm.x(), lm.y())
                                    }
                                    Keypoint(raw.first, raw.second)
                                }
                                val newFaceData = TestFaceData(
                                    landmarks = rawLandmarks,
                                    imageWidth = mpImage.width,
                                    imageHeight = mpImage.height
                                )
                                latestFaceData.set(newFaceData)
                                lastDetectedTime.set(System.currentTimeMillis())
                                isFaceDetected = true
                            } else {
                                if (System.currentTimeMillis() - lastDetectedTime.get() > 350L) {
                                    latestFaceData.set(null)
                                    isFaceDetected = false
                                }
                            }
                        }
                        .setErrorListener { error ->
                            error.printStackTrace()
                        }
                        .build()
                    val landmarker = FaceLandmarker.createFromOptions(context, options)
                    faceLandmarkerState.value = landmarker
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(activeFilter) {
        val isAnimalFilter = activeFilter == "sincap" || activeFilter == "kurt"
        if (!isAnimalFilter) {
            isTalking = false
            return@LaunchedEffect
        }
        val sampleRate = 44100
        val bufSize = android.media.AudioRecord.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        val audioRecord = try {
            android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            ).also { it.startRecording() }
        } catch (e: Exception) { null }
        try {
            val buffer = ShortArray(bufSize / 2)
            while (activeFilter == "sincap" || activeFilter == "kurt") {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val rms = Math.sqrt(buffer.take(read).map { it.toDouble() * it }.average())
                    isTalking = rms > 800.0
                }
                kotlinx.coroutines.delay(50)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { audioRecord?.stop() } catch (e: Exception) {}
            try { audioRecord?.release() } catch (e: Exception) {}
            isTalking = false
        }
    }
    
    val filters = remember {
        listOf(
            FilterInfo("Yok", "none", Color.Gray),
            FilterInfo("Gözlük", "gozluk", Color(0xFF3B82F6)),
            FilterInfo("Bıyık", "biyik", Color(0xFF10B981)),
            FilterInfo("Taç", "tac", Color(0xFFF59E0B)),
            FilterInfo("Sakal", "sakal", Color(0xFF8B5CF6)),
            FilterInfo("Kulak", "kulak", Color(0xFFEC4899)),
            FilterInfo("Sincap", "sincap", Color(0xFFD97706)),
            FilterInfo("Kurt", "kurt", Color(0xFF4B5563))
        )
    }
    
    DisposableEffect(context) {
        onDispose {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                } else {
                    cameraProviderFuture.addListener({
                        try {
                            cameraProviderFuture.get().unbindAll()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (permissionsGranted) {            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx)
                    provider.addListener({
                        val cameraProvider = provider.get()
                        previewView.post {
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            try {
                                cameraProvider.unbindAll()
                                
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .setTargetResolution(Size(640, 480))
                                    .build()

                                val mainHandler = Handler(Looper.getMainLooper())
                                val errorConsumer = androidx.core.util.Consumer<Throwable> { error ->
                                    error.printStackTrace()
                                }
                                val overlayEffect = OverlayEffect(
                                    CameraEffect.PREVIEW,
                                    0,
                                    mainHandler,
                                    errorConsumer
                                )
                                
                                overlayEffect.setOnDrawListener { frame: Frame ->
                                    val canvas = frame.overlayCanvas
                                    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    
                                    val filter = activeFilter
                                    val face = latestFaceData.get()
                                    
                                    if (face != null && filter != "none" && face.landmarks.isNotEmpty()) {
                                        val savedCount = canvas.save()
                                        canvas.concat(frame.sensorToBufferTransform)
                                        
                                        val landmarks = face.landmarks
                                        val imgW = face.imageWidth.toFloat()
                                        val imgH = face.imageHeight.toFloat()
                                        
                                        fun getX(idx: Int) = landmarks[idx].x * imgW
                                        fun getY(idx: Int) = landmarks[idx].y * imgH
                                        
                                        val lex = getX(33)
                                        val ley = getY(33)
                                        val rex = getX(263)
                                        val rey = getY(263)
                                        
                                        val dX = rex - lex
                                        val dY = rey - ley
                                        val rollAngle = Math.toDegrees(Math.atan2(dY.toDouble(), dX.toDouble())).toFloat()
                                        
                                        val eyeMidX = (lex + rex) / 2f
                                        val eyeMidY = (ley + rey) / 2f
                                        
                                        canvas.rotate(rollAngle, eyeMidX, eyeMidY)
                                        
                                        val eyeDistance = Math.hypot((rex - lex).toDouble(), (rey - ley).toDouble()).toFloat()
                                        val faceW = eyeDistance * 2.2f
                                        val faceH = eyeDistance * 2.2f
                                        
                                        when (filter) {
                                            "gozluk" -> {
                                                val glassPaint = Paint().apply {
                                                    color = android.graphics.Color.argb(220, 20, 20, 20)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val highlightPaint = Paint().apply {
                                                    color = android.graphics.Color.argb(128, 255, 255, 255)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val bridgePaint = Paint().apply {
                                                    color = android.graphics.Color.BLACK
                                                    style = Paint.Style.STROKE
                                                    strokeWidth = faceW * 0.04f
                                                    isAntiAlias = true
                                                }
                                                val lensW = eyeDistance * 0.7f
                                                val lensH = eyeDistance * 0.5f
                                                val spacing = eyeDistance * 0.2f
                                                val eyeY = eyeMidY
                                                canvas.drawRoundRect(eyeMidX - spacing/2 - lensW, eyeY - lensH/2, eyeMidX - spacing/2, eyeY + lensH/2, lensW*0.3f, lensW*0.3f, glassPaint)
                                                canvas.drawCircle(eyeMidX - spacing/2 - lensW*0.7f, eyeY - lensH*0.2f, lensW*0.08f, highlightPaint)
                                                canvas.drawRoundRect(eyeMidX + spacing/2, eyeY - lensH/2, eyeMidX + spacing/2 + lensW, eyeY + lensH/2, lensW*0.3f, lensW*0.3f, glassPaint)
                                                canvas.drawCircle(eyeMidX + spacing/2 + lensW*0.3f, eyeY - lensH*0.2f, lensW*0.08f, highlightPaint)
                                                canvas.drawLine(eyeMidX - spacing/2, eyeY, eyeMidX + spacing/2, eyeY, bridgePaint)
                                            }
                                            "biyik" -> {
                                                val mustachePaint = Paint().apply {
                                                    color = android.graphics.Color.BLACK
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val mx = getX(164)
                                                val my = getY(164)
                                                val mw = eyeDistance * 0.8f
                                                val mh = eyeDistance * 0.22f
                                                val leftPath = Path().apply {
                                                    moveTo(mx, my)
                                                    cubicTo(mx - mw*0.3f, my - mh*0.3f, mx - mw*0.7f, my - mh*0.5f, mx - mw, my + mh*0.2f)
                                                    cubicTo(mx - mw*0.6f, my + mh*0.8f, mx - mw*0.2f, my + mh*0.4f, mx, my + mh*0.1f)
                                                    close()
                                                }
                                                canvas.drawPath(leftPath, mustachePaint)
                                                val rightPath = Path().apply {
                                                    moveTo(mx, my)
                                                    cubicTo(mx + mw*0.3f, my - mh*0.3f, mx + mw*0.7f, my - mh*0.5f, mx + mw, my + mh*0.2f)
                                                    cubicTo(mx + mw*0.6f, my + mh*0.8f, mx + mw*0.2f, my + mh*0.4f, mx, my + mh*0.1f)
                                                    close()
                                                }
                                                canvas.drawPath(rightPath, mustachePaint)
                                            }
                                            "tac" -> {
                                                val crownPaint = Paint().apply {
                                                    color = android.graphics.Color.rgb(253, 224, 71)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val jewelPaint = Paint().apply {
                                                    color = android.graphics.Color.rgb(239, 68, 68)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val rx = getX(10)
                                                val ry = getY(10) - eyeDistance * 0.4f
                                                val rw = eyeDistance * 0.9f
                                                val rh = eyeDistance * 0.6f
                                                val crownPath = Path().apply {
                                                    moveTo(rx - rw, ry)
                                                    lineTo(rx - rw*0.8f, ry - rh*0.6f)
                                                    lineTo(rx - rw*0.4f, ry - rh*0.3f)
                                                    lineTo(rx, ry - rh)
                                                    lineTo(rx + rw*0.4f, ry - rh*0.3f)
                                                    lineTo(rx + rw*0.8f, ry - rh*0.6f)
                                                    lineTo(rx + rw, ry)
                                                    close()
                                                }
                                                canvas.drawPath(crownPath, crownPaint)
                                                val jRadius = faceW * 0.025f
                                                canvas.drawCircle(rx - rw*0.8f, ry - rh*0.6f, jRadius, jewelPaint)
                                                canvas.drawCircle(rx, ry - rh, jRadius, jewelPaint)
                                                canvas.drawCircle(rx + rw*0.8f, ry - rh*0.6f, jRadius, jewelPaint)
                                            }
                                            "sakal" -> {
                                                val beardPaint = Paint().apply {
                                                    color = android.graphics.Color.rgb(40, 30, 20)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val beardPath = Path().apply {
                                                    val bx = getX(164)
                                                    val by = getY(164) + eyeDistance * 0.1f
                                                    val bw = Math.hypot((getX(454) - getX(234)).toDouble(), (getY(454) - getY(234)).toDouble()).toFloat() * 0.5f
                                                    val bh = eyeDistance * 1.0f
                                                    moveTo(bx - bw, by)
                                                    cubicTo(bx - bw, by + bh*0.5f, bx - bw*0.6f, by + bh, bx, by + bh)
                                                    cubicTo(bx + bw*0.6f, by + bh, bx + bw, by + bh*0.5f, bx + bw, by)
                                                    lineTo(bx + bw*0.6f, by)
                                                    cubicTo(bx + bw*0.4f, by + bh*0.2f, bx + bw*0.2f, by + bh*0.3f, bx, by + bh*0.3f)
                                                    cubicTo(bx - bw*0.2f, by + bh*0.3f, bx - bw*0.4f, by + bh*0.2f, bx - bw*0.6f, by)
                                                    close()
                                                }
                                                canvas.drawPath(beardPath, beardPaint)
                                            }
                                            "kulak" -> {
                                                val earOuterPaint = Paint().apply {
                                                    color = android.graphics.Color.rgb(244, 63, 94)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val earInnerPaint = Paint().apply {
                                                    color = android.graphics.Color.rgb(254, 205, 211)
                                                    style = Paint.Style.FILL
                                                    isAntiAlias = true
                                                }
                                                val earW = eyeDistance * 0.45f
                                                val earH = eyeDistance * 0.45f
                                                val earSpacing = eyeDistance * 0.55f
                                                val earY = getY(10) - eyeDistance * 0.3f
                                                val leftEarPath = Path().apply {
                                                    moveTo(eyeMidX - earSpacing - earW/2, earY)
                                                    lineTo(eyeMidX - earSpacing, earY - earH)
                                                    lineTo(eyeMidX - earSpacing + earW/2, earY)
                                                    close()
                                                }
                                                canvas.drawPath(leftEarPath, earOuterPaint)
                                                val leftInnerPath = Path().apply {
                                                    moveTo(eyeMidX - earSpacing - earW*0.3f, earY)
                                                    lineTo(eyeMidX - earSpacing, earY - earH*0.7f)
                                                    lineTo(eyeMidX - earSpacing + earW*0.3f, earY)
                                                    close()
                                                }
                                                canvas.drawPath(leftInnerPath, earInnerPaint)
                                                val rightEarPath = Path().apply {
                                                    moveTo(eyeMidX + earSpacing - earW/2, earY)
                                                    lineTo(eyeMidX + earSpacing, earY - earH)
                                                    lineTo(eyeMidX + earSpacing + earW/2, earY)
                                                    close()
                                                }
                                                canvas.drawPath(rightEarPath, earOuterPaint)
                                                val rightInnerPath = Path().apply {
                                                    moveTo(eyeMidX + earSpacing - earW*0.3f, earY)
                                                    lineTo(eyeMidX + earSpacing, earY - earH*0.7f)
                                                    lineTo(eyeMidX + earSpacing + earW*0.3f, earY)
                                                    close()
                                                }
                                                canvas.drawPath(rightInnerPath, earInnerPaint)
                                            }
                                            "sincap", "kurt" -> {
                                                val bitmap = when (filter) {
                                                    "sincap" -> if (isTalking) sincapAcik else sincapKapali
                                                    "kurt"   -> if (isTalking) kurtAcik   else kurtKapali
                                                    else     -> null
                                                }
                                                if (bitmap != null) {
                                                    val animalPaint = Paint().apply {
                                                        isAntiAlias = true
                                                        alpha = 178
                                                    }
                                                    val size = eyeDistance * 3.3f
                                                    val left = getX(1) - size / 2f
                                                    val top = getY(1) - size / 2f - eyeDistance * 0.2f
                                                    val right = getX(1) + size / 2f
                                                    val bottom = getY(1) + size / 2f - eyeDistance * 0.2f
                                                    val dst = android.graphics.RectF(left, top, right, bottom)
                                                    canvas.drawBitmap(bitmap, null, dst, animalPaint)
                                                }
                                            }
                                        }
                                        canvas.restoreToCount(savedCount)
                                    }
                                    true
                                }
                                
                                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    val landmarker = faceLandmarkerState.value
                                    if (landmarker != null) {
                                        try {
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            latestRotation.set(rotation)
                                            val imageProcessingOptions = com.google.mediapipe.tasks.vision.core.ImageProcessingOptions.builder()
                                                .setRotationDegrees(rotation)
                                                .build()
                                            val bitmap = imageProxy.toBitmap()
                                            val mpImage = BitmapImageBuilder(bitmap).build()
                                            landmarker.detectAsync(mpImage, imageProcessingOptions, imageProxy.imageInfo.timestamp / 1_000_000)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                                
                                val useCaseGroupBuilder = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(imageAnalysis)
                                    .addEffect(overlayEffect)
                                    
                                previewView.viewPort?.let {
                                    useCaseGroupBuilder.setViewPort(it)
                                }
                                val useCaseGroup = useCaseGroupBuilder.build()
                                
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    useCaseGroup
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamera İzni Gerekli",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Status indicator in top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isFaceDetected) Color(0xFF10B981) else Color(0xFFEF4848),
                            CircleShape
                        )
                )
                Text(
                    text = if (isFaceDetected) "YÜZ ALGILANDI" else "YÜZ ARANIYOR...",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Bottom Filters Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
                .background(Color(0xFF1E1E1E).copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AR FİLTRE TEST PANELİ",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    val isSelected = activeFilter == filter.key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) filter.color else Color.White.copy(alpha = 0.08f))
                            .clickable { activeFilter = filter.key }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = filter.name,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

data class FilterInfo(
    val name: String,
    val key: String,
    val color: Color
)

private data class TestFaceData(
    val landmarks: List<Keypoint>,
    val imageWidth: Int,
    val imageHeight: Int
)

private fun loadAndCleanBitmap(context: android.content.Context, resId: Int): android.graphics.Bitmap? {
    return try {
        val src = android.graphics.BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val mutable = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val width = mutable.width
        val height = mutable.height
        val pixels = IntArray(width * height)
        mutable.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val visited = BooleanArray(width * height)
        val queue = java.util.ArrayDeque<Int>()
        
        for (x in 0 until width) {
            queue.add(x)
            visited[x] = true
            queue.add((height - 1) * width + x)
            visited[(height - 1) * width + x] = true
        }
        for (y in 1 until height - 1) {
            queue.add(y * width)
            visited[y * width] = true
            queue.add(y * width + (width - 1))
            visited[y * width + (width - 1)] = true
        }
        
        while (!queue.isEmpty()) {
            val idx = queue.poll()!!
            val px = idx % width
            val py = idx / width
            
            val c = pixels[idx]
            val a = android.graphics.Color.alpha(c)
            if (a == 0) continue
            
            val r = android.graphics.Color.red(c)
            val g = android.graphics.Color.green(c)
            val b = android.graphics.Color.blue(c)
            
            val isNeutral = Math.abs(r - g) < 15 && Math.abs(g - b) < 15
            val isLight = r > 150
            
            if (isNeutral && isLight) {
                pixels[idx] = android.graphics.Color.TRANSPARENT
                
                val dx = intArrayOf(-1, 1, 0, 0)
                val dy = intArrayOf(0, 0, -1, 1)
                for (i in 0 until 4) {
                    val nx = px + dx[i]
                    val ny = py + dy[i]
                    if (nx in 0 until width && ny in 0 until height) {
                        val nIdx = ny * width + nx
                        if (!visited[nIdx]) {
                            visited[nIdx] = true
                            queue.add(nIdx)
                        }
                    }
                }
            }
        }
        
        mutable.setPixels(pixels, 0, width, 0, 0, width, height)
        src.recycle()
        mutable
    } catch (e: Exception) {
        null
    }
}
