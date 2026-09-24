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
import java.io.File

data class PluginInfo(
    val plugin: MusicPlayerPlugin,
    val isEnabled: Boolean,
    val isUserPlugin: Boolean = false
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

    private val projectsDir: File = File(context.filesDir, "user_plugins").apply {
        if (!exists()) mkdirs()
    }

    private val registeredPlugins = mutableMapOf<String, MusicPlayerPlugin>()
    private val userPluginProjects = mutableMapOf<String, UserPluginProject>()

    private val _pluginsState = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsState: StateFlow<List<PluginInfo>> = _pluginsState.asStateFlow()

    private val _userProjectsState = MutableStateFlow<List<UserPluginProject>>(emptyList())
    val userProjectsState: StateFlow<List<UserPluginProject>> = _userProjectsState.asStateFlow()

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

    init {
        loadSavedUserPlugins()
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

    fun unregisterPlugin(pluginId: String) {
        val plugin = registeredPlugins.remove(pluginId)
        if (plugin != null) {
            try {
                plugin.onDisable()
            } catch (e: Exception) {
                Log.e("PluginManager", "Error disabling plugin $pluginId during unregister", e)
            }
            _registeredActions.value = _registeredActions.value.filter { it.pluginId != pluginId }
        }
        updateState()
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val plugin = registeredPlugins[pluginId] ?: return
        prefs.edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()

        // Also update project model if it's a user plugin
        val userProject = userPluginProjects[pluginId]
        if (userProject != null) {
            val updated = userProject.copy(isEnabled = enabled)
            userPluginProjects[pluginId] = updated
            saveProjectFile(updated)
            _userProjectsState.value = userPluginProjects.values.toList()
        }

        try {
            if (enabled) {
                plugin.onEnable()
            } else {
                plugin.onDisable()
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
            PluginInfo(
                plugin = plugin,
                isEnabled = enabled,
                isUserPlugin = userPluginProjects.containsKey(plugin.id)
            )
        }
        _pluginsState.value = list
    }

    // ==========================================
    // USER PLUGIN PROJECTS ENGINE (SAVE / BUILD / DELETE)
    // ==========================================

    private fun loadSavedUserPlugins() {
        try {
            val files = projectsDir.listFiles { file -> file.extension == "json" } ?: return
            for (file in files) {
                try {
                    val content = file.readText()
                    val project = UserPluginProject.fromJson(content)
                    if (project != null) {
                        userPluginProjects[project.id] = project
                        val dynamicPlugin = DynamicExecutableUserPlugin(project)
                        registerPlugin(dynamicPlugin)
                    }
                } catch (e: Exception) {
                    Log.e("PluginManager", "Failed to load user plugin file ${file.name}", e)
                }
            }
            _userProjectsState.value = userPluginProjects.values.toList()
        } catch (e: Exception) {
            Log.e("PluginManager", "Error reading user plugins directory", e)
        }
    }

    fun saveUserPlugin(project: UserPluginProject): ValidationResult {
        val validation = UserPluginValidator.validate(
            id = project.id,
            name = project.name,
            version = project.version,
            sourceCode = project.sourceCode
        )

        val finalProject = project.copy(
            isValidated = validation.isValid,
            lastBuildMessage = validation.summary,
            updatedAt = System.currentTimeMillis()
        )

        userPluginProjects[project.id] = finalProject
        saveProjectFile(finalProject)
        _userProjectsState.value = userPluginProjects.values.toList()

        if (validation.isValid) {
            // Unregister old version if already present
            unregisterPlugin(project.id)
            // Register executable plugin instance
            val dynamicPlugin = DynamicExecutableUserPlugin(finalProject)
            registerPlugin(dynamicPlugin)
        }

        return validation
    }

    fun deleteUserPlugin(pluginId: String) {
        unregisterPlugin(pluginId)
        userPluginProjects.remove(pluginId)
        _userProjectsState.value = userPluginProjects.values.toList()

        try {
            val file = File(projectsDir, "$pluginId.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to delete plugin file for $pluginId", e)
        }

        prefs.edit().remove("plugin_enabled_$pluginId").apply()
        updateState()
    }

    private fun saveProjectFile(project: UserPluginProject) {
        try {
            val file = File(projectsDir, "${project.id}.json")
            file.writeText(project.toJson())
        } catch (e: Exception) {
            Log.e("PluginManager", "Failed to save project file for ${project.id}", e)
        }
    }

    // ==========================================
    // EVENT DISPATCHERS
    // ==========================================

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
