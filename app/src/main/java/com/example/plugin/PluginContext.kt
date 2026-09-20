package com.example.plugin

import android.content.Context
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.Song

class PluginSecurityException(message: String) : SecurityException(message)

enum class PluginActionTarget {
    SETTINGS,
    CONTEXT_MENU,
    PLAYER,
    LIBRARY,
    PLAYLIST
}

data class PluginAction(
    val id: String,
    val pluginId: String,
    val label: String,
    val target: PluginActionTarget,
    val onClick: (actionContext: ActionContext) -> Unit
)

data class ActionContext(
    val currentSong: Song? = null,
    val playlistId: Long? = null
)

/**
 * Sandboxed execution context provided to plugins.
 * Strictly enforces capability permissions before granting access.
 */
class PluginContext(
    val appContext: Context,
    val pluginId: String,
    private val grantedCapabilities: Set<PluginCapability>,
    private val playerControllerProvider: () -> PlaybackControllerApi,
    private val queueProvider: () -> QueueApi,
    private val libraryProvider: () -> LibraryApi,
    private val playlistsProvider: () -> PlaylistsApi,
    private val settingsProvider: () -> SettingsApi,
    private val uiExtensionProvider: () -> UiExtensionApi
) {
    fun hasCapability(capability: PluginCapability): Boolean {
        return grantedCapabilities.contains(capability)
    }

    private fun checkCapability(capability: PluginCapability) {
        if (!hasCapability(capability)) {
            throw PluginSecurityException("Плагин не имеет разрешения: ${capability.title} (${capability.name})")
        }
    }

    val player: PlaybackControllerApi
        get() {
            checkCapability(PluginCapability.CONTROL_PLAYER)
            return playerControllerProvider()
        }

    val queue: QueueApi
        get() {
            checkCapability(PluginCapability.READ_QUEUE)
            return queueProvider()
        }

    val library: LibraryApi
        get() {
            checkCapability(PluginCapability.READ_LIBRARY)
            return libraryProvider()
        }

    val playlists: PlaylistsApi
        get() {
            checkCapability(PluginCapability.MODIFY_PLAYLISTS)
            return playlistsProvider()
        }

    val settings: SettingsApi
        get() {
            checkCapability(PluginCapability.READ_SETTINGS)
            return settingsProvider()
        }

    val ui: UiExtensionApi
        get() {
            checkCapability(PluginCapability.UI_EXTENSION)
            return uiExtensionProvider()
        }
}

typealias PlayerApi = PlaybackControllerApi
typealias PlaylistApi = PlaylistsApi

interface PlaybackControllerApi {
    fun play()
    fun pause()
    fun stop()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun getPlaybackState(): PlaybackState
}

interface QueueApi {
    fun getQueue(): List<Song>
    fun addToQueue(song: Song)
    fun removeFromQueue(index: Int)
    fun clearQueue()
}

interface LibraryApi {
    fun getAllSongs(): List<Song>
    fun getFavoriteSongs(): List<Song>
    fun getSongById(id: Long): Song?
}

interface PlaylistsApi {
    fun getAllPlaylists(): List<Playlist>
    fun addSongToPlaylist(playlistId: Long, songId: Long)
    fun removeSongFromPlaylist(playlistId: Long, songId: Long)
}

interface SettingsApi {
    fun getString(key: String, defaultValue: String = ""): String
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun setString(key: String, value: String)
    fun setBoolean(key: String, value: Boolean)
    fun setFloat(key: String, value: Float)
    fun setInt(key: String, value: Int)
}

interface UiExtensionApi {
    fun registerAction(action: PluginAction)
    fun unregisterAction(actionId: String)
}
