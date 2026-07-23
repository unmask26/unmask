package com.example.unmask.features.game

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.example.unmask.data.Game
import com.example.unmask.data.Task
import kotlinx.coroutines.launch
import java.util.UUID

data class CardDeckInfo(val code: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGameScreen(
    repository: DataRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var gameName by remember { mutableStateOf("") }
    var selectedCategoryTag by remember { mutableStateOf("EĞLENCE") }
    
    // State array for 52 tasks
    val tasksTexts = remember { mutableStateListOf(*Array(52) { "" }) }

    // Standard 52-card deck mapped chronologically
    val deck = remember {
        listOf(
            // Spades (Maça)
            CardDeckInfo("2S", "Maça 2"), CardDeckInfo("3S", "Maça 3"), CardDeckInfo("4S", "Maça 4"), CardDeckInfo("5S", "Maça 5"),
            CardDeckInfo("6S", "Maça 6"), CardDeckInfo("7S", "Maça 7"), CardDeckInfo("8S", "Maça 8"), CardDeckInfo("9S", "Maça 9"),
            CardDeckInfo("10S", "Maça 10"), CardDeckInfo("JS", "Maça Vale"), CardDeckInfo("QS", "Maça Kız"), CardDeckInfo("KS", "Maça Papaz"), CardDeckInfo("AS", "Maça As"),
            // Hearts (Kupa)
            CardDeckInfo("2H", "Kupa 2"), CardDeckInfo("3H", "Kupa 3"), CardDeckInfo("4H", "Kupa 4"), CardDeckInfo("5H", "Kupa 5"),
            CardDeckInfo("6H", "Kupa 6"), CardDeckInfo("7H", "Kupa 7"), CardDeckInfo("8H", "Kupa 8"), CardDeckInfo("9H", "Kupa 9"),
            CardDeckInfo("10H", "Kupa 10"), CardDeckInfo("JH", "Kupa Vale"), CardDeckInfo("QH", "Kupa Kız"), CardDeckInfo("KH", "Kupa Papaz"), CardDeckInfo("AH", "Kupa As"),
            // Diamonds (Karo)
            CardDeckInfo("2D", "Karo 2"), CardDeckInfo("3D", "Karo 3"), CardDeckInfo("4D", "Karo 4"), CardDeckInfo("5D", "Karo 5"),
            CardDeckInfo("6D", "Karo 6"), CardDeckInfo("7D", "Karo 7"), CardDeckInfo("8D", "Karo 8"), CardDeckInfo("9D", "Karo 9"),
            CardDeckInfo("10D", "Karo 10"), CardDeckInfo("JD", "Karo Vale"), CardDeckInfo("QD", "Karo Kız"), CardDeckInfo("KD", "Karo Papaz"), CardDeckInfo("AD", "Karo As"),
            // Clubs (Sinek)
            CardDeckInfo("2C", "Sinek 2"), CardDeckInfo("3C", "Sinek 3"), CardDeckInfo("4C", "Sinek 4"), CardDeckInfo("5C", "Sinek 5"),
            CardDeckInfo("6C", "Sinek 6"), CardDeckInfo("7C", "Sinek 7"), CardDeckInfo("8C", "Sinek 8"), CardDeckInfo("9C", "Sinek 9"),
            CardDeckInfo("10C", "Sinek 10"), CardDeckInfo("JC", "Sinek Vale"), CardDeckInfo("QC", "Sinek Kız"), CardDeckInfo("KC", "Sinek Papaz"), CardDeckInfo("AC", "Sinek As")
        )
    }

    val handlePublish = {
        if (gameName.trim().isEmpty()) {
            Toast.makeText(context, "Lütfen oyun başlığı girin!", Toast.LENGTH_SHORT).show()
        } else {
            // Count filled tasks
            val emptyIndex = tasksTexts.indexOfFirst { it.trim().isEmpty() }
            if (emptyIndex != -1) {
                Toast.makeText(
                    context, 
                    "Lütfen tüm 52 görevi doldurun! (${emptyIndex + 1}. Kutu boş)", 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                coroutineScope.launch {
                    val gameSlug = "custom-" + UUID.randomUUID().toString().take(8)
                    
                    // Create game registered under KENDİ OYUNLARIM category
                    val newGame = Game(
                        id = gameSlug,
                        name = gameName.trim(),
                        category = "KENDİ OYUNLARIM",
                        price = 0,
                        isFree = true
                    )
                    
                    // Create tasks
                    val newTasks = deck.mapIndexed { index, card ->
                        Task(
                            id = "$gameSlug-${card.code}",
                            gameId = gameSlug,
                            cardCode = card.code,
                            text = tasksTexts[index].trim(),
                            duration = 15,
                            hasVideo = true
                        )
                    }
                    
                    repository.saveCustomGame(newGame, newTasks)
                    Toast.makeText(context, "Oyun başarıyla yayınlandı!", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "OYUN YARAT",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }

                Button(
                    onClick = { handlePublish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Publish, contentDescription = "Publish", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("YAYINLA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Game Config Header Box
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(24.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Game Name Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "OYUN BAŞLIĞI",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.4f),
                                letterSpacing = 1.sp
                            )
                            OutlinedTextField(
                                value = gameName,
                                onValueChange = { gameName = it },
                                placeholder = { Text("Oyun adını yazın") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Black.copy(alpha = 0.2f),
                                    unfocusedBorderColor = Color.Black.copy(alpha = 0.05f),
                                    focusedContainerColor = Color(0xFFF9F9F9),
                                    unfocusedContainerColor = Color(0xFFF9F9F9)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Category Tag Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "ETİKET KATEGORİSİ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.4f),
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("EĞLENCE", "SPOR", "BİLGİ", "GEZİ", "ADULT").forEach { tag ->
                                    val isSelected = selectedCategoryTag == tag
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color.Black else Color(0xFFF0F0F0))
                                            .clickable { selectedCategoryTag = tag }
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Header for Tasks List
                item {
                    Text(
                        text = "GÖREV LİSTESİ (52 ADET KART)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 52 Task boxes rendered efficiently
                itemsIndexed(deck) { index, card ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${index + 1}. Kutu - ${card.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        OutlinedTextField(
                            value = tasksTexts[index],
                            onValueChange = { tasksTexts[index] = it },
                            placeholder = { Text("Görevi buraya yazın...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Black.copy(alpha = 0.2f),
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.05f),
                                focusedContainerColor = Color(0xFFF9F9F9),
                                unfocusedContainerColor = Color(0xFFF9F9F9)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Spacing at the bottom of the list
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
