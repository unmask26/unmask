package com.example.unmask.features.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



import com.example.unmask.data.DataRepository
import com.example.unmask.data.DirectGameRequest
import com.example.unmask.data.OnlineOpponentHistory
import com.example.unmask.data.OnlineUserPresence
import com.example.unmask.features.online.OnlineCategoryInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatLastSeenTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Son görülme bilinmiyor"
    val diffMs = System.currentTimeMillis() - timestamp
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHour = diffMin / 60
    val diffDay = diffHour / 24

    return when {
        diffMin < 1 -> "En son az önce online"
        diffMin < 60 -> "En son ${diffMin}dk önce online"
        diffHour < 24 -> "En son ${diffHour}saat önce online"
        diffDay < 7 -> "En son ${diffDay}gün önce online"
        else -> {
            val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
            "Son görülme: ${sdf.format(Date(timestamp))}"
        }
    }
}

@Composable
fun GecmisScreen(
    repository: DataRepository,
    userId: String,
    onNavigateToLobby: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserProfile by repository.currentUser.collectAsState(initial = null)

    val onlineOpponents by repository.getOnlineHistoryOpponents(userId).collectAsState(initial = emptyList())
    val allPresences by repository.getAllUserPresences().collectAsState(initial = emptyList())
    val incomingRequests by repository.observeIncomingGameRequests(userId).collectAsState(initial = emptyList())
    val sentRequests by repository.observeSentGameRequests(userId).collectAsState(initial = emptyList())
    val activeSession by repository.observeActiveSession(userId).collectAsState(initial = null)
    val isUserInGame = activeSession != null && activeSession?.status != "finished"
    val followedUsers = currentUserProfile?.following ?: emptyList()

    var selectedOpponentForRequest by remember { mutableStateOf<OnlineOpponentHistory?>(null) }
    var selectedRequestForLobby by remember { mutableStateOf<DirectGameRequest?>(null) }
    var selectedFollowedUserForRequest by remember { mutableStateOf<String?>(null) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var isStartingSession by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            OnlineCategoryInfo("İLİŞKİLER", "iliskiler", Color(0xFFEC4899), 0),
            OnlineCategoryInfo("ADRENALİN", "adrenalin", Color(0xFFEAB308), 0),
            OnlineCategoryInfo("BİLGİ", "bilgi", Color(0xFF3B82F6), 0),
            OnlineCategoryInfo("AKTÜEL", "aktuel", Color(0xFF10B981), 0),
            OnlineCategoryInfo("HATIRALAR", "hatiralar", Color(0xFF8B5CF6), 0),
            OnlineCategoryInfo("FANTEZİLER", "fanteziler", Color(0xFFF97316), 0),
            OnlineCategoryInfo("ADULT (+18)", "adult", Color(0xFFDC2626), 0),
            OnlineCategoryInfo("SOFTHUB", "softhub", Color(0xFFF43F5E), 0)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ARKADAŞLAR",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        text = "İstekler, oyun geçmişiniz ve takip ettiğiniz kişiler",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                // ─── 📦 0. BOX: DAVET ET
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), clip = true)
                    .border(
                        BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4F46E5), // Indigo
                                    Color(0xFF7C3AED), // Purple
                                    Color(0xFFDB2777)  // Pinkish Red
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    var username by remember { mutableStateOf("") }
                    val context = LocalContext.current

                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Davet Et",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "OYUNA DAVET ET",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Kullanıcı adı ile anında istek gönder",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = {
                                Text(
                                    "Kullanıcı adı (ör: @nickname)",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            },
                            trailingIcon = {
                                if (username.isNotEmpty()) {
                                    IconButton(onClick = { username = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Temizle",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.8f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF4F46E5),
                                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            onClick = {
                                coroutineScope.launch {
                                    val senderNickname = currentUserProfile?.nickname ?: currentUserProfile?.displayName ?: "Oyuncu"
                                    val senderGender = currentUserProfile?.gender ?: "Erkek"
                                    try {
                                        repository.sendDirectGameRequest(
                                            senderId = userId,
                                            senderNickname = senderNickname,
                                            senderGender = senderGender,
                                            receiverId = "",
                                            receiverNickname = username.trim().removePrefix("@"),
                                            receiverGender = "Erkek",
                                            selectedCategory = ""
                                        )
                                        Toast.makeText(context, "DAVET @${username.trim().removePrefix("@")} kullanıcısına gönderildi!", Toast.LENGTH_SHORT).show()
                                        username = ""
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = username.isNotBlank()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DAVET GÖNDER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── 📦 1. BOX (EN ÜST): SİZE DAVET GÖNDEREN KULLANICILAR (GELEN İSTEKLER) ──────────
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF8B5CF6), CircleShape)
                                )
                                Text(
                                    text = "1. SİZE DAVET GÖNDEREN OYUNCULAR (${incomingRequests.size})",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            if (incomingRequests.isEmpty()) {
                                Text(
                                    text = "Henüz tarafınıza gönderilmiş gelen bir oyun daveti yok.",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                incomingRequests.forEach { req ->
                                    val isLobbyChosenByMe = req.status == "lobby_selected"
                                    val senderCatName = categories.find { it.key == req.selectedCategory }?.name ?: req.selectedCategory.takeIf { it.isNotBlank() }?.uppercase()
                                    val senderName = req.senderNickname.ifEmpty { "Oyuncu" }
                                    val cardMessageText = if (!senderCatName.isNullOrBlank()) {
                                        "@$senderName size $senderCatName lobisinde oyun daveti gönderdi 🎮"
                                    } else {
                                        "@$senderName size oyun daveti gönderdi 🎮"
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFF3E8FF))
                                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = cardMessageText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.Black,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.banUser(senderName, req.id)
                                                        Toast.makeText(context, "@$senderName engellendi ve Banlananlar kutusuna eklendi!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                                border = BorderStroke(1.dp, Color.Red),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("BANLA 🚫", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.rejectDirectGameRequest(req.id)
                                                    }
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("REDDET ✕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { selectedRequestForLobby = req },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                            ) {
                                                Text(
                                                    text = if (isLobbyChosenByMe) "LOBİ DEĞİŞTİR" else "KABUL ET & OYNA 🚀",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 📦 2. BOX: GÖNDERDİĞİNİZ OYUN İSTEKLERİ ───────────────────────────
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFF10B981), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    text = "2. GÖNDERDİĞİNİZ OYUN İSTEKLERİ (${sentRequests.size})",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            if (sentRequests.isEmpty()) {
                                Text(
                                    text = "Henüz bir oyuncuya gönderdiğiniz aktif davet bulunmuyor.",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                sentRequests.forEach { req ->
                                    val isLobbySelected = req.status == "lobby_selected" || req.status == "playing"
                                    val catName = categories.find { it.key == req.selectedCategory }?.name ?: req.selectedCategory.takeIf { it.isNotBlank() }?.uppercase()

                                    val subtitleText = when {
                                        isLobbySelected && !catName.isNullOrBlank() -> "🎯 Rakibin Seçtiği Lobi: $catName (Oyun Başlıyor...)"
                                        !catName.isNullOrBlank() -> "Önerilen Lobi: $catName 🎯 (Yanıt bekleniyor...)"
                                        else -> "Davet Gönderildi (Yanıt bekleniyor...)"
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isLobbySelected) Color(0xFFD1FAE5) else Color(0xFFF3F4F6))
                                            .border(1.dp, if (isLobbySelected) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(14.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "@${req.receiverNickname.ifEmpty { "Oyuncu" }}",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 15.sp,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = subtitleText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLobbySelected) Color(0xFF047857) else Color.Black.copy(alpha = 0.5f)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.cancelSentGameRequest(req.id)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "İptal Et",
                                                    tint = Color.Red.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        if (isLobbySelected) {
                                            Button(
                                                onClick = {
                                                    isStartingSession = true
                                                    coroutineScope.launch {
                                                        try {
                                                            repository.launchSessionFromDirectRequest(req)
                                                            onNavigateToLobby(req.selectedCategory)
                                                            Toast.makeText(context, "$catName lobisinde oyun başlatılıyor!", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            Toast.makeText(context, "Oyun başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isStartingSession = false
                                                        }
                                                    }
                                                },
                                                enabled = !isStartingSession,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                            ) {
                                                if (isStartingSession) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                                } else {
                                                    Text(
                                                        text = "KABUL ET & OYUNA BAŞLA ($catName) 🚀",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 📦 3. BOX: DAHA ÖNCE OYNADIĞIN OYUNCULAR ────────────────────────────
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF3B82F6), CircleShape)
                                )
                                Text(
                                    text = "3. DAHA ÖNCE OYNADIĞIN OYUNCULAR (${onlineOpponents.size})",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            if (onlineOpponents.isEmpty()) {
                                Text(
                                    text = "Henüz online oyun oynadığınız bir rakip bulunmuyor.",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                onlineOpponents.forEach { opp ->
                                    val presence = allPresences.find { it.userId == opp.opponentId }
                                    val isOnline = presence != null && (System.currentTimeMillis() - presence.lastActive < 30_000)
                                    val isCurrentOpponent = activeSession != null && activeSession?.status != "finished" &&
                                            (activeSession?.user1Id == opp.opponentId || activeSession?.user2Id == opp.opponentId)

                                    val statusText = remember(presence, isOnline, opp.lastPlayedTimestamp, isCurrentOpponent) {
                                        if (isCurrentOpponent) {
                                            "Şu an aktif oyununuz devam ediyor 🎮"
                                        } else if (isOnline) {
                                            val st = presence!!.status
                                            when {
                                                st.startsWith("searching:") -> {
                                                    val catKey = st.substringAfter("searching:")
                                                    if (catKey == "adult") {
                                                        "Çevrimiçi"
                                                    } else {
                                                        val catName = categories.find { it.key == catKey }?.name ?: catKey.uppercase()
                                                        "$catName Lobisinde"
                                                    }
                                                }
                                                st == "playing" -> "Oyunda"
                                                else -> "Çevrimiçi"
                                            }
                                        } else {
                                            formatLastSeenTime(presence?.lastActive ?: opp.lastPlayedTimestamp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isCurrentOpponent) Color(0xFFD1FAE5) else if (isOnline) Color(0xFFECFDF5) else Color(0xFFEFF6FF))
                                            .clickable { selectedOpponentForRequest = opp }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val oppAge = presence?.age ?: opp.opponentAge
                                            val ageColor = com.example.unmask.data.AgeUtils.getCheckmarkColor(oppAge)
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(ageColor.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = opp.opponentName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 15.sp,
                                                    color = ageColor
                                                )
                                            }
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = opp.opponentName,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 15.sp,
                                                        color = Color.Black
                                                    )
                                                    if (isOnline && !isCurrentOpponent) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("ÇEVRİMİÇİ 🟢", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = statusText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrentOpponent || isOnline) Color(0xFF047857) else Color.Black.copy(alpha = 0.4f)
                                                )
                                            }
                                        }

                                        if (!isCurrentOpponent) {
                                            Button(
                                                onClick = {
                                                    if (isUserInGame) {
                                                        Toast.makeText(context, "⚠️ Halen aktif bir oyundasınız. Aynı anda sadece 1 kişi ile oyun oynayabilirsiniz!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        selectedOpponentForRequest = opp
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("DAVET ET 🎮", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 📦 4. BOX: TAKİP ETTİĞİN KİŞİLER (DÜNYA) ─────────────────────────
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF8B5CF6), CircleShape)
                                )
                                Text(
                                    text = "4. TAKİP ETTİĞİN KİŞİLER (DÜNYA) (${followedUsers.size})",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            if (followedUsers.isEmpty()) {
                                Text(
                                    text = "Dünya sekmesinden henüz kimseyi takip etmediniz.\nVideolardaki '+ TAKİP ET' butonuna basarak kişileri takip edebilirsiniz.",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                followedUsers.forEach { userName ->
                                    val presence = allPresences.find { it.userName.equals(userName, ignoreCase = true) }
                                    val isFollowedOnline = presence != null && (System.currentTimeMillis() - presence.lastActive < 30_000)
                                    val isCurrentFollowedOpponent = activeSession != null && activeSession?.status != "finished" &&
                                            ((presence != null && (activeSession?.user1Id == presence.userId || activeSession?.user2Id == presence.userId)) ||
                                            activeSession?.user1Name.equals(userName, ignoreCase = true) || activeSession?.user2Name.equals(userName, ignoreCase = true))

                                    val followedStatusText = remember(presence, isFollowedOnline, isCurrentFollowedOpponent) {
                                        if (isCurrentFollowedOpponent) {
                                            "Şu an aktif oyununuz devam ediyor 🎮"
                                        } else if (isFollowedOnline) {
                                            val st = presence!!.status
                                            when {
                                                st.startsWith("searching:") -> {
                                                    val catKey = st.substringAfter("searching:")
                                                    if (catKey == "adult") {
                                                        "Çevrimiçi"
                                                    } else {
                                                        val catName = categories.find { it.key == catKey }?.name ?: catKey.uppercase()
                                                        "$catName Lobisinde"
                                                    }
                                                }
                                                st == "playing" -> "Oyunda"
                                                else -> "Çevrimiçi"
                                            }
                                        } else {
                                            formatLastSeenTime(presence?.lastActive ?: 0L)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isCurrentFollowedOpponent) Color(0xFFD1FAE5) else if (isFollowedOnline) Color(0xFFECFDF5) else Color(0xFFF3E8FF))
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val followedAge = presence?.age ?: 22
                                            val ageColor = com.example.unmask.data.AgeUtils.getCheckmarkColor(followedAge)
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(ageColor.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = userName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 15.sp,
                                                    color = ageColor
                                                )
                                            }
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "@$userName",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 15.sp,
                                                        color = Color.Black
                                                    )
                                                    if (isFollowedOnline && !isCurrentFollowedOpponent) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("ÇEVRİMİÇİ 🟢", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = followedStatusText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrentFollowedOpponent || isFollowedOnline) Color(0xFF047857) else Color.Black.copy(alpha = 0.4f)
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (!isCurrentFollowedOpponent) {
                                                Button(
                                                    onClick = {
                                                        if (isUserInGame) {
                                                            Toast.makeText(context, "⚠️ Halen aktif bir oyundasınız. Aynı anda sadece 1 kişi ile oyun oynayabilirsiniz!", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            selectedFollowedUserForRequest = userName
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("DAVET 🎮", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.toggleFollowUser(userName)
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Takibi Bırak",
                                                    tint = Color.Red.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 📦 5. BOX (EN ALT): BANLANAN KULLANICILAR ─────────────────────────
                val bannedUsersList = currentUserProfile?.bannedUsers ?: emptyList()
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                )
                                Text(
                                    text = "5. BANLANAN KULLANICILAR (${bannedUsersList.size})",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }

                            if (bannedUsersList.isEmpty()) {
                                Text(
                                    text = "Henüz engellenmiş (banlanmış) kullanıcı bulunmuyor.",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                bannedUsersList.forEach { userName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFFEE2E2))
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@$userName",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = "Engellendi 🚫",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    repository.unbanUser(userName)
                                                    Toast.makeText(context, "@$userName kullanıcısının engeli kaldırıldı!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "BANI KALDIR 🔓",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 🎯 NICKNAME'E BASILINCA ÇIKAN OYUN İSTEĞİ GÖNDER KARTI (GEÇMİŞ OYUNCULAR İÇİN)
        if (selectedOpponentForRequest != null) {
            val opp = selectedOpponentForRequest!!
            val presence = allPresences.find { it.userId == opp.opponentId }
            val isOnline = presence != null && (System.currentTimeMillis() - presence.lastActive < 30_000)

            AlertDialog(
                onDismissRequest = { selectedOpponentForRequest = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                        )
                        Text(
                            text = opp.opponentName,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isOnline) "🟢 Oyuncu şu an çevrimiçi! Aşağıdan bir lobi seçerek doğrudan davet gönderebilirsiniz." else "🔴 Oyuncu şu an çevrimdışı, ancak davetiniz uygulamayı açtığında bildirilecek.",
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))

                        Text("Oyun Lobisi Seçin:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        categories.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { cat ->
                                    Button(
                                        onClick = {
                                            isSendingRequest = true
                                            coroutineScope.launch {
                                                try {
                                                    repository.sendDirectGameRequest(
                                                        senderId = userId,
                                                        senderNickname = currentUserProfile?.nickname?.ifEmpty { null } ?: currentUserProfile?.displayName ?: "Oyuncu",
                                                        senderGender = currentUserProfile?.gender ?: "Erkek",
                                                        receiverId = opp.opponentId,
                                                        receiverNickname = opp.opponentName,
                                                        receiverGender = "Erkek",
                                                        selectedCategory = cat.key
                                                    )
                                                    Toast.makeText(context, "${opp.opponentName} oyuncusuna ${cat.name} isteği gönderildi!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSendingRequest = false
                                                    selectedOpponentForRequest = null
                                                }
                                            }
                                        },
                                        enabled = !isSendingRequest,
                                        colors = ButtonDefaults.buttonColors(containerColor = cat.color),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedOpponentForRequest = null }) {
                        Text("KAPAT", fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.5f))
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 🎯 TAKİP EDİLEN KİŞİYE OYUN İSTEĞİ GÖNDERME DİYALOĞU
        if (selectedFollowedUserForRequest != null) {
            val targetName = selectedFollowedUserForRequest!!

            AlertDialog(
                onDismissRequest = { selectedFollowedUserForRequest = null },
                title = {
                    Text(
                        text = "@$targetName",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "@$targetName oyuncusunu bir lobiye davet etmek için aşağıdan kategori seçin:",
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))

                        categories.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { cat ->
                                    Button(
                                        onClick = {
                                            isSendingRequest = true
                                            coroutineScope.launch {
                                                try {
                                                    val presence = allPresences.find { it.userName.equals(targetName, ignoreCase = true) }
                                                    val targetUserId = presence?.userId?.ifEmpty { null } ?: targetName
                                                    val targetGender = presence?.gender ?: "Erkek"

                                                    repository.sendDirectGameRequest(
                                                        senderId = userId,
                                                        senderNickname = currentUserProfile?.nickname?.ifEmpty { null } ?: currentUserProfile?.displayName ?: "Oyuncu",
                                                        senderGender = currentUserProfile?.gender ?: "Erkek",
                                                        receiverId = targetUserId,
                                                        receiverNickname = targetName,
                                                        receiverGender = targetGender,
                                                        selectedCategory = cat.key
                                                    )
                                                    Toast.makeText(context, "$targetName kullanıcısına ${cat.name} daveti gönderildi!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isSendingRequest = false
                                                    selectedFollowedUserForRequest = null
                                                }
                                            }
                                        },
                                        enabled = !isSendingRequest,
                                        colors = ButtonDefaults.buttonColors(containerColor = cat.color),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedFollowedUserForRequest = null }) {
                        Text("KAPAT", fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.5f))
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 🎯 LOBİ SEÇME DİYALOĞU (GELEN İSTEK İÇİN)
        if (selectedRequestForLobby != null) {
            val req = selectedRequestForLobby!!

            val senderCatName = categories.find { it.key == req.selectedCategory }?.name ?: req.selectedCategory.takeIf { it.isNotBlank() }?.uppercase()

            AlertDialog(
                onDismissRequest = { selectedRequestForLobby = null },
                title = { Text("Lobi Seç ve Oyuna Başla", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!senderCatName.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "🎯 @${req.senderNickname} sizi '$senderCatName' lobisine davet etti.\nİster önerilen lobiyi kabul edin, ister başka bir lobi seçin!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D28D9)
                                )
                            }
                        } else {
                            Text("@${req.senderNickname} oyuncusu ile hangi lobide oynamak istersiniz?")
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        categories.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { cat ->
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    repository.acceptDirectGameRequest(req, cat.key)
                                                    onNavigateToLobby(cat.key)
                                                    Toast.makeText(context, "${cat.name} lobisinde oyun başlatılıyor! 🚀", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    selectedRequestForLobby = null
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = cat.color),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedRequestForLobby = null }) {
                        Text("İPTAL", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
