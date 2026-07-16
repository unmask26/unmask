package com.example.unmask.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
import androidx.camera.effects.Frame
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.unmask.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DunyaScreen(
    repository: DataRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val customGames by repository.customGames.collectAsState(initial = emptyList())
    val customTasks by repository.customTasks.collectAsState(initial = emptyList())

    if (currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.Black)
        }
        return
    }

    val user = currentUser!!
    
    var selectedOnlineCategory by remember { mutableStateOf<String?>(null) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var inputPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val onlineCategories = remember {
        listOf(
            OnlineCategoryInfo("İLİŞKİLER", "iliskiler", Color(0xFF8B5CF6), com.example.unmask.R.drawable.relations_icon),
            OnlineCategoryInfo("ADRENALİN", "adrenalin", Color(0xFFEF4444), com.example.unmask.R.drawable.adrenaline_icon),
            OnlineCategoryInfo("BİLGİ", "bilgi", Color(0xFFFBBF24), com.example.unmask.R.drawable.knowledge_icon),
            OnlineCategoryInfo("AKTÜEL", "aktuel", Color(0xFF3B82F6), com.example.unmask.R.drawable.actual_icon),
            OnlineCategoryInfo("HATIRALAR", "hatiralar", Color(0xFF10B981), com.example.unmask.R.drawable.memories_icon),
            OnlineCategoryInfo("FANTEZİLER", "fanteziler", Color(0xFFEC4899), com.example.unmask.R.drawable.fantasies_icon),
            OnlineCategoryInfo("+18", "adult", Color(0xFF111827), com.example.unmask.R.drawable.adult_icon),
            OnlineCategoryInfo("SOFTHUB", "softhub", Color(0xFFF43F5E), com.example.unmask.R.drawable.softhub_icon)
        )
    }

    val activeSession by repository.observeActiveSession(user.uid).collectAsState(initial = null)
    val onlineUsers by repository.getOnlineUsers(user.uid).collectAsState(initial = emptyList())

    // Heartbeat & Active Presence registration inside the lobby
    LaunchedEffect(activeSession, selectedOnlineCategory) {
        while (activeSession == null) {
            val now = System.currentTimeMillis()
            val banUntil = user.banUntil ?: 0L
            if (banUntil <= now) {
                val status = if (selectedOnlineCategory != null) "searching:$selectedOnlineCategory" else "idle"
                repository.updatePresence(user.uid, user.displayName, status, gender = user.gender)
            } else {
                repository.updatePresence(user.uid, user.displayName, "offline", banUntil, gender = user.gender)
            }
            delay(4_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                repository.removePresence(user.uid)
            }
        }
    }

    // Handle session heartbeats when in-game (preventing infinite recomposition loop and race conditions)
    LaunchedEffect(activeSession?.id) {
        val sessionId = activeSession?.id
        if (sessionId != null) {
            val isCreator = activeSession?.user1Id == user.uid
            while (true) {
                repository.updatePresence(user.uid, user.displayName, "playing")
                if (isCreator) {
                    repository.updateSessionHeartbeat(sessionId)
                }
                delay(5000)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
    ) {
        val userBanUntil = user.banUntil ?: 0L
        if (userBanUntil > System.currentTimeMillis()) {
            // Ban display screen
            val diffMs = userBanUntil - System.currentTimeMillis()
            val remainingMins = diffMs / 60_000
            val remainingSecs = (diffMs % 60_000) / 1000
            val remainingText = if (remainingMins > 0) {
                "$remainingMins dakika $remainingSecs saniye"
            } else {
                "$remainingSecs saniye"
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Banned",
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ENGEL SÜRESİ!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "30 saniye içinde oyun seçmediğiniz için online oyun oynamanız 2 dakika süreyle engellenmiştir.\n\nKalan Süre: $remainingText",
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (activeSession == null) {
            // Matchmaking Lobby / Online Categories Choice
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                if (selectedOnlineCategory == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ÇEVRİMİÇİ LOBİLER",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Oynamak istediğiniz lobi kategorisini seçin.",
                        fontSize = 13.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(onlineCategories.chunked(2)) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val leftCat = pair[0]
                                val leftCount = onlineUsers.count { it.status == "searching:${leftCat.key}" }
                                OnlineCategoryCard(
                                    category = leftCat,
                                    playerCount = leftCount,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (leftCat.key == "adult") {
                                            pendingCategory = leftCat.key
                                            showPasswordPrompt = true
                                        } else {
                                            selectedOnlineCategory = leftCat.key
                                        }
                                    }
                                )

                                if (pair.size > 1) {
                                    val rightCat = pair[1]
                                    val rightCount = onlineUsers.count { it.status == "searching:${rightCat.key}" }
                                    OnlineCategoryCard(
                                        category = rightCat,
                                        playerCount = rightCount,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (rightCat.key == "adult") {
                                                pendingCategory = rightCat.key
                                                showPasswordPrompt = true
                                            } else {
                                                selectedOnlineCategory = rightCat.key
                                            }
                                        }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    val categoryInfo = onlineCategories.find { it.key == selectedOnlineCategory }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { selectedOnlineCategory = null },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.Black)
                                Text("Lobilere Dön", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "${categoryInfo?.name ?: ""} LOBİSİ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktif oyuncular listesinden rakip seçin.",
                        fontSize = 13.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    val lobbyUsers = onlineUsers.filter {
                        it.status == "searching:$selectedOnlineCategory"
                    }

                    if (lobbyUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.Black.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Aktif oyuncu aranıyor...",
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(lobbyUsers) { targetUser ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                repository.createSession(
                                                    user1Id = user.uid,
                                                    user1Name = user.displayName,
                                                    user1Gender = user.gender,
                                                    user2Id = targetUser.userId,
                                                    user2Name = targetUser.userName,
                                                    user2Gender = targetUser.gender,
                                                    category = selectedOnlineCategory ?: ""
                                                )
                                            }
                                        }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), RoundedCornerShape(50))
                                            ) {
                                                Text(
                                                    text = targetUser.userName.take(1).uppercase(),
                                                    color = Color(0xFF8B5CF6),
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = targetUser.userName,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = "Lobide • Hazır",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Oyna",
                                            tint = Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showPasswordPrompt) {
                    AlertDialog(
                        onDismissRequest = {
                            showPasswordPrompt = false
                            inputPassword = ""
                            passwordError = false
                        },
                        title = { Text("Adult İçerik Doğrulaması", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Bu kategori +18 içerik barındırmaktadır. Devam etmek için şifreyi giriniz.")
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = inputPassword,
                                    onValueChange = { inputPassword = it },
                                    placeholder = { Text("Şifre") },
                                    singleLine = true,
                                    isError = passwordError,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (passwordError) {
                                    Text("Hatalı şifre! Lütfen tekrar deneyin.", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                onClick = {
                                    if (inputPassword == "adult") {
                                        showPasswordPrompt = false
                                        selectedOnlineCategory = pendingCategory
                                        inputPassword = ""
                                        passwordError = false
                                    } else {
                                        passwordError = true
                                    }
                                }
                            ) {
                                Text("GİRİŞ")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showPasswordPrompt = false
                                inputPassword = ""
                                passwordError = false
                            }) {
                                Text("İPTAL", color = Color.Black)
                            }
                        }
                    )
                }
            }
        } else {
            // Real-Time Active Session View
            val session = activeSession!!
            val isUser1 = session.user1Id == user.uid
            
            // Check for opponent disconnect/stale heartbeats
            val heartbeatDiff = System.currentTimeMillis() - session.lastHeartbeat
            if (heartbeatDiff > 25_000 && session.user1Id != "offline_demo_user") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Bağlantı Kesildi",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Rakip oyuncu çevrimdışı oldu. Lobiye geri dönülüyor...",
                            textAlign = TextAlign.Center,
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { coroutineScope.launch { repository.deleteSession(session.id) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("TAMAM")
                        }
                    }
                }
            } else {
                when (session.status) {
                    "category_selection" -> {
                        CategorySelectionView(
                            session = session,
                            isUser1 = isUser1,
                            onSubmit = { preferences ->
                                var updated = if (isUser1) {
                                    session.copy(user1Categories = preferences)
                                } else {
                                    session.copy(user2Categories = preferences)
                                }
                                if (updated.user1Categories.isNotEmpty() && updated.user2Categories.isNotEmpty()) {
                                    val cats = listOf("SPOR", "EĞLENCE", "BİLGİ", "GEZİ", "ADULT")
                                    var bestCat = "EĞLENCE"
                                    var maxPoints = -1
                                    for (cat in cats) {
                                        val idx1 = updated.user1Categories.indexOf(cat)
                                        val idx2 = updated.user2Categories.indexOf(cat)
                                        val p1 = if (idx1 != -1) (5 - idx1) else 0
                                        val p2 = if (idx2 != -1) (5 - idx2) else 0
                                        val totalPoints = p1 + p2
                                        if (totalPoints > maxPoints) {
                                            maxPoints = totalPoints
                                            bestCat = cat
                                        }
                                    }
                                    updated = updated.copy(
                                        commonCategory = bestCat,
                                        status = "game_selection",
                                        lastHeartbeat = System.currentTimeMillis()
                                    )
                                }
                                coroutineScope.launch {
                                    repository.updateSession(updated)
                                }
                            },
                            onCloseSession = {
                                coroutineScope.launch { repository.deleteSession(session.id) }
                            }
                        )
                    }
                    "game_selection" -> {
                        GameSelectionView(
                            session = session,
                            isUser1 = isUser1,
                            customGames = customGames,
                            onGameSelected = { gameId ->
                                val tasks = (Constants.TASKS + customTasks).filter { it.gameId == gameId }
                                val task = tasks.randomOrNull() ?: Constants.TASKS.first()
                                val updated = session.copy(
                                    selectedGameId = gameId,
                                    status = "playing",
                                    currentTurn = session.user2Id,
                                    activeCardCode = task.cardCode,
                                    activeTaskId = task.id,
                                    lastHeartbeat = System.currentTimeMillis()
                                )
                                coroutineScope.launch { repository.updateSession(updated) }
                            },
                            onTimeout = {
                                coroutineScope.launch {
                                     if (isUser1) {
                                         repository.banUser(user.uid, 120_000)
                                     }
                                    repository.deleteSession(session.id)
                                    Toast.makeText(context, "Zaman aşımı! Oyun seçilmedi.", Toast.LENGTH_LONG).show()
                                }
                            },
                            onCloseSession = {
                                coroutineScope.launch { repository.deleteSession(session.id) }
                            }
                        )
                    }
                    "playing" -> {
                        OnlineGameplayView(
                            session = session,
                            userId = user.uid,
                            customGames = customGames,
                            customTasks = customTasks,
                            repository = repository,
                            onCloseSession = {
                                coroutineScope.launch { repository.deleteSession(session.id) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionView(
    session: OnlineSession,
    isUser1: Boolean,
    onSubmit: (List<String>) -> Unit,
    onCloseSession: () -> Unit
) {
    val mySelected = remember { mutableStateListOf<String>() }
    val categories = remember { listOf("SPOR", "EĞLENCE", "BİLGİ", "GEZİ", "ADULT") }
    val hasSubmitted = if (isUser1) session.user1Categories.isNotEmpty() else session.user2Categories.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KATEGORİ TERCİHİ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Text(
                    text = "Öncelik sırasına göre seçin",
                    fontSize = 11.sp,
                    color = Color.Black.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onCloseSession,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("OYUNU BİTİR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (hasSubmitted) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Seçimleriniz gönderildi.\nDiğer oyuncunun seçimi bekleniyor...",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                categories.forEach { cat ->
                    val rank = mySelected.indexOf(cat)
                    val isSelected = rank != -1
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.Black else Color.White)
                            .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .clickable {
                                if (isSelected) {
                                    mySelected.remove(cat)
                                } else {
                                    mySelected.add(cat)
                                }
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.Black
                            )
                            if (isSelected) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White, RoundedCornerShape(50))
                                ) {
                                    Text(
                                        text = "+${5 - rank} P",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { if (mySelected.size == 5) onSubmit(mySelected.toList()) },
                enabled = mySelected.size == 5,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, disabledContainerColor = Color.Black.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("TERCİHLERİ GÖNDER", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun GameSelectionView(
    session: OnlineSession,
    isUser1: Boolean,
    customGames: List<Game>,
    onGameSelected: (String) -> Unit,
    onTimeout: () -> Unit,
    onCloseSession: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(30) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft -= 1
        }
        if (isUser1) {
            onTimeout()
        }
    }

    val alternatedGames = remember(session.commonCategory, customGames) {
        var categoryToUse = session.commonCategory
        var games = (Constants.GAMES + customGames).filter { it.category == categoryToUse }
        
        if (games.isEmpty()) {
            categoryToUse = "EĞLENCE"
            games = (Constants.GAMES + customGames).filter { it.category == categoryToUse }
        }
        
        val free = games.filter { it.isFree }
        val paid = games.filter { !it.isFree }
        
        val list = mutableListOf<Game>()
        val max = maxOf(free.size, paid.size)
        for (i in 0 until max) {
            if (i < free.size) list.add(free[i])
            if (i < paid.size) list.add(paid[i])
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OYUN SEÇİMİ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Kategori: ${session.commonCategory}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8B5CF6)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(50))
                ) {
                    Text(
                        text = timeLeft.toString(),
                        color = Color.Red,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
                Button(
                    onClick = onCloseSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("OYUNU BİTİR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isUser1) "Oyun seçmek için 30 saniyeniz var. Aksi takdirde 1 saat ceza alacaksınız." 
                   else "Oyuncu 1 oyun seçiyor... Lütfen bekleyin.",
            fontSize = 12.sp,
            color = Color.Black.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alternatedGames) { game ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .clickable(enabled = isUser1) { onGameSelected(game.id) }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = game.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = if (game.isFree) "Ücretsiz Oyun" else "Ücretli Oyun",
                                fontSize = 11.sp,
                                color = if (game.isFree) Color(0xFF10B981) else Color(0xFFF97316),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isUser1) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seç",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

data class Keypoint(val x: Float, val y: Float)

data class FaceData(
    val landmarks: List<Keypoint>,
    val imageWidth: Int,
    val imageHeight: Int
)

@Composable
fun OnlineGameplayView(
    session: OnlineSession,
    userId: String,
    customGames: List<Game>,
    customTasks: List<Task>,
    repository: DataRepository,
    onCloseSession: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isMyTurn = session.currentTurn == userId
    val activeGame = remember(session.selectedGameId, session.commonCategory, customGames) {
        (Constants.GAMES + customGames).find { it.id == session.selectedGameId } ?: if (session.commonCategory.isNotEmpty()) {
            Game(
                id = session.selectedGameId,
                name = session.commonCategory.uppercase(),
                category = session.commonCategory
            )
        } else null
    }
    val activeTask = remember(session.activeTaskId, customTasks) {
        (Constants.TASKS + customTasks).find { it.id == session.activeTaskId }
    }
    val currentTaskText = remember(session.activeTaskText, activeTask) {
        if (session.activeTaskText.isNotEmpty()) session.activeTaskText else (activeTask?.text ?: "")
    }

    var recordedUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    var showCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recordingTimeRemaining by remember { mutableStateOf(30) }
    var recordingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val latestFaceData = remember { java.util.concurrent.atomic.AtomicReference<FaceData?>(null) }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewState = remember { mutableStateOf<Preview?>(null) }
    val imageAnalysisState = remember { mutableStateOf<ImageAnalysis?>(null) }
    val faceLandmarkerState = remember { mutableStateOf<FaceLandmarker?>(null) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    DisposableEffect(context) {
        onDispose {
            try {
                cameraProviderState.value?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            analysisExecutor.shutdown()
        }
    }
    val latestRotation = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val lastDetectedTime = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val overlayEffectState = remember { mutableStateOf<androidx.camera.effects.OverlayEffect?>(null) }
    
    var sincapKapali by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sincapAcik by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var kurtKapali by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var kurtAcik by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isTalking by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("none") } // "none", "gozluk", "biyik", "tac", "sakal", "kulak"

    LaunchedEffect(showCamera) {
        if (!showCamera) {
            try {
                cameraProviderState.value?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(100)
            faceLandmarkerState.value?.close()
            faceLandmarkerState.value = null
            recordingJob?.cancel()
            recordingJob = null
            if (isRecording) {
                activeRecording?.stop()
                isRecording = false
            }
            return@LaunchedEffect
        }
        
        lastDetectedTime.set(System.currentTimeMillis())
        
        // Initialize FaceLandmarker with Delegate.GPU and CPU fallback
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .setDelegate(Delegate.GPU)
                    .build()
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, mpImage ->
                        val frameTime = System.currentTimeMillis()
                        val landmarks = result.faceLandmarks()
                        if (landmarks != null && landmarks.isNotEmpty()) {
                            lastDetectedTime.set(frameTime)
                            val firstFace = landmarks[0]
                            val rot = latestRotation.get()
                            val keypoints = firstFace.map { landmark ->
                                val xVal = landmark.x()
                                val yVal = landmark.y()
                                when (rot) {
                                    90 -> Keypoint(yVal, 1f - xVal)
                                    180 -> Keypoint(1f - xVal, 1f - yVal)
                                    270 -> Keypoint(1f - yVal, xVal)
                                    else -> Keypoint(xVal, yVal)
                                }
                            }
                            latestFaceData.set(FaceData(
                                landmarks = keypoints,
                                imageWidth = mpImage.width,
                                imageHeight = mpImage.height
                            ))
                        } else {
                            if (frameTime - lastDetectedTime.get() > 1000) {
                                latestFaceData.set(null)
                            }
                        }
                    }
                    .setErrorListener { error ->
                        error.printStackTrace()
                    }
                    .build()
                faceLandmarkerState.value = FaceLandmarker.createFromOptions(context, options)
            } catch (gpuException: Exception) {
                gpuException.printStackTrace()
                // Fallback to CPU
                try {
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .setDelegate(Delegate.CPU)
                        .build()
                    val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener { result, mpImage ->
                            val frameTime = System.currentTimeMillis()
                            val landmarks = result.faceLandmarks()
                            if (landmarks != null && landmarks.isNotEmpty()) {
                                lastDetectedTime.set(frameTime)
                                val firstFace = landmarks[0]
                                val rot = latestRotation.get()
                                val keypoints = firstFace.map { landmark ->
                                    val xVal = landmark.x()
                                    val yVal = landmark.y()
                                    when (rot) {
                                        90 -> Keypoint(yVal, 1f - xVal)
                                        180 -> Keypoint(1f - xVal, 1f - yVal)
                                        270 -> Keypoint(1f - yVal, xVal)
                                        else -> Keypoint(xVal, yVal)
                                    }
                                }
                                latestFaceData.set(FaceData(
                                    landmarks = keypoints,
                                    imageWidth = mpImage.width,
                                    imageHeight = mpImage.height
                                ))
                            } else {
                                if (frameTime - lastDetectedTime.get() > 1000) {
                                    latestFaceData.set(null)
                                }
                            }
                        }
                        .setErrorListener { error ->
                            error.printStackTrace()
                        }
                        .build()
                    faceLandmarkerState.value = FaceLandmarker.createFromOptions(context, options)
                } catch (cpuException: Exception) {
                    cpuException.printStackTrace()
                }
            }
        }

        // Load overlay animal bitmaps
        sincapKapali = loadAndCleanBitmap(context, "sincap_kapali")
        sincapAcik = loadAndCleanBitmap(context, "sincap_acik")
        kurtKapali = loadAndCleanBitmap(context, "kurt_kapali")
        kurtAcik = loadAndCleanBitmap(context, "kurt_acik")

        // Audio talk detection loop
        recordingJob = launch {
            try {
                val sampleRate = 16000
                val bufferSize = android.media.AudioRecord.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                if (bufferSize > 0) {
                    val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (permission == PackageManager.PERMISSION_GRANTED) {
                        val audioRecord = android.media.AudioRecord(
                            android.media.MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            android.media.AudioFormat.CHANNEL_IN_MONO,
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                        val buffer = ShortArray(bufferSize)
                        audioRecord.startRecording()
                        while (showCamera && isRecording) {
                            val read = audioRecord.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                var sum = 0.0
                                for (i in 0 until read) {
                                    sum += buffer[i] * buffer[i]
                                }
                                val rms = Math.sqrt(sum / read)
                                isTalking = rms > 800.0
                            }
                            delay(50)
                        }
                        audioRecord.stop()
                        audioRecord.release()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (activeGame == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Oyun yükleniyor...")
        }
        return
    }

    LaunchedEffect(session.activeCardCode, isMyTurn) {
        if (session.activeCardCode.isEmpty() && isMyTurn) {
            val isOnlineMatchmaking = session.commonCategory.isNotEmpty()
            if (isOnlineMatchmaking) {
                val currentTurnGender = if (session.currentTurn == session.user1Id) session.user1Gender else session.user2Gender
                val otherGender = if (session.currentTurn == session.user1Id) session.user2Gender else session.user1Gender
                val poolKey = when {
                    currentTurnGender == "Kadın" && otherGender == "Erkek" -> "kadinin_erkege"
                    currentTurnGender == "Erkek" && otherGender == "Kadın" -> "erkege_kadina"
                    currentTurnGender == "Kadın" && otherGender == "Kadın" -> "kadinin_kadina"
                    currentTurnGender == "Erkek" && otherGender == "Erkek" -> "erkege_erkege"
                    else -> "erkege_kadina"
                }
                val chosenText = when (session.commonCategory.lowercase()) {
                    "iliskiler" -> Constants.ONLINE_RELATION_TASKS[poolKey]?.randomOrNull() ?: "İlişkiler hakkında samimi bir itirafta bulun."
                    "adrenalin" -> Constants.ONLINE_ADRENALIN_TASKS[poolKey]?.randomOrNull() ?: "Adrenalin dolu bir anını anlat."
                    "bilgi" -> Constants.ONLINE_BILGI_TASKS[poolKey]?.randomOrNull() ?: "Genel kültür bilgini test et."
                    "aktuel" -> Constants.ONLINE_AKTUEL_TASKS[poolKey]?.randomOrNull() ?: "Aktüel bir konu hakkında yorum yap."
                    "hatiralar" -> Constants.ONLINE_HATIRALAR_TASKS[poolKey]?.randomOrNull() ?: "Unutamadığın bir hatıranı anlat."
                    "fanteziler" -> Constants.ONLINE_FANTEZILER_TASKS[poolKey]?.randomOrNull() ?: "Bir fantezini samimiyetle paylaş."
                    "adult" -> Constants.ONLINE_ADULT_TASKS[poolKey]?.randomOrNull() ?: "+18 cesur bir itirafta bulun."
                    "softhub" -> Constants.ONLINE_SOFTHUB_TASKS[poolKey]?.randomOrNull() ?: "Eğlenceli bir itirafta bulun."
                    else -> "Bir görev seçin."
                }
                repository.updateSession(
                    session.copy(
                        activeCardCode = "ON",
                        activeTaskId = "online-task-${java.util.UUID.randomUUID()}",
                        activeTaskText = chosenText,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                )
            } else {
                val tasks = (Constants.TASKS + customTasks).filter { it.gameId == activeGame.id }
                val task = tasks.randomOrNull() ?: Constants.TASKS.first()
                repository.updateSession(
                    session.copy(
                        activeCardCode = task.cardCode,
                        activeTaskId = task.id,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    if (session.activeCardCode.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeGame.name.uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Text(
                            text = "Sıra: ${if (isMyTurn) "Sizde" else "Rakipte"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMyTurn) Color(0xFF8B5CF6) else Color.Black.copy(alpha = 0.4f)
                        )
                    }
                    
                    IconButton(onClick = onCloseSession) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Exit", tint = Color.Black)
                    }
                }
                
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isMyTurn) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Black)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Kart çekiliyor...",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.4f)
                            )
                        }
                    } else {
                        Text(
                            text = "Rakibinizin kart çekmesi bekleniyor...",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    } else {
        // Card is drawn
        if (activeTask != null || session.activeTaskText.isNotEmpty()) {
            if (session.videoUrl.isEmpty()) {
                if (isMyTurn) {
                    if (showCamera) {
                        // Full Screen Camera View
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            val permissionLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestMultiplePermissions()
                            ) { }

                            LaunchedEffect(Unit) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            }

                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val provider = ProcessCameraProvider.getInstance(ctx)
                                    provider.addListener({
                                        val cameraProvider = provider.get()
                                        cameraProviderState.value = cameraProvider
                                        previewView.post {
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            previewState.value = preview

                                            val recorder = Recorder.Builder()
                                                .setQualitySelector(QualitySelector.from(Quality.SD))
                                                .build()
                                            val videoCapture = VideoCapture.withOutput(recorder)
                                            videoCaptureState.value = videoCapture

                                            val mainHandler = Handler(Looper.getMainLooper())
                                            val errorConsumer = androidx.core.util.Consumer<Throwable> { error ->
                                                error.printStackTrace()
                                            }
                                            val overlayEffect = androidx.camera.effects.OverlayEffect(
                                                androidx.camera.core.CameraEffect.PREVIEW or androidx.camera.core.CameraEffect.VIDEO_CAPTURE,
                                                0,
                                                mainHandler,
                                                errorConsumer
                                            )

                                            overlayEffect.setOnDrawListener { frame: androidx.camera.effects.Frame ->
                                                val canvas = frame.overlayCanvas
                                                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

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
                                                            val cx_crown = getX(10)
                                                            val cy_crown = getY(10) - eyeDistance * 0.35f
                                                            val cw = eyeDistance * 1.5f
                                                            val ch = eyeDistance * 0.9f
                                                            val crownPath = Path().apply {
                                                                moveTo(cx_crown - cw/2, cy_crown)
                                                                lineTo(cx_crown - cw/2, cy_crown - ch*0.6f)
                                                                lineTo(cx_crown - cw*0.25f, cy_crown - ch*0.3f)
                                                                lineTo(cx_crown, cy_crown - ch)
                                                                lineTo(cx_crown + cw*0.25f, cy_crown - ch*0.3f)
                                                                lineTo(cx_crown + cw/2, cy_crown - ch*0.6f)
                                                                lineTo(cx_crown + cw/2, cy_crown)
                                                                close()
                                                            }
                                                            canvas.drawPath(crownPath, crownPaint)
                                                            canvas.drawCircle(cx_crown, cy_crown - ch*0.85f, cw*0.06f, jewelPaint)
                                                            canvas.drawCircle(cx_crown - cw/2, cy_crown - ch*0.55f, cw*0.04f, jewelPaint)
                                                            canvas.drawCircle(cx_crown + cw/2, cy_crown - ch*0.55f, cw*0.04f, jewelPaint)
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
                                            overlayEffectState.value = overlayEffect

                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                                .setTargetResolution(android.util.Size(640, 480))
                                                .build()
                                            imageAnalysisState.value = imageAnalysis

                                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                val landmarker = faceLandmarkerState.value
                                                if (landmarker != null) {
                                                    try {
                                                        val rot = imageProxy.imageInfo.rotationDegrees
                                                        latestRotation.set(rot)
                                                        val bitmap = imageProxy.toBitmap()
                                                        val mpImage = BitmapImageBuilder(bitmap).build()
                                                        
                                                        val processingOptions = ImageProcessingOptions.builder()
                                                            .setRotationDegrees(rot)
                                                            .build()
                                                            
                                                        landmarker.detectAsync(
                                                            mpImage, 
                                                            processingOptions,
                                                            imageProxy.imageInfo.timestamp / 1_000_000
                                                        )
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    } finally {
                                                        imageProxy.close()
                                                    }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }

                                            try {
                                                cameraProvider.unbindAll()
                                                val useCaseGroup = UseCaseGroup.Builder()
                                                    .addUseCase(preview)
                                                    .addUseCase(videoCapture)
                                                    .addUseCase(imageAnalysis)
                                                    .addEffect(overlayEffect)
                                                    .build()

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

                            // Top overlay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(24.dp)
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isRecording) {
                                            recordingJob?.cancel()
                                            recordingJob = null
                                            activeRecording?.stop()
                                            isRecording = false
                                        }
                                        showCamera = false
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                ) {
                                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
                                }

                                if (isRecording) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.White, RoundedCornerShape(50))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "00:${if (recordingTimeRemaining < 10) "0" else ""}$recordingTimeRemaining",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Bottom overlay (translucent task card & controls)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(24.dp)
                                    .align(Alignment.BottomCenter),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = currentTaskText,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                 // Filter Selection Bar
                                 LazyRow(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.spacedBy(10.dp),
                                     contentPadding = PaddingValues(horizontal = 4.dp)
                                 ) {
                                     val filters = listOf(
                                         "none" to "❌ Filtresiz",
                                         "gozluk" to "🕶️ Gözlük",
                                         "biyik" to "👨 Bıyık",
                                         "tac" to "👑 Taç",
                                         "sakal" to "🧔 Sakal",
                                         "kulak" to "🐱 Kulak"
                                     )
                                     items(filters) { (id, label) ->
                                         val isSelected = activeFilter == id
                                         Box(
                                             modifier = Modifier
                                                 .clip(RoundedCornerShape(20.dp))
                                                 .background(if (isSelected) Color(0xFF8B5CF6) else Color.Black.copy(alpha = 0.5f))
                                                 .border(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                                 .clickable { activeFilter = id }
                                                 .padding(horizontal = 16.dp, vertical = 8.dp)
                                         ) {
                                             Text(
                                                 text = label,
                                                 color = Color.White,
                                                 fontSize = 13.sp,
                                                 fontWeight = FontWeight.Bold
                                             )
                                         }
                                     }
                                 }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {

                                    if (!isUploading) {
                                        Button(
                                            onClick = {
                                                val capture = videoCaptureState.value
                                                if (capture != null) {
                                                    if (isRecording) {
                                                        recordingJob?.cancel()
                                                        recordingJob = null
                                                        activeRecording?.stop()
                                                        isRecording = false
                                                    } else {
                                                        recordedUri = null
                                                        val file = File(context.cacheDir, "temp_online.mp4")
                                                        val outputOptions = FileOutputOptions.Builder(file).build()
                                                        val recording = capture.output
                                                            .prepareRecording(context, outputOptions)
                                                            .withAudioEnabled()
                                                            .start(ContextCompat.getMainExecutor(context)) { event ->
                                                                if (event is VideoRecordEvent.Finalize) {
                                                                    if (!event.hasError()) {
                                                                        recordedUri = Uri.fromFile(file)
                                                                    }
                                                                }
                                                            }
                                                        activeRecording = recording
                                                        isRecording = true
                                                        
                                                        recordingTimeRemaining = 30
                                                        recordingJob = coroutineScope.launch {
                                                            while (recordingTimeRemaining > 0 && isRecording) {
                                                                delay(1000)
                                                                recordingTimeRemaining--
                                                            }
                                                            if (isRecording) {
                                                                activeRecording?.stop()
                                                                isRecording = false
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color.White),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .height(56.dp)
                                                .weight(1f)
                                        ) {
                                            Text(
                                                text = if (isRecording) {
                                                    "KAYDI DURDUR ($recordingTimeRemaining)"
                                                } else if (recordedUri != null) {
                                                    "TEKRAR ÇEK"
                                                } else {
                                                    "KAYDA BAŞLA"
                                                },
                                                color = if (isRecording) Color.White else Color.Black,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    if (recordedUri != null && !isUploading) {
                                        Button(
                                            onClick = {
                                                isUploading = true
                                                coroutineScope.launch {
                                                    try {
                                                        val url = repository.uploadOnlineVideo(session.id, recordedUri!!)
                                                        repository.updateSession(
                                                            session.copy(
                                                                videoUrl = url,
                                                                videoSenderId = userId,
                                                                currentTurn = if (session.user1Id == userId) session.user2Id else session.user1Id,
                                                                lastHeartbeat = System.currentTimeMillis()
                                                            )
                                                        )
                                                        showCamera = false
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Yükleme Hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                    } finally {
                                                        isUploading = false
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .height(56.dp)
                                                .weight(1f)
                                        ) {
                                            Text("GÖNDER", fontWeight = FontWeight.Black)
                                        }
                                    }

                                    if (isUploading) {
                                        CircularProgressIndicator(color = Color.White)
                                    }
                                }
                                
                                if (recordedUri != null && !isUploading) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                saveLocallyRecordedVideo(
                                                    context = context,
                                                    recordedUri = recordedUri!!,
                                                    repository = repository,
                                                    gameName = activeGame.name,
                                                    gameId = activeGame.id,
                                                    taskId = activeTask?.id ?: session.activeTaskId,
                                                    taskText = currentTaskText
                                                )
                                                Toast.makeText(context, "Video Anılara kaydedildi!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height(56.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("İNDİR", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    } else {
                        // Static Drawn Card screen before opening camera
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .statusBarsPadding()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = activeGame.name.uppercase(),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Sıra: Sizde",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8B5CF6)
                                        )
                                    }
                                    Button(
                                         onClick = onCloseSession,
                                         colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                         shape = RoundedCornerShape(12.dp),
                                         contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                         modifier = Modifier.height(36.dp)
                                     ) {
                                         Text("OYUNU BİTİR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }
                                }

                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .aspectRatio(0.7f)
                                        .border(4.dp, Color.White, RoundedCornerShape(24.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = currentTaskText,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = { showCamera = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .padding(horizontal = 24.dp)
                                 ) {
                                     Text(
                                         text = "GÖREV VİDEOSU ÇEK",
                                         color = Color.White,
                                         fontWeight = FontWeight.Black,
                                         fontSize = 16.sp
                                     )
                                 }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .statusBarsPadding()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = activeGame.name.uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Sıra: Rakipte",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.4f)
                                    )
                                }
                                IconButton(onClick = onCloseSession) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Exit", tint = Color.Black)
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .aspectRatio(0.7f)
                                    .border(4.dp, Color.White, RoundedCornerShape(24.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = currentTaskText,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.Black)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Rakip görevi yapıyor. Video yüklenmesi bekleniyor...",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // Video is uploaded
                if (session.videoSenderId != userId) {
                    // Receiver plays the video ONCE in full screen
                    var videoFinished by remember(session.videoUrl) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        if (!videoFinished) {
                            androidx.compose.runtime.key(session.videoUrl) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoPath(session.videoUrl)
                                            setOnPreparedListener { start() }
                                            setOnCompletionListener {
                                                videoFinished = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Translucent Task card overlayed at the bottom while playing
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(24.dp)
                                    .align(Alignment.BottomCenter),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = currentTaskText,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        )
                                        Text(
                                            text = "UNMASK",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        } else {
                            // Video finished. Show download request options
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = "GÖSTERİM TAMAMLANDI",
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val videoUrlToDelete = session.videoUrl
                                            coroutineScope.launch {
                                                if (videoUrlToDelete.isNotEmpty()) {
                                                    repository.deleteOnlineVideo(videoUrlToDelete)
                                                }
                                                val tasks = (Constants.TASKS + customTasks).filter { it.gameId == activeGame.id }
                                                val task = tasks.randomOrNull() ?: Constants.TASKS.first()
                                                repository.updateSession(
                                                    session.copy(
                                                        activeCardCode = task.cardCode,
                                                        activeTaskId = task.id,
                                                        videoUrl = "",
                                                        downloadRequestStatus = "none",
                                                        currentTurn = userId,
                                                        lastHeartbeat = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6), contentColor = Color.White),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .height(56.dp)
                                            .fillMaxWidth(0.6f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Draw Card", tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("KART ÇEK", fontWeight = FontWeight.Black)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    when (session.downloadRequestStatus) {
                                        "none" -> {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.updateSession(session.copy(downloadRequestStatus = "requested"))
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Download, contentDescription = "Download")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("İNDİR", fontWeight = FontWeight.Black)
                                            }
                                        }
                                        "requested" -> {
                                            CircularProgressIndicator(color = Color.White)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("İzin bekleniyor...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                        "approved" -> {
                                            LaunchedEffect(Unit) {
                                                coroutineScope.launch {
                                                    downloadAndSaveVideo(
                                                        context = context,
                                                        videoUrl = session.videoUrl,
                                                        repository = repository,
                                                        gameName = activeGame.name,
                                                        gameId = activeGame.id,
                                                        taskId = activeTask?.id ?: session.activeTaskId,
                                                        taskText = currentTaskText
                                                    )
                                                    Toast.makeText(context, "Video indirildi ve Anılara eklendi!", Toast.LENGTH_LONG).show()
                                                    
                                                    val videoUrlToDelete = session.videoUrl
                                                    // Complete round immediately to clear UI
                                                    repository.updateSession(
                                                        session.copy(
                                                            activeCardCode = "",
                                                            activeTaskId = "",
                                                            videoUrl = "",
                                                            downloadRequestStatus = "none",
                                                            currentTurn = userId
                                                        )
                                                    )

                                                    // Delete from R2 bucket 30 seconds later in background
                                                    coroutineScope.launch {
                                                        delay(30_000)
                                                        if (videoUrlToDelete.isNotEmpty()) {
                                                            repository.deleteOnlineVideo(videoUrlToDelete)
                                                        }
                                                    }
                                                }
                                            }
                                            Text("Görüntü İndiriliyor...", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                        }
                                        "rejected" -> {
                                            Text("İndirme izni reddedildi.", fontWeight = FontWeight.Bold, color = Color.Red)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = onCloseSession,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Text("LOBİYE DÖN", fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Sender waits for request approval
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .statusBarsPadding()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = activeGame.name.uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Sıra: Rakipte",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.4f)
                                    )
                                }
                                IconButton(onClick = onCloseSession) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Exit", tint = Color.Black)
                                }
                            }

                            if (session.downloadRequestStatus == "requested") {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(20.dp))
                                        .padding(16.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "İNDİRME İZNİ TALEBİ",
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Karşı kullanıcı videoyu indirmek istiyor",
                                            fontSize = 12.sp,
                                            color = Color.Black.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.updateSession(session.copy(downloadRequestStatus = "rejected"))
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("REDDET")
                                            }
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.updateSession(session.copy(downloadRequestStatus = "approved"))
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("İZİN VER")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Video rakibe ulaştı. Aksiyon bekleniyor...",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Spacer to align layout nicely
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

// Background downloader and MediaStore saver helper
suspend fun downloadAndSaveVideo(
    context: Context,
    videoUrl: String,
    repository: DataRepository,
    gameName: String,
    gameId: String,
    taskId: String,
    taskText: String
) {
    withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(videoUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connect()
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val cardCode = if (taskId.contains("-")) taskId.substringAfterLast("-") else taskId
                val name = "unmask_${gameId}_${cardCode}_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(System.currentTimeMillis())
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UNMASK")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    // Register memory locally
                    repository.addMemory(
                        gameId = gameId,
                        gameName = gameName,
                        category = "EĞLENCE",
                        taskId = taskId,
                        taskText = taskText,
                        videoUri = uri,
                        isPublic = false
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun saveLocallyRecordedVideo(
    context: Context,
    recordedUri: Uri,
    repository: DataRepository,
    gameName: String,
    gameId: String,
    taskId: String,
    taskText: String
) {
    withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(recordedUri)
            if (inputStream != null) {
                val cardCode = if (taskId.contains("-")) taskId.substringAfterLast("-") else taskId
                val name = "unmask_${gameId}_${cardCode}_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(System.currentTimeMillis())
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UNMASK")
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    repository.addMemory(
                        gameId = gameId,
                        gameName = gameName,
                        category = "EĞLENCE",
                        taskId = taskId,
                        taskText = taskText,
                        videoUri = uri,
                        isPublic = false
                    )
                }
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun loadAndCleanBitmap(context: Context, name: String): android.graphics.Bitmap? {
    try {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId == 0) return null
        val original = android.graphics.BitmapFactory.decodeResource(context.resources, resId) ?: return null
        val width = original.width
        val height = original.height
        val mutable = original.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
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
            visited[(width - 1) + y * width] = true
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
        return mutable
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

data class OnlineCategoryInfo(
    val name: String,
    val key: String,
    val color: Color,
    val iconResId: Int
)

@Composable
fun OnlineCategoryCard(
    category: OnlineCategoryInfo,
    playerCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isLocked = category.key == "adult"
    
    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(24.dp))
            .background(category.color)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = category.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = if (category.key == "bilgi") Color.Black else Color.White,
                    textAlign = TextAlign.Center
                )
                if (isLocked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Text(
                    text = playerCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
