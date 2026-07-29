package com.example.unmask.features.lobby

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
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

    var activeToastRequest by remember { mutableStateOf<com.example.unmask.data.DirectGameRequest?>(null) }
    var lastHandledRequestId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingRequests) {
        val latest = incomingRequests.firstOrNull()
        if (latest != null && latest.id != lastHandledRequestId && latest.status == "pending") {
            lastHandledRequestId = latest.id
            activeToastRequest = latest
            kotlinx.coroutines.delay(3000L)
            if (activeToastRequest?.id == latest.id) {
                activeToastRequest = null
            }
        }
    }

    LaunchedEffect(activeSession) {
        if (activeSession != null) {
            activeTab = "oyun"
        }
    }

    // 🟢 GLOBAL APP-WIDE ONLINE PRESENCE (Uygulama açık olduğu sürece online kalır)
    val userProfile = currentUser
    LaunchedEffect(userProfile?.uid) {
        val uid = userProfile?.uid ?: return@LaunchedEffect
        val name = userProfile.nickname?.takeIf { it.isNotBlank() } ?: userProfile.displayName
        val gender = userProfile.gender
        while (true) {
            val banUntil = userProfile.banUntil ?: 0L
            val now = System.currentTimeMillis()
            if (banUntil <= now) {
                repository.updatePresence(uid, name, status = "app_open", gender = gender)
            } else {
                repository.updatePresence(uid, name, status = "offline", banUntil = banUntil, gender = gender)
            }
            kotlinx.coroutines.delay(5000L) // 5 saniyede bir heartbeat
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

    var selectedLobbyCategory by remember { mutableStateOf<String?>(null) }

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
                        repository = repository
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
                }

                // 🔔 3 Saniye Sonra Otomatik Kapanan Oyun İsteği Popup Bildirimi
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeToastRequest != null,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    val req = activeToastRequest
                    if (req != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .clickable {
                                    activeTab = "gecmis"
                                    activeToastRequest = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gamepad,
                                        contentDescription = "Game Request",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "YENİ OYUN İSTEĞİ! 🎮",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${req.senderNickname} size oyun isteği gönderdi. (Tıkla ve Geçmiş'e git)",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.9f)
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
