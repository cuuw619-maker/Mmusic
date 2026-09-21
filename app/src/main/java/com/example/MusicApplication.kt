package com.example

import android.app.Application
import com.example.data.database.MusicDatabase
import com.example.data.preferences.PreferencesManager
import com.example.model.Playlist
import com.example.model.Song
import com.example.playback.EqualizerManager
import com.example.playback.MusicControllerManager
import com.example.plugin.LibraryApi
import com.example.plugin.PlaybackControllerApi
import com.example.plugin.PlaylistsApi
import com.example.plugin.PluginManager
import com.example.plugin.QueueApi
import com.example.plugin.SettingsApi
import com.example.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MusicApplication : Application() {

    lateinit var database: MusicDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var equalizerManager: EqualizerManager
        private set

    lateinit var musicControllerManager: MusicControllerManager
        private set

    lateinit var pluginManager: PluginManager
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MusicDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        musicRepository = MusicRepository(this, database.musicDao(), preferencesManager)
        equalizerManager = EqualizerManager(preferencesManager)

        musicControllerManager = MusicControllerManager(
            context = this,
            preferencesManager = preferencesManager,
            equalizerManager = equalizerManager,
            onSongPlayed = { songId ->
                applicationScope.launch {
                    musicRepository.recordPlayback(songId)
                }
            }
        )

        // Initialize Plugin System Infrastructure
        pluginManager = PluginManager(
            context = this,
            playerControllerProvider = {
                object : PlaybackControllerApi {
                    override fun play() {
                        if (!musicControllerManager.playbackState.value.isPlaying) {
                            musicControllerManager.playPause()
                        }
                    }
                    override fun pause() {
                        if (musicControllerManager.playbackState.value.isPlaying) {
                            musicControllerManager.playPause()
                        }
                    }
                    override fun stop() {
                        musicControllerManager.stop()
                    }
                    override fun skipToNext() = musicControllerManager.skipToNext()
                    override fun skipToPrevious() = musicControllerManager.skipToPrevious()
                    override fun seekTo(positionMs: Long) = musicControllerManager.seekTo(positionMs)
                    override fun setVolume(volume: Float) {
                        musicControllerManager.mediaController?.volume = volume.coerceIn(0f, 1f)
                    }
                    override fun getPlaybackState() = musicControllerManager.playbackState.value
                }
            },
            queueProvider = {
                object : QueueApi {
                    override fun getQueue(): List<Song> = musicControllerManager.getQueue()
                    override fun addToQueue(song: Song) = musicControllerManager.addToQueue(song)
                    override fun removeFromQueue(index: Int) = musicControllerManager.removeFromQueue(index)
                    override fun clearQueue() = musicControllerManager.clearQueue()
                }
            },
            libraryProvider = {
                object : LibraryApi {
                    override fun getAllSongs(): List<Song> = musicRepository.allSongs.value
                    override fun getFavoriteSongs(): List<Song> = musicRepository.allSongs.value.filter { it.isFavorite }
                    override fun getSongById(id: Long): Song? = musicRepository.allSongs.value.find { it.id == id }
                }
            },
            playlistsProvider = {
                object : PlaylistsApi {
                    override fun getAllPlaylists(): List<Playlist> = musicRepository.playlists.value
                    override fun addSongToPlaylist(playlistId: Long, songId: Long) {
                        applicationScope.launch {
                            musicRepository.addSongToPlaylist(playlistId, songId)
                        }
                    }
                    override fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
                        applicationScope.launch {
                            musicRepository.removeSongFromPlaylist(playlistId, songId)
                        }
                    }
                }
            },
            settingsProvider = {
                object : SettingsApi {
                    override fun getString(key: String, defaultValue: String): String =
                        preferencesManager.getCustomString(key, defaultValue)
                    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
                        preferencesManager.getCustomBoolean(key, defaultValue)
                    override fun getFloat(key: String, defaultValue: Float): Float =
                        preferencesManager.getCustomFloat(key, defaultValue)
                    override fun getInt(key: String, defaultValue: Int): Int =
                        preferencesManager.getCustomInt(key, defaultValue)
                    override fun setString(key: String, value: String) =
                        preferencesManager.setCustomString(key, value)
                    override fun setBoolean(key: String, value: Boolean) =
                        preferencesManager.setCustomBoolean(key, value)
                    override fun setFloat(key: String, value: Float) =
                        preferencesManager.setCustomFloat(key, value)
                    override fun setInt(key: String, value: Int) =
                        preferencesManager.setCustomInt(key, value)
                }
            }
        )

        musicControllerManager.pluginManager = pluginManager
    }

    companion object {
        lateinit var instance: MusicApplication
            private set
    }
}

