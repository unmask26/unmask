package com.example.unmask.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.unmask.data.Constants
import com.example.unmask.data.DataRepository
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScreen(
    repository: DataRepository,
    activeGameId: String? = null,
    onBack: () -> Unit,
    onCardScanned: (String, String) -> Unit // gameId, taskId
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    var manualCode by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    // Camera Permissions flow
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Find if user is logged in
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val customTasks by repository.customTasks.collectAsState(initial = emptyList())
    val allTasks = remember(customTasks) { Constants.TASKS + customTasks }
    
    // Simulate Scan handler
    val handleSimulateScan = {
        // Select a random card task matching the active game if available
        val filteredTasks = if (!activeGameId.isNullOrEmpty()) {
            allTasks.filter { it.gameId == activeGameId }
        } else {
            allTasks
        }
        if (filteredTasks.isNotEmpty()) {
            val randomTask = filteredTasks[Random.nextInt(filteredTasks.size)]
            onCardScanned(randomTask.gameId, randomTask.id)
        }
    }

    val handleManualSubmit = {
        val code = manualCode.trim().uppercase()
        if (code.isNotEmpty()) {
            // Find task by cardCode and activeGameId if available, fallback to search by cardCode only
            val foundTask = if (!activeGameId.isNullOrEmpty()) {
                allTasks.find { it.gameId == activeGameId && it.cardCode == code }
                    ?: allTasks.find { it.cardCode == code }
            } else {
                allTasks.find { it.cardCode == code }
            }
            if (foundTask != null) {
                onCardScanned(foundTask.gameId, foundTask.id)
                manualCode = ""
            } else {
                notificationMessage = "\"$code\" kodlu bir görev bulunamadı! Lütfen tekrar deneyin (Örn: AS, 10H, JQ)."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Camera Preview Background (60% opacity)
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val barcodeScanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )

                        var isProcessingBarcode = false

                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !isProcessingBarcode) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue
                                            if (rawValue != null) {
                                                // Extract cardCode from scanned string (e.g. "2S" or "https://domain.com/2s")
                                                val code = rawValue.substringAfterLast("/").trim().uppercase()
                                                val foundTask = if (!activeGameId.isNullOrEmpty()) {
                                                    allTasks.find { it.gameId == activeGameId && it.cardCode == code }
                                                        ?: allTasks.find { it.cardCode == code }
                                                } else {
                                                    allTasks.find { it.cardCode == code }
                                                }
                                                if (foundTask != null) {
                                                    isProcessingBarcode = true
                                                    onCardScanned(foundTask.gameId, foundTask.id)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnFailureListener {
                                        it.printStackTrace()
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProviderState.value = cameraProvider
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .border(4.dp, Color.White, RoundedCornerShape(32.dp))
            )
            // Black overlay for opacity
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        } else {
            // No Permission Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.DarkGray)
                    .border(4.dp, Color.White, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Kamera İzni Gerekli",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
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
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "QR OKU",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.width(48.dp)) // Spacer to align title center
        }

        // Viewfinder and Inputs
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Viewfinder Bracket box
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Diagonal Brackets simulated by background and overlaying borders
                // For simplicity, we put a large scanner action button in center
                IconButton(
                    onClick = handleSimulateScan,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Scan Simulator",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Kamerayı QR koda odaklayın veya\nkart simülatörüne dokunun",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Manual Code entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    placeholder = { 
                        Text(
                            text = "Kart Kodu (Örn: AS, 10H)", 
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        ) 
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = handleManualSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        text = "GİR",
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        // Notification dialog
        if (notificationMessage != null) {
            AlertDialog(
                onDismissRequest = { notificationMessage = null },
                title = { Text("BİLGİ", fontWeight = FontWeight.Black) },
                text = { Text(notificationMessage!!, fontWeight = FontWeight.Bold) },
                confirmButton = {
                    Button(
                        onClick = { notificationMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Text("TAMAM", fontWeight = FontWeight.Black)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
