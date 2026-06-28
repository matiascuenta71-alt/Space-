package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.database.SongEntity
import com.example.data.repository.SpaceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

enum class PlaybackState { IDLE, BUFFERING, PLAYING, PAUSED, STOPPED }
enum class RepeatMode { NONE, ONE, ALL }

object SpacePlayerManager {

    private const val TAG = "SpacePlayerManager"

    private var mediaPlayer: MediaPlayer? = null
    private var repository: SpaceRepository? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- STATE FLOWS ---
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _queue = MutableStateFlow<List<SongEntity>>(emptyList())
    val queue: StateFlow<List<SongEntity>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    // --- SLEEP TIMER STATE ---
    private val _sleepTimerMinutesLeft = MutableStateFlow(0)
    val sleepTimerMinutesLeft: StateFlow<Int> = _sleepTimerMinutesLeft.asStateFlow()
    private var sleepTimerJob: Job? = null

    // --- LYRICS STATE ---
    private val _currentLrcLine = MutableStateFlow("")
    val currentLrcLine: StateFlow<String> = _currentLrcLine.asStateFlow()
    private var parsedLyrics: List<LrcLine> = emptyList()

    // Position updates tracker
    private var progressTrackerJob: Job? = null
    private var fadeJob: Job? = null

    fun initialize(repo: SpaceRepository) {
        this.repository = repo
    }

    // --- MAIN CONTROLS ---

    fun playSong(song: SongEntity, newQueue: List<SongEntity> = emptyList()) {
        scope.launch {
            if (newQueue.isNotEmpty()) {
                _queue.value = newQueue
                _queueIndex.value = newQueue.indexOfFirst { it.id == song.id }
            } else if (!_queue.value.contains(song)) {
                val currentQueue = _queue.value.toMutableList()
                currentQueue.add(song)
                _queue.value = currentQueue
                _queueIndex.value = currentQueue.size - 1
            } else {
                _queueIndex.value = _queue.value.indexOfFirst { it.id == song.id }
            }

            _currentSong.value = song
            stopMediaPlayer()
            _playbackState.value = PlaybackState.BUFFERING

            try {
                mediaPlayer = MediaPlayer().apply {
                    if (song.isVirtual && song.streamUrl != null) {
                        setDataSource(song.streamUrl)
                    } else {
                        setDataSource(song.filePath)
                    }

                    // Speed config
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        playbackParams = playbackParams.setSpeed(_playbackSpeed.value)
                    }

                    setVolume(_volume.value, _volume.value)

                    setOnPreparedListener { mp ->
                        _duration.value = mp.duration.toLong()
                        _playbackState.value = PlaybackState.PLAYING
                        mp.start()
                        startProgressTracker()
                        applyFadeIn()
                        // Record to stats/history
                        scope.launch(Dispatchers.IO) {
                            repository?.addHistory(song.id, "audio", song.title, song.artist)
                            val updatedSong = song.copy(
                                playCount = song.playCount + 1,
                                lastPlayed = System.currentTimeMillis()
                            )
                            repository?.updateSong(updatedSong)
                        }
                    }

                    setOnCompletionListener {
                        handleSongCompletion()
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        _playbackState.value = PlaybackState.STOPPED
                        handleNext() // skip on error
                        true
                    }

                    prepareAsync()
                }

                // Parse and track lyrics
                loadLyricsForSong(song)

            } catch (e: Exception) {
                Log.e(TAG, "Error playing song: ${song.title}", e)
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            applyFadeOut {
                player.pause()
                _playbackState.value = PlaybackState.PAUSED
                stopProgressTracker()
            }
        } else {
            player.start()
            _playbackState.value = PlaybackState.PLAYING
            startProgressTracker()
            applyFadeIn()
        }
    }

    fun handleNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        var nextIndex = _queueIndex.value + 1
        if (_isShuffle.value) {
            nextIndex = (q.indices).random()
        } else if (nextIndex >= q.size) {
            nextIndex = if (_repeatMode.value == RepeatMode.ALL) 0 else return
        }

        if (nextIndex in q.indices) {
            playSong(q[nextIndex])
        }
    }

    fun handlePrevious() {
        val q = _queue.value
        if (q.isEmpty()) return

        var prevIndex = _queueIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = if (_repeatMode.value == RepeatMode.ALL) q.size - 1 else 0
        }

        if (prevIndex in q.indices) {
            playSong(q[prevIndex])
        }
    }

    fun stop() {
        applyFadeOut {
            stopMediaPlayer()
            _playbackState.value = PlaybackState.STOPPED
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPosition.value = positionMs
        updateLyricsLine(positionMs)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { player ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player.isPlaying) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set playback speed", e)
                }
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        mediaPlayer?.setVolume(vol, vol)
    }

    // --- SLEEP TIMER ---

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = minutes
        if (minutes <= 0) return

        sleepTimerJob = scope.launch {
            while (_sleepTimerMinutesLeft.value > 0) {
                delay(60000) // 1 minute
                _sleepTimerMinutesLeft.value -= 1
            }
            // Stop playback when timer ends
            stop()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = 0
    }

    // --- FADE IN / FADE OUT EFFECTS ---

    private fun applyFadeIn() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            var currentVol = 0.0f
            val targetVol = _volume.value
            mediaPlayer?.setVolume(0.0f, 0.0f)
            while (currentVol < targetVol) {
                currentVol += 0.05f
                if (currentVol > targetVol) currentVol = targetVol
                mediaPlayer?.setVolume(currentVol, currentVol)
                delay(30) // smooth step
            }
        }
    }

    private fun applyFadeOut(onComplete: () -> Unit) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            var currentVol = _volume.value
            while (currentVol > 0.0f) {
                currentVol -= 0.05f
                if (currentVol < 0.0f) currentVol = 0.0f
                mediaPlayer?.setVolume(currentVol, currentVol)
                delay(30)
            }
            onComplete()
            // Reset player volume back to the set master volume
            mediaPlayer?.setVolume(_volume.value, _volume.value)
        }
    }

    // --- INTERNAL HELPER METHODS ---

    private fun handleSongCompletion() {
        if (_repeatMode.value == RepeatMode.ONE) {
            _currentSong.value?.let { playSong(it) }
        } else {
            handleNext()
        }
    }

    private fun stopMediaPlayer() {
        stopProgressTracker()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing media player", e)
            }
        }
        mediaPlayer = null
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.toLong()
                        _currentPosition.value = pos
                        updateLyricsLine(pos)
                    }
                }
                delay(200) // update 5 times per sec
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    // --- LYRICS INTEGRATION ---

    private fun loadLyricsForSong(song: SongEntity) {
        scope.launch {
            _currentLrcLine.value = "🌌 Cargando letras desde la red neural..."
            // Generate or fetch lyrics
            val lyricsString = com.example.api.GeminiClient.getSynchronizedLyrics(song.title, song.artist)
            parsedLyrics = parseLrc(lyricsString)
            _currentLrcLine.value = if (parsedLyrics.isNotEmpty()) parsedLyrics.first().text else "🛸 Transmisión instrumental de la galaxia."
        }
    }

    private fun updateLyricsLine(posMs: Long) {
        if (parsedLyrics.isEmpty()) return
        
        var matchingLine = parsedLyrics.firstOrNull()?.text ?: ""
        for (line in parsedLyrics) {
            if (posMs >= line.timestampMs) {
                matchingLine = line.text
            } else {
                break
            }
        }
        _currentLrcLine.value = matchingLine
    }

    private fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d+))?]\\s*(.*)")
        
        lrcContent.split("\n").forEach { rawLine ->
            val match = regex.find(rawLine.trim())
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msFraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val text = match.groupValues[4]
                
                val timestamp = (min * 60 * 1000) + (sec * 1000) + msFraction
                lines.add(LrcLine(timestamp, text))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}

data class LrcLine(val timestampMs: Long, val text: String)
