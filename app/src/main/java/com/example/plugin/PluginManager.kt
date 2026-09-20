package com.example.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PluginInfo(
    val plugin: MusicPlayerPlugin,
    val isEnabled: Boolean
)

class PluginManager(
    private val context: Context,
    private val playerControllerProvider: () -> PlaybackControllerApi,
    private val queueProvider: () -> QueueApi,
    private val libraryProvider: () -> LibraryApi,
    private val playlistsProvider: () -> PlaylistsApi,
    private val settingsProvider: () -> SettingsApi
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("music_player_plugins", Context.MODE_PRIVATE)

    private val registeredPlugins = mutableMapOf<String, MusicPlayerPlugin>()
    private val _pluginsState = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsState: StateFlow<List<PluginInfo>> = _pluginsState.asStateFlow()

    private val _registeredActions = MutableStateFlow<List<PluginAction>>(emptyList())
    val registeredActions: StateFlow<List<PluginAction>> = _registeredActions.asStateFlow()

    private val uiExtensionApi = object : UiExtensionApi {
        override fun registerAction(action: PluginAction) {
            _registeredActions.value = _registeredActions.value.filter { it.id != action.id } + action
        }

        override fun unregisterAction(actionId: String) {
            _registeredActions.value = _registeredActions.value.filter { it.id != actionId }
        }
    }

    fun registerPlugin(plugin: MusicPlayerPlugin) {
        registeredPlugins[plugin.id] = plugin
        val isEnabled = prefs.getBoolean("plugin_enabled_${plugin.id}", true)

        try {
            val pluginContext = PluginContext(
                appContext = context,
                pluginId = plugin.id,
                grantedCapabilities = plugin.requiredCapabilities,
                playerControllerProvider = playerControllerProvider,
                queueProvider = queueProvider,
                libraryProvider = libraryProvider,
                playlistsProvider = playlistsProvider,
                settingsProvider = settingsProvider,
                uiExtensionProvider = { uiExtensionApi }
            )
            plugin.onLoad(pluginContext)
            if (isEnabled) {
                plugin.onEnable()
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to load plugin ${plugin.id}", e)
        }

        updateState()
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val plugin = registeredPlugins[pluginId] ?: return
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()

        try {
            if (enabled) {
                plugin.onEnable()
            } else {
                plugin.onDisable()
                // Remove actions registered by this plugin
                _registeredActions.value = _registeredActions.value.filter { it.pluginId != pluginId }
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Error toggling plugin $pluginId", e)
        }

        updateState()
    }

    private fun updateState() {
        val list = registeredPlugins.values.map { plugin ->
            val enabled = prefs.getBoolean("plugin_enabled_${plugin.id}", true)
            PluginInfo(plugin = plugin, isEnabled = enabled)
        }
        _pluginsState.value = list
    }

    // Event dispatchers
    fun dispatchPlaybackStateChanged(state: PlaybackState) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaybackStateChanged(state)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaybackStateChanged: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchTrackChanged(song: Song?) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onTrackChanged(song)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onTrackChanged: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchPlaybackStarted() {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaybackStarted()
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaybackStarted: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchPlaybackPaused() {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaybackPaused()
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaybackPaused: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchPlaybackStopped() {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaybackStopped()
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaybackStopped: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchPlaybackCompleted() {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaybackCompleted()
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaybackCompleted: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchQueueChanged(queue: List<Song>) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onQueueChanged(queue)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onQueueChanged: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchFavoriteChanged(songId: Long, isFavorite: Boolean) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onFavoriteChanged(songId, isFavorite)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onFavoriteChanged: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchPlaylistChanged(playlistId: Long) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onPlaylistChanged(playlistId)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onPlaylistChanged: ${info.plugin.id}", e)
                }
            }
        }
    }

    fun dispatchSettingsChanged(key: String, value: Any?) {
        for (info in _pluginsState.value) {
            if (info.isEnabled) {
                try {
                    info.plugin.onSettingsChanged(key, value)
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin error in onSettingsChanged: ${info.plugin.id}", e)
                }
            }
        }
    }
}
