package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- Favorites ---
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavoriteSync(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun deleteFavorite(songId: Long)

    // --- Playlists ---
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @Transaction
    suspend fun deletePlaylistWithSongs(playlistId: Long) {
        deletePlaylistSongs(playlistId)
        deletePlaylist(playlistId)
    }

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistSongIdsSync(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun countSongsInPlaylist(playlistId: Long): Flow<Int>

    // --- History & Stats ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayback(history: PlaybackHistoryEntity)

    @Query("SELECT songId FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentlyPlayedSongIds(limit: Int = 50): Flow<List<Long>>

    @Query("SELECT songId FROM playback_history GROUP BY songId ORDER BY COUNT(*) DESC LIMIT :limit")
    fun getMostPlayedSongIds(limit: Int = 50): Flow<List<Long>>

    @Query("SELECT songId, COUNT(*) as playCount, MAX(playedAt) as lastPlayed FROM playback_history GROUP BY songId")
    suspend fun getAllSongStats(): List<SongStatTuple>

    // --- Saved Queue ---
    @Query("SELECT songId FROM saved_queue ORDER BY orderIndex ASC")
    suspend fun getSavedQueueSongIds(): List<Long>

    @Query("DELETE FROM saved_queue")
    suspend fun clearSavedQueue()

    @Transaction
    suspend fun replaceSavedQueue(songIds: List<Long>) {
        clearSavedQueue()
        val items = songIds.mapIndexed { index, songId ->
            SavedQueueEntity(songId = songId, orderIndex = index)
        }
        insertSavedQueueItems(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQueueItems(items: List<SavedQueueEntity>)

    // --- Search History ---
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

data class SongStatTuple(
    val songId: Long,
    val playCount: Int,
    val lastPlayed: Long
)
