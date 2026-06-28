package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.database.VideoEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SpaceVideoPlayer(
    video: VideoEntity,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Auto-rotate to landscape for immersive video
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    
    // Restore orientation when leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        onClose()
    }

    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var videoDuration by remember { mutableStateOf(1L) }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    
    // Gestures states
    var brightness by remember { mutableStateOf(0.7f) } // simulated
    var volumeLevel by remember { mutableStateOf(0.5f) } // simulated
    var gestureIndicatorText by remember { mutableStateOf("") }
    var gestureIndicatorIcon by remember { mutableStateOf(Icons.Default.VolumeUp) }
    var showGestureIndicator by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Timer to hide controllers
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Position updates
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewInstance?.let { vv ->
                currentPos = vv.currentPosition.toLong()
            }
            delay(300)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val halfWidth = size.width / 2
                            videoViewInstance?.let { vv ->
                                if (offset.x < halfWidth) {
                                    // Rewind
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPos = newPos.toLong()
                                    gestureIndicatorText = "-10s"
                                    gestureIndicatorIcon = Icons.Default.Replay10
                                } else {
                                    // Forward
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                    vv.seekTo(newPos)
                                    currentPos = newPos.toLong()
                                    gestureIndicatorText = "+10s"
                                    gestureIndicatorIcon = Icons.Default.Forward10
                                }
                                showGestureIndicator = true
                                coroutineScope.launch {
                                    delay(1000)
                                    showGestureIndicator = false
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val halfWidth = size.width / 2
                            if (change.position.x < halfWidth) {
                                // Left side: Brightness adjustment
                                brightness = (brightness - dragAmount.y * 0.005f).coerceIn(0.1f, 1.0f)
                                gestureIndicatorText = "Brillo: ${(brightness * 100).toInt()}%"
                                gestureIndicatorIcon = Icons.Default.Brightness6
                            } else {
                                // Right side: Volume adjustment
                                volumeLevel = (volumeLevel - dragAmount.y * 0.005f).coerceIn(0.0f, 1.0f)
                                gestureIndicatorText = "Volumen: ${(volumeLevel * 100).toInt()}%"
                                gestureIndicatorIcon = if (volumeLevel == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp
                                // Set media player volume if instance exists
                                videoViewInstance?.setVolume(volumeLevel)
                            }
                            showGestureIndicator = true
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                delay(1200)
                                showGestureIndicator = false
                            }
                        }
                    )
                }
            }
    ) {
        // --- NATIVE VIDEO VIEW WRAPPER ---
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val videoUri = if (video.isVirtual && video.streamUrl != null) {
                        Uri.parse(video.streamUrl)
                    } else {
                        Uri.parse(video.filePath)
                    }
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        videoDuration = duration.toLong()
                        // Restore last position if exists
                        if (video.lastPosition > 0) {
                            seekTo(video.lastPosition.toInt())
                        }
                        start()
                        isPlaying = true
                        // Set current system-like volume
                        mp.setVolume(volumeLevel, volumeLevel)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { videoView ->
                videoViewInstance = videoView
            }
        )

        // --- GESTURE FEEDBACK INDICATOR OVERLAY ---
        if (showGestureIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = gestureIndicatorIcon,
                        contentDescription = "Gesture",
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gestureIndicatorText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // --- PREMIUM CONTROLLER OVERLAYS ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Cerrar", tint = Color.White)
                    }
                    
                    Text(
                        text = video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )

                    // Control Lock Toggle
                    IconButton(onClick = { isLocked = !isLocked }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (isLocked) Color(0xFFFF4081) else Color.White
                        )
                    }
                }
            }
        }

        // Mid Play/Pause controller (Only when not locked)
        AnimatedVisibility(
            visible = showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                IconButton(
                    onClick = {
                        videoViewInstance?.let { vv ->
                            val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                            vv.seekTo(newPos)
                            currentPos = newPos.toLong()
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(56.dp)
                ) {
                    Icon(Icons.Default.Replay10, "Rewind", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                IconButton(
                    onClick = {
                        videoViewInstance?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.background(Color(0xFF00D4FF), CircleShape).size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = {
                        videoViewInstance?.let { vv ->
                            val newPos = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                            vv.seekTo(newPos)
                            currentPos = newPos.toLong()
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).size(56.dp)
                ) {
                    Icon(Icons.Default.Forward10, "Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Bottom seeker / controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                if (!isLocked) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            Slider(
                                value = currentPos.toFloat(),
                                onValueChange = { newValue ->
                                    currentPos = newValue.toLong()
                                    videoViewInstance?.seekTo(newValue.toInt())
                                },
                                valueRange = 0f..videoDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF00D4FF),
                                    thumbColor = Color(0xFF00D4FF)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                            )
                            
                            Text(
                                text = formatTime(videoDuration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Speed Selector
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, "Velocidad", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentSpeed}x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val nextSpeed = when (currentSpeed) {
                                                0.5f -> 1.0f
                                                1.0f -> 1.5f
                                                1.5f -> 2.0f
                                                else -> 0.5f
                                            }
                                            currentSpeed = nextSpeed
                                            videoViewInstance?.let { vv ->
                                                vv.setPlaybackSpeed(nextSpeed)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Resolution quality badge
                            Text(
                                text = video.resolution,
                                color = Color(0xFF00D4FF),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .background(Color(0xFF00D4FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    // Locked warning info
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, "Locked", tint = Color(0xFFFF4081), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Controles bloqueados - Pulsa el candado arriba para desbloquear", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// Utility to set volume level inside VideoView
private fun VideoView.setVolume(volume: Float) {
    try {
        val field = VideoView::class.java.getDeclaredField("mMediaPlayer")
        field.isAccessible = true
        val mp = field.get(this) as? MediaPlayer
        mp?.setVolume(volume, volume)
    } catch (e: Exception) { }
}

private fun VideoView.setPlaybackSpeed(speed: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val field = VideoView::class.java.getDeclaredField("mMediaPlayer")
            field.isAccessible = true
            val mp = field.get(this) as? MediaPlayer
            mp?.let {
                it.playbackParams = it.playbackParams.setSpeed(speed)
            }
        } catch (e: Exception) { }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
