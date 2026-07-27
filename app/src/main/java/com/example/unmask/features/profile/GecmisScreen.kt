package com.example.unmask.features.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    var selectedOpponentForRequest by remember { mutableStateOf<OnlineOpponentHistory?>(null) }
    var selectedRequestForLobby by remember { mutableStateOf<DirectGameRequest?>(null) }
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
                        text = "ONLINE GEÇMİŞ",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        text = "Son oynadığınız oyuncular ve oyun istekleri",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 📤 GÖNDERİLEN OYUN İSTEKLERİ KARTI
                if (sentRequests.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        text = "GÖNDERİLEN OYUN İSTEKLERİ (${sentRequests.size})",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }

                                sentRequests.forEach { req ->
                                    val isLobbySelected = req.status == "lobby_selected"
                                    val catName = categories.find { it.key == req.selectedCategory }?.name ?: req.selectedCategory.uppercase()

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
                                                    text = req.receiverNickname.ifEmpty { "Oyuncu" },
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 16.sp,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = if (isLobbySelected) "$catName LOBİSİNİ SEÇTİ! 🎯" else "İstek gönderildi (Lobi seçmesi bekleniyor...)",
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

                                        // 🎯 KARŞI TARAF LOBİ SEÇİNCE ÇIKAN "KABUL ET & OYUNA BAŞLA" İBARESİ
                                        if (isLobbySelected) {
                                            Button(
                                                onClick = {
                                                    isStartingSession = true
                                                    coroutineScope.launch {
                                                        try {
                                                            repository.launchSessionFromDirectRequest(req)
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
                                                        text = "KABUL ET & OYUNA BAŞLA ($catName LOBİSİ) 🚀",
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

                // 📩 GELEN OYUN İSTEKLERİ KARTI
                if (incomingRequests.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, Color(0xFF8B5CF6), RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        text = "GELEN OYUN İSTEKLERİ (${incomingRequests.size})",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }

                                incomingRequests.forEach { req ->
                                    val isLobbyChosenByMe = req.status == "lobby_selected"
                                    val chosenCatName = categories.find { it.key == req.selectedCategory }?.name ?: req.selectedCategory.uppercase()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF3E8FF))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = req.senderNickname.ifEmpty { "Oyuncu" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.Black
                                            )
                                            Text(
                                                text = if (isLobbyChosenByMe) "$chosenCatName lobisi seçildi (Başlatılması bekleniyor...)" else "Size oyun isteği gönderdi",
                                                fontSize = 11.sp,
                                                color = Color.Black.copy(alpha = 0.6f)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.rejectDirectGameRequest(req.id)
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Reddet",
                                                    tint = Color.Red
                                                )
                                            }

                                            Button(
                                                onClick = { selectedRequestForLobby = req },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (isLobbyChosenByMe) "LOBİ DEĞİŞTİR" else "LOBİ SEÇ",
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

                // 👤 GEÇMİŞTE OYNANAN OYUNCULARIN LİSTESİ
                if (onlineOpponents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Henüz online oynadığınız bir oyuncu yok.",
                                color = Color.Black.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(onlineOpponents) { opp ->
                        val presence = allPresences.find { it.userId == opp.opponentId }
                        val isOnline = presence != null && (System.currentTimeMillis() - presence.lastActive < 30_000)

                        val statusText = remember(presence, isOnline, opp.lastPlayedTimestamp) {
                            if (isOnline) {
                                val st = presence.status
                                when {
                                    st.startsWith("searching:") -> {
                                        val catKey = st.substringAfter("searching:")
                                        val catName = categories.find { it.key == catKey }?.name ?: catKey.uppercase()
                                        "$catName Lobisinde"
                                    }
                                    st == "playing" -> "Oyunda"
                                    else -> "Çevrimiçi"
                                }
                            } else {
                                formatLastSeenTime(presence?.lastActive ?: opp.lastPlayedTimestamp)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOpponentForRequest = opp }
                                .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Online indicator dot
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                                CircleShape
                                            )
                                    )

                                    Column {
                                        Text(
                                            text = opp.opponentName,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = statusText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOnline) Color(0xFF10B981) else Color.Black.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Oyun İsteği",
                                    tint = Color.Black.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 🎯 NICKNAME'E BASILINCA ÇIKAN OYUN İSTEĞİ GÖNDER KARTI
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
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isOnline) "🟢 Şu an online" else "🔴 ${formatLastSeenTime(presence?.lastActive ?: opp.lastPlayedTimestamp)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) Color(0xFF10B981) else Color.Black.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Bu kullanıcıya oyun davet kartı gönderebilirsiniz. Karşı taraf lobi seçtiğinde onay verip oyunu başlatabilirsiniz.",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val myProfile = currentUserProfile ?: return@Button
                            val myNickname = myProfile.nickname?.takeIf { it.isNotBlank() } ?: myProfile.displayName
                            val myGender = myProfile.gender
                            isSendingRequest = true
                            coroutineScope.launch {
                                try {
                                    repository.sendDirectGameRequest(
                                        senderId = userId,
                                        senderNickname = myNickname,
                                        senderGender = myGender,
                                        receiverId = opp.opponentId,
                                        receiverNickname = opp.opponentName,
                                        receiverGender = opp.opponentGender
                                    )
                                    Toast.makeText(context, "Oyun isteği ${opp.opponentName} kullanıcısına gönderildi!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "İstek gönderilemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSendingRequest = false
                                    selectedOpponentForRequest = null
                                }
                            }
                        },
                        enabled = !isSendingRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isSendingRequest) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("OYUN İSTEĞİ GÖNDER 🚀", fontWeight = FontWeight.Black)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { selectedOpponentForRequest = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("İPTAL", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 🎮 LOBİ SEÇ MODAL DIALOG
        if (selectedRequestForLobby != null) {
            val req = selectedRequestForLobby!!
            AlertDialog(
                onDismissRequest = { selectedRequestForLobby = null },
                title = {
                    Text("LOBİ SEÇİN", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.Black)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${req.senderNickname} ile hangi lobide oynamak istersiniz?",
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cat.color),
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                try {
                                                    repository.acceptDirectGameRequest(req, cat.key)
                                                    Toast.makeText(context, "${cat.name} lobisi seçildi! Seçtiğiniz lobiye yönlendiriliyorsunuz...", Toast.LENGTH_SHORT).show()
                                                    onNavigateToLobby(cat.key)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    Toast.makeText(context, "Lobi seçilemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    selectedRequestForLobby = null
                                                }
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { selectedRequestForLobby = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("KAPAT", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
