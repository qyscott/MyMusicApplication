package com.example.mymusicapplication.music

import kotlinx.coroutines.flow.StateFlow

/** Shared music metadata used by UI, repository, and platform engines. */
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String? = null,
    val lastModified: Long = 0L,
    val isFavorite: Boolean = false,
)

enum class PlaybackMode {
    SEQUENTIAL,
    REPEAT_ONE,
    SHUFFLE,
}

data class PlaybackPreferences(
    val mode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val volume: Float = 1f,
    val speed: Float = 1f,
)

data class EnginePlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPrepared: Boolean = false,
)

sealed interface AudioEngineEvent {
    data object TrackEnded : AudioEngineEvent
    data class Error(val message: String) : AudioEngineEvent
}

interface AudioEngine {
    val playbackState: StateFlow<EnginePlaybackState>
    val events: kotlinx.coroutines.flow.Flow<AudioEngineEvent>

    suspend fun prepare(track: AudioTrack, playWhenReady: Boolean = true, positionMs: Long = 0L)
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun release()
}

interface MusicScanner {
    suspend fun scanLocalTracks(): List<AudioTrack>
}

data class PlaybackUiState(
    val library: List<AudioTrack> = emptyList(),
    val favorites: List<AudioTrack> = emptyList(),
    val queue: List<AudioTrack> = emptyList(),
    val currentTrack: AudioTrack? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val isRefreshingLibrary: Boolean = false,
    val errorMessage: String? = null,
)

