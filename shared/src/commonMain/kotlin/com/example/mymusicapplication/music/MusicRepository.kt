package com.example.mymusicapplication.music

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.example.mymusicapplication.db.MusicDatabase
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

interface SqlDriverFactory {
    fun createDriver(): SqlDriver
}

interface MusicRepository {
    val library: Flow<List<AudioTrack>>
    val favorites: Flow<List<AudioTrack>>

    suspend fun refreshLibrary(): Result<Int>
    suspend fun getTrack(trackId: String): AudioTrack?
    suspend fun toggleFavorite(trackId: String): Boolean
}

class MusicRepositoryImpl(
    driverFactory: SqlDriverFactory,
    private val scanner: MusicScanner,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MusicRepository {
    private val database = MusicDatabase(driverFactory.createDriver())
    private val queries = database.musicDatabaseQueries

    override val library: Flow<List<AudioTrack>> = queries
        .selectLibrary(::mapTrack)
        .asFlow()
        .mapToList(dispatcher)

    override val favorites: Flow<List<AudioTrack>> = queries
        .selectFavorites(::mapTrack)
        .asFlow()
        .mapToList(dispatcher)

    override suspend fun refreshLibrary(): Result<Int> = withContext(dispatcher) {
        runCatching {
            val tracks = scanner.scanLocalTracks()
            database.transaction {
                queries.clearLibrary()
                tracks.forEach { track ->
                    queries.upsertTrack(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration_ms = track.durationMs,
                        uri = track.uri,
                        artwork_uri = track.artworkUri,
                        last_modified = track.lastModified,
                    )
                }
            }
            tracks.size
        }
    }

    override suspend fun getTrack(trackId: String): AudioTrack? = withContext(dispatcher) {
        queries.selectTrackById(trackId, ::mapTrack).executeAsOneOrNull()
    }

    override suspend fun toggleFavorite(trackId: String): Boolean = withContext(dispatcher) {
        val isFavorite = queries.isFavorite(trackId).executeAsOne() > 0L
        if (isFavorite) {
            queries.removeFavorite(trackId)
        } else {
            queries.insertFavorite(trackId, 0L)
        }
        !isFavorite
    }

    private fun mapTrack(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        uri: String,
        artworkUri: String?,
        lastModified: Long,
        isFavorite: Long,
    ): AudioTrack = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        artworkUri = artworkUri,
        lastModified = lastModified,
        isFavorite = isFavorite == 1L,
    )
}

class PlaybackSettingsRepository(
    private val settings: Settings? = runCatching { Settings() }.getOrNull(),
) {
    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<PlaybackPreferences> = _preferences.asStateFlow()

    fun updateMode(mode: PlaybackMode) {
        settings?.putString(KEY_MODE, mode.name)
        _preferences.update { it.copy(mode = mode) }
    }

    fun updateVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        settings?.putFloat(KEY_VOLUME, normalized)
        _preferences.update { it.copy(volume = normalized) }
    }

    fun updateSpeed(speed: Float) {
        val normalized = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        settings?.putFloat(KEY_SPEED, normalized)
        _preferences.update { it.copy(speed = normalized) }
    }

    private fun loadPreferences(): PlaybackPreferences {
        val mode = settings?.getStringOrNull(KEY_MODE)
            ?.let { stored -> PlaybackMode.entries.firstOrNull { it.name == stored } }
            ?: PlaybackMode.SEQUENTIAL
        return PlaybackPreferences(
            mode = mode,
            volume = settings?.getFloat(KEY_VOLUME, 1f)?.coerceIn(0f, 1f) ?: 1f,
            speed = settings?.getFloat(KEY_SPEED, 1f)?.coerceIn(MIN_SPEED, MAX_SPEED) ?: 1f,
        )
    }

    private companion object {
        const val KEY_MODE = "playback_mode"
        const val KEY_VOLUME = "playback_volume"
        const val KEY_SPEED = "playback_speed"
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f
    }
}

