package com.example.mymusicapplication.music

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SqlDriverFactory> { IosSqlDriverFactory() }
    single<MusicScanner> { IosMusicScanner() }
    single<AudioEngine> { IosAudioEngine() }
}

private class IosSqlDriverFactory : SqlDriverFactory {
    override fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = musicDatabaseSchema,
        name = "music.db",
    )
}

private class IosMusicScanner : MusicScanner {
    override suspend fun scanLocalTracks(): List<AudioTrack> = emptyList()
}

private class IosAudioEngine : AudioEngine {
    private val state = MutableStateFlow(EnginePlaybackState())
    private val eventFlow = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = 1)

    override val playbackState: StateFlow<EnginePlaybackState> = state.asStateFlow()
    override val events: Flow<AudioEngineEvent> = eventFlow.asSharedFlow()

    override suspend fun prepare(track: AudioTrack, playWhenReady: Boolean, positionMs: Long) {
        state.value = EnginePlaybackState(
            currentTrack = track,
            isPlaying = playWhenReady,
            positionMs = positionMs,
            durationMs = track.durationMs,
            isPrepared = true,
        )
    }

    override suspend fun play() {
        state.value = state.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        state.value = state.value.copy(isPlaying = false)
    }

    override suspend fun stop() {
        state.value = EnginePlaybackState()
    }

    override suspend fun seekTo(positionMs: Long) {
        state.value = state.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun setPlaybackSpeed(speed: Float) = Unit

    override suspend fun release() = Unit
}

