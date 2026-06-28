package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SpaceRepository(private val context: Context, private val database: SpaceDatabase) {

    private val songDao = database.songDao()
    private val videoDao = database.videoDao()
    private val playlistDao = database.playlistDao()
    private val historyDao = database.historyDao()
    private val configDao = database.configDao()

    // Expose flows to ViewModels
    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<SongEntity>> = songDao.getFavoriteSongs()
    val recentlyPlayedSongs: Flow<List<SongEntity>> = songDao.getRecentlyPlayedSongs()
    val mostPlayedSongs: Flow<List<SongEntity>> = songDao.getMostPlayedSongs()

    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    val favoriteVideos: Flow<List<VideoEntity>> = videoDao.getFavoriteVideos()
    val recentlyPlayedVideos: Flow<List<VideoEntity>> = videoDao.getRecentlyPlayedVideos()

    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    val playbackHistory: Flow<List<HistoryEntity>> = historyDao.getPlaybackHistory()
    val configsFlow: Flow<List<ConfigEntity>> = configDao.getAllConfigsFlow()

    // --- MANAGE CONFIGS / SETTINGS ---
    suspend fun getConfig(key: String, defaultValue: String): String {
        return configDao.getConfigValue(key) ?: defaultValue
    }

    suspend fun setConfig(key: String, value: String) {
        configDao.insertConfig(ConfigEntity(key, value))
    }

    // --- MANAGE SONGS ---
    suspend fun insertSong(song: SongEntity): Long = songDao.insertSong(song)
    suspend fun updateSong(song: SongEntity) = songDao.updateSong(song)
    suspend fun deleteSong(song: SongEntity) = songDao.deleteSong(song)

    // --- MANAGE VIDEOS ---
    suspend fun insertVideo(video: VideoEntity): Long = videoDao.insertVideo(video)
    suspend fun updateVideo(video: VideoEntity) = videoDao.updateVideo(video)
    suspend fun deleteVideo(video: VideoEntity) = videoDao.deleteVideo(video)

    // --- MANAGE PLAYLISTS ---
    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        playlistDao.updatePlaylist(playlist.copy(name = newName))
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: Int) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    // --- MANAGE PLAYBACK HISTORY ---
    suspend fun addHistory(mediaId: Int, mediaType: String, title: String, artist: String) {
        historyDao.insertHistory(
            HistoryEntity(
                mediaId = mediaId,
                mediaType = mediaType,
                title = title,
                artist = artist
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    // --- AUTO-SCANNING LOCAL STORAGE & FALLBACK PRE-SEED ---
    suspend fun scanMedia(forceSeed: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_VIDEO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }

            val hasAudioPerm = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
            val hasVideoPerm = ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED

            var scannedSongsCount = 0
            var scannedVideosCount = 0

            if (hasAudioPerm) {
                scannedSongsCount = scanLocalSongs()
            }
            if (hasVideoPerm) {
                scannedVideosCount = scanLocalVideos()
            }

            // If no physical media scanned, or forced, pre-seed beautiful Virtual Space Content
            val existingVirtualSongs = database.songDao().getSongByPath("virtual://andromeda")
            if (existingVirtualSongs == null || forceSeed || (scannedSongsCount == 0 && scannedVideosCount == 0)) {
                preSeedVirtualCosmicContent()
            }

        } catch (e: Exception) {
            Log.e("SpaceRepository", "Error during media scanning", e)
            preSeedVirtualCosmicContent()
        }
    }

    private suspend fun scanLocalSongs(): Int {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        val cursor: Cursor? = resolver.query(uri, projection, selection, null, null)
        var count = 0
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (c.moveToNext()) {
                val filePath = c.getString(dataCol)
                val title = c.getString(titleCol) ?: "Unknown Track"
                val artist = c.getString(artistCol) ?: "Unknown Cosmic Artist"
                val album = c.getString(albumCol) ?: "Space Void"
                val duration = c.getLong(durationCol)
                val size = c.getLong(sizeCol)

                // Skip scanning if already inserted
                val existing = songDao.getSongByPath(filePath)
                if (existing == null) {
                    val song = SongEntity(
                        filePath = filePath,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        size = size,
                        genre = "Local Audio",
                        quality = "FLAC/MP3"
                    )
                    songDao.insertSong(song)
                    count++
                }
            }
        }
        return count
    }

    private suspend fun scanLocalVideos(): Int {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RESOLUTION
        )

        val cursor: Cursor? = resolver.query(uri, projection, null, null, null)
        var count = 0
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val resCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)

            while (c.moveToNext()) {
                val filePath = c.getString(dataCol)
                val title = c.getString(titleCol) ?: "Cosmic Horizon Video"
                val duration = c.getLong(durationCol)
                val size = c.getLong(sizeCol)
                val resolution = c.getString(resCol) ?: "1080p"

                val existing = videoDao.getVideoByPath(filePath)
                if (existing == null) {
                    val video = VideoEntity(
                        filePath = filePath,
                        title = title,
                        duration = duration,
                        size = size,
                        resolution = resolution,
                        quality = "Local Video"
                    )
                    videoDao.insertVideo(video)
                    count++
                }
            }
        }
        return count
    }

    // --- SEED BEAUTIFUL COSMIC CONTENT ---
    suspend fun preSeedVirtualCosmicContent() {
        val virtualSongs = listOf(
            SongEntity(
                id = 1001,
                filePath = "virtual://andromeda",
                title = "Andromeda Cosmic Symphony",
                artist = "Galaxion",
                album = "Nebula Voyager",
                duration = 240000, // 4 mins
                size = 9600000,
                genre = "Cosmic Ambient",
                quality = "Hi-Res FLAC 24-bit",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            ),
            SongEntity(
                id = 1002,
                filePath = "virtual://supernova",
                title = "Supernova Deep Bass",
                artist = "Pulsar Project",
                album = "Gravitational Beats",
                duration = 180000, // 3 mins
                size = 7200000,
                genre = "Psychedelic Synth",
                quality = "320kbps MP3",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
            ),
            SongEntity(
                id = 1003,
                filePath = "virtual://hyperdrive",
                title = "Hyperdrive Galactic Synthwave",
                artist = "Stellar Rider",
                album = "Neon Wormhole",
                duration = 210000, // 3.5 mins
                size = 8400000,
                genre = "Synthwave",
                quality = "320kbps MP3",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
            ),
            SongEntity(
                id = 1004,
                filePath = "virtual://nebular",
                title = "Nebular Serenade (Slow Orbit)",
                artist = "Orion Sleep",
                album = "Astral Relaxation",
                duration = 300000, // 5 mins
                size = 12000000,
                genre = "Lofi Ambient",
                quality = "Master FLAC",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
            ),
            SongEntity(
                id = 1005,
                filePath = "virtual://blackhole",
                title = "Black Hole Gravity Echoes",
                artist = "Event Horizon",
                album = "Singularity",
                duration = 150000, // 2.5 mins
                size = 6000000,
                genre = "Dark Ambient Drone",
                quality = "320kbps MP3",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
            ),
            SongEntity(
                id = 1006,
                filePath = "virtual://solar",
                title = "Solar Wind Odyssey",
                artist = "Aurora Project",
                album = "Thermal Radiance",
                duration = 270000, // 4.5 mins
                size = 10800000,
                genre = "Progressive Ambient",
                quality = "Hi-Res FLAC",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"
            ),
            SongEntity(
                id = 1007,
                filePath = "virtual://kepler",
                title = "Exoplanet Kepler-186f Vibes",
                artist = "Kepler Crew",
                album = "Deep Exploration",
                duration = 195000, // 3.25 mins
                size = 7800000,
                genre = "Chill Space Synth",
                quality = "320kbps MP3",
                isVirtual = true,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"
            )
        )

        val virtualVideos = listOf(
            VideoEntity(
                id = 2001,
                filePath = "virtual_video://nebula_flyby",
                title = "Andromeda Deep Flyby Simulation",
                duration = 120000, // 2 mins
                size = 45000000,
                resolution = "1920x1080 (FHD)",
                quality = "60 FPS Master",
                isVirtual = true,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" // Stable Google streaming sample
            ),
            VideoEntity(
                id = 2002,
                filePath = "virtual_video://saturn_rings",
                title = "Saturn Rings Overflight Cinematic",
                duration = 90000, // 1.5 mins
                size = 32000000,
                resolution = "3840x2160 (4K UHD)",
                quality = "HDR Galactic Prime",
                isVirtual = true,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
            ),
            VideoEntity(
                id = 2003,
                filePath = "virtual_video://wormhole_tunnel",
                title = "Relativistic Wormhole Gravitational Warp",
                duration = 150000, // 2.5 mins
                size = 58000000,
                resolution = "1920x1080 (FHD)",
                quality = "Supermassive High Speed",
                isVirtual = true,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            )
        )

        songDao.insertSongs(virtualSongs)
        videoDao.insertVideos(virtualVideos)

        // Seed default playlists if database is empty
        val playlists = database.playlistDao().getAllPlaylists()
        // Wait, since we are returning Flow, we can insert standard Cosmic default playlists
        // directly.
        if (database.playlistDao().getPlaylistById(1) == null) {
            val pl1 = database.playlistDao().insertPlaylist(PlaylistEntity(id = 1, name = "Cosmic Relaxation"))
            val pl2 = database.playlistDao().insertPlaylist(PlaylistEntity(id = 2, name = "Warp Drive Synth"))
            
            // Add some virtual songs to these playlists
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(1, 1001))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(1, 1004))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(1, 1005))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(2, 1002))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(2, 1003))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(2, 1007))
        }
    }
}
