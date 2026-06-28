package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- SONG ENTITY ---
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in ms
    val size: Long,     // in bytes
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val genre: String = "Cosmic",
    val quality: String = "320kbps",
    val isVirtual: Boolean = false, // if true, it's a built-in cosmic stream/synth track
    val streamUrl: String? = null    // if virtual, can play online royalty-free track
)

// --- VIDEO ENTITY ---
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val title: String,
    val duration: Long, // in ms
    val size: Long,     // in bytes
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val resolution: String = "1080p",
    val quality: String = "FHD",
    val lastPosition: Long = 0, // for resume
    val isVirtual: Boolean = false,
    val streamUrl: String? = null
)

// --- PLAYLIST ENTITY ---
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

// --- PLAYLIST SONG CROSS REFERENCE ---
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: Int
)

// --- HISTORY ENTITY ---
@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: Int,
    val mediaType: String, // "audio" or "video"
    val title: String,
    val artist: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- CONFIG ENTITY (SETTINGS) ---
@Entity(tableName = "settings_config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)

// --- DAOS ---

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY lastPlayed DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayed > 0 ORDER BY lastPlayed DESC")
    fun getRecentlyPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT 20")
    fun getMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Int): SongEntity?

    @Query("SELECT * FROM songs WHERE filePath = :filePath")
    suspend fun getSongByPath(filePath: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE isVirtual = 0")
    suspend fun clearLocalSongs()
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY title ASC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY lastPlayed DESC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE lastPlayed > 0 ORDER BY lastPlayed DESC")
    fun getRecentlyPlayedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Int): VideoEntity?

    @Query("SELECT * FROM videos WHERE filePath = :filePath")
    suspend fun getVideoByPath(filePath: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE isVirtual = 0")
    suspend fun clearLocalVideos()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // Playlist details
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY s.title ASC")
    fun getSongsInPlaylist(playlistId: Int): Flow<List<SongEntity>>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT 100")
    fun getPlaybackHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM settings_config")
    fun getAllConfigsFlow(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM settings_config")
    suspend fun getAllConfigs(): List<ConfigEntity>

    @Query("SELECT value FROM settings_config WHERE `key` = :key")
    suspend fun getConfigValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConfigEntity)
}

// --- DATABASE CLASS ---
@Database(
    entities = [
        SongEntity::class,
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class,
        ConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SpaceDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun configDao(): ConfigDao

    companion object {
        @Volatile
        private var INSTANCE: SpaceDatabase? = null

        fun getDatabase(context: Context): SpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpaceDatabase::class.java,
                    "space_music_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
