package com.example.unmask.features.ar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class TestFaceData(
    val landmarks: List<Keypoint>,
    val rawWidth: Int,
    val rawHeight: Int,
    val rotation: Int,
    val analysisSensorToBuffer: android.graphics.Matrix
)

@Composable
fun TestScreen(repository: DataRepository) {

    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Filter state ──────────────────────────────────────────────────────────
    var activeFilter           by remember { mutableStateOf("none") }
    var selectedCategoryFilter by remember { mutableStateOf<MaskCategory?>(null) }

    // ── Camera / AR state ─────────────────────────────────────────────────────
    val latestFaceData       = remember { AtomicReference<TestFaceData?>(null) }
    val latestRotation       = remember { AtomicInteger(0) }
    val latestSensorToBuffer = remember { AtomicReference<android.graphics.Matrix?>(null) }

    val faceLandmarkerState = remember { mutableStateOf<com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker?>(null) }
    val smoothedMetrics     = remember { SmoothedFaceMetrics() }
    val lastFrameTimeNs     = remember { AtomicLong(0L) }
    val analysisExecutor    = remember { Executors.newSingleThreadExecutor() }
    val lastDetectedTime    = remember { AtomicLong(0L) }
    var isFaceDetected      by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    // ── Permissions ───────────────────────────────────────────────────────────
    val permissions = arrayOf(Manifest.permission.CAMERA)
    var permissionsGranted by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map -> permissionsGranted = map.values.all { it } }

    LaunchedEffect(Unit) { if (!permissionsGranted) launcher.launch(permissions) }

    // ── Create FaceLandmarker ─────────────────────────────────────────────────
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) {
            faceLandmarkerState.value?.close()
            faceLandmarkerState.value = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val landmarker = PartyMaskEngine.createFaceLandmarker(context) { result, mpImage ->
                val faceLandmarks = result.faceLandmarks()?.firstOrNull()
                if (faceLandmarks != null) {
                    val keypoints = faceLandmarks.map { lm -> Keypoint(lm.x(), lm.y()) }

                    val sensorToBuffer = android.graphics.Matrix(
                        latestSensorToBuffer.get() ?: android.graphics.Matrix()
                    )

                    latestFaceData.set(
                        TestFaceData(
                            landmarks              = keypoints,
                            rawWidth               = mpImage.width,
                            rawHeight              = mpImage.height,
                            rotation               = latestRotation.get(),
                            analysisSensorToBuffer = sensorToBuffer
                        )
                    )
                    lastDetectedTime.set(System.currentTimeMillis())
                    isFaceDetected = true
                } else {
                    if (System.currentTimeMillis() - lastDetectedTime.get() > 350L) {
                        latestFaceData.set(null)
                        isFaceDetected = false
                        smoothedMetrics.reset()
                    }
                }
            }
            faceLandmarkerState.value = landmarker
        }
    }

    val visibleMasks = remember(selectedCategoryFilter) {
        if (selectedCategoryFilter == null) PartyMaskEngine.PARTY_MASKS
        else PartyMaskEngine.PARTY_MASKS.filter { it.category == selectedCategoryFilter }
    }

    // Unbind camera on leave
    DisposableEffect(context) {
        onDispose {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                val unbind = { try { future.get().unbindAll() } catch (e: Exception) { e.printStackTrace() } }
                if (future.isDone) unbind()
                else future.addListener({ unbind() }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        if (permissionsGranted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()
                        previewView.post {
                            try {
                                cameraProvider.unbindAll()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .build()

                                val mainHandler   = Handler(Looper.getMainLooper())
                                val errorConsumer = androidx.core.util.Consumer<Throwable> { it.printStackTrace() }
                                val overlayEffect = OverlayEffect(
                                    CameraEffect.PREVIEW, 0, mainHandler, errorConsumer
                                )

                                overlayEffect.setOnDrawListener { frame ->
                                    val overlayCanvas = frame.overlayCanvas
                                    overlayCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                                    val filter = activeFilter
                                    val face   = latestFaceData.get()
                                    if (face != null && filter != "none") {
                                        PartyMaskEngine.renderMask(
                                            canvas                = overlayCanvas,
                                            filterId              = filter,
                                            face                  = FaceGeometryData(
                                                landmarks              = face.landmarks,
                                                rawWidth               = face.rawWidth,
                                                rawHeight              = face.rawHeight,
                                                rotation               = face.rotation,
                                                analysisSensorToBuffer = face.analysisSensorToBuffer
                                            ),
                                            overlaySensorToCanvas = frame.sensorToBufferTransform,
                                            metrics               = smoothedMetrics
                                        )
                                    }
                                    true
                                }

                                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    val landmarker = faceLandmarkerState.value
                                    if (landmarker != null) {
                                        try {
                                            val now = System.nanoTime()
                                            if (now - lastFrameTimeNs.get() >= PartyMaskEngine.FRAME_INTERVAL_NS) {
                                                lastFrameTimeNs.set(now)
                                                val rot = imageProxy.imageInfo.rotationDegrees
                                                latestRotation.set(rot)
                                                latestSensorToBuffer.set(
                                                    android.graphics.Matrix(
                                                        imageProxy.imageInfo.sensorToBufferTransformMatrix
                                                    )
                                                )
                                                val bitmap  = imageProxy.toBitmap()
                                                val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
                                                val opts    = com.google.mediapipe.tasks.vision.core.ImageProcessingOptions.builder()
                                                    .setRotationDegrees(rot).build()
                                                landmarker.detectAsync(mpImage, opts, imageProxy.imageInfo.timestamp / 1_000_000)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val useCaseGroup = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(imageAnalysis)
                                    .addEffect(overlayEffect)
                                    .apply { previewView.viewPort?.let { setViewPort(it) } }
                                    .build()

                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    useCaseGroup
                                )
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Kamera İzni Gerekli", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Face detection indicator (top-right) ──────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(0.65f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(
                    if (isFaceDetected) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape
                ))
                Text(
                    text = if (isFaceDetected) "YÜZ ALGILANDI" else "YÜZ ARANIYOR...",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Bottom mask picker ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .background(Color(0xFF1A1A1A).copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎭 VİDEO PARTİ MASKELERİ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)

            // Category tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(null,                   "HEPSİ",         Color(0xFF7C3AED)),
                    Triple(MaskCategory.FEMININE,  "💃 FEMİNİN",    Color(0xFFDB2777)),
                    Triple(MaskCategory.MASCULINE, "🥷 MASKÜLEN",   Color(0xFF2563EB))
                ).forEach { (cat, label, accent) ->
                    val selected = selectedCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) accent else Color.White.copy(0.08f))
                            .clickable { selectedCategoryFilter = cat }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Mask carousel
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val sel = activeFilter == "none"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Color.White else Color(0xFF2A2A2A))
                            .clickable { activeFilter = "none" }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("❌ Filtresiz", color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(visibleMasks) { mask ->
                    val sel = activeFilter == mask.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) mask.badgeColor else Color(0xFF2A2A2A))
                            .border(if (sel) 1.5.dp else 0.dp, Color.White.copy(0.5f), RoundedCornerShape(14.dp))
                            .clickable { activeFilter = mask.id }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mask.icon, fontSize = 14.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(mask.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
