package com.example.repository

import android.content.Context
import com.example.data.database.FavoriteEntity
import com.example.data.database.MusicDao
import com.example.data.database.PlaybackHistoryEntity
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistSongCrossRef
import com.example.data.database.SearchHistoryEntity
import com.example.data.mediastore.MediaStoreScanner
import com.example.data.preferences.PreferencesManager
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Folder
import com.example.model.Genre
import com.example.model.Playlist
import com.example.model.SearchCategory
import com.example.model.SearchResult
import com.example.model.Song
import com.example.model.SortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanner = MediaStoreScanner(context)

    private val _rawSongs = MutableStateFlow<List<Song>>(emptyList())
    val rawSongs: StateFlow<List<Song>> = _rawSongs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val favoriteSongIds: Flow<List<Long>> = musicDao.getAllFavoriteSongIds()
    val searchHistory: Flow<List<String>> = musicDao.getSearchHistory()

    // Real combined songs with favorite flag and play count statistics
    val allSongs: StateFlow<List<Song>> = combine(_rawSongs, favoriteSongIds) { songs, favIds ->
        val favSet = favIds.toHashSet()
        val stats = try {
            musicDao.getAllSongStats().associateBy { it.songId }
        } catch (e: Exception) {
            emptyMap()
        }

        songs.map { song ->
            val stat = stats[song.id]
            song.copy(
                isFavorite = favSet.contains(song.id),
                playCount = stat?.playCount ?: 0,
                lastPlayedTimestamp = stat?.lastPlayed ?: 0L
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Sorted songs list
    val sortedSongs: StateFlow<List<Song>> = combine(allSongs, _sortOrder) { songs, sort ->
        when (sort) {
            SortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            SortOrder.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
            SortOrder.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            SortOrder.ALBUM_DESC -> songs.sortedByDescending { it.album.lowercase() }
            SortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
            SortOrder.DURATION_DESC -> songs.sortedByDescending { it.durationMs }
            SortOrder.DURATION_ASC -> songs.sortedBy { it.durationMs }
            SortOrder.PLAY_COUNT_DESC -> songs.sortedByDescending { it.playCount }
            SortOrder.YEAR_DESC -> songs.sortedByDescending { it.year }
            SortOrder.YEAR_ASC -> songs.sortedBy { it.year }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Favorites list
    val favoriteSongs: StateFlow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.isFavorite }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Albums
    val albums: StateFlow<List<Album>> = allSongs.map { songs ->
        songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = albumId,
                title = first.album,
                artist = first.artist,
                songCount = albumSongs.size,
                albumArtUriString = first.albumArtUriString,
                year = first.year
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Artists
    val artists: StateFlow<List<Artist>> = allSongs.map { songs ->
        songs.groupBy { it.artist.lowercase() }.map { (_, artistSongs) ->
            val artistName = artistSongs.first().artist
            val albumCount = artistSongs.map { it.albumId }.distinct().size
            Artist(
                name = artistName,
                songCount = artistSongs.size,
                albumCount = albumCount
            )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Genres
    val genres: StateFlow<List<Genre>> = allSongs.map { songs ->
        songs.groupBy { it.genre }.map { (genreName, genreSongs) ->
            Genre(name = genreName, songCount = genreSongs.size)
        }.sortedBy { it.name }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Folders
    val folders: StateFlow<List<Folder>> = allSongs.map { songs ->
        songs.groupBy { it.folderName }.map { (name, folderSongs) ->
            Folder(
                path = folderSongs.first().dataPath.substringBeforeLast("/", ""),
                name = name,
                songCount = folderSongs.size
            )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Recently Added (last 30)
    val recentlyAddedSongs: StateFlow<List<Song>> = allSongs.map { songs ->
        songs.sortedByDescending { it.dateAdded }.take(30)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Recently Played songs
    val recentlyPlayedSongIds: Flow<List<Long>> = musicDao.getRecentlyPlayedSongIds(50)
    val recentlyPlayedSongs: StateFlow<List<Song>> = combine(allSongs, recentlyPlayedSongIds) { songs, ids ->
        val songMap = songs.associateBy { it.id }
        ids.mapNotNull { songMap[it] }.distinctBy { it.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Most Played songs
    val mostPlayedSongIds: Flow<List<Long>> = musicDao.getMostPlayedSongIds(50)
    val mostPlayedSongs: StateFlow<List<Song>> = combine(allSongs, mostPlayedSongIds) { songs, ids ->
        val songMap = songs.associateBy { it.id }
        ids.mapNotNull { songMap[it] }.distinctBy { it.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Playlists
    val playlists: StateFlow<List<Playlist>> = musicDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            val songIds = musicDao.getPlaylistSongIdsSync(entity.id)
            val firstArt = _rawSongs.value.firstOrNull { it.id in songIds && it.albumArtUriString != null }?.albumArtUriString
            Playlist(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt,
                songCount = songIds.size,
                firstSongAlbumArt = firstArt
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scanLibrary()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun scanLibrary() {
        scope.launch {
            _isScanning.value = true
            try {
                val songs = scanner.scanAudioFiles()
                _rawSongs.value = songs
            } finally {
                _isScanning.value = false
            }
        }
    }

    suspend fun deleteSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val deleted = scanner.deleteAudioFile(song)
        if (deleted) {
            _rawSongs.value = _rawSongs.value.filter { it.id != song.id }
            musicDao.deleteFavorite(song.id)
        }
        deleted
    }

    suspend fun toggleFavorite(songId: Long) = withContext(Dispatchers.IO) {
        val isFav = musicDao.isFavoriteSync(songId)
        if (isFav) {
            musicDao.deleteFavorite(songId)
        } else {
            musicDao.insertFavorite(FavoriteEntity(songId = songId))
        }
    }

    suspend fun recordPlayback(songId: Long) = withContext(Dispatchers.IO) {
        musicDao.insertPlayback(PlaybackHistoryEntity(songId = songId))
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        musicDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val existing = musicDao.getPlaylistById(playlistId)
        if (existing != null) {
            musicDao.updatePlaylist(existing.copy(name = newName))
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylistWithSongs(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val currentIds = musicDao.getPlaylistSongIdsSync(playlistId)
        if (songId !in currentIds) {
            musicDao.insertPlaylistSong(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = songId,
                    orderIndex = currentIds.size
                )
            )
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return combine(allSongs, musicDao.getPlaylistSongIds(playlistId)) { songs, ids ->
            val songMap = songs.associateBy { it.id }
            ids.mapNotNull { songMap[it] }
        }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return allSongs.value.filter { it.albumId == albumId }
    }

    fun getSongsForArtist(artistName: String): List<Song> {
        return allSongs.value.filter { it.artist.equals(artistName, ignoreCase = true) }
    }

    fun getSongsForFolder(folderName: String): List<Song> {
        return allSongs.value.filter { it.folderName.equals(folderName, ignoreCase = true) }
    }

    fun getSongsForGenre(genreName: String): List<Song> {
        return allSongs.value.filter { it.genre.equals(genreName, ignoreCase = true) }
    }

    suspend fun saveQueue(queue: List<Song>) = withContext(Dispatchers.IO) {
        musicDao.replaceSavedQueue(queue.map { it.id })
    }

    suspend fun getSavedQueue(): List<Song> = withContext(Dispatchers.IO) {
        val ids = musicDao.getSavedQueueSongIds()
        val map = allSongs.value.associateBy { it.id }
        ids.mapNotNull { map[it] }
    }

    // Search operations
    suspend fun search(query: String, filter: SearchCategory = SearchCategory.ALL): SearchResult = withContext(Dispatchers.Default) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext SearchResult()

        val all = allSongs.value
        val matchedSongs = if (filter == SearchCategory.ALL || filter == SearchCategory.SONGS) {
            all.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.genre.lowercase().contains(q) ||
                it.folderName.lowercase().contains(q)
            }
        } else emptyList()

        val matchedAlbums = if (filter == SearchCategory.ALL || filter == SearchCategory.ALBUMS) {
            albums.value.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        } else emptyList()

        val matchedArtists = if (filter == SearchCategory.ALL || filter == SearchCategory.ARTISTS) {
            artists.value.filter { it.name.lowercase().contains(q) }
        } else emptyList()

        val playlistEntities = musicDao.getAllPlaylists()
        // We do not block for playlists, return empty or filtered from state
        val matchedPlaylists = emptyList<Playlist>()

        SearchResult(
            query = query,
            songs = matchedSongs,
            albums = matchedAlbums,
            artists = matchedArtists,
            playlists = matchedPlaylists
        )
    }

    suspend fun addSearchHistory(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            musicDao.insertSearchQuery(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun deleteSearchHistory(query: String) = withContext(Dispatchers.IO) {
        musicDao.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        musicDao.clearSearchHistory()
    }
}
