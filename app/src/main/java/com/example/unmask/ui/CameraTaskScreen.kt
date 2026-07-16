package com.example.unmask.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import androidx.camera.effects.Frame
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.Matrix
import android.text.TextPaint
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.speech.tts.TextToSpeech
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.unmask.data.Constants
import com.example.unmask.data.DataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraTaskScreen(
    repository: DataRepository,
    gameId: String,
    taskId: String,
    onBack: () -> Unit,
    onRecordingFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(context) {
        onDispose {
            try {
                cameraProviderState.value?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val customGames = repository.customGames.collectAsState(initial = emptyList()).value
    val customTasks = repository.customTasks.collectAsState(initial = emptyList()).value

    // Locate the active game and task (including custom games/tasks)
    val game = remember(gameId, customGames) {
        Constants.GAMES.find { it.id == gameId } ?: customGames.find { it.id == gameId }
    }
    val task = remember(taskId, customTasks) {
        Constants.TASKS.find { it.id == taskId } ?: customTasks.find { it.id == taskId }
    }

    if (game == null || task == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Görev veya oyun bulunamadı.")
        }
        return
    }

    var countdown by remember { mutableStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    // Camera and Audio permissions
    val permissions = remember {
        mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

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

    // Blinking REC dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Camera Video Capture references
    var videoCaptureState by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecordingState by remember { mutableStateOf<Recording?>(null) }

    // Start recording trigger
    val startRecordingAction = {
        val videoCapture = videoCaptureState
        if (videoCapture != null) {
            val name = "unmask_${game.id}_${task.cardCode}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UNMASK")
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            isRecording = true
            
            val recording = videoCapture.output
                .prepareRecording(context, mediaStoreOutputOptions)
                .apply {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            // Launch Elapsed Timer Coroutine when recording officially starts
                            coroutineScope.launch {
                                countdown = 0
                                while (isRecording) {
                                    delay(1000)
                                    countdown += 1
                                }
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            isUploading = true
                            activeRecordingState = null
                            
                            if (!recordEvent.hasError()) {
                                val outputUri = recordEvent.outputResults.outputUri
                                coroutineScope.launch {
                                    try {
                                        repository.addMemory(
                                            gameId = game.id,
                                            gameName = game.name,
                                            category = game.category,
                                            taskId = task.id,
                                            taskText = task.text,
                                            videoUri = outputUri,
                                            isPublic = false
                                        )
                                        repository.addHistoryItem(
                                            gameName = game.name,
                                            note = "Görev tamamlandı: ${task.text}"
                                        )
                                        Toast.makeText(context, "Kayıt tamamlandı ve Galeriye kaydedildi!", Toast.LENGTH_LONG).show()
                                        onRecordingFinished()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isUploading = false
                                    }
                                }
                            } else {
                                isUploading = false
                                Toast.makeText(context, "Kayıt hatası: ${recordEvent.error}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            activeRecordingState = recording
        } else {
            Toast.makeText(context, "Kamera kaydedici hazır değil!", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera View (80% Height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .align(Alignment.TopCenter)
        ) {
            if (permissionsGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HD))
                                .build()
                            val videoCapture = VideoCapture.withOutput(recorder)
                            videoCaptureState = videoCapture

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            val mainHandler = Handler(Looper.getMainLooper())
                            val errorConsumer = androidx.core.util.Consumer<Throwable> { error ->
                                error.printStackTrace()
                            }
                            val overlayEffect = OverlayEffect(
                                CameraEffect.VIDEO_CAPTURE,
                                0,
                                mainHandler,
                                errorConsumer
                            )

                            val cardPaint = Paint().apply {
                                color = android.graphics.Color.argb(128, 0, 0, 0) // Saydam siyah (translucent black)
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }
                            val borderPaint = Paint().apply {
                                color = android.graphics.Color.argb(64, 255, 255, 255) // Saydam beyaz kenarlık
                                style = Paint.Style.STROKE
                                strokeWidth = 2f
                                isAntiAlias = true
                            }
                            val headerPaint = Paint().apply {
                                color = android.graphics.Color.WHITE // Solid white for UNMASK header inside card
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }
                            val textPaint = TextPaint().apply {
                                color = android.graphics.Color.WHITE // Beyaz yazı
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                isAntiAlias = true
                            }

                            overlayEffect.setOnDrawListener { frame: Frame ->
                                val canvas = frame.overlayCanvas
                                canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                
                                val sensorToUi = previewView.sensorToViewTransform
                                if (sensorToUi != null) {
                                    val uiToSensor = Matrix()
                                    sensorToUi.invert(uiToSensor)
                                    uiToSensor.postConcat(frame.sensorToBufferTransform)
                                    canvas.setMatrix(uiToSensor)
                                    
                                    val width = previewView.width.toFloat()
                                    val height = previewView.height.toFloat()

                                    // Check if coordinate space is mirrored (reflected) and un-mirror it
                                    val values = FloatArray(9)
                                    uiToSensor.getValues(values)
                                    val mScaleX = values[Matrix.MSCALE_X]
                                    val mSkewX = values[Matrix.MSKEW_X]
                                    val mScaleY = values[Matrix.MSCALE_Y]
                                    val mSkewY = values[Matrix.MSKEW_Y]
                                    val determinant = mScaleX * mScaleY - mSkewX * mSkewY
                                    
                                    if (determinant < 0f) {
                                        // Flip horizontally around the center of the UI to draw text normally
                                        canvas.scale(-1f, 1f, width / 2f, height / 2f)
                                    }

                                    // Draw relative to view coordinates (portrait screen)
                                    val cardWidth = width * 0.9f
                                    val cardHeight = height * 0.16f
                                    val cardLeft = width * 0.05f
                                    val cardRight = width * 0.95f
                                    val cardBottom = height * 0.94f
                                    val cardTop = cardBottom - cardHeight
                                    val cornerRadius = width * 0.03f
                                    val padding = width * 0.04f

                                    // Set text sizes dynamically based on resolution
                                    headerPaint.textSize = width * 0.03f
                                    textPaint.textSize = width * 0.045f

                                    // Draw background card and border
                                    canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, cornerRadius, cornerRadius, cardPaint)
                                    canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, cornerRadius, cornerRadius, borderPaint)

                                    // Draw header "UNMASK" inside the card
                                    val headerX = cardLeft + padding
                                    val headerY = cardTop + padding + headerPaint.textSize
                                    canvas.drawText("UNMASK", headerX, headerY, headerPaint)

                                    // Draw task text wrapped with StaticLayout
                                    canvas.save()
                                    val textX = cardLeft + padding
                                    val textY = headerY + (width * 0.015f) // small gap below header
                                    canvas.translate(textX, textY)

                                    val textWidth = (cardWidth - padding * 2).toInt()
                                    val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        android.text.StaticLayout.Builder
                                            .obtain(task.text, 0, task.text.length, textPaint, textWidth)
                                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                                            .setLineSpacing(0f, 1.1f)
                                            .setIncludePad(false)
                                            .build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.text.StaticLayout(
                                            task.text,
                                            textPaint,
                                            textWidth,
                                            android.text.Layout.Alignment.ALIGN_NORMAL,
                                            1.1f,
                                            0f,
                                            false
                                        )
                                    }
                                    staticLayout.draw(canvas)
                                    canvas.restore()
                                } else {
                                    // Fallback: draw in buffer coordinates
                                    val width = canvas.width.toFloat()
                                    val height = canvas.height.toFloat()

                                    // Check if canvas default matrix is mirrored and un-mirror it
                                    val currentMatrix = Matrix()
                                    canvas.getMatrix(currentMatrix)
                                    val values = FloatArray(9)
                                    currentMatrix.getValues(values)
                                    val mScaleX = values[Matrix.MSCALE_X]
                                    val mSkewX = values[Matrix.MSKEW_X]
                                    val mScaleY = values[Matrix.MSCALE_Y]
                                    val mSkewY = values[Matrix.MSKEW_Y]
                                    val determinant = mScaleX * mScaleY - mSkewX * mSkewY
                                    
                                    if (determinant < 0f) {
                                        canvas.scale(-1f, 1f, width / 2f, height / 2f)
                                    }

                                    val cardWidth = width * 0.9f
                                    val cardHeight = height * 0.16f
                                    val cardLeft = width * 0.05f
                                    val cardRight = width * 0.95f
                                    val cardBottom = height * 0.94f
                                    val cardTop = cardBottom - cardHeight
                                    val cornerRadius = width * 0.03f
                                    val padding = width * 0.04f

                                    headerPaint.textSize = width * 0.03f
                                    textPaint.textSize = width * 0.045f

                                    canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, cornerRadius, cornerRadius, cardPaint)
                                    canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, cornerRadius, cornerRadius, borderPaint)

                                    // Draw header "UNMASK" inside the card
                                    val headerX = cardLeft + padding
                                    val headerY = cardTop + padding + headerPaint.textSize
                                    canvas.drawText("UNMASK", headerX, headerY, headerPaint)

                                    canvas.save()
                                    val textX = cardLeft + padding
                                    val textY = headerY + (width * 0.015f)
                                    canvas.translate(textX, textY)

                                    val textWidth = (cardWidth - padding * 2).toInt()
                                    val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        android.text.StaticLayout.Builder
                                            .obtain(task.text, 0, task.text.length, textPaint, textWidth)
                                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                                            .setLineSpacing(0f, 1.1f)
                                            .setIncludePad(false)
                                            .build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.text.StaticLayout(
                                            task.text,
                                            textPaint,
                                            textWidth,
                                            android.text.Layout.Alignment.ALIGN_NORMAL,
                                            1.1f,
                                            0f,
                                            false
                                        )
                                    }
                                    staticLayout.draw(canvas)
                                    canvas.restore()
                                }
                                true
                            }

                            val useCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(videoCapture)
                                .addEffect(overlayEffect)
                                .build()

                            try {
                                cameraProviderState.value = cameraProvider
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    useCaseGroup
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Kamera veya Ses Kayıt İzni Bekleniyor", color = Color.White)
                }
            }

            // REC Pulsing Label (when recording)
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 24.dp, start = 20.dp)
                        .background(Color.Red.copy(alpha = alphaAnim), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Text(
                        text = "REC",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Watermark logo
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 24.dp, start = 20.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "UNMASK",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isRecording) {
                        activeRecordingState?.stop()
                    }
                    onBack()
                },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (game != null) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Active Session",
                        tint = Color(0xFFFACC15)
                    )
                }
            }
        }

        // Bottom Task description card panel (22% Height)
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.25f)
                .align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Task details
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "UNMASK",
                        color = Color.Black.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = task.text,
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 24.sp
                    )
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timer box
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .border(2.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Text(
                                text = countdown.toString(),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.Black
                            )
                        }
                        Text(
                            text = "SANİYE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black.copy(alpha = 0.4f),
                            letterSpacing = 1.sp
                        )
                    }

                    // Recording Trigger Button
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(36.dp))
                    } else {
                        Button(
                            onClick = {
                                if (!isRecording) {
                                    startRecordingAction()
                                } else {
                                    activeRecordingState?.stop()
                                    isRecording = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color.Red else Color.Black,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = "Record",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) "KAYDI DURDUR" else "KAYDI BAŞLAT",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
