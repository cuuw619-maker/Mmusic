package com.example.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    suspend fun scanAudioFiles(): List<Song> = withContext(Dispatchers.IO) {
        // Clean up any legacy sample files so no demo audio persists
        try {
            val sampleDir = File(context.filesDir, "samples")
            if (sampleDir.exists()) {
                sampleDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w("MediaStoreScanner", "Could not clean old sample directory", e)
        }

        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.BITRATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection.add(MediaStore.Audio.Media.GENRE)
        }

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND (${MediaStore.Audio.Media.DURATION} >= 1000 OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                } else -1
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: ""
                    val rawArtist = cursor.getString(artistCol) ?: ""
                    val rawAlbum = cursor.getString(albumCol) ?: ""
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val dataPath = cursor.getString(dataCol) ?: ""
                    val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val size = cursor.getLong(sizeCol)
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0
                    val bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) / 1000 else 0
                    val genre = if (genreCol >= 0) cursor.getString(genreCol) ?: "Unknown" else "Unknown"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri = try {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } catch (e: Exception) {
                        null
                    } ?: contentUri.toString()

                    val title = rawTitle.ifBlank {
                        dataPath.substringAfterLast("/").substringBeforeLast(".")
                    }.ifBlank { "Track $id" }

                    val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }

                    val album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") {
                        "Unknown Album"
                    } else {
                        rawAlbum
                    }

                    val folderName = if (dataPath.isNotEmpty()) {
                        try {
                            File(dataPath).parentFile?.name ?: "Music"
                        } catch (e: Exception) {
                            "Music"
                        }
                    } else "Music"

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            durationMs = duration,
                            contentUriString = contentUri.toString(),
                            albumArtUriString = albumArtUri,
                            dataPath = dataPath,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            size = size,
                            folderName = folderName,
                            genre = if (genre.isBlank()) "Music" else genre,
                            year = year,
                            bitrateKbps = bitrate
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreScanner", "Error querying MediaStore", e)
        }

        songs
    }

    suspend fun deleteAudioFile(song: Song): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(song.contentUriString)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e("MediaStoreScanner", "Failed to delete song: ${song.id}", e)
            false
        }
    }
}
