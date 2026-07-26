package com.example.unmask.features.online

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
    val displayedName = remember(user) { user.nickname?.takeIf { it.isNotBlank() } ?: user.displayName }
    LaunchedEffect(activeSession, selectedOnlineCategory) {
        while (activeSession == null) {
            val now = System.currentTimeMillis()
            val banUntil = user.banUntil ?: 0L
            if (banUntil <= now) {
                val status = if (selectedOnlineCategory != null) "searching:$selectedOnlineCategory" else "idle"
                repository.updatePresence(user.uid, displayedName, status, gender = user.gender)
            } else {
                repository.updatePresence(user.uid, displayedName, "offline", banUntil, gender = user.gender)
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
                repository.updatePresence(user.uid, displayedName, "playing")
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
                                                    user1Name = displayedName,
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
                    val hasAdultPassword = !user.adultPassword.isNullOrBlank()
                    // Giriş engellendi mi? (isAdult=false VEYA şifre set edilmemiş)
                    val isBlocked = !user.isAdult || !hasAdultPassword

                    AlertDialog(
                        onDismissRequest = {
                            showPasswordPrompt = false
                            inputPassword = ""
                            passwordError = false
                        },
                        title = { Text("+18 İçerik Doğrulaması", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                if (!user.isAdult) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Bu kategori yalnızca 18 yaş üzeri kullanıcılara açıktır.\n\n" +
                                        "Profil ayarlarından doğum tarihinizi girerek yaş doğrulaması yapmanız gerekmektedir.",
                                        fontSize = 14.sp
                                    )
                                } else if (!hasAdultPassword) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Adult içeriğe erişmek için bir şifre oluşturmanız gerekmektedir.\n\n" +
                                        "Profil ayarlarından adult içerik şifrenizi belirleyiniz.",
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text("Bu kategori +18 içerik barındırmaktadır. Profilinizde kayıtlı şifrenizi giriniz.")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextField(
                                        value = inputPassword,
                                        onValueChange = { inputPassword = it },
                                        placeholder = { Text("Şifre") },
                                        singleLine = true,
                                        isError = passwordError,
                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (passwordError) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Hatalı şifre! Lütfen tekrar deneyin.", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (isBlocked) {
                                // Sadece kapat — giriş yok
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    onClick = {
                                        showPasswordPrompt = false
                                        inputPassword = ""
                                        passwordError = false
                                    }
                                ) { Text("TAMAM") }
                            } else {
                                // Şifre doğrulama
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    onClick = {
                                        if (inputPassword == user.adultPassword) {
                                            showPasswordPrompt = false
                                            selectedOnlineCategory = pendingCategory
                                            inputPassword = ""
                                            passwordError = false
                                        } else {
                                            passwordError = true
                                        }
                                    }
                                ) { Text("GİRİŞ") }
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
                                // Çevrimiçi lobi seçimlerinde cinsiyete göre görev havuzu kullan
                                val isOnlineMatchmaking = session.commonCategory.isNotEmpty()
                                val updated: OnlineSession
                                if (isOnlineMatchmaking) {
                                    // İlk sıra session.user2Id'de (oyun seçiminde currentTurn user2'ye geçer)
                                    val firstTurnUserId = session.user2Id
                                    val currentTurnGender = if (firstTurnUserId == session.user1Id) session.user1Gender else session.user2Gender
                                    val otherGender = if (firstTurnUserId == session.user1Id) session.user2Gender else session.user1Gender
                                    val poolKey = when {
                                        currentTurnGender == "Kadın" && otherGender == "Erkek" -> "kadinin_erkege"
                                        currentTurnGender == "Erkek" && otherGender == "Kadın" -> "erkege_kadina"
                                        currentTurnGender == "Kadın" && otherGender == "Kadın" -> "kadinin_kadina"
                                        currentTurnGender == "Erkek" && otherGender == "Erkek" -> "erkege_erkege"
                                        else -> "erkege_kadina"
                                    }
                                    val fullPool: List<String> = when (session.commonCategory.lowercase()) {
                                        "iliskiler"  -> Constants.ONLINE_RELATION_TASKS[poolKey] ?: emptyList()
                                        "adrenalin"  -> Constants.ONLINE_ADRENALIN_TASKS[poolKey] ?: emptyList()
                                        "bilgi"      -> Constants.ONLINE_BILGI_TASKS[poolKey] ?: emptyList()
                                        "aktuel"     -> Constants.ONLINE_AKTUEL_TASKS[poolKey] ?: emptyList()
                                        "hatiralar"  -> Constants.ONLINE_HATIRALAR_TASKS[poolKey] ?: emptyList()
                                        "fanteziler" -> Constants.ONLINE_FANTEZILER_TASKS[poolKey] ?: emptyList()
                                        "adult"      -> Constants.ONLINE_ADULT_TASKS[poolKey] ?: emptyList()
                                        "softhub"    -> Constants.ONLINE_SOFTHUB_TASKS[poolKey] ?: emptyList()
                                        else         -> emptyList()
                                    }
                                    val availablePool = fullPool.filter { it !in session.usedTaskTexts }
                                    val taskPool = if (availablePool.isEmpty()) fullPool else availablePool
                                    val chosenText = taskPool.randomOrNull() ?: "Bir görev seçin."
                                    val newUsedTexts = if (availablePool.isEmpty()) listOf(chosenText) else (session.usedTaskTexts + chosenText)
                                    updated = session.copy(
                                        selectedGameId = gameId,
                                        status = "playing",
                                        currentTurn = session.user2Id,
                                        activeCardCode = "ON",
                                        activeTaskId = "online-task-${java.util.UUID.randomUUID()}",
                                        activeTaskText = chosenText,
                                        usedTaskTexts = newUsedTexts,
                                        lastHeartbeat = System.currentTimeMillis()
                                    )
                                } else {
                                    val allTasks = (Constants.TASKS + customTasks).filter { it.gameId == gameId }
                                    val availableTasks = allTasks.filter { it.id !in session.usedTaskIds }
                                    val taskPool = if (availableTasks.isEmpty()) allTasks else availableTasks
                                    val task = taskPool.randomOrNull() ?: Constants.TASKS.first()
                                    val newUsedIds = if (availableTasks.isEmpty()) listOf(task.id) else (session.usedTaskIds + task.id)
                                    updated = session.copy(
                                        selectedGameId = gameId,
                                        status = "playing",
                                        currentTurn = session.user2Id,
                                        activeCardCode = task.cardCode,
                                        activeTaskId = task.id,
                                        usedTaskIds = newUsedIds,
                                        lastHeartbeat = System.currentTimeMillis()
                                    )
                                }
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
                    "finished" -> {
                        GameFinishedView(
                            session = session,
                            userId = user.uid,
                            userNickname = displayedName,
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

// Keypoint is defined in PartyMaskEngine.kt — do not duplicate here.
// Re-export via typealias for backward compat inside this file:
private typealias Keypoint = com.example.unmask.features.ar.Keypoint

/**
 * Face data produced by the MediaPipe result callback and consumed by the render thread.
 *
 * @param landmarks            Normalized MediaPipe landmarks [0,1] in display-upright space.
 * @param rawWidth             mpImage.width  — analysis buffer width  (sensor-aligned, NOT rotated).
 * @param rawHeight            mpImage.height — analysis buffer height (sensor-aligned, NOT rotated).
 * @param rotation             Clockwise degrees needed to make the analysis buffer upright.
 * @param analysisSensorToBuffer  Sensor → analysis-buffer matrix stored AS-IS (inverted per draw call).
 */
data class FaceData(
    val landmarks: List<com.example.unmask.features.ar.Keypoint>,
    val rawWidth: Int,
    val rawHeight: Int,
    val rotation: Int,
    val analysisSensorToBuffer: android.graphics.Matrix
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
    var isPublishedToWorld by remember { mutableStateOf(false) }
    var isPublishingToWorld by remember { mutableStateOf(false) }

    LaunchedEffect(recordedUri) {
        if (recordedUri == null) {
            isPublishedToWorld = false
        }
    }

    var showCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recordingTimeRemaining by remember { mutableStateOf(30) }
    var recordingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewState = remember { mutableStateOf<Preview?>(null) }

    val latestFaceData       = remember { java.util.concurrent.atomic.AtomicReference<FaceData?>(null) }
    val imageAnalysisState   = remember { mutableStateOf<ImageAnalysis?>(null) }
    val faceLandmarkerState  = remember { mutableStateOf<com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker?>(null) }
    val analysisExecutor     = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val latestRotation       = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val latestMatrix         = remember { java.util.concurrent.atomic.AtomicReference<android.graphics.Matrix?>(null) }
    val lastDetectedTime     = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val smoothedMetrics      = remember { com.example.unmask.features.ar.SmoothedFaceMetrics() }
    val lastFrameTimeNs      = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    var activeFilter           by remember { mutableStateOf("none") }
    var selectedCategoryFilter by remember { mutableStateOf<com.example.unmask.features.ar.MaskCategory?>(null) }
    var isFaceDetected         by remember { mutableStateOf(false) }

    val visibleMasks = remember(selectedCategoryFilter) {
        if (selectedCategoryFilter == null) com.example.unmask.features.ar.PartyMaskEngine.PARTY_MASKS
        else com.example.unmask.features.ar.PartyMaskEngine.PARTY_MASKS.filter { it.category == selectedCategoryFilter }
    }

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

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val landmarker = com.example.unmask.features.ar.PartyMaskEngine.createFaceLandmarker(context) { result, mpImage ->
                val frameTime = System.currentTimeMillis()
                val landmarks = result.faceLandmarks()
                if (landmarks != null && landmarks.isNotEmpty()) {
                    lastDetectedTime.set(frameTime)
                    val firstFace = landmarks[0]
                    val keypoints = firstFace.map { landmark -> com.example.unmask.features.ar.Keypoint(landmark.x(), landmark.y()) }
                    val rot = latestRotation.get()
                    val sensorToBuffer = android.graphics.Matrix(
                        latestMatrix.get() ?: android.graphics.Matrix()
                    )
                    latestFaceData.set(FaceData(
                        landmarks              = keypoints,
                        rawWidth               = mpImage.width,
                        rawHeight              = mpImage.height,
                        rotation               = rot,
                        analysisSensorToBuffer = sensorToBuffer
                    ))
                    isFaceDetected = true
                } else {
                    if (frameTime - lastDetectedTime.get() > 1000) {
                        latestFaceData.set(null)
                        isFaceDetected = false
                        smoothedMetrics.reset()
                    }
                }
            }
            faceLandmarkerState.value = landmarker
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
                val fullPool: List<String> = when (session.commonCategory.lowercase()) {
                    "iliskiler"  -> Constants.ONLINE_RELATION_TASKS[poolKey] ?: emptyList()
                    "adrenalin"  -> Constants.ONLINE_ADRENALIN_TASKS[poolKey] ?: emptyList()
                    "bilgi"      -> Constants.ONLINE_BILGI_TASKS[poolKey] ?: emptyList()
                    "aktuel"     -> Constants.ONLINE_AKTUEL_TASKS[poolKey] ?: emptyList()
                    "hatiralar"  -> Constants.ONLINE_HATIRALAR_TASKS[poolKey] ?: emptyList()
                    "fanteziler" -> Constants.ONLINE_FANTEZILER_TASKS[poolKey] ?: emptyList()
                    "adult"      -> Constants.ONLINE_ADULT_TASKS[poolKey] ?: emptyList()
                    "softhub"    -> Constants.ONLINE_SOFTHUB_TASKS[poolKey] ?: emptyList()
                    else         -> emptyList()
                }
                val availablePool = fullPool.filter { it !in session.usedTaskTexts }
                val taskPool = if (availablePool.isEmpty()) fullPool else availablePool
                val chosenText = taskPool.randomOrNull() ?: "Bir görev seçin."
                val newUsedTexts = if (availablePool.isEmpty()) listOf(chosenText) else (session.usedTaskTexts + chosenText)
                repository.updateSession(
                    session.copy(
                        activeCardCode = "ON",
                        activeTaskId = "online-task-${java.util.UUID.randomUUID()}",
                        activeTaskText = chosenText,
                        usedTaskTexts = newUsedTexts,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                )
            } else {
                val allTasks = (Constants.TASKS + customTasks).filter { it.gameId == activeGame.id }
                val availableTasks = allTasks.filter { it.id !in session.usedTaskIds }
                val taskPool = if (availableTasks.isEmpty()) allTasks else availableTasks
                val task = taskPool.randomOrNull() ?: Constants.TASKS.first()
                val newUsedIds = if (availableTasks.isEmpty()) listOf(task.id) else (session.usedTaskIds + task.id)
                repository.updateSession(
                    session.copy(
                        activeCardCode = task.cardCode,
                        activeTaskId = task.id,
                        usedTaskIds = newUsedIds,
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
                                .pointerInput(visibleMasks, activeFilter) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onDragEnd = {
                                            val filterOptions = listOf("none") + visibleMasks.map { it.id }
                                            val currentIndex = filterOptions.indexOf(activeFilter)
                                            if (currentIndex != -1 && filterOptions.size > 1) {
                                                if (totalDrag > 100f) {
                                                    // Swipe Right (finger moves left-to-right): Next filter
                                                    val nextIndex = if (currentIndex >= filterOptions.size - 1) 0 else currentIndex + 1
                                                    activeFilter = filterOptions[nextIndex]
                                                } else if (totalDrag < -100f) {
                                                    // Swipe Left (finger moves right-to-left): Previous filter
                                                    val prevIndex = if (currentIndex <= 0) filterOptions.size - 1 else currentIndex - 1
                                                    activeFilter = filterOptions[prevIndex]
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                        }
                                    )
                                }
                        ) {
                            val permissionLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.RequestMultiplePermissions()
                            ) { }

                            LaunchedEffect(Unit) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            }

                            // Camera preview is always present
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
                                            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
                                                val filter = activeFilter
                                                val face   = latestFaceData.get()
                                                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

                                                if (face != null && filter != "none") {
                                                    com.example.unmask.features.ar.PartyMaskEngine.renderMask(
                                                        canvas    = canvas,
                                                        filterId  = filter,
                                                        face      = com.example.unmask.features.ar.FaceGeometryData(
                                                            landmarks              = face.landmarks,
                                                            rawWidth               = face.rawWidth,
                                                            rawHeight              = face.rawHeight,
                                                            rotation               = face.rotation,
                                                            analysisSensorToBuffer = face.analysisSensorToBuffer
                                                        ),
                                                        overlaySensorToCanvas = frame.sensorToBufferTransform,
                                                        metrics   = smoothedMetrics
                                                    )
                                                }
                                                true
                                            }

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
                                                        val now = System.nanoTime()
                                                        if (now - lastFrameTimeNs.get() >= com.example.unmask.features.ar.PartyMaskEngine.FRAME_INTERVAL_NS) {
                                                            lastFrameTimeNs.set(now)
                                                            val rot = imageProxy.imageInfo.rotationDegrees
                                                            latestRotation.set(rot)
                                                            latestMatrix.set(android.graphics.Matrix(imageProxy.imageInfo.sensorToBufferTransformMatrix))
                                                            val bitmap = imageProxy.toBitmap()
                                                            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
                                                            val imageProcessingOptions = com.google.mediapipe.tasks.vision.core.ImageProcessingOptions.builder()
                                                                .setRotationDegrees(rot)
                                                                .build()
                                                            landmarker.detectAsync(mpImage, imageProcessingOptions, imageProxy.imageInfo.timestamp / 1_000_000)
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

                                            try {
                                                cameraProvider.unbindAll()
                                                val useCaseGroup = androidx.camera.core.UseCaseGroup.Builder()
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

                            // Video playback overlay: shown on top of camera when video is recorded
                            if (recordedUri != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        val rootLayout = android.widget.FrameLayout(ctx).apply {
                                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                        val vv = android.widget.VideoView(ctx).apply {
                                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.view.Gravity.CENTER
                                            )
                                        }
                                        rootLayout.addView(vv)
                                        rootLayout
                                    },
                                    update = { root ->
                                        val vv = root.getChildAt(0) as android.widget.VideoView
                                        if (vv.tag != recordedUri) {
                                            vv.tag = recordedUri
                                            vv.setVideoURI(recordedUri)
                                            vv.setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                vv.start()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

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

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Face detection indicator
                                    Box(
                                        modifier = Modifier
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
                                                        val ts = System.currentTimeMillis()
                                                        val file = File(context.cacheDir, "temp_online_${ts}.mp4")
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
                                                        val uriSnapshot = recordedUri ?: return@launch
                                                        val url = repository.uploadOnlineVideo(session.id, uriSnapshot)
                                                        val newUser1Count = if (session.user1Id == userId) session.user1TaskCount + 1 else session.user1TaskCount
                                                        val newUser2Count = if (session.user2Id == userId) session.user2TaskCount + 1 else session.user2TaskCount
                                                        repository.updateSession(
                                                            session.copy(
                                                                videoUrl = url,
                                                                videoSenderId = userId,
                                                                currentTurn = if (session.user1Id == userId) session.user2Id else session.user1Id,
                                                                user1TaskCount = newUser1Count,
                                                                user2TaskCount = newUser2Count,
                                                                lastHeartbeat = System.currentTimeMillis()
                                                            )
                                                        )
                                                        // Safely tear down camera before leaving screen
                                                        try { cameraProviderState.value?.unbindAll() } catch (_: Exception) {}
                                                        recordedUri = null
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

                                // Adult kategorisinde dünyaya yayınlanamaz
                                val isAdultCategory = session.commonCategory.lowercase() == "adult"
                                if (recordedUri != null && !isUploading && !isAdultCategory) {
                                    Button(
                                        onClick = {
                                            isPublishingToWorld = true
                                            coroutineScope.launch {
                                                try {
                                                    val filterName = if (activeFilter == "none") "" else {
                                                        com.example.unmask.features.ar.PartyMaskEngine.PARTY_MASKS.find { it.id == activeFilter }?.name ?: activeFilter
                                                    }
                                                    repository.publishPublicVideo(
                                                        gameName = activeGame.name,
                                                        taskText = currentTaskText,
                                                        filterName = filterName,
                                                        videoUri = recordedUri!!
                                                    )
                                                    isPublishedToWorld = true
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Videonuz Dünya sekmesinde paylaşıldı!", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Paylaşım Hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    isPublishingToWorld = false
                                                }
                                            }
                                        },
                                        enabled = !isPublishedToWorld && !isPublishingToWorld,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF8B5CF6),
                                            disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                    ) {
                                        if (isPublishingToWorld) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                        } else {
                                            Text(
                                                text = if (isPublishedToWorld) "DÜNYAYA SUNULDU! 🌍" else "DÜNYAYA SUN 🌍",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
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
                    var localVideoPath by remember(session.videoUrl) { mutableStateOf<String?>(null) }
                    var isCaching by remember(session.videoUrl) { mutableStateOf(true) }

                    // Download video to local cache before playing to avoid buffering
                    LaunchedEffect(session.videoUrl) {
                        if (session.videoUrl.isNotEmpty()) {
                            isCaching = true
                            localVideoPath = null
                            try {
                                val cachedFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    // videoUrl'in hash'ini kullanarak her farklı video için benzersiz cache dosyası oluştur
                                    val urlHash = session.videoUrl.hashCode().let { if (it < 0) "n${-it}" else "$it" }
                                    val fileName = "online_recv_${urlHash}.mp4"
                                    val file = java.io.File(context.cacheDir, fileName)
                                    // Re-download only if file doesn't exist or is empty
                                    if (!file.exists() || file.length() == 0L) {
                                        val url = java.net.URL(session.videoUrl)
                                        url.openStream().use { input ->
                                            file.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                    file
                                }
                                localVideoPath = cachedFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // Fallback: stream directly if download fails
                                localVideoPath = session.videoUrl
                            } finally {
                                isCaching = false
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        if (isCaching) {
                            // Show loading screen while video is being cached
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Video yükleniyor...",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (!videoFinished && localVideoPath != null) {
                            androidx.compose.runtime.key(localVideoPath) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoPath(localVideoPath!!)
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Videoyu tekrar izleyebilir veya yeni kart çekebilirsin.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Tekrar İzle
                                    OutlinedButton(
                                        onClick = { videoFinished = false },
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .height(56.dp)
                                            .fillMaxWidth(0.6f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Tekrar İzle",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("TEKRAR İZLE", fontWeight = FontWeight.Black, color = Color.White)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    val totalTasksDone = session.user1TaskCount + session.user2TaskCount
                                    val maxTasks = 10
                                    if (totalTasksDone >= maxTasks) {
                                        Button(
                                            onClick = {
                                                val videoUrlToDelete = session.videoUrl
                                                coroutineScope.launch {
                                                    if (videoUrlToDelete.isNotEmpty()) repository.deleteOnlineVideo(videoUrlToDelete)
                                                    repository.updateSession(
                                                        session.copy(
                                                            status = "finished",
                                                            videoUrl = "",
                                                            downloadRequestStatus = "none",
                                                            lastHeartbeat = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                                        ) {
                                            Text("🏆 OYUNU BİTİR", fontWeight = FontWeight.Black)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val videoUrlToDelete = session.videoUrl
                                                coroutineScope.launch {
                                                    if (videoUrlToDelete.isNotEmpty()) {
                                                        repository.deleteOnlineVideo(videoUrlToDelete)
                                                    }
                                                    val isMatchmaking = session.commonCategory.isNotEmpty()
                                                    if (isMatchmaking) {
                                                        repository.updateSession(
                                                            session.copy(
                                                                activeCardCode = "",
                                                                activeTaskId = "",
                                                                activeTaskText = "",
                                                                videoUrl = "",
                                                                downloadRequestStatus = "none",
                                                                currentTurn = userId,
                                                                lastHeartbeat = System.currentTimeMillis()
                                                            )
                                                        )
                                                    } else {
                                                        val allTasks = (Constants.TASKS + customTasks).filter { it.gameId == activeGame.id }
                                                        val availableTasks = allTasks.filter { it.id !in session.usedTaskIds }
                                                        val taskPool = if (availableTasks.isEmpty()) allTasks else availableTasks
                                                        val task = taskPool.randomOrNull() ?: Constants.TASKS.first()
                                                        val newUsedIds = if (availableTasks.isEmpty()) listOf(task.id) else (session.usedTaskIds + task.id)
                                                        repository.updateSession(
                                                            session.copy(
                                                                activeCardCode = task.cardCode,
                                                                activeTaskId = task.id,
                                                                usedTaskIds = newUsedIds,
                                                                videoUrl = "",
                                                                downloadRequestStatus = "none",
                                                                currentTurn = userId,
                                                                lastHeartbeat = System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
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
                                            Text("KART ÇEK (${totalTasksDone / 2 + 1}/5)", fontWeight = FontWeight.Black)
                                        }
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

@Composable
fun GameFinishedView(
    session: OnlineSession,
    userId: String,
    userNickname: String,
    repository: DataRepository,
    onCloseSession: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isRequester = session.replayRequesterId == userId
    val isOpponentRequesting = session.replayRequestStatus == "requested" && !isRequester

    if (isOpponentRequesting) {
        AlertDialog(
            onDismissRequest = {
                coroutineScope.launch {
                    repository.updateSession(session.copy(replayRequestStatus = "rejected"))
                }
            },
            title = {
                Text(text = "Tekrar Oyun İsteği", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "${session.replayRequesterName} size tekrar oyun isteği gönderdi.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val isMatchmaking = session.commonCategory.isNotEmpty()
                            val nextStatus = if (isMatchmaking) "playing" else "game_selection"
                            repository.updateSession(
                                session.copy(
                                    status = nextStatus,
                                    user1TaskCount = 0,
                                    user2TaskCount = 0,
                                    activeCardCode = "",
                                    activeTaskId = "",
                                    activeTaskText = "",
                                    videoUrl = "",
                                    downloadRequestStatus = "none",
                                    usedTaskIds = emptyList(),
                                    usedTaskTexts = emptyList(),
                                    replayRequestStatus = "none",
                                    replayRequesterId = "",
                                    replayRequesterName = "",
                                    currentTurn = session.user2Id,
                                    lastHeartbeat = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("KABUL ET", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.updateSession(session.copy(replayRequestStatus = "rejected"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("REDDET", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Text(
                text = "🏆",
                fontSize = 64.sp
            )
            Text(
                text = "OYUN TAMAMLANDI!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Her iki oyuncu da 5 görevini başarıyla tamamladı.",
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = session.user1Name, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(text = "${session.user1TaskCount}/5 Görev", fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                    }
                    Divider(color = Color.Black.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = session.user2Name, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(text = "${session.user2TaskCount}/5 Görev", fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (session.replayRequestStatus == "requested" && isRequester) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tekrar oyun isteği gönderildi. Yanıt bekleniyor...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (session.replayRequestStatus == "rejected" && isRequester) {
                Text(
                    text = "Tekrar oyun isteği reddedildi.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.updateSession(
                                session.copy(
                                    replayRequestStatus = "requested",
                                    replayRequesterId = userId,
                                    replayRequesterName = userNickname
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Replay", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "TEKRAR OYNA", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }

            Button(
                onClick = onCloseSession,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "LOBİYE DÖN", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
    }
}
