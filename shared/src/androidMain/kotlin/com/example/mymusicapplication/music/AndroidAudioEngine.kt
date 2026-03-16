package com.example.mymusicapplication.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidAudioEngine(
    private val context: Context,
) : AudioEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playbackState = MutableStateFlow(EnginePlaybackState())
    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = 4)

    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()
    override val events: Flow<AudioEngineEvent> = _events.asSharedFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var cachedVolume: Float = 1f
    private var cachedSpeed: Float = 1f

    override suspend fun prepare(track: AudioTrack, playWhenReady: Boolean, positionMs: Long) {
        withContext(Dispatchers.Main.immediate) {
            releasePlayer()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setOnPreparedListener {
                    val safeDuration = runCatching { duration.toLong() }.getOrDefault(track.durationMs)
                    val clampedPosition = positionMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
                    _playbackState.value = EnginePlaybackState(
                        currentTrack = track,
                        isPlaying = false,
                        positionMs = clampedPosition,
                        durationMs = safeDuration,
                        isPrepared = true,
                    )
                    if (clampedPosition > 0L) {
                        runCatching {
                            seekTo(clampedPosition.toInt())
                        }
                    }
                    setVolume(cachedVolume, cachedVolume)
                    applyPlaybackSpeed(this, cachedSpeed)
                    if (playWhenReady) {
                        start()
                        startProgressUpdates()
                        _playbackState.update { state -> state.copy(isPlaying = true) }
                    }
                }
                setOnCompletionListener {
                    progressJob?.cancel()
                    _playbackState.update { state ->
                        state.copy(
                            isPlaying = false,
                            positionMs = state.durationMs,
                        )
                    }
                    _events.tryEmit(AudioEngineEvent.TrackEnded)
                }
                setOnErrorListener { _, _, _ ->
                    progressJob?.cancel()
                    _playbackState.update { state -> state.copy(isPlaying = false) }
                    _events.tryEmit(AudioEngineEvent.Error("音频播放失败，请检查文件是否仍然可用"))
                    true
                }
            }

            mediaPlayer = player
            runCatching {
                player.setDataSource(context, Uri.parse(track.uri))
                player.prepareAsync()
            }.onFailure {
                _playbackState.value = EnginePlaybackState(currentTrack = track)
                _events.tryEmit(AudioEngineEvent.Error(it.message ?: "无法加载音频文件"))
            }
        }
    }

    override suspend fun play() {
        withContext(Dispatchers.Main.immediate) {
            val player = mediaPlayer ?: return@withContext
            runCatching {
                player.start()
                startProgressUpdates()
                _playbackState.update { it.copy(isPlaying = true) }
            }.onFailure {
                _events.tryEmit(AudioEngineEvent.Error(it.message ?: "无法开始播放"))
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            val player = mediaPlayer ?: return@withContext
            runCatching {
                if (player.isPlaying) {
                    player.pause()
                }
                progressJob?.cancel()
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        positionMs = player.safeCurrentPosition(),
                    )
                }
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) {
            releasePlayer()
            _playbackState.value = EnginePlaybackState()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main.immediate) {
            val player = mediaPlayer ?: return@withContext
            runCatching {
                player.seekTo(positionMs.toInt())
                _playbackState.update { state -> state.copy(positionMs = positionMs.coerceAtLeast(0L)) }
            }
        }
    }

    override suspend fun setVolume(volume: Float) {
        cachedVolume = volume.coerceIn(0f, 1f)
        withContext(Dispatchers.Main.immediate) {
            mediaPlayer?.setVolume(cachedVolume, cachedVolume)
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        cachedSpeed = speed.coerceIn(0.5f, 2f)
        withContext(Dispatchers.Main.immediate) {
            mediaPlayer?.let { applyPlaybackSpeed(it, cachedSpeed) }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.Main.immediate) {
            releasePlayer()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null) {
                    _playbackState.update { state ->
                        state.copy(
                            positionMs = player.safeCurrentPosition(),
                            durationMs = player.safeDuration(state.durationMs),
                            isPlaying = player.isPlaying,
                        )
                    }
                }
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.runCatching {
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun applyPlaybackSpeed(player: MediaPlayer, speed: Float) {
        runCatching {
            player.playbackParams = PlaybackParams().setSpeed(speed)
        }
    }

    private fun MediaPlayer.safeCurrentPosition(): Long = runCatching { currentPosition.toLong() }.getOrDefault(0L)

    private fun MediaPlayer.safeDuration(fallback: Long): Long = runCatching { duration.toLong() }.getOrDefault(fallback)

    private companion object {
        const val PROGRESS_POLL_MS = 500L
    }
}

