package com.example.mymusicapplication.music

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaybackController(
    private val repository: MusicRepository,
    private val audioEngine: AudioEngine,
    private val settingsRepository: PlaybackSettingsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val internalState = MutableStateFlow(InternalPlaybackState())

    val uiState: StateFlow<PlaybackUiState> = combine(
        repository.library,
        repository.favorites,
        audioEngine.playbackState,
        settingsRepository.preferences,
        internalState,
    ) { library, favorites, engineState, preferences, internal ->
        val queue = internal.queueIds.mapNotNull { queuedId -> library.firstOrNull { it.id == queuedId } }
        PlaybackUiState(
            library = library,
            favorites = favorites,
            queue = queue,
            currentTrack = engineState.currentTrack,
            currentIndex = internal.currentIndex,
            isPlaying = engineState.isPlaying,
            progressMs = engineState.positionMs,
            durationMs = engineState.durationMs.takeIf { it > 0 } ?: engineState.currentTrack?.durationMs ?: 0L,
            playbackMode = preferences.mode,
            volume = preferences.volume,
            speed = preferences.speed,
            isRefreshingLibrary = internal.isRefreshingLibrary,
            errorMessage = internal.errorMessage,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlaybackUiState())

    init {
        scope.launch {
            settingsRepository.preferences.collect { preferences ->
                audioEngine.setVolume(preferences.volume)
                audioEngine.setPlaybackSpeed(preferences.speed)
            }
        }
        scope.launch {
            repository.library.collect { library ->
                if (internalState.value.queueIds.isEmpty() && library.isNotEmpty()) {
                    internalState.update { it.copy(queueIds = library.map(AudioTrack::id), currentIndex = 0) }
                }
            }
        }
        scope.launch {
            audioEngine.events.collect { event ->
                when (event) {
                    AudioEngineEvent.TrackEnded -> playNext(autoTriggered = true)
                    is AudioEngineEvent.Error -> internalState.update { it.copy(errorMessage = event.message) }
                }
            }
        }
    }

    suspend fun refreshLibrary() {
        internalState.update { it.copy(isRefreshingLibrary = true, errorMessage = null) }
        val result = repository.refreshLibrary()
        internalState.update {
            it.copy(
                isRefreshingLibrary = false,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }
        if (result.isSuccess) {
            val library = repository.library.first()
            if (library.isNotEmpty() && internalState.value.queueIds.isEmpty()) {
                internalState.update { state -> state.copy(queueIds = library.map(AudioTrack::id), currentIndex = 0) }
            }
        }
    }

    suspend fun playTrack(trackId: String) {
        val track = repository.getTrack(trackId) ?: return
        ensureQueue(trackId)
        val index = internalState.value.queueIds.indexOf(track.id)
        internalState.update { it.copy(currentIndex = index.takeIf { value -> value >= 0 } ?: 0, errorMessage = null) }
        audioEngine.prepare(track, playWhenReady = true, positionMs = 0L)
    }

    suspend fun togglePlayPause() {
        val currentTrack = uiState.value.currentTrack
        if (currentTrack == null) {
            val fallback = uiState.value.library.firstOrNull() ?: return
            playTrack(fallback.id)
            return
        }
        if (uiState.value.isPlaying) {
            audioEngine.pause()
        } else {
            audioEngine.play()
        }
    }

    suspend fun playNext(autoTriggered: Boolean = false) {
        val queue = uiState.value.queue
        if (queue.isEmpty()) return
        val currentIndex = internalState.value.currentIndex.takeIf { it in queue.indices } ?: 0
        val nextIndex = when (uiState.value.playbackMode) {
            PlaybackMode.REPEAT_ONE -> if (autoTriggered) currentIndex else calculateSequentialNext(currentIndex, queue.size, autoTriggered)
            PlaybackMode.SHUFFLE -> calculateShuffleNext(currentIndex, queue.size)
            PlaybackMode.SEQUENTIAL -> calculateSequentialNext(currentIndex, queue.size, autoTriggered)
        }

        if (nextIndex == null) {
            audioEngine.pause()
            audioEngine.seekTo(0L)
            return
        }

        val track = queue[nextIndex]
        internalState.update { it.copy(currentIndex = nextIndex, errorMessage = null) }
        audioEngine.prepare(track, playWhenReady = true, positionMs = 0L)
    }

    suspend fun playPrevious() {
        val queue = uiState.value.queue
        if (queue.isEmpty()) return
        if (uiState.value.progressMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            audioEngine.seekTo(0L)
            return
        }
        val currentIndex = internalState.value.currentIndex.takeIf { it in queue.indices } ?: 0
        val previousIndex = when (uiState.value.playbackMode) {
            PlaybackMode.SHUFFLE -> calculateShuffleNext(currentIndex, queue.size)
            else -> if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        }
        val track = queue[previousIndex]
        internalState.update { it.copy(currentIndex = previousIndex, errorMessage = null) }
        audioEngine.prepare(track, playWhenReady = true, positionMs = 0L)
    }

    suspend fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        settingsRepository.updateMode(mode)
    }

    fun setVolume(volume: Float) {
        settingsRepository.updateVolume(volume)
    }

    fun setPlaybackSpeed(speed: Float) {
        settingsRepository.updateSpeed(speed)
    }

    suspend fun toggleFavorite(trackId: String) {
        repository.toggleFavorite(trackId)
    }

    suspend fun stopPlayback() {
        audioEngine.stop()
        internalState.update { it.copy(errorMessage = null) }
    }

    fun clearError() {
        internalState.update { it.copy(errorMessage = null) }
    }

    private fun ensureQueue(trackId: String) {
        val currentQueue = internalState.value.queueIds
        if (currentQueue.contains(trackId) && currentQueue.isNotEmpty()) {
            return
        }
        val libraryIds = uiState.value.library.map(AudioTrack::id)
        if (libraryIds.isNotEmpty()) {
            internalState.update { it.copy(queueIds = libraryIds) }
        }
    }

    private fun calculateSequentialNext(currentIndex: Int, size: Int, autoTriggered: Boolean): Int? {
        return when {
            size <= 0 -> null
            currentIndex < size - 1 -> currentIndex + 1
            autoTriggered -> null
            else -> 0
        }
    }

    private fun calculateShuffleNext(currentIndex: Int, size: Int): Int {
        if (size <= 1) return currentIndex.coerceAtLeast(0)
        val candidates = (0 until size).filterNot { it == currentIndex }
        return candidates.random()
    }

    private data class InternalPlaybackState(
        val queueIds: List<String> = emptyList(),
        val currentIndex: Int = -1,
        val isRefreshingLibrary: Boolean = false,
        val errorMessage: String? = null,
    )

    private companion object {
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}

