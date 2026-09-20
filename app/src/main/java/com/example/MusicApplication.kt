package com.example

import android.app.Application
import com.example.data.database.MusicDatabase
import com.example.data.preferences.PreferencesManager
import com.example.playback.EqualizerManager
import com.example.playback.MusicControllerManager
import com.example.plugin.HeadphoneEnhancerPlugin
import com.example.plugin.LibraryApi
import com.example.plugin.LyricsSyncPlugin
import com.example.plugin.PlaybackControllerApi
import com.example.plugin.PlaybackStatsPlugin
import com.example.plugin.PluginManager
import com.example.plugin.SleepFadePlugin
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

        // Initialize Plugin System
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
                    override fun skipToNext() = musicControllerManager.skipToNext()
                    override fun skipToPrevious() = musicControllerManager.skipToPrevious()
                    override fun seekTo(positionMs: Long) = musicControllerManager.seekTo(positionMs)
                    override fun setVolume(volume: Float) {
                        musicControllerManager.mediaController?.volume = volume.coerceIn(0f, 1f)
                    }
                    override fun getPlaybackState() = musicControllerManager.playbackState.value
                }
            },
            libraryProvider = {
                object : LibraryApi {
                    override fun getAllSongs() = musicRepository.allSongs.value
                    override fun getFavoriteSongs() = musicRepository.allSongs.value.filter { it.isFavorite }
                }
            }
        )

        musicControllerManager.pluginManager = pluginManager

        // Register built-in plugins
        pluginManager.registerPlugin(PlaybackStatsPlugin())
        pluginManager.registerPlugin(SleepFadePlugin())
        pluginManager.registerPlugin(LyricsSyncPlugin())
        pluginManager.registerPlugin(HeadphoneEnhancerPlugin())
    }

    companion object {
        lateinit var instance: MusicApplication
            private set
    }
}
