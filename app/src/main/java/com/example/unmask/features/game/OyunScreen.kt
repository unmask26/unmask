package com.example.unmask.features.game

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.unmask.data.CategoryInfo
import com.example.unmask.data.Constants
import com.example.unmask.data.Game
import com.example.unmask.data.UserProfile

@Composable
fun OyunScreen(
    user: UserProfile?,
    activeGame: Game?,
    customGames: List<Game> = emptyList(),
    repository: com.example.unmask.data.DataRepository,
    initialOnlineCategory: String? = null,
    onStartSession: (Game) -> Unit,
    onEndSession: () -> Unit,
    onNavigateToQR: (String?) -> Unit,
    onMenuClick: () -> Unit
) {
    var showOnlineSection by remember(initialOnlineCategory) { mutableStateOf(initialOnlineCategory != null) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    var showPurchaseModal by remember { mutableStateOf<Game?>(null) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var pendingCategory by remember { mutableStateOf<String?>(null) }
    var inputPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val outerPadding = if (showOnlineSection) 0.dp else 16.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(outerPadding)
    ) {
        if (showOnlineSection) {
            DunyaScreen(
                repository = repository,
                initialCategory = initialOnlineCategory,
                onBack = { showOnlineSection = false }
            )
        } else if (selectedCategory != null) {
            // Games list inside selected Category
            val categoryName = selectedCategory!!
            val allGames = Constants.GAMES + customGames
            val filteredGames = allGames.filter { it.category == categoryName }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedCategory = null }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Back",
                            modifier = Modifier.size(28.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = categoryName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    if (activeGame != null) {
                        IconButton(onClick = onEndSession) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Active Session",
                                tint = Color(0xFFFACC15) // yellow-500
                            )
                        }
                    }
                }

                // Games List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredGames) { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                .clickable {
                                    if (game.isFree) {
                                        onStartSession(game)
                                        onNavigateToQR(game.id)
                                    } else {
                                        showPurchaseModal = game
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${game.name}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (game.isFree) "(ücretsiz)" else "(${game.price} TL)",
                                color = Color.Black.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Main Category Grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                modifier = Modifier.size(28.dp),
                                tint = Color.Black
                            )
                        }
                        Text(
                            text = "OYUN",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showOnlineSection = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.05f),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape)
                                )
                                Text(
                                    text = "ONLINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    if (activeGame != null) {
                        IconButton(onClick = onEndSession) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Active Session",
                                tint = Color(0xFFFACC15)
                            )
                        }
                    }
                }

                // Grid Layout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val categories = Constants.CATEGORIES
                    for (i in categories.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left item
                            val leftCat = categories[i]
                            CategoryCard(
                                category = leftCat,
                                user = user,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (leftCat.name == "ADULT") {
                                        pendingCategory = leftCat.name
                                        showPasswordPrompt = true
                                    } else {
                                        selectedCategory = leftCat.name
                                    }
                                }
                            )

                            // Right item (if exists)
                            if (i + 1 < categories.size) {
                                val rightCat = categories[i + 1]
                                CategoryCard(
                                    category = rightCat,
                                    user = user,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (rightCat.name == "ADULT") {
                                            pendingCategory = rightCat.name
                                            showPasswordPrompt = true
                                        } else {
                                            selectedCategory = rightCat.name
                                        }
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Notification Modal
        if (notificationMessage != null) {
            AlertDialog(
                onDismissRequest = { notificationMessage = null },
                title = { 
                    Text("BİLGİ", fontWeight = FontWeight.Black, color = Color.Black) 
                },
                text = { 
                    Text(notificationMessage!!, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.7f)) 
                },
                confirmButton = {
                    Button(
                        onClick = { notificationMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("TAMAM", fontWeight = FontWeight.Black)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Purchase Modal
        if (showPurchaseModal != null) {
            val game = showPurchaseModal!!
            AlertDialog(
                onDismissRequest = { showPurchaseModal = null },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = game.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "SATIN ALMA ONAYI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black.copy(alpha = 0.4f),
                            letterSpacing = 1.sp
                        )
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${game.price} TL",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
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
                                onStartSession(game)
                                showPurchaseModal = null
                                onNavigateToQR(game.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("SATIN AL VE BAŞLA", fontWeight = FontWeight.Black)
                        }
                        TextButton(
                            onClick = { showPurchaseModal = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "VAZGEÇ",
                                color = Color.Black.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Adult Password Verification Dialog
        if (showPasswordPrompt) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordPrompt = false
                    inputPassword = ""
                    passwordError = false
                },
                title = {
                    Text(
                        text = "YETİŞKİN KATEGORİSİ (18+)",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color.Black
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "UYARI: Bu kategoriye erişmek için 18 yaşından büyük olmalısınız!\n\nLütfen profil ayarlarında belirlediğiniz adult oyun şifresini girin.",
                            color = Color.Black.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = {
                                inputPassword = it
                                passwordError = false
                            },
                            placeholder = { Text("Adult Oyun Şifresi") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = passwordError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Black,
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (passwordError) {
                            Text(
                                text = "Hatalı şifre! Lütfen tekrar deneyin.",
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (user?.adultPassword.isNullOrEmpty()) {
                            Text(
                                text = "Herhangi bir şifre belirlenmemiş. Doğrudan 'ONAYLA' diyerek girebilir veya profil ayarlarından şifre tanımlayabilirsiniz.",
                                color = Color.Black.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val savedPassword = user?.adultPassword ?: ""
                            if (inputPassword == savedPassword) {
                                selectedCategory = pendingCategory
                                showPasswordPrompt = false
                                inputPassword = ""
                                passwordError = false
                            } else {
                                passwordError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ONAYLA", fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordPrompt = false
                            inputPassword = ""
                            passwordError = false
                        }
                    ) {
                        Text("İPTAL", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun CategoryCard(
    category: CategoryInfo,
    user: UserProfile?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isLocked = category.name == "ADULT"
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(category.color)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        if (isLocked) {
            // Lock UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            val textColor = if (category.name == "BİLGİ") Color.Black else Color.White
            Text(
                text = category.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
