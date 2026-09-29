package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutesLeft: Int? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val equalizerEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    val bandLevels: List<Short> = emptyList(),
    val presetNames: List<String> = emptyList(),
    val currentPreset: Int = -1,
    val errorMessage: String? = null
)

class PlayerController(private val context: Context) {

    private val tag = "BeatStreamPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    var onTrackChangedListener: ((Track) -> Unit)? = null

    init {
        initLocks()
    }

    private fun initLocks() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeatStream:AudioWakeLock")

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BeatStream:WifiLock")
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire wake/wifi locks", e)
        }
    }

    private fun getOrCreatePlayer(): MediaPlayer {
        mediaPlayer?.let { return it }
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)

            setOnPreparedListener { mp ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = true,
                        durationMs = mp.duration.toLong()
                    )
                }
                mp.start()
                applyPlaybackSpeed(_state.value.playbackSpeed)
                startProgressTracker()
                initAudioFx(mp.audioSessionId)
            }

            setOnCompletionListener {
                handleTrackCompletion()
            }

            setOnErrorListener { _, what, extra ->
                Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        errorMessage = "Stream playback error. Reconnecting..."
                    )
                }
                // Try fallback or next
                skipNext()
                true
            }
        }
        mediaPlayer = player
        return player
    }

    private fun initAudioFx(sessionId: Int) {
        try {
            if (sessionId != 0) {
                equalizer?.release()
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = _state.value.equalizerEnabled
                }

                bassBoost?.release()
                bassBoost = BassBoost(0, sessionId).apply {
                    enabled = _state.value.equalizerEnabled
                    setStrength(_state.value.bassBoostStrength)
                }

                equalizer?.let { eq ->
                    val numBands = eq.numberOfBands.toInt()
                    val levels = mutableListOf<Short>()
                    for (i in 0 until numBands) {
                        levels.add(eq.getBandLevel(i.toShort()))
                    }

                    val presets = mutableListOf<String>()
                    val numPresets = eq.numberOfPresets.toInt()
                    for (p in 0 until numPresets) {
                        presets.add(eq.getPresetName(p.toShort()))
                    }

                    _state.update {
                        it.copy(
                            bandLevels = levels,
                            presetNames = presets
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "AudioFX not available on this device", e)
        }
    }

    fun playTrack(track: Track, newQueue: List<Track>? = null) {
        val currentQueue = if (newQueue != null && newQueue.isNotEmpty()) {
            newQueue
        } else if (_state.value.queue.none { it.id == track.id }) {
            listOf(track) + _state.value.queue
        } else {
            _state.value.queue
        }

        val trackIdx = currentQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _state.update {
            it.copy(
                currentTrack = track,
                queue = currentQueue,
                queueIndex = trackIdx,
                isLoading = true,
                errorMessage = null,
                currentPositionMs = 0L,
                durationMs = (track.durationSeconds * 1000).toLong()
            )
        }

        onTrackChangedListener?.invoke(track)

        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
        wifiLock?.acquire()

        try {
            val player = getOrCreatePlayer()
            player.reset()
            val url = if (track.streamUrl.isNotBlank()) track.streamUrl else "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3"
            player.setDataSource(url)
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start playback for track ${track.title}", e)
            _state.update { it.copy(isLoading = false, errorMessage = "Could not load track") }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _state.update { it.copy(isPlaying = false) }
            stopProgressTracker()
        } else {
            player.start()
            _state.update { it.copy(isPlaying = true) }
            startProgressTracker()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _state.update { s -> s.copy(isPlaying = false) }
                stopProgressTracker()
            }
        }
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _state.update { s -> s.copy(isPlaying = true) }
                startProgressTracker()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _state.update { it.copy(currentPositionMs = positionMs) }
    }

    fun skipNext() {
        val queue = _state.value.queue
        if (queue.isEmpty()) return

        var nextIdx = _state.value.queueIndex + 1
        if (nextIdx >= queue.size) {
            if (_state.value.repeatMode == RepeatMode.ALL) {
                nextIdx = 0
            } else {
                return
            }
        }

        val nextTrack = queue[nextIdx]
        _state.update { it.copy(queueIndex = nextIdx) }
        playTrack(nextTrack)
    }

    fun skipPrevious() {
        // If current position > 3 seconds, replay track
        if (_state.value.currentPositionMs > 3000) {
            seekTo(0)
            return
        }

        val queue = _state.value.queue
        if (queue.isEmpty()) return

        var prevIdx = _state.value.queueIndex - 1
        if (prevIdx < 0) {
            prevIdx = if (_state.value.repeatMode == RepeatMode.ALL) queue.size - 1 else 0
        }

        val prevTrack = queue[prevIdx]
        _state.update { it.copy(queueIndex = prevIdx) }
        playTrack(prevTrack)
    }

    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeatMode = nextMode) }
    }

    fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        _state.update { it.copy(isShuffle = newShuffle) }
        if (newShuffle && _state.value.queue.isNotEmpty()) {
            val current = _state.value.currentTrack
            val remaining = _state.value.queue.filter { it.id != current?.id }.shuffled()
            val newQueue = if (current != null) listOf(current) + remaining else remaining
            _state.update { it.copy(queue = newQueue, queueIndex = 0) }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.update { it.copy(playbackSpeed = speed) }
        applyPlaybackSpeed(speed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Playback speed change failed", e)
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerMinutesLeft = null) }
            return
        }

        _state.update { it.copy(sleepTimerMinutesLeft = minutes) }
        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                _state.update { it.copy(sleepTimerMinutesLeft = remaining) }
            }
            // Timer expired: fade out and pause
            pause()
            _state.update { it.copy(sleepTimerMinutesLeft = null) }
        }
    }

    fun addToQueue(track: Track) {
        _state.update { it.copy(queue = it.queue + track) }
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _state.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            val newIdx = if (index < _state.value.queueIndex) {
                _state.value.queueIndex - 1
            } else {
                _state.value.queueIndex
            }.coerceAtMost((currentQueue.size - 1).coerceAtLeast(0))

            _state.update { it.copy(queue = currentQueue, queueIndex = newIdx) }
        }
    }

    fun clearQueue() {
        val current = _state.value.currentTrack
        _state.update {
            it.copy(
                queue = if (current != null) listOf(current) else emptyList(),
                queueIndex = 0
            )
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        _state.update { it.copy(equalizerEnabled = enabled) }
    }

    fun setBassBoost(strength: Short) {
        bassBoost?.setStrength(strength)
        _state.update { it.copy(bassBoostStrength = strength) }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            val levels = _state.value.bandLevels.toMutableList()
            if (band.toInt() in levels.indices) {
                levels[band.toInt()] = level
                _state.update { it.copy(bandLevels = levels, currentPreset = -1) }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set band level", e)
        }
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            val levels = mutableListOf<Short>()
            for (i in 0 until numBands) {
                levels.add(equalizer?.getBandLevel(i.toShort()) ?: 0)
            }
            _state.update {
                it.copy(
                    currentPreset = presetIndex.toInt(),
                    bandLevels = levels
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to apply preset", e)
        }
    }

    private fun handleTrackCompletion() {
        when (_state.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                mediaPlayer?.start()
                _state.update { it.copy(isPlaying = true) }
            }
            RepeatMode.ALL -> {
                skipNext()
            }
            RepeatMode.OFF -> {
                if (_state.value.queueIndex < _state.value.queue.size - 1) {
                    skipNext()
                } else {
                    _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                    stopProgressTracker()
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _state.update {
                            it.copy(
                                currentPositionMs = mp.currentPosition.toLong(),
                                durationMs = mp.duration.toLong().coerceAtLeast(1L)
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    fun release() {
        stopProgressTracker()
        sleepTimerJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null

        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }
}
