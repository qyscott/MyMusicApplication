package com.example.mymusicapplication.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidMusicScanner(
    private val context: Context,
) : MusicScanner {
    override suspend fun scanLocalTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 1000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = mutableListOf<AudioTrack>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                val albumId = cursor.getLong(albumIdIndex)
                val artworkUri = albumId.takeIf { it > 0 }?.let {
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId).toString()
                }
                tracks += AudioTrack(
                    id = contentUri.toString(),
                    title = cursor.getString(titleIndex).orEmpty().ifBlank { "未知歌曲" },
                    artist = cursor.getString(artistIndex).orEmpty().ifBlank { "未知歌手" },
                    album = cursor.getString(albumIndex),
                    durationMs = cursor.getLong(durationIndex),
                    uri = contentUri.toString(),
                    artworkUri = artworkUri,
                    lastModified = cursor.getLong(modifiedIndex) * 1000L,
                )
            }
        }
        tracks
    }
}

