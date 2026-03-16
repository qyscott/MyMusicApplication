package com.example.mymusicapplication.music

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val firstTrack = AudioTrack(
        id = "1",
        title = "Song A",
        artist = "Artist A",
        durationMs = 180_000,
        uri = "content://music/1",
    )
    private val secondTrack = AudioTrack(
        id = "2",
        title = "Song B",
        artist = "Artist B",
        durationMs = 200_000,
        uri = "content://music/2",
    )

    @Test
    fun `play next advances in sequential mode`() = runTest(dispatcher.scheduler) {
        val repository = FakeMusicRepository(listOf(firstTrack, secondTrack))
        val engine = FakeAudioEngine()
        val settings = PlaybackSettingsRepository()
        val controller = PlaybackController(repository, engine, settings, dispatcher)

        controller.playTrack(firstTrack.id)
        advanceUntilIdle()
        controller.playNext()
        advanceUntilIdle()

        assertEquals(secondTrack.id, controller.uiState.value.currentTrack?.id)
        assertEquals(1, controller.uiState.value.currentIndex)
        assertTrue(controller.uiState.value.isPlaying)
    }

    @Test
    fun `repeat one keeps current track on auto next`() = runTest(dispatcher.scheduler) {
        val repository = FakeMusicRepository(listOf(firstTrack, secondTrack))
        val engine = FakeAudioEngine()
        val settings = PlaybackSettingsRepository()
        val controller = PlaybackController(repository, engine, settings, dispatcher)

        controller.playTrack(firstTrack.id)
        advanceUntilIdle()
        controller.setPlaybackMode(PlaybackMode.REPEAT_ONE)
        advanceUntilIdle()
        controller.playNext(autoTriggered = true)
        advanceUntilIdle()

        assertEquals(firstTrack.id, controller.uiState.value.currentTrack?.id)
        assertEquals(0, controller.uiState.value.currentIndex)
    }

    @Test
    fun `toggle favorite updates favorites flow`() = runTest(dispatcher.scheduler) {
        val repository = FakeMusicRepository(listOf(firstTrack, secondTrack))
        val engine = FakeAudioEngine()
        val settings = PlaybackSettingsRepository()
        val controller = PlaybackController(repository, engine, settings, dispatcher)

        controller.toggleFavorite(secondTrack.id)
        advanceUntilIdle()

        assertEquals(listOf(secondTrack.id), controller.uiState.value.favorites.map(AudioTrack::id))
        assertTrue(controller.uiState.value.library.first { it.id == secondTrack.id }.isFavorite)
    }

    @Test
    fun `play previous rewinds current track when progress passed threshold`() = runTest(dispatcher.scheduler) {
        val repository = FakeMusicRepository(listOf(firstTrack, secondTrack))
        val engine = FakeAudioEngine()
        val settings = PlaybackSettingsRepository()
        val controller = PlaybackController(repository, engine, settings, dispatcher)

        controller.playTrack(secondTrack.id)
        advanceUntilIdle()
        controller.seekTo(10_000)
        advanceUntilIdle()

        controller.playPrevious()
        advanceUntilIdle()

        assertEquals(secondTrack.id, controller.uiState.value.currentTrack?.id)
        assertEquals(0L, controller.uiState.value.progressMs)
        assertFalse(controller.uiState.value.currentIndex < 0)
    }
}

private class FakeMusicRepository(initialTracks: List<AudioTrack>) : MusicRepository {
    private val tracksState = MutableStateFlow(initialTracks)

    override val library: Flow<List<AudioTrack>> = tracksState
    override val favorites: Flow<List<AudioTrack>> = tracksState.map { tracks -> tracks.filter(AudioTrack::isFavorite) }

    override suspend fun refreshLibrary(): Result<Int> = Result.success(tracksState.value.size)

    override suspend fun getTrack(trackId: String): AudioTrack? = tracksState.value.firstOrNull { it.id == trackId }

    override suspend fun toggleFavorite(trackId: String): Boolean {
        var newValue = false
        tracksState.value = tracksState.value.map { track ->
            if (track.id == trackId) {
                newValue = !track.isFavorite
                track.copy(isFavorite = newValue)
            } else {
                track
            }
        }
        return newValue
    }
}

private class FakeAudioEngine : AudioEngine {
    private val state = MutableStateFlow(EnginePlaybackState())
    private val eventFlow = MutableSharedFlow<AudioEngineEvent>()

    override val playbackState: StateFlow<EnginePlaybackState> = state
    override val events: Flow<AudioEngineEvent> = eventFlow

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
        state.value = state.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun setPlaybackSpeed(speed: Float) = Unit

    override suspend fun release() = Unit
}

