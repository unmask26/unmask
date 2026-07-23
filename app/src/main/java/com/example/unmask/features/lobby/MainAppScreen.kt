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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Face
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

    // Session states
    val onStartSession: (Game) -> Unit = { game ->
        activeGame = game
    }
    val onEndSession: () -> Unit = {
        activeGame = null
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
                        onStartSession = onStartSession,
                        onEndSession = onEndSession,
                        onNavigateToQR = onNavigateToQR,
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        }
                    )
                    "gecmis" -> GecmisScreen(
                        repository = repository,
                        userId = currentUser!!.uid
                    )
                    "ani" -> AniScreen(
                        repository = repository,
                        userId = currentUser!!.uid
                    )
                    "dunya" -> DunyaFeedScreen(
                        repository = repository
                    )
                    "test" -> TestScreen(
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
                    BottomTabItem("gecmis", Icons.Default.History, "GEÇMİŞ"),
                    BottomTabItem("qr", Icons.Default.QrCodeScanner, "QR"),
                    BottomTabItem("ani", Icons.Default.Book, "ANI"),
                    BottomTabItem("dunya", Icons.Default.Public, "DÜNYA"),
                    BottomTabItem("test", Icons.Default.Face, "TEST")
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
