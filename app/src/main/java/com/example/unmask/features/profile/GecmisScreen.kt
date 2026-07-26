package com.example.unmask.features.profile

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import androidx.compose.animation.*
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
import androidx.compose.material3.*
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
import com.example.unmask.data.OnlineHistoryItem
import com.example.unmask.data.OnlineUserPresence
import com.example.unmask.data.GameInvite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GecmisScreen(
    repository: DataRepository,
    userId: String
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val onlineHistory by repository.getOnlineHistory(userId).collectAsState(initial = emptyList())
    val onlineUsers by repository.getOnlineUsers(userId).collectAsState(initial = emptyList())
    val incomingInvites by repository.observeIncomingGameInvites(userId).collectAsState(initial = emptyList())

    var selectedOpponent by remember { mutableStateOf<OnlineHistoryItem?>(null) }
    var showInviteSentPopup by remember { mutableStateOf(false) }
    var inviteSentToName by remember { mutableStateOf("") }

    // Gelen istek seçildiğinde lobi seçimi
    var showLobbyPickerForInvite by remember { mutableStateOf<GameInvite?>(null) }

    // Popup 3 saniye sonra otomatik kapansın
    LaunchedEffect(showInviteSentPopup) {
        if (showInviteSentPopup) {
            delay(3000)
            showInviteSentPopup = false
        }
    }

    // Gelen davet popup'ı (kullanıcı başka oyun oynuyorsa bile çıkar)
    if (showInviteSentPopup) {
        AlertDialog(
            onDismissRequest = { showInviteSentPopup = false },
            title = {
                Text("✅ İstek Gönderildi", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "$inviteSentToName kullanıcısına oyun isteği gönderildi.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showInviteSentPopup = false }) {
                    Text("TAMAM", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Seçilen rakibin profil kartı
    if (selectedOpponent != null) {
        val opponent = selectedOpponent!!
        val opponentPresence = onlineUsers.find { it.userId == opponent.opponentId }
        val isOnline = opponentPresence != null && opponentPresence.status != "offline"

        AlertDialog(
            onDismissRequest = { selectedOpponent = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isOnline) Color(0xFF10B981) else Color.Red,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = opponent.opponentName,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isOnline && opponentPresence != null) {
                        val status = opponentPresence.status
                        val lobbyText = when {
                            status.startsWith("searching:") -> {
                                val cat = status.removePrefix("searching:")
                                "$cat lobisinde aranıyor"
                            }
                            status == "playing" -> "Oyun oynuyor"
                            else -> "Çevrimiçi"
                        }
                        Text(
                            text = "🟢 $lobbyText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    } else {
                        val lastSeen = if (opponentPresence != null && opponentPresence.lastActive > 0) {
                            val diff = System.currentTimeMillis() - opponentPresence.lastActive
                            val minutes = diff / 60000
                            val hours = minutes / 60
                            val days = hours / 24
                            when {
                                minutes < 1 -> "Az önce"
                                minutes < 60 -> "$minutes dk önce"
                                hours < 24 -> "$hours saat önce"
                                else -> "$days gün önce"
                            }
                        } else {
                            "Bilinmiyor"
                        }
                        Text(
                            text = "🔴 Çevrimdışı • Son görülme: $lastSeen",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "Son oyun: ${opponent.category.uppercase()}",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val opName = opponent.opponentName
                            val opId = opponent.opponentId
                            selectedOpponent = null
                            coroutineScope.launch {
                                repository.sendGameInvite(opId, opName)
                                inviteSentToName = opName
                                showInviteSentPopup = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OYUN İSTEĞİ GÖNDER", fontWeight = FontWeight.Black)
                    }

                    TextButton(
                        onClick = { selectedOpponent = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VAZGEÇ", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Gelen davet için lobi seçim dialogu
    if (showLobbyPickerForInvite != null) {
        val invite = showLobbyPickerForInvite!!
        val categories = listOf("İLİŞKİLER", "ADRENALİN", "BİLGİ", "AKTÜEL", "HATIRALAR", "FANTEZİLER", "SOFTHUB")
        val categoryKeys = listOf("iliskiler", "adrenalin", "bilgi", "aktuel", "hatiralar", "fanteziler", "softhub")

        AlertDialog(
            onDismissRequest = {
                coroutineScope.launch { repository.rejectGameInvite(invite.id) }
                showLobbyPickerForInvite = null
            },
            title = {
                Text("LOBİ SEÇ", fontWeight = FontWeight.Black, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${invite.fromUserName} ile oynayacağınız lobiyi seçin:",
                        fontSize = 13.sp,
                        color = Color.Black.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    categories.forEachIndexed { index, catName ->
                        Button(
                            onClick = {
                                val catKey = categoryKeys[index]
                                showLobbyPickerForInvite = null
                                coroutineScope.launch {
                                    repository.acceptGameInvite(invite.id, catKey)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(catName, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    coroutineScope.launch { repository.rejectGameInvite(invite.id) }
                    showLobbyPickerForInvite = null
                }) {
                    Text("REDDET", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
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
            Text(
                text = "GEÇMİŞ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Gelen İstekler Bölümü
            if (incomingInvites.isNotEmpty()) {
                Text(
                    text = "GELEN İSTEKLER (${incomingInvites.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                incomingInvites.forEach { invite ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(16.dp))
                            .clickable {
                                showLobbyPickerForInvite = invite
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = invite.fromUserName,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = "Size oyun isteği gönderdi",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Kabul Et",
                                tint = Color(0xFF8B5CF6)
                            )
                        }
                    }
                }

                Divider(
                    color = Color.Black.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Online Geçmiş Listesi
            if (onlineHistory.isEmpty() && incomingInvites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎮",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Henüz kimseyle online oyun oynamadınız.",
                            color = Color.Black.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (onlineHistory.isNotEmpty()) {
                Text(
                    text = "OYNADIĞIN KİŞİLER",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(onlineHistory) { historyItem ->
                        val opponentPresence = onlineUsers.find { it.userId == historyItem.opponentId }
                        val isOnline = opponentPresence != null && opponentPresence.status != "offline"

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedOpponent = historyItem
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Online/Offline Dot
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                if (isOnline) Color(0xFF10B981) else Color.Red,
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = historyItem.opponentName,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )

                                        if (isOnline && opponentPresence != null) {
                                            val status = opponentPresence.status
                                            val lobbyText = when {
                                                status.startsWith("searching:") -> {
                                                    val cat = status.removePrefix("searching:")
                                                    cat.uppercase() + " lobisinde"
                                                }
                                                status == "playing" -> "Oyun oynuyor"
                                                else -> "Çevrimiçi"
                                            }
                                            Text(
                                                text = lobbyText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        } else {
                                            val lastSeen = if (opponentPresence != null && opponentPresence.lastActive > 0) {
                                                val diff = System.currentTimeMillis() - opponentPresence.lastActive
                                                val minutes = diff / 60000
                                                val hours = minutes / 60
                                                val days = hours / 24
                                                when {
                                                    minutes < 1 -> "Az önce"
                                                    minutes < 60 -> "$minutes dk önce"
                                                    hours < 24 -> "$hours saat önce"
                                                    else -> "$days gün önce"
                                                }
                                            } else {
                                                "Bilinmiyor"
                                            }
                                            Text(
                                                text = "Son: $lastSeen",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black.copy(alpha = 0.35f)
                                            )
                                        }
                                    }
                                }

                                // Sağ taraf: Kategori badge
                                Text(
                                    text = historyItem.category.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF8B5CF6),
                                    modifier = Modifier
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
