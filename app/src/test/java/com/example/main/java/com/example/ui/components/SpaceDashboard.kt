package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.PlaylistEntity
import com.example.data.database.SongEntity
import com.example.data.database.VideoEntity
import com.example.player.PlaybackState
import com.example.ui.ListeningStats
import com.example.ui.SpaceViewModel
import com.example.ui.theme.getAccentColor
import kotlinx.coroutines.launch

@Composable
fun SpaceDashboard(viewModel: SpaceViewModel) {
    val currentTab by viewModel.selectedTab.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val language by viewModel.appLanguage.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var activePlayingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Back button handler: collapse player if expanded
    if (isPlayerExpanded) {
        BackHandler {
            isPlayerExpanded = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- STARFIELD BACKGROUND SHADER ---
        StarfieldBackground(
            performanceMode = viewModel.performanceMode.collectAsState().value,
            batterySaver = viewModel.batterySaverMode.collectAsState().value,
            themeSelected = appTheme,
            customBgSelected = viewModel.customBgSelected.collectAsState().value
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // PREMIUM GLASSMORPHIC BOTTOM NAVIGATION BAR
                Column {
                    // --- MINI PLAYER FOR AUDIO ---
                    if (currentSong != null && !isPlayerExpanded) {
                        MiniPlayer(
                            song = currentSong!!,
                            playbackState = playbackState,
                            accentColor = accentColor,
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onExpandClick = { isPlayerExpanded = true }
                        )
                    }

                    NavigationBar(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        val tabs = listOf(
                            NavigationTabItem("library", if (language == "es") "Biblioteca" else "Library", Icons.Default.LibraryMusic),
                            NavigationTabItem("playlists", if (language == "es") "Listas" else "Playlists", Icons.Default.PlaylistPlay),
                            NavigationTabItem("discovery", if (language == "es") "Canal IA" else "AI Channel", Icons.Default.Hub),
                            NavigationTabItem("equalizer", "EQ", Icons.Default.GraphicEq),
                            NavigationTabItem("stats", if (language == "es") "Métricas" else "Stats", Icons.Default.Leaderboard),
                            NavigationTabItem("settings", if (language == "es") "Ajustes" else "Settings", Icons.Default.Settings)
                        )

                        tabs.forEach { tab ->
                            val isSelected = currentTab == tab.id
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.updateSelectedTab(tab.id) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (isSelected) accentColor else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) accentColor else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = accentColor.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // --- SWITCH MAIN TABS ---
                when (currentTab) {
                    "library" -> LibraryTab(viewModel, onVideoClick = { activePlayingVideo = it })
                    "playlists" -> PlaylistTab(viewModel, snackbarHostState)
                    "discovery" -> DiscoveryTab(viewModel)
                    "equalizer" -> EqualizerTab(viewModel)
                    "stats" -> StatsTab(viewModel)
                    "settings" -> SettingsTab(viewModel)
                }
            }
        }

        // --- FULL SCREEN AUDIO COSMIC PLAYER PANEL ---
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            CosmicPlayerPanel(
                viewModel = viewModel,
                onCollapse = { isPlayerExpanded = false }
            )
        }

        // --- IMMERSIVE OVERLAY VIDEO PLAYER ---
        if (activePlayingVideo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                SpaceVideoPlayer(
                    video = activePlayingVideo!!,
                    onClose = { activePlayingVideo = null }
                )
            }
        }
    }
}

data class NavigationTabItem(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// ==========================================
// --- MINI PLAYER FOR SLIDING PANEL ---
// ==========================================
@Composable
fun MiniPlayer(
    song: SongEntity,
    playbackState: PlaybackState,
    accentColor: Color,
    onPlayPauseToggle: () -> Unit,
    onExpandClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFF131124).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { onExpandClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Spinning small space art
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor, Color(0xFF280F4D), Color(0xFF070410))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.8f))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPlayPauseToggle) {
                Icon(
                    imageVector = if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ==========================================
// --- LIBRARY SCREEN COMPOSABLE ---
// ==========================================
@Composable
fun LibraryTab(viewModel: SpaceViewModel, onVideoClick: (VideoEntity) -> Unit) {
    val selectedSubTab by viewModel.selectedLibrarySubTab.collectAsState()
    val songs by viewModel.allSongs.collectAsState()
    val videos by viewModel.allVideos.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val language by viewModel.appLanguage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        val query by viewModel.searchQuery.collectAsState()
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text(if (language == "es") "Buscar canciones, videos, artistas..." else "Search songs, videos, artists...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = accentColor) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Row songs vs videos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            val items = listOf("songs", "videos")
            items.forEach { subTab ->
                val isSelected = selectedSubTab == subTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .clickable { viewModel.updateLibrarySubTab(subTab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (subTab == "songs") {
                            if (language == "es") "MÚSICA (${songs.size})" else "MUSIC (${songs.size})"
                        } else {
                            if (language == "es") "VIDEOS (${videos.size})" else "VIDEOS (${videos.size})"
                        },
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter lists by query
        if (selectedSubTab == "songs") {
            val filteredSongs = songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
            if (filteredSongs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron canciones.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSongs) { song ->
                        SongRowItem(song, accentColor, onPlay = { viewModel.playSong(song, filteredSongs) }, onFav = { viewModel.toggleFavoriteSong(song) })
                    }
                }
            }
        } else {
            val filteredVideos = videos.filter {
                it.title.contains(query, ignoreCase = true)
            }
            if (filteredVideos.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron videos.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredVideos) { video ->
                        VideoRowItem(video, accentColor, onPlay = { onVideoClick(video) }, onFav = { viewModel.toggleFavoriteVideo(video) })
                    }
                }
            }
        }
    }
}

@Composable
fun SongRowItem(song: SongEntity, accentColor: Color, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = accentColor)
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} • ${song.album}", color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = song.quality,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onFav) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Fav",
                    tint = if (song.isFavorite) Color(0xFFFF4081) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VideoRowItem(video: VideoEntity, accentColor: Color, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayCircle, null, tint = Color.White)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(video.quality, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = video.resolution,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onFav) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Fav",
                    tint = if (video.isFavorite) Color(0xFFFF4081) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// --- PLAYLISTS TAB COMPOSABLE ---
// ==========================================
@Composable
fun PlaylistTab(viewModel: SpaceViewModel, snackbarHostState: SnackbarHostState) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val language by viewModel.appLanguage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()

    if (selectedPlaylistForDetail != null) {
        // DETAIL OF SINGLE PLAYLIST
        val songsInPlaylist by viewModel.getSongsInPlaylist(selectedPlaylistForDetail!!.id).collectAsState(emptyList())

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(onClick = { selectedPlaylistForDetail = null }) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = selectedPlaylistForDetail!!.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (songsInPlaylist.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Lista de reproducción vacía. Añade pistas desde la biblioteca.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songsInPlaylist) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.playSong(song, songsInPlaylist) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = accentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(song.artist, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.removeSongFromPlaylist(selectedPlaylistForDetail!!.id, song.id) }) {
                                Icon(Icons.Default.Delete, "Remove", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // LIST OF ALL PLAYLISTS
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = if (language == "es") "TUS LISTAS DE REPRODUCCIÓN" else "YOUR PLAYLISTS",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (language == "es") "Crear" else "Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No has creado ninguna lista de reproducción.", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(playlists) { playlist ->
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.25f), Color(0xFF1B073A))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { selectedPlaylistForDetail = playlist }
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlaylistPlay, null, tint = accentColor, modifier = Modifier.size(32.dp))
                                    IconButton(
                                        onClick = {
                                            viewModel.deletePlaylist(playlist)
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Playlist borrada",
                                                    actionLabel = "Deshacer",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoDeletePlaylist()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (language == "es") "Nueva Lista de Reproducción" else "New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Escribe el nombre de la lista...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.createPlaylist(playlistNameInput)
                            playlistNameInput = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Crear", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }
}

// ==========================================
// --- AI DISCOVERY TAB COMPOSABLE ---
// ==========================================
@Composable
fun DiscoveryTab(viewModel: SpaceViewModel) {
    val aiRecommendation by viewModel.aiRecommendation.collectAsState()
    val isLoading by viewModel.isRecommendationLoading.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val language by viewModel.appLanguage.collectAsState()

    // Orb rotation effect
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (language == "es") "AI DISCOVERY CHANNEL" else "AI DISCOVERY CHANNEL",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (language == "es") "Algoritmo que aprende de tus gustos" else "Algorithm that learns from your taste",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large glowing central visual ORB
        Box(
            modifier = Modifier
                .size(160.dp)
                .rotate(if (isLoading) rotation else 0f)
                .shadow(if (isLoading) 32.dp else 16.dp, CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor, Color(0xFF230046), Color.Black)
                    ),
                    CircleShape
                )
                .border(2.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                .clickable(enabled = !isLoading) { viewModel.sintonizarCanalIA() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "AI",
                    tint = if (isLoading) Color.White else Color.Black,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLoading) "GENERANDO..." else "GENERAR",
                    color = if (isLoading) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Text display card with recommendation report
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando recomendaciones de la base de datos...", color = Color.LightGray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = aiRecommendation,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// --- EQUALIZER TAB COMPOSABLE ---
// ==========================================
@Composable
fun EqualizerTab(viewModel: SpaceViewModel) {
    val bass by viewModel.eqBass.collectAsState()
    val mid by viewModel.eqMid.collectAsState()
    val treble by viewModel.eqTreble.collectAsState()
    val preset by viewModel.eqPreset.collectAsState()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val language by viewModel.appLanguage.collectAsState()

    val presets = listOf("Normal", "Rock", "Pop", "Jazz", "Bass Boost", "Electronic")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = if (language == "es") "ECUALIZADOR" else "EQUALIZER",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (language == "es") "Ajusta la configuración de sonido a tu gusto" else "Adjust the sound settings to your preference",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Preset Grid Chips
        Text(
            text = if (language == "es") "PREAJUSTES" else "PRESETS",
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(84.dp)
        ) {
            items(presets) { p ->
                val isSelected = preset == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.05f))
                        .clickable { viewModel.selectEqPreset(p) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = p,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Custom Slider Bands (Bass, Mid, Treble)
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Bass
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Graves (Bass)" else "Bass", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$bass%", color = accentColor)
                }
                Slider(
                    value = bass.toFloat(),
                    onValueChange = { viewModel.setEqBands(it.toInt(), mid, treble); viewModel.selectEqPreset("Custom") },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(activeTrackColor = accentColor, thumbColor = accentColor)
                )
            }

            // Mid
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Medios (Mids)" else "Mids", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$mid%", color = accentColor)
                }
                Slider(
                    value = mid.toFloat(),
                    onValueChange = { viewModel.setEqBands(bass, it.toInt(), treble); viewModel.selectEqPreset("Custom") },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(activeTrackColor = accentColor, thumbColor = accentColor)
                )
            }

            // Treble
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Agudos (Treble)" else "Treble", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$treble%", color = accentColor)
                }
                Slider(
                    value = treble.toFloat(),
                    onValueChange = { viewModel.setEqBands(bass, mid, it.toInt()); viewModel.selectEqPreset("Custom") },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(activeTrackColor = accentColor, thumbColor = accentColor)
                )
            }
        }
    }
}

// ==========================================
// --- STATS TAB COMPOSABLE ---
// ==========================================
@Composable
fun StatsTab(viewModel: SpaceViewModel) {
    val stats = viewModel.getListeningStats()
    val accentColor = getAccentColor(viewModel.accentColorName.collectAsState().value)
    val language by viewModel.appLanguage.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (language == "es") "ESTADÍSTICAS" else "PLAYBACK STATS",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (language == "es") "Resumen de tu actividad de reproducción" else "Summary of your playback activity",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            // Main hours meter card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.2f), Color(0xFF0F071D))
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.HourglassEmpty, null, tint = accentColor, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${stats.totalTimeMinutes} Min",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == "es") "TIEMPO TOTAL ESCUCHADO" else "TOTAL LISTENING TIME",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        item {
            // Multi grids items
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Icon(Icons.Default.PlayArrow, null, tint = accentColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${stats.totalPlays}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (language == "es") "Reprod. totales" else "Total Plays", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4081))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${stats.favoritesCount}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (language == "es") "Favoritos" else "Favorites", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Icon(Icons.Default.Movie, null, tint = accentColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${stats.videosWatched}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (language == "es") "Videos vistos" else "Videos Watched", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Icon(Icons.Default.FolderOpen, null, tint = accentColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(String.format("%.2f GB", stats.totalGbSize), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (language == "es") "Espacio de medios" else "Media Space Used", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            // Top artists card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (language == "es") "CAMPEONES DEL SECTOR" else "SECTOR CHAMPIONS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (language == "es") "Artista Favorito:" else "Favorite Artist:", color = Color.LightGray)
                        Text(stats.topArtist, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (language == "es") "Álbum Favorito:" else "Favorite Album:", color = Color.LightGray)
                        Text(stats.topAlbum, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- SETTINGS & CREDITS TAB COMPOSABLE ---
// ==========================================
@Composable
fun SettingsTab(viewModel: SpaceViewModel) {
    val language by viewModel.appLanguage.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val accentName by viewModel.accentColorName.collectAsState()
    val batterySaver by viewModel.batterySaverMode.collectAsState()
    val performance by viewModel.performanceMode.collectAsState()
    val customBg by viewModel.customBgSelected.collectAsState()
    val accentColor = getAccentColor(accentName)

    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val file = File(context.filesDir, "custom_background.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.setCustomBgSelected(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (language == "es") "CONFIGURACIÓN" else "SETTINGS",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (language == "es") "Ajusta la configuración de la aplicación" else "Adjust application settings",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Language Select
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(if (language == "es") "IDIOMA" else "LANGUAGE", color = Color.LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("es", "en").forEach { lang ->
                        val isSelected = language == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.05f))
                                .clickable { viewModel.setAppLanguage(lang) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "es") "Español" else "English",
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Theme Select
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(if (language == "es") "TEMA DE GALAXIA" else "GALAXY THEME", color = Color.LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf("Galaxy Dark", "Deep Cosmic", "Solar Aurora", "OLED")
                    themes.forEach { t ->
                        val isSelected = appTheme == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.05f))
                                .clickable { viewModel.setAppTheme(t) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t.split(" ").last(),
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Custom Background selection
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(if (language == "es") "FONDOS PREDEFINIDOS" else "PREDEFINED BACKGROUNDS", color = Color.LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bgs = listOf("1", "2", "3", "4")
                    bgs.forEach { bg ->
                        val isSelected = customBg == bg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.05f))
                                .clickable { viewModel.setCustomBgSelected(bg) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (bg) {
                                    "1" -> if (language == "es") "Vía Láctea" else "Milky Way"
                                    "2" -> if (language == "es") "Nebulosa" else "Nebula"
                                    "3" -> "OLED"
                                    "4" -> "Aurora"
                                    else -> ""
                                },
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Custom Background Image Upload
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (language == "es") "FONDO PERSONALIZADO" else "CUSTOM BACKGROUND IMAGE",
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isCustomSelected = customBg != "1" && customBg != "2" && customBg != "3" && customBg != "4"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCustomSelected) accentColor else Color.White.copy(alpha = 0.05f))
                            .clickable {
                                val file = File(context.filesDir, "custom_background.jpg")
                                if (file.exists()) {
                                    viewModel.setCustomBgSelected(file.absolutePath)
                                } else {
                                    pickerLauncher.launch("image/*")
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (language == "es") "Usar mi imagen" else "Use my image",
                            color = if (isCustomSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { pickerLauncher.launch("image/*") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "Subir fondo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == "es") "Subir" else "Upload",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Toggle battery saver / performance
        item {
            Column {
                Text(if (language == "es") "RENDIMIENTO Y BATERÍA" else "BATTERY & PERFORMANCE", color = Color.LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Modo Ahorro de Batería" else "Battery Saver Mode", color = Color.White)
                    Switch(checked = batterySaver, onCheckedChange = { viewModel.setBatterySaver(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor))
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Animación de Estrellas" else "Starfield Animation", color = Color.White)
                    Switch(checked = performance, onCheckedChange = { viewModel.setPerformanceMode(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor))
                }
            }
        }

        // Extra operations (cache, reset, etc)
        item {
            Column {
                Text(if (language == "es") "MANTENIMIENTO" else "MAINTENANCE", color = Color.LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.triggerAutoScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Escanear almacenamiento local" else "Scan local storage", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.forceMediaSeed() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (language == "es") "Sembrar pistas cósmicas virtuales" else "Pre-seed virtual space content", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.clearCache() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (language == "es") "Limpiar Caché" else "Clear Cache", color = Color.White)
                    }
                    Button(
                        onClick = { viewModel.resetApp() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (language == "es") "Restablecer" else "Reset App", color = Color.White)
                    }
                }
            }
        }

        // --- CREATOR CREDITS (KSKNF) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (language == "es") "CREADORES DEL PROYECTO" else "PROJECT CREATORS",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Desarrollado por KSKNF",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Gracias por utilizar Space Music. Únete a nuestra comunidad para soporte, futuras actualizaciones y nuevas funciones.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dc.gg/lunatic"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Link, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Discord Oficial: dc.gg/lunatic", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Discord:\nksknf_", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/matiascuenta71"))
                                    context.startActivity(intent)
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Instagram:\n@matiascuenta71", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
