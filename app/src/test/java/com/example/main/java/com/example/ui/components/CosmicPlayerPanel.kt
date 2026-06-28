package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.getAccentColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.SongEntity
import com.example.player.PlaybackState
import com.example.player.RepeatMode
import com.example.ui.SpaceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun CosmicPlayerPanel(
    viewModel: SpaceViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val lrcLine by viewModel.currentLrcLine.collectAsState()
    val sleepTimerLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)

    val coroutineScope = rememberCoroutineScope()
    var isShowLyricsMode by remember { mutableStateOf(false) }

    // Vinyl rotation angle
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )
    
    // Smoothly accumulate rotation angle only when playing
    var accumulatedRotation by remember { mutableStateOf(0f) }
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.PLAYING) {
            while (playbackState == PlaybackState.PLAYING) {
                accumulatedRotation = (accumulatedRotation + 1f) % 360f
                delay(30)
            }
        }
    }

    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay canciones en reproducción.", color = Color.White)
        }
        return
    }

    val song = currentSong!!

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        // Starfield overlay in back of player
        StarfieldBackground(
            performanceMode = viewModel.performanceMode.collectAsState().value,
            batterySaver = viewModel.batterySaverMode.collectAsState().value,
            themeSelected = viewModel.appTheme.collectAsState().value,
            customBgSelected = viewModel.customBgSelected.collectAsState().value
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // --- TOP NAVIGATION HEADER ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Deslizar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "REPRODUCTOR DE AUDIO",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = song.genre,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(
                    onClick = {
                        val timerMinutes = when (sleepTimerLeft) {
                            0 -> 15
                            15 -> 30
                            30 -> 45
                            45 -> 60
                            60 -> 90
                            else -> 0
                        }
                        if (timerMinutes > 0) {
                            viewModel.startSleepTimer(timerMinutes)
                        } else {
                            viewModel.cancelSleepTimer()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Temporizador de apagado",
                        tint = if (sleepTimerLeft > 0) accentColor else Color.White
                    )
                }
            }

            // Sleep Timer Banner
            if (sleepTimerLeft > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "El reproductor se apagará en $sleepTimerLeft minutos.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- MAIN VIEW: SPINNING VINYL OR LRC LYRICS ---
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!isShowLyricsMode) {
                    // SPINNING COSMIC VINYL RECORD GALAXY DESIGN
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .shadow(24.dp, shape = CircleShape, ambientColor = accentColor, spotColor = accentColor)
                            .border(2.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                            .graphicsLayer {
                                rotationZ = accumulatedRotation
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer black vinyl grooves
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF0D0B12),
                                radius = size.minDimension / 2
                            )
                            // Draw grooves
                            for (r in 10..120 step 15) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.08f),
                                    radius = r.dp.toPx(),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        // Central cosmic wormhole / galaxy photo
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accentColor,
                                            Color(0xFF3F007F),
                                            Color(0xFF000518)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Space Music Note",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                } else {
                    // MINI INTEGRATED SCROLLING KARAOKE LYRICS PANEL
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lyrics,
                                contentDescription = "Lyrics",
                                tint = accentColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = lrcLine,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "✨ Transcripción Sincronizada por Red de IA",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TRACK INFORMATION AND FAVORITE TOGGLE ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    // Show Lyrics Button Toggle
                    IconButton(onClick = { isShowLyricsMode = !isShowLyricsMode }) {
                        Icon(
                            imageVector = if (isShowLyricsMode) Icons.Default.Album else Icons.Default.Lyrics,
                            contentDescription = "Ver Letras/Disco",
                            tint = if (isShowLyricsMode) accentColor else Color.White
                        )
                    }

                    // Heart Toggle Favorites
                    IconButton(onClick = { viewModel.toggleFavoriteSong(song) }) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color(0xFFFF4081) else Color.White
                        )
                    }
                }
            }

            // --- REAL-TIME CANVA SOUND WAVE VISUALIZER ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                val isPlaying = playbackState == PlaybackState.PLAYING
                var tick by remember { mutableStateOf(0f) }

                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        while (true) {
                            tick += 0.1f
                            delay(16)
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val barCount = 45
                    val barWidth = (width / barCount) * 0.7f
                    val spacing = (width / barCount) * 0.3f

                    for (i in 0 until barCount) {
                        // Calculate amplitude based on play position, index, and oscillation
                        val sinOffset = sin((i * 0.3f) + tick)
                        val multiplier = if (isPlaying) (abs(sinOffset) * 0.8f + 0.2f) else 0.05f
                        // Height variation relative to indices
                        val maxBarHeight = height * (1f - (abs(i - barCount / 2) / (barCount / 2f)))
                        val barHeight = maxBarHeight * multiplier

                        val x = i * (barWidth + spacing)
                        val y = (height - barHeight) / 2

                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.5f + (multiplier * 0.5f)),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }

            // --- PROGRESS SLIDER ---
            Column(modifier = Modifier.fillMaxWidth()) {
                val formattedPos = formatTime(currentPos)
                val formattedDur = formatTime(duration)

                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { newValue ->
                        viewModel.seekTo(newValue.toLong())
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        thumbColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = formattedPos, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    
                    // Quality/bitrate badge
                    Text(
                        text = song.quality,
                        color = accentColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )

                    Text(text = formattedDur, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PRIMARY PLAYBACK CONTROLS ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Shuffle Icon Toggle
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accentColor else Color.LightGray
                    )
                }

                // Prev Icon
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause central button
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(76.dp)
                        .background(accentColor, CircleShape)
                        .shadow(12.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                ) {
                    Icon(
                        imageVector = if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Next Icon
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Toggle
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val repeatIcon = when (repeatMode) {
                        RepeatMode.NONE -> Icons.Default.Repeat
                        RepeatMode.ALL -> Icons.Default.Repeat
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                    }
                    val repeatColor = if (repeatMode != RepeatMode.NONE) accentColor else Color.LightGray
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatColor
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // --- EXTRA SETTING CONTROLS (VOLUME & SPEED SLIDERS) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Speed Controller Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            val nextSpeed = when (speed) {
                                0.5f -> 0.75f
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 0.5f
                            }
                            viewModel.setPlaybackSpeed(nextSpeed)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Speed, "Speed", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${speed}x", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                // Interactive Audio Volume slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.7f).padding(start = 24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = volume,
                        onValueChange = { viewModel.setVolume(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.LightGray,
                            thumbColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun abs(value: Float): Float = if (value < 0) -value else value
private fun abs(value: Int): Int = if (value < 0) -value else value
