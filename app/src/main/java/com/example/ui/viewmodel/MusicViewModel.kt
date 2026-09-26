package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MusicApplication
import com.example.data.preferences.PreferencesManager
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Folder
import com.example.model.Genre
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.SearchCategory
import com.example.model.SearchResult
import com.example.model.SleepTimerOption
import com.example.model.SleepTimerState
import com.example.model.Song
import com.example.model.SortOrder
import com.example.playback.SleepTimerManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicApplication
    val repository = app.musicRepository
    val controllerManager = app.musicControllerManager
    val equalizerManager = app.equalizerManager
    val preferencesManager = app.preferencesManager

    val playbackState: StateFlow<PlaybackState> = controllerManager.playbackState
    val equalizerState = equalizerManager.equalizerState

    // Sleep Timer
    val sleepTimerManager = SleepTimerManager(
        onTimeExpired = {
            controllerManager.playPause()
        }
    )
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerManager.state

    // Repository flows
    val allSongs = repository.allSongs
    val sortedSongs = repository.sortedSongs
    val albums = repository.albums
    val artists = repository.artists
    val genres = repository.genres
    val folders = repository.folders
    val recentlyAdded = repository.recentlyAddedSongs
    val recentlyPlayed = repository.recentlyPlayedSongs
    val mostPlayed = repository.mostPlayedSongs
    val favoriteSongs = repository.favoriteSongs
    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = repository.isScanning
    val sortOrder = repository.sortOrder

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCategory = MutableStateFlow(SearchCategory.ALL)
    val searchCategory: StateFlow<SearchCategory> = _searchCategory.asStateFlow()

    private val _searchResult = MutableStateFlow(SearchResult())
    val searchResult: StateFlow<SearchResult> = _searchResult.asStateFlow()

    val searchHistory: StateFlow<List<String>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered songs for Library Screen
    val filteredSongs: StateFlow<List<Song>> = combine(sortedSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Preferences
    val themeMode = preferencesManager.themeMode
    val dynamicColor = preferencesManager.dynamicColor
    val resumePlayback = preferencesManager.resumePlayback
    val amoledDark = preferencesManager.amoledDark
    val playbackSpeed = preferencesManager.playbackSpeed
    val pitchSemitones = preferencesManager.pitchSemitones
    val preservePitch = preferencesManager.preservePitch
    val crossfadeEnabled = preferencesManager.crossfadeEnabled
    val crossfadeDuration = preferencesManager.crossfadeDuration
    val reducedMotion = preferencesManager.reducedMotion
    val hapticEnabled = preferencesManager.hapticEnabled
    val animationScale = preferencesManager.animationScale

    // Plugins
    val pluginManager = app.pluginManager
    val plugins = pluginManager.pluginsState
    val userPlugins = pluginManager.userProjectsState

    // UI state for sheets and dialogs
    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()

    private val _isEqualizerSheetVisible = MutableStateFlow(false)
    val isEqualizerSheetVisible: StateFlow<Boolean> = _isEqualizerSheetVisible.asStateFlow()

    private val _isSleepTimerSheetVisible = MutableStateFlow(false)
    val isSleepTimerSheetVisible: StateFlow<Boolean> = _isSleepTimerSheetVisible.asStateFlow()

    private val _isCreatePlaylistDialogVisible = MutableStateFlow(false)
    val isCreatePlaylistDialogVisible: StateFlow<Boolean> = _isCreatePlaylistDialogVisible.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    // Navigation drill down (detail screens inside Library/Playlists)
    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist: StateFlow<Artist?> = _selectedArtist.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _selectedGenre = MutableStateFlow<Genre?>(null)
    val selectedGenre: StateFlow<Genre?> = _selectedGenre.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()

    init {
        // Debounce search
        viewModelScope.launch {
            combine(_searchQuery.debounce(250).distinctUntilChanged(), _searchCategory) { query, category ->
                query to category
            }.collect { (query, category) ->
                if (query.isNotBlank()) {
                    val result = repository.search(query, category)
                    _searchResult.value = result
                    repository.addSearchHistory(query)
                } else {
                    _searchResult.value = SearchResult()
                }
            }
        }

        // Restore last played song into playback state on launch so Mini Player is immediately visible
        viewModelScope.launch {
            allSongs.collect { songs ->
                if (songs.isNotEmpty() && controllerManager.playbackState.value.currentSong == null) {
                    val lastId = preferencesManager.getLastSongId()
                    val lastPos = preferencesManager.getLastPositionMs()
                    val targetSong = songs.find { it.id == lastId } ?: songs.firstOrNull()
                    if (targetSong != null) {
                        controllerManager.restoreLastPlayedSong(targetSong, songs, lastPos)
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchCategory(category: SearchCategory) {
        _searchCategory.value = category
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setQueueSheetVisible(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun setEqualizerSheetVisible(visible: Boolean) {
        _isEqualizerSheetVisible.value = visible
    }

    fun setSleepTimerSheetVisible(visible: Boolean) {
        _isSleepTimerSheetVisible.value = visible
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerManager.setTimer(option)
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
    }

    fun setCreatePlaylistDialogVisible(visible: Boolean) {
        _isCreatePlaylistDialogVisible.value = visible
    }

    fun setSongToAddToPlaylist(song: Song?) {
        _songToAddToPlaylist.value = song
    }

    fun selectAlbum(album: Album?) {
        _selectedAlbum.value = album
    }

    fun selectArtist(artist: Artist?) {
        _selectedArtist.value = artist
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
    }

    fun selectGenre(genre: Genre?) {
        _selectedGenre.value = genre
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                repository.getSongsForPlaylist(playlist.id).collect { songs ->
                    _playlistSongs.value = songs
                }
            }
        } else {
            _playlistSongs.value = emptyList()
        }
    }

    fun setSortOrder(order: SortOrder) = repository.setSortOrder(order)
    fun scanLibrary() = repository.scanLibrary()

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    // Playback actions
    fun playSong(song: Song, queue: List<Song> = allSongs.value) {
        controllerManager.playSong(song, queue)
    }

    fun playQueue(queue: List<Song>, startIndex: Int = 0) {
        controllerManager.playQueue(queue, startIndex)
    }

    fun playPause() = controllerManager.playPause()
    fun seekTo(posMs: Long) = controllerManager.seekTo(posMs)
    fun skipToNext() = controllerManager.skipToNext()
    fun skipToPrevious() = controllerManager.skipToPrevious()
    fun rewind10() = controllerManager.rewind10()
    fun forward10() = controllerManager.forward10()
    fun toggleShuffle() = controllerManager.toggleShuffle()
    fun cycleRepeatMode() = controllerManager.cycleRepeatMode()

    fun playQueueIndex(index: Int) = controllerManager.playQueueIndex(index)
    fun removeFromQueue(index: Int) = controllerManager.removeFromQueue(index)
    fun reorderQueue(fromIndex: Int, toIndex: Int) = controllerManager.reorderQueue(fromIndex, toIndex)
    fun clearQueue() = controllerManager.clearQueue()
    fun addToQueue(song: Song) = controllerManager.addToQueue(song)

    // Favorites & Playlists
    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(songId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = newName)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // Preferences & Equalizer
    fun setThemeMode(mode: PreferencesManager.ThemeMode) = preferencesManager.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = preferencesManager.setDynamicColor(enabled)
    fun setResumePlayback(enabled: Boolean) = preferencesManager.setResumePlayback(enabled)
    fun setAmoledDark(enabled: Boolean) = preferencesManager.setAmoledDark(enabled)
    fun setReducedMotion(enabled: Boolean) = preferencesManager.setReducedMotion(enabled)
    fun setHapticEnabled(enabled: Boolean) = preferencesManager.setHapticEnabled(enabled)
    fun setAnimationScale(scale: Float) = preferencesManager.setAnimationScale(scale)

    fun setPlaybackSpeed(speed: Float) = controllerManager.setPlaybackSpeed(speed)
    fun setPitchSemitones(semitones: Int) = controllerManager.setPitchSemitones(semitones)
    fun setPreservePitch(preserve: Boolean) = controllerManager.setPreservePitch(preserve)
    fun setCrossfadeEnabled(enabled: Boolean) = preferencesManager.setCrossfadeEnabled(enabled)
    fun setCrossfadeDuration(seconds: Float) = preferencesManager.setCrossfadeDuration(seconds)

    fun setPluginEnabled(pluginId: String, enabled: Boolean) = pluginManager.setPluginEnabled(pluginId, enabled)
    fun saveUserPlugin(project: com.example.plugin.UserPluginProject) = pluginManager.saveUserPlugin(project)
    fun deleteUserPlugin(pluginId: String) = pluginManager.deleteUserPlugin(pluginId)

    fun setEqualizerEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)
    fun setEqualizerBandLevel(band: Short, level: Short) = equalizerManager.setBandLevel(band, level)
    fun setBassBoost(level: Short) = equalizerManager.setBassBoost(level)
    fun setVirtualizer(strength: Short) = equalizerManager.setVirtualizer(strength)
    fun setLoudnessEnhancer(targetGainmB: Int) = equalizerManager.setLoudnessEnhancer(targetGainmB)
    fun useEqualizerPreset(presetIndex: Short) = equalizerManager.usePreset(presetIndex)
    fun applyEqualizerPreset(presetName: String) = equalizerManager.applyPreset(presetName)
}
