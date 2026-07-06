package com.example.unmask.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.unmask.data.DataRepository
import com.example.unmask.data.Memory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniScreen(
    repository: DataRepository,
    userId: String
) {
    val coroutineScope = rememberCoroutineScope()
    val memories by repository.getMemories(userId).collectAsState(initial = emptyList())
    var selectedMemory by remember { mutableStateOf<Memory?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Memory?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(16.dp)
    ) {
        if (selectedMemory != null) {
            val memory = selectedMemory!!
            // Video detail screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Video Player Area (Full screen)
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(Uri.parse(memory.videoUrl))
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    update = { view ->
                        // Video source updates
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            selectedMemory = null
                            showMenu = false
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("YAYINLA / KALDIR", fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = "Publish") },
                                onClick = {
                                    coroutineScope.launch {
                                        repository.toggleMemoryPublic(memory.id, memory.isPublic)
                                        selectedMemory = memory.copy(isPublic = !memory.isPublic)
                                        showMenu = false
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("SİL", color = Color.Red, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) },
                                onClick = {
                                    showDeleteConfirm = memory
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                // Translucent Task card overlayed at the bottom while playing
                if (memory.taskText.isNotEmpty()) {
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
                                    text = memory.taskText,
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
                }
            }
        } else {
            // Memory Grid List
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
                        text = "ANILARIM",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }

                if (memories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz bir anınız yok.",
                            color = Color.Black.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(memories) { memory ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMemory = memory }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.75f) // 3:4 ratio
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black)
                                ) {
                                    VideoThumbnailImage(
                                        videoUrl = memory.videoUrl,
                                        contentDescription = memory.gameName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = memory.gameName,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = memory.date,
                                    fontSize = 10.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirm Dialog
        if (showDeleteConfirm != null) {
            val memory = showDeleteConfirm!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = {
                    Text("EMİN MİSİNİZ?", fontWeight = FontWeight.Black, color = Color.Black)
                },
                text = {
                    Text(
                        text = "Bu anı kalıcı olarak silinecektir.",
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
                                    repository.deleteMemory(memory.id)
                                    selectedMemory = null
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
    }
}
