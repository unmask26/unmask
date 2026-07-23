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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.example.unmask.data.HistoryItem
import kotlinx.coroutines.launch

@Composable
fun GecmisScreen(
    repository: DataRepository,
    userId: String
) {
    val coroutineScope = rememberCoroutineScope()
    val history by repository.getHistory(userId).collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf<HistoryItem?>(null) }
    var editingNoteItem by remember { mutableStateOf<HistoryItem?>(null) }
    var editedNoteText by remember { mutableStateOf("") }

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
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GEÇMİŞ",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz bir oyun geçmişiniz yok.",
                        color = Color.Black.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Title & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = item.gameName,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color.Black
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Date",
                                            tint = Color.Black.copy(alpha = 0.4f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = item.date,
                                            fontSize = 12.sp,
                                            color = Color.Black.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Note Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF9F9F9))
                                    .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        editingNoteItem = item
                                        editedNoteText = item.note
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (item.note.isEmpty()) "📝 [Not eklemek için tıkla]" else "📝 ${item.note}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.note.isEmpty()) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.7f)
                                )
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        editingNoteItem = item
                                        editedNoteText = item.note
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "DÜZENLE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        showDeleteConfirm = item
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "SİL",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirm Dialog
        if (showDeleteConfirm != null) {
            val item = showDeleteConfirm!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = {
                    Text("EMİN MİSİNİZ?", fontWeight = FontWeight.Black, color = Color.Black)
                },
                text = {
                    Text(
                        text = "Bu oyun kaydı geçmişinizden silinecektir.",
                        fontWeight = FontWeight.Medium,
                        color = Color.Black.copy(alpha = 0.5f)
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.deleteHistoryItem(item.id)
                                    showDeleteConfirm = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("EVET, SİL", fontWeight = FontWeight.Black)
                        }

                        TextButton(
                            onClick = { showDeleteConfirm = null },
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

        // Edit Note Dialog
        if (editingNoteItem != null) {
            val item = editingNoteItem!!
            AlertDialog(
                onDismissRequest = { editingNoteItem = null },
                title = {
                    Text("NOT DÜZENLE", fontWeight = FontWeight.Black, color = Color.Black)
                },
                text = {
                    OutlinedTextField(
                        value = editedNoteText,
                        onValueChange = { editedNoteText = it },
                        placeholder = { Text("Notunuzu buraya yazın...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedBorderColor = Color.Black.copy(alpha = 0.2f),
                            unfocusedBorderColor = Color.Black.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.updateHistoryNote(item.id, editedNoteText)
                                    editingNoteItem = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("KAYDET", fontWeight = FontWeight.Black)
                        }

                        TextButton(
                            onClick = { editingNoteItem = null },
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
    }
}
