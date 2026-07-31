package com.example.unmask.features.online

import com.example.unmask.data.DataRepository
import com.example.unmask.data.PublicVideo
import com.example.unmask.data.Comment
import com.example.unmask.data.UserProfile
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.profile.*

import android.net.Uri
import android.widget.VideoView
import android.media.MediaPlayer
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DunyaFeedScreen(
    repository: DataRepository
) {
    val rawPublicVideos by repository.getPublicVideos().collectAsState(initial = emptyList())
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val currentUserId = currentUser?.uid ?: ""

    val permanentPromoVideo = remember {
        PublicVideo(
            id = "permanent_promo_fiziki_kart",
            userId = "unmask_official",
            userName = "UNMASK",
            videoUrl = "asset:///promo_fiziki_kart.mp4",
            gameName = "FİZİKİ KART OYUNU 🎴",
            taskText = "fiziki kart satın almak için profil ayarlarından satın alabilirsiniz.",
            createdAt = 1700000000000L, // Fixed past timestamp: new videos will have higher timestamp and shift it down
            expiresAt = Long.MAX_VALUE // Never deleted
        )
    }

    val publicVideos = remember(rawPublicVideos) {
        val listWithoutPromo = rawPublicVideos.filter { it.id != permanentPromoVideo.id }
        (listWithoutPromo + permanentPromoVideo).sortedByDescending { it.createdAt }
    }
    
    var isMuted by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Live remaining time tick loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000) // update every 10 seconds
            currentTime = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (publicVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Dünya",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Henüz yayınlanan video yok.",
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Bir online oyun oynayıp videonuzu\nDünya'ya sunan ilk siz olun!",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { publicVideos.size })

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val video = publicVideos[pageIndex]
                val isPlaying = pagerState.currentPage == pageIndex
                
                ReelsPageItem(
                    video = video,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    onToggleMute = { isMuted = !isMuted },
                    currentTime = currentTime,
                    currentUserId = currentUserId,
                    currentUser = currentUser,
                    repository = repository
                )
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Dünya",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "DÜNYA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsPageItem(
    video: PublicVideo,
    isPlaying: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    currentTime: Long,
    currentUserId: String,
    currentUser: UserProfile?,
    repository: DataRepository
) {
    val context = LocalContext.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showMuteIconIndicator by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val remainingMs = video.expiresAt - currentTime
    val remainingMins = (remainingMs / 60000).coerceAtLeast(0)

    // Handle global volume changes and restart when unmuted
    LaunchedEffect(isMuted) {
        mediaPlayer?.let { mp ->
            try {
                if (isMuted) {
                    mp.setVolume(0f, 0f)
                } else {
                    mp.setVolume(1f, 1f)
                    videoView?.seekTo(0)
                    videoView?.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoView?.stopPlayback()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                onClick = {
                    onToggleMute()
                    showMuteIconIndicator = true
                    coroutineScope.launch {
                        delay(750)
                        showMuteIconIndicator = false
                    }
                },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        // Full screen VideoView
        AndroidView(
            factory = { ctx ->
                val rootLayout = FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                val vv = VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                }
                rootLayout.addView(vv)
                videoView = vv
                rootLayout
            },
            update = { root ->
                val vv = root.getChildAt(0) as VideoView
                if (isPlaying) {
                    if (vv.tag != video.videoUrl) {
                        vv.tag = video.videoUrl
                        
                        val cachedFile = com.example.unmask.data.VideoCacheManager.getCachedVideoFile(context, video.videoUrl)
                        val videoUri = when {
                            video.videoUrl == "asset:///promo_fiziki_kart.mp4" || video.videoUrl.contains("promo_fiziki_kart.mp4") -> {
                                val assetFile = java.io.File(context.cacheDir, "promo_fiziki_kart.mp4")
                                if (!assetFile.exists() || assetFile.length() == 0L) {
                                    try {
                                        context.assets.open("promo_fiziki_kart.mp4").use { input ->
                                            assetFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                Uri.fromFile(assetFile)
                            }
                            cachedFile != null -> Uri.fromFile(cachedFile)
                            video.videoUrl.startsWith("/") -> Uri.fromFile(java.io.File(video.videoUrl))
                            video.videoUrl.startsWith("file://") -> Uri.parse(video.videoUrl)
                            video.videoUrl.startsWith("http://") || video.videoUrl.startsWith("https://") -> Uri.parse(video.videoUrl)
                            else -> Uri.fromFile(java.io.File(video.videoUrl))
                        }
                        
                        vv.setOnErrorListener { _, _, _ -> true }
                        vv.setVideoURI(videoUri)
                        
                        if (cachedFile == null) {
                            coroutineScope.launch {
                                try {
                                    com.example.unmask.data.VideoCacheManager.prefetchVideo(context, video.videoUrl)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        vv.setOnPreparedListener { mp ->
                            mediaPlayer = mp
                            mp.isLooping = true
                            if (isMuted) {
                                mp.setVolume(0f, 0f)
                            } else {
                                mp.setVolume(1f, 1f)
                            }
                            vv.start()
                        }
                    } else {
                        mediaPlayer?.let { mp ->
                            if (isMuted) {
                                mp.setVolume(0f, 0f)
                            } else {
                                mp.setVolume(1f, 1f)
                            }
                        }
                        if (!vv.isPlaying) {
                            vv.start()
                        }
                    }
                } else {
                    if (vv.tag != null) {
                        vv.stopPlayback()
                        vv.tag = null
                        mediaPlayer = null
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Reels content overlay (Bottom Left)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = video.userName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = "@${video.userName}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )

                val myNickname = currentUser?.nickname?.takeIf { it.isNotBlank() } ?: "Oyuncu"
                val isMyOwnVideo = video.userId == currentUserId || 
                                   (myNickname.isNotEmpty() && video.userName.equals(myNickname, ignoreCase = true))
                val isPromoVideo = video.id == "permanent_promo_fiziki_kart" || video.taskText.contains("fiziki kart", ignoreCase = true)

                if (!isMyOwnVideo && !isPromoVideo) {
                    val isFollowing = currentUser?.following?.contains(video.userName) == true

                    // TAKİP ET / TAKİPTESİN Butonu
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                repository.toggleFollowUser(video.userName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color(0xFF10B981) else Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isFollowing) Color(0xFF10B981) else Color.White.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isFollowing) "TAKİPTESİN ✓" else "+ TAKİP ET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.gameName,
                    color = Color(0xFFFACC15),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = video.taskText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                // Açıklama Kutusu (Description Box) & Özel SATIN AL Butonu
                val isPromoVideo = video.id == "permanent_promo_fiziki_kart" || video.taskText.contains("fiziki kart", ignoreCase = true)
                if (isPromoVideo) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFACC15)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Açıklama",
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "fiziki kart satın almak için profil ayarlarından satın alabilirsiniz.",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            // Sadece Bu Videoya Özel SATIN AL Butonu
                            Button(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "🛒 Fiziki Kart Satın Alma: Profil Ayarları sayfasından satın alma yapabilirsiniz.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Satın Al",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🛒 SATIN AL",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reels Actions Column (Right Side overlay)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val isLiked = video.likedBy.contains(currentUserId)
            
            // Like Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                repository.toggleLikePublicVideo(video.id)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Beğen",
                        tint = if (isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = video.likesCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comment Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showCommentsSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "Yorum Yap",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = video.comments.size.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Right overlay (Timer chip - hidden on promo video)
        val isPromoVideo = video.id == "permanent_promo_fiziki_kart" || video.taskText.contains("fiziki kart", ignoreCase = true)
        if (!isPromoVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 20.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Süre",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$remainingMins dk kaldı",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }

        // Center Volume Indicator Overlay
        AnimatedVisibility(
            visible = showMuteIconIndicator,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = "Ses",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Comments Bottom Sheet
        if (showCommentsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCommentsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF151515),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                CommentsSheetContent(
                    video = video,
                    currentUserId = currentUserId,
                    onAddComment = { text ->
                        coroutineScope.launch {
                            try {
                                repository.addCommentToPublicVideo(video.id, text)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onClose = { showCommentsSheet = false }
                )
            }
        }
    }
}

@Composable
fun CommentsSheetContent(
    video: PublicVideo,
    currentUserId: String,
    onAddComment: (String) -> Unit,
    onClose: () -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxHeight(0.75f)
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yorumlar (${video.comments.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // List of comments
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (video.comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "İlk yorumu siz yapın!",
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(video.comments, key = { it.id }) { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = comment.userName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@${comment.userName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                if (comment.userId == currentUserId) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Sen",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = comment.text,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Input text field
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("Yorum ekle...", color = Color.White.copy(alpha = 0.4f)) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (commentText.isNotBlank()) {
                        onAddComment(commentText)
                        commentText = ""
                    }
                },
                enabled = commentText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Gönder", fontWeight = FontWeight.Bold)
            }
        }
    }
}
