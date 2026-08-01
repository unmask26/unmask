package com.example.unmask.features.lobby

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.example.unmask.data.Game
import com.example.unmask.data.UserProfile

@Composable
fun MainAppScreen(
    repository: DataRepository,
    onNavigateToQR: (String?) -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val customGames by repository.customGames.collectAsState(initial = emptyList())
    var activeTab by remember { mutableStateOf("oyun") }
    var activeGame by remember { mutableStateOf<Game?>(null) }
    var selectedLobbyCategory by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Sync activeTab with global GameScreenTracker
    LaunchedEffect(activeTab) {
        com.example.unmask.core.GameScreenTracker.isGameTabSelected = (activeTab == "oyun")
    }

    // Check intent for navigate_to extra (e.g. when user clicks notification)
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val target = activity?.intent?.getStringExtra("navigate_to")
        if (target == "oyun") {
            activeTab = "oyun"
            activity.intent?.removeExtra("navigate_to")
        }
        onDispose {}
    }

    LaunchedEffect(Unit) {
        repository.getPublicVideos().collect { videos ->
            videos.forEach { video ->
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.example.unmask.data.VideoCacheManager.prefetchVideo(context, video.videoUrl)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Session states
    val onStartSession: (Game) -> Unit = { game ->
        activeGame = game
    }
    val onEndSession: () -> Unit = {
        activeGame = null
    }

    val userId = currentUser?.uid
    val incomingRequests by remember(userId) {
        if (userId != null) repository.observeIncomingGameRequests(userId) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val activeSession by remember(userId) {
        if (userId != null) repository.observeActiveSession(userId) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    // Trigger notification when opponent sends a video while user is in another tab (not "oyun")
    var lastNotifiedVideoUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeSession?.videoUrl, activeTab) {
        val session = activeSession
        val videoUrl = session?.videoUrl
        if (session != null && !videoUrl.isNullOrEmpty() && session.videoSenderId != userId) {
            if (videoUrl != lastNotifiedVideoUrl) {
                lastNotifiedVideoUrl = videoUrl
                if (activeTab != "oyun") {
                    val senderName = if (session.user1Id == userId) session.user2Name else session.user1Name
                    com.example.unmask.core.GameNotificationManager.showVideoReceivedNotification(
                        context = context,
                        senderNickname = senderName,
                        notificationIdKey = "${session.id}_${videoUrl.hashCode()}"
                    )
                }
            }
        }
    }

    var activeToastRequest by remember { mutableStateOf<com.example.unmask.data.DirectGameRequest?>(null) }
    var lastHandledRequestId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingRequests) {
        val latest = incomingRequests.firstOrNull { it.status == "pending" }
        if (latest != null && latest.id != lastHandledRequestId) {
            lastHandledRequestId = latest.id
            activeToastRequest = latest
        }
    }

    var handledSessionId by remember { mutableStateOf<String?>(null) }
    var endedGameDialogSession by remember { mutableStateOf<com.example.unmask.data.OnlineSession?>(null) }

    LaunchedEffect(activeSession) {
        val s = activeSession
        if (s != null && s.status == "ended_by_user" && s.endedByUserId != userId) {
            endedGameDialogSession = s
        }
    }

    LaunchedEffect(activeSession?.id) {
        val s = activeSession
        val currentSessionId = s?.id
        if (currentSessionId != null && currentSessionId != handledSessionId) {
            handledSessionId = currentSessionId
            if (!s.commonCategory.isNullOrBlank()) {
                selectedLobbyCategory = s.commonCategory
            }
            activeTab = "oyun"
        } else if (currentSessionId == null) {
            handledSessionId = null
        }
    }

    // 🟢 GLOBAL APP-WIDE ONLINE PRESENCE (Dünya/Oyun harici sekmelerde idle varlığı korur)
    val userProfile = currentUser
    LaunchedEffect(userProfile?.uid, activeTab) {
        val uid = userProfile?.uid ?: return@LaunchedEffect
        val name = userProfile.nickname?.takeIf { it.isNotBlank() } ?: userProfile.displayName
        val gender = userProfile.gender
        // Dünya ve Oyun sekmeleri kendi lobi/oyun varlığını yönettikleri için çakışmayı önlüyoruz
        if (activeTab != "dunya" && activeTab != "oyun") {
            while (true) {
                val banUntil = userProfile.banUntil ?: 0L
                val now = System.currentTimeMillis()
                if (banUntil <= now) {
                    repository.updatePresence(uid, name, status = "idle", gender = gender)
                } else {
                    repository.updatePresence(uid, name, status = "offline", banUntil = banUntil, gender = gender)
                }
                kotlinx.coroutines.delay(5000L) // 5 saniyede bir heartbeat
            }
        }
    }

    DisposableEffect(userProfile?.uid) {
        val uid = userProfile?.uid
        onDispose {
            if (uid != null) {
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    repository.removePresence(uid)
                }
            }
        }
    }

    if (currentUser == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.Black)
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White
            ) {
                Spacer(Modifier.height(12.dp))
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "UNMASK",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUser?.displayName ?: "Oyuncu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                    Text(
                        text = currentUser?.gender ?: "Belirtilmemiş",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f)
                    )
                }
                HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))
                
                // Drawer Items
                NavigationDrawerItem(
                    label = { Text("Profil Ayarları", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Profil") },
                    selected = activeTab == "profil",
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        activeTab = "profil"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color.Black.copy(alpha = 0.05f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = Color.Black,
                        unselectedTextColor = Color.Black.copy(alpha = 0.6f),
                        selectedIconColor = Color.Black,
                        unselectedIconColor = Color.Black.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Oyun Yarat", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Oyun Yarat") },
                    selected = activeTab == "createGame",
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        activeTab = "createGame"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color.Black.copy(alpha = 0.05f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = Color.Black,
                        unselectedTextColor = Color.Black.copy(alpha = 0.6f),
                        selectedIconColor = Color.Black,
                        unselectedIconColor = Color.Black.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Satın Al 🛒", fontWeight = FontWeight.Bold, color = Color(0xFF10B981)) },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Satın Al", tint = Color(0xFF10B981)) },
                    selected = activeTab == "buyCard",
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        activeTab = "buyCard"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.1f),
                        unselectedContainerColor = Color.Transparent,
                        selectedTextColor = Color(0xFF10B981),
                        unselectedTextColor = Color(0xFF10B981),
                        selectedIconColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF10B981)
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Çıkış Yap", fontWeight = FontWeight.Bold, color = Color.Red) },
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Çıkış", tint = Color.Red) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { 
                            drawerState.close() 
                            onLogout()
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.Red,
                        unselectedIconColor = Color.Red
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                BottomNav(
                    activeTab = activeTab,
                    onTabChange = { tabId ->
                        if (tabId == "qr") {
                            onNavigateToQR(activeGame?.id)
                        } else {
                            activeTab = tabId
                        }
                    }
                )
            },
            containerColor = Color(0xFFF9F9F9)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (activeTab) {
                    "oyun" -> OyunScreen(
                        user = currentUser,
                        activeGame = activeGame,
                        customGames = customGames,
                        repository = repository,
                        initialOnlineCategory = selectedLobbyCategory,
                        onStartSession = onStartSession,
                        onEndSession = onEndSession,
                        onNavigateToQR = onNavigateToQR,
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        }
                    )
                    "gecmis" -> GecmisScreen(
                        repository = repository,
                        userId = currentUser!!.uid,
                        onNavigateToLobby = { catKey ->
                            selectedLobbyCategory = catKey
                            activeTab = "oyun"
                        }
                    )
                    "ani" -> AniScreen(
                        repository = repository,
                        userId = currentUser!!.uid
                    )
                    "dunya" -> DunyaFeedScreen(
                        repository = repository,
                        onNavigateToBuyCard = {
                            activeTab = "buyCard"
                        }
                    )
                    "profil" -> ProfileScreen(
                        repository = repository,
                        onProfileSaved = {
                            activeTab = "oyun"
                        }
                    )
                    "createGame" -> CreateGameScreen(
                        repository = repository,
                        onBack = {
                            activeTab = "oyun"
                        }
                    )
                    "buyCard" -> BuyCardScreen(
                        repository = repository,
                        onBack = {
                            activeTab = "oyun"
                        }
                    )
                }

                // 🔔 HANGİ SAYFADA OLURSA OLSUN TOP-LEVEL (AlertDialog) YENİ OYUN İSTEĞİ BİLDİRİMİ
                if (activeToastRequest != null) {
                    val req = activeToastRequest!!
                    AlertDialog(
                        onDismissRequest = {
                            activeToastRequest = null
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gamepad,
                                        contentDescription = "Game Request",
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "🎮 OYUN İSTEĞİ GELDİ!",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.Black
                                )
                            }
                        },
                        text = {
                            val catName = when (req.selectedCategory.lowercase()) {
                                "iliskiler" -> "İLİŞKİLER"
                                "adrenalin" -> "ADRENALİN"
                                "bilgi" -> "BİLGİ"
                                "aktuel" -> "AKTÜEL"
                                "hatiralar" -> "HATIRALAR"
                                "fanteziler" -> "FANTEZİLER"
                                "adult" -> "ADULT (+18)"
                                "softhub" -> "SOFTHUB"
                                else -> req.selectedCategory.takeIf { it.isNotBlank() }?.uppercase()
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "@${req.senderNickname.ifEmpty { "Rakip" }} sizinle oyun oynamak istiyor.",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                if (!catName.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "🎯 Önerilen Lobi: $catName",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF6D28D9)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val senderName = req.senderNickname.ifEmpty { "Oyuncu" }
                                        coroutineScope.launch {
                                            repository.banUser(senderName, req.id)
                                            Toast.makeText(context, "@$senderName engellendi ve Banlananlar kutusuna eklendi!", Toast.LENGTH_SHORT).show()
                                            activeToastRequest = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("BANLA 🚫", fontWeight = FontWeight.Black, color = Color.White, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        activeTab = "gecmis"
                                        activeToastRequest = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text("İSTEĞİ İNCELE & OYNA 🚀", fontWeight = FontWeight.Black, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    activeToastRequest = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("KAPAT / SONRA BAK", fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                // 🔴 GLOBAL RAKİP OYUNU BİTİRDİ BİLDİRİM PENCERESİ
                if (endedGameDialogSession != null) {
                    val s = endedGameDialogSession!!
                    val endedByName = s.endedByUserName.ifEmpty {
                        if (s.user1Id == s.endedByUserId) s.user1Name else s.user2Name
                    }.ifEmpty { "Rakip Oyuncu" }

                    AlertDialog(
                        onDismissRequest = {
                            val sessId = s.id
                            endedGameDialogSession = null
                            coroutineScope.launch {
                                try { repository.deleteSession(sessId) } catch (_: Exception) {}
                            }
                        },
                        title = {
                            Text(
                                text = "🔴 RAKİP OYUNU BİTİRDİ",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Color(0xFFEF4444)
                            )
                        },
                        text = {
                            Text(
                                text = "Rakibiniz (@$endedByName) 'OYUNU BİTİR' butonuna basarak oyunu sonlandırdı.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.8f)
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val sessId = s.id
                                    endedGameDialogSession = null
                                    coroutineScope.launch {
                                        try { repository.deleteSession(sessId) } catch (_: Exception) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ANLADIM / TAMAM", fontWeight = FontWeight.Black, color = Color.White)
                            }
                        },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNav(
    activeTab: String,
    onTabChange: (String) -> Unit
) {
    Surface(
        color = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column {
            Divider(color = Color.Black.copy(alpha = 0.08f), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    BottomTabItem("oyun", Icons.Default.Gamepad, "OYUN"),
                    BottomTabItem("gecmis", Icons.Default.Person, "ARKADAŞLAR"),
                    BottomTabItem("qr", Icons.Default.QrCodeScanner, "QR"),
                    BottomTabItem("ani", Icons.Default.Book, "ANI"),
                    BottomTabItem("dunya", Icons.Default.Public, "DÜNYA")
                )

                tabs.forEach { tab ->
                    val isSelected = activeTab == tab.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabChange(tab.id) }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.4f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

data class BottomTabItem(
    val id: String,
    val icon: ImageVector,
    val label: String
)
