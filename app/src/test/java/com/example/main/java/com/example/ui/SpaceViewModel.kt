package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.database.*
import com.example.data.repository.SpaceRepository
import com.example.player.PlaybackState
import com.example.player.RepeatMode
import com.example.player.SpacePlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SpaceRepository
    
    // --- MEDIA LIST FLOWS ---
    val allSongs: StateFlow<List<SongEntity>>
    val favoriteSongs: StateFlow<List<SongEntity>>
    val recentlyPlayedSongs: StateFlow<List<SongEntity>>
    val mostPlayedSongs: StateFlow<List<SongEntity>>

    val allVideos: StateFlow<List<VideoEntity>>
    val favoriteVideos: StateFlow<List<VideoEntity>>

    val allPlaylists: StateFlow<List<PlaylistEntity>>
    val playbackHistory: StateFlow<List<HistoryEntity>>

    // --- SEARCH FLOWS ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow("library") // "library", "playlists", "discovery", "equalizer", "stats", "settings"
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _selectedLibrarySubTab = MutableStateFlow("songs") // "songs", "videos", "albums", "artists", "favorites"
    val selectedLibrarySubTab: StateFlow<String> = _selectedLibrarySubTab.asStateFlow()

    // --- CONFIG & DESIGN SETTINGS FLOWS ---
    private val _appLanguage = MutableStateFlow("es") // "es" or "en"
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _appTheme = MutableStateFlow("Galaxy Dark") // "Galaxy Dark", "Deep Cosmic", "Solar Aurora", "OLED"
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _accentColorName = MutableStateFlow("Purple") // "Blue", "Purple", "Pink", "Red", "Green", "Gold"
    val accentColorName: StateFlow<String> = _accentColorName.asStateFlow()

    private val _batterySaverMode = MutableStateFlow(false)
    val batterySaverMode: StateFlow<Boolean> = _batterySaverMode.asStateFlow()

    private val _performanceMode = MutableStateFlow(true)
    val performanceMode: StateFlow<Boolean> = _performanceMode.asStateFlow()

    private val _customBgSelected = MutableStateFlow("1") // "1" (Blue Galaxy), "2" (Nebula Purple), "3" (Cosmic Black), "4" (Solar Aurora)
    val customBgSelected: StateFlow<String> = _customBgSelected.asStateFlow()

    // --- EQUALIZER STATE ---
    private val _eqBass = MutableStateFlow(50) // 0-100
    val eqBass: StateFlow<Int> = _eqBass.asStateFlow()
    private val _eqMid = MutableStateFlow(50)
    val eqMid: StateFlow<Int> = _eqMid.asStateFlow()
    private val _eqTreble = MutableStateFlow(50)
    val eqTreble: StateFlow<Int> = _eqTreble.asStateFlow()
    private val _eqPreset = MutableStateFlow("Normal") // "Normal", "Rock", "Pop", "Jazz", "Bass Boost", "Electronic"
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()

    // --- DISCOVERY STATE ---
    private val _aiRecommendation = MutableStateFlow("✨ Pulsa el botón central para recibir recomendaciones de la IA...")
    val aiRecommendation: StateFlow<String> = _aiRecommendation.asStateFlow()
    private val _isRecommendationLoading = MutableStateFlow(false)
    val isRecommendationLoading: StateFlow<Boolean> = _isRecommendationLoading.asStateFlow()

    // --- ACTIVE PLAYING QUEUE CONTEXT ---
    val currentSong: StateFlow<SongEntity?> = SpacePlayerManager.currentSong
    val playbackState: StateFlow<PlaybackState> = SpacePlayerManager.playbackState
    val currentPosition: StateFlow<Long> = SpacePlayerManager.currentPosition
    val duration: StateFlow<Long> = SpacePlayerManager.duration
    val currentLrcLine: StateFlow<String> = SpacePlayerManager.currentLrcLine
    val playbackSpeed: StateFlow<Float> = SpacePlayerManager.playbackSpeed
    val repeatMode: StateFlow<RepeatMode> = SpacePlayerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = SpacePlayerManager.isShuffle
    val volume: StateFlow<Float> = SpacePlayerManager.volume
    val sleepTimerMinutesLeft: StateFlow<Int> = SpacePlayerManager.sleepTimerMinutesLeft

    // For undoing deleted playlist
    private var lastDeletedPlaylist: PlaylistEntity? = null
    private var lastDeletedPlaylistSongs: List<SongEntity> = emptyList()

    init {
        val database = SpaceDatabase.getDatabase(application)
        repository = SpaceRepository(application, database)
        SpacePlayerManager.initialize(repository)

        // Read flows from Repository
        allSongs = repository.allSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        favoriteSongs = repository.favoriteSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        recentlyPlayedSongs = repository.recentlyPlayedSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        mostPlayedSongs = repository.mostPlayedSongs.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        allVideos = repository.allVideos.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        favoriteVideos = repository.favoriteVideos.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        allPlaylists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        playbackHistory = repository.playbackHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Load configs
        loadConfigs()

        // Trigger auto-scan
        triggerAutoScan()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            _appLanguage.value = repository.getConfig("language", "es")
            _appTheme.value = repository.getConfig("theme", "Galaxy Dark")
            _accentColorName.value = repository.getConfig("accent_color", "Purple")
            _batterySaverMode.value = repository.getConfig("battery_saver", "false") == "true"
            _performanceMode.value = repository.getConfig("performance_mode", "true") == "true"
            _customBgSelected.value = repository.getConfig("custom_bg", "1")

            // EQ
            _eqPreset.value = repository.getConfig("eq_preset", "Normal")
            _eqBass.value = repository.getConfig("eq_bass", "50").toInt()
            _eqMid.value = repository.getConfig("eq_mid", "50").toInt()
            _eqTreble.value = repository.getConfig("eq_treble", "50").toInt()
        }
    }

    fun triggerAutoScan() {
        viewModelScope.launch {
            repository.scanMedia(forceSeed = false)
        }
    }

    fun forceMediaSeed() {
        viewModelScope.launch {
            repository.scanMedia(forceSeed = true)
        }
    }

    // --- SEARCH & FILTER ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedTab(tab: String) {
        _selectedTab.value = tab
    }

    fun updateLibrarySubTab(subTab: String) {
        _selectedLibrarySubTab.value = subTab
    }

    // --- MUSIC CONTROLS ---
    fun playSong(song: SongEntity, currentList: List<SongEntity> = allSongs.value) {
        SpacePlayerManager.playSong(song, currentList)
    }

    fun togglePlayPause() {
        SpacePlayerManager.togglePlayPause()
    }

    fun playNext() {
        SpacePlayerManager.handleNext()
    }

    fun playPrevious() {
        SpacePlayerManager.handlePrevious()
    }

    fun seekTo(pos: Long) {
        SpacePlayerManager.seekTo(pos)
    }

    fun setPlaybackSpeed(speed: Float) {
        SpacePlayerManager.setSpeed(speed)
    }

    fun toggleRepeatMode() {
        SpacePlayerManager.toggleRepeatMode()
    }

    fun toggleShuffle() {
        SpacePlayerManager.toggleShuffle()
    }

    fun setVolume(vol: Float) {
        SpacePlayerManager.setVolume(vol)
    }

    fun startSleepTimer(minutes: Int) {
        SpacePlayerManager.startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        SpacePlayerManager.cancelSleepTimer()
    }

    // --- FAVORITES AND RATING ---
    fun toggleFavoriteSong(song: SongEntity) {
        viewModelScope.launch {
            val updated = song.copy(isFavorite = !song.isFavorite)
            repository.updateSong(updated)
        }
    }

    fun toggleFavoriteVideo(video: VideoEntity) {
        viewModelScope.launch {
            val updated = video.copy(isFavorite = !video.isFavorite)
            repository.updateVideo(updated)
        }
    }

    // --- PLAYLISTS CRUD ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            // Save for undo
            lastDeletedPlaylist = playlist
            repository.getSongsInPlaylist(playlist.id).firstOrNull()?.let { songs ->
                lastDeletedPlaylistSongs = songs
            }
            repository.deletePlaylist(playlist)
        }
    }

    fun undoDeletePlaylist() {
        val playlistToRestore = lastDeletedPlaylist ?: return
        viewModelScope.launch {
            val newId = repository.createPlaylist(playlistToRestore.name)
            lastDeletedPlaylistSongs.forEach { song ->
                repository.addSongToPlaylist(newId.toInt(), song.id)
            }
            lastDeletedPlaylist = null
            lastDeletedPlaylistSongs = emptyList()
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlist, newName)
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>> {
        return repository.getSongsInPlaylist(playlistId)
    }

    // --- CONFIG & CUSTOMIZATION SETTINGS ---
    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        viewModelScope.launch { repository.setConfig("language", lang) }
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        viewModelScope.launch { repository.setConfig("theme", theme) }
    }

    fun setAccentColorName(color: String) {
        _accentColorName.value = color
        viewModelScope.launch { repository.setConfig("accent_color", color) }
    }

    fun setBatterySaver(enabled: Boolean) {
        _batterySaverMode.value = enabled
        viewModelScope.launch { repository.setConfig("battery_saver", enabled.toString()) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        _performanceMode.value = enabled
        viewModelScope.launch { repository.setConfig("performance_mode", enabled.toString()) }
    }

    fun setCustomBgSelected(bgIndex: String) {
        _customBgSelected.value = bgIndex
        viewModelScope.launch { repository.setConfig("custom_bg", bgIndex) }
    }

    // --- EQUALIZER PRESETS ---
    fun selectEqPreset(preset: String) {
        _eqPreset.value = preset
        when (preset) {
            "Normal" -> setEqBands(50, 50, 50)
            "Rock" -> setEqBands(70, 45, 65)
            "Pop" -> setEqBands(40, 60, 60)
            "Jazz" -> setEqBands(60, 50, 40)
            "Bass Boost" -> setEqBands(90, 40, 45)
            "Electronic" -> setEqBands(80, 45, 75)
        }
        viewModelScope.launch { repository.setConfig("eq_preset", preset) }
    }

    fun setEqBands(bass: Int, mid: Int, treble: Int) {
        _eqBass.value = bass
        _eqMid.value = mid
        _eqTreble.value = treble
        viewModelScope.launch {
            repository.setConfig("eq_bass", bass.toString())
            repository.setConfig("eq_mid", mid.toString())
            repository.setConfig("eq_treble", treble.toString())
        }
    }

    // --- DYNAMIC STATS GENERATOR ---
    fun getListeningStats(): ListeningStats {
        val songs = allSongs.value
        val history = playbackHistory.value
        val favorites = favoriteSongs.value
        val videos = allVideos.value

        val totalPlays = songs.sumOf { it.playCount } + videos.sumOf { it.playCount }
        val hoursScanned = (songs.sumOf { it.duration } / (1000 * 60 * 60.0)) + (videos.sumOf { it.duration } / (1000 * 60 * 60.0))
        val listenTimeMinutes = totalPlays * 3.5 // assumption: average listen is 3.5 minutes

        val artistCounts = songs.groupBy { it.artist }.mapValues { (_, sList) -> sList.sumOf { it.playCount } }
        val topArtist = artistCounts.maxByOrNull { it.value }?.key ?: "N/A"

        val albumCounts = songs.groupBy { it.album }.mapValues { (_, sList) -> sList.sumOf { it.playCount } }
        val topAlbum = albumCounts.maxByOrNull { it.value }?.key ?: "N/A"

        return ListeningStats(
            totalTimeMinutes = listenTimeMinutes.toInt(),
            totalPlays = totalPlays,
            favoritesCount = favorites.size,
            videosWatched = videos.sumOf { it.playCount },
            topArtist = topArtist,
            topAlbum = topAlbum,
            songsScannedCount = songs.size,
            videosScannedCount = videos.size,
            totalGbSize = (songs.sumOf { it.size } + videos.sumOf { it.size }) / (1024 * 1024 * 1024.0)
        )
    }

    // --- DISCOVERY ORBIT RECOMMENDATIONS ---
    fun sintonizarCanalIA() {
        _isRecommendationLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val historySummary = playbackHistory.value.take(5).joinToString(", ") { "${it.title} por ${it.artist}" }
            val favoritesSummary = favoriteSongs.value.take(5).joinToString(", ") { "${it.title} (${it.artist})" }

            val response = GeminiClient.getSpaceDiscoveryRecommendations(
                historySummary = if (historySummary.isEmpty()) "Ninguno en órbita" else historySummary,
                favoritesSummary = if (favoritesSummary.isEmpty()) "Ninguno en órbita" else favoritesSummary
            )

            _aiRecommendation.value = response
            _isRecommendationLoading.value = false
        }
    }

    // --- SYSTEM UTILS ---
    fun clearCache() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            repository.setConfig("language", "es")
            repository.setConfig("theme", "Galaxy Dark")
            repository.setConfig("accent_color", "Purple")
            repository.setConfig("custom_bg", "1")
            repository.setConfig("eq_preset", "Normal")
            repository.setConfig("eq_bass", "50")
            repository.setConfig("eq_mid", "50")
            repository.setConfig("eq_treble", "50")
            loadConfigs()
        }
    }
}

data class ListeningStats(
    val totalTimeMinutes: Int,
    val totalPlays: Int,
    val favoritesCount: Int,
    val videosWatched: Int,
    val topArtist: String,
    val topAlbum: String,
    val songsScannedCount: Int,
    val videosScannedCount: Int,
    val totalGbSize: Double
)
