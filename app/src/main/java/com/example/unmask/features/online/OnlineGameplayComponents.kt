package com.example.unmask.features.online

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.unmask.data.DataRepository
import com.example.unmask.data.Game
import com.example.unmask.data.OnlineSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@Composable
fun ReplayRequestDialog(
    session: OnlineSession,
    repository: DataRepository,
    coroutineScope: CoroutineScope
) {
    val isRequester = session.replayRequesterId == session.user1Id
    val isOpponentRequesting = session.replayRequestStatus == "requested" && !isRequester

    if (isOpponentRequesting) {
        AlertDialog(
            onDismissRequest = {
                coroutineScope.launch {
                    repository.updateSession(session.copy(replayRequestStatus = "rejected"))
                }
            },
            title = {
                Text(text = "Tekrar Oyun İsteği", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "${session.replayRequesterName} size tekrar oyun isteği gönderdi.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val isMatchmaking = session.commonCategory.isNotEmpty()
                            val nextStatus = if (isMatchmaking) "playing" else "game_selection"
                            repository.updateSession(
                                session.copy(
                                    status = nextStatus,
                                    user1TaskCount = 0,
                                    user2TaskCount = 0,
                                    activeCardCode = "",
                                    activeTaskId = "",
                                    activeTaskText = "",
                                    videoUrl = "",
                                    downloadRequestStatus = "none",
                                    usedTaskIds = emptyList(),
                                    usedTaskTexts = emptyList(),
                                    replayRequestStatus = "none",
                                    replayRequesterId = "",
                                    replayRequesterName = "",
                                    currentTurn = session.user2Id,
                                    lastHeartbeat = System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("KABUL ET", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.updateSession(session.copy(replayRequestStatus = "rejected"))
                        }
                    }
                ) {
                    Text("REDDET", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun OnlineReceiverPlaybackView(
    session: OnlineSession,
    userId: String,
    userNickname: String,
    currentTaskText: String,
    repository: DataRepository,
    coroutineScope: CoroutineScope,
    context: Context
) {
    val isRequester = session.replayRequesterId == userId
    var videoFinished by remember(session.videoUrl) { mutableStateOf(false) }
    var localVideoPath by remember(session.videoUrl) { mutableStateOf<String?>(null) }
    var isCaching by remember(session.videoUrl) { mutableStateOf(true) }

    LaunchedEffect(session.videoUrl) {
        if (session.videoUrl.isNotEmpty()) {
            isCaching = true
            localVideoPath = null
            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    val urlHash = session.videoUrl.hashCode().let { if (it < 0) "n${-it}" else "$it" }
                    val fileName = "online_recv_${urlHash}.mp4"
                    val file = File(context.cacheDir, fileName)
                    if (!file.exists() || file.length() == 0L) {
                        val url = URL(session.videoUrl)
                        url.openStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    file
                }
                localVideoPath = cachedFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                localVideoPath = session.videoUrl
            } finally {
                isCaching = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isCaching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Video yükleniyor...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (!videoFinished && localVideoPath != null) {
            key(localVideoPath) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val vPath = localVideoPath
                            if (vPath != null) {
                                setVideoPath(vPath)
                                setOnPreparedListener { start() }
                                setOnCompletionListener { videoFinished = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                            text = currentTaskText,
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
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "GÖSTERİM TAMAMLANDI",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Videoyu tekrar izleyebilir veya tekrar oyun isteği gönderebilirsin.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = { videoFinished = false },
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth(0.6f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Tekrar İzle",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TEKRAR İZLE", fontWeight = FontWeight.Black, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (session.replayRequestStatus == "requested" && isRequester) {
                        Text("Tekrar oyun isteği gönderildi. Yanıt bekleniyor...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                    } else if (session.replayRequestStatus == "rejected" && isRequester) {
                        Text("Tekrar oyun isteği reddedildi.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    repository.updateSession(
                                        session.copy(
                                            replayRequestStatus = "requested",
                                            replayRequesterId = userId,
                                            replayRequesterName = userNickname
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Replay", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TEKRAR OYNA", fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val totalTasksDone = session.user1TaskCount + session.user2TaskCount
                    if (totalTasksDone >= 10) {
                        Button(
                            onClick = {
                                val videoUrlToDelete = session.videoUrl
                                coroutineScope.launch {
                                    if (videoUrlToDelete.isNotEmpty()) repository.deleteOnlineVideo(videoUrlToDelete)
                                    repository.updateSession(
                                        session.copy(
                                            status = "finished",
                                            videoUrl = "",
                                            downloadRequestStatus = "none",
                                            lastHeartbeat = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                        ) {
                            Text("🏆 OYUNU BİTİR", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = {
                                val videoUrlToDelete = session.videoUrl
                                coroutineScope.launch {
                                    if (videoUrlToDelete.isNotEmpty()) repository.deleteOnlineVideo(videoUrlToDelete)
                                    val isMatchmaking = session.commonCategory.isNotEmpty()
                                    if (isMatchmaking) {
                                        repository.updateSession(
                                            session.copy(
                                                activeCardCode = "",
                                                activeTaskId = "",
                                                activeTaskText = "",
                                                videoUrl = "",
                                                downloadRequestStatus = "none",
                                                currentTurn = userId,
                                                lastHeartbeat = System.currentTimeMillis()
                                            )
                                        )
                                    } else {
                                        repository.updateSession(
                                            session.copy(
                                                status = "game_selection",
                                                activeCardCode = "",
                                                activeTaskId = "",
                                                activeTaskText = "",
                                                videoUrl = "",
                                                downloadRequestStatus = "none",
                                                currentTurn = userId,
                                                lastHeartbeat = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(56.dp).fillMaxWidth(0.6f)
                        ) {
                            Text("SONRAKİ TUR ➔", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
