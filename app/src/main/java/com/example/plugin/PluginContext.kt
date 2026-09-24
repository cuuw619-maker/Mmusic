package com.example.plugin

import android.content.Context
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.Song
import java.io.File

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

interface PlayerApi {
    fun play()
    fun pause()
    fun stop()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setPitch(pitchSemitones: Float)
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

interface NotificationApi {
    fun getNotificationState(): Boolean
    fun addNotificationAction(action: PluginAction)
    fun removeNotificationAction(actionId: String)
}

interface EventBusApi {
    fun subscribe(eventType: String, listener: (Any?) -> Unit)
    fun unsubscribe(eventType: String, listener: (Any?) -> Unit)
}

interface PluginStorageApi {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getStorageDir(): File
}

typealias PlaybackControllerApi = PlayerApi
typealias PlaylistApi = PlaylistsApi

/**
 * Sandboxed execution context provided to plugins.
 * Strictly enforces capability permissions before granting access.
 */
interface PluginContext {
    val appContext: Context
    val pluginId: String
    fun hasCapability(capability: PluginCapability): Boolean

    val player: PlayerApi
    val queue: QueueApi
    val library: LibraryApi
    val playlists: PlaylistsApi
    val settings: SettingsApi
    val ui: UiExtensionApi
    val notifications: NotificationApi
    val events: EventBusApi
    val storage: PluginStorageApi
}

fun PluginContext(
    appContext: Context,
    pluginId: String,
    grantedCapabilities: Set<PluginCapability>,
    playerControllerProvider: () -> PlayerApi,
    queueProvider: () -> QueueApi,
    libraryProvider: () -> LibraryApi,
    playlistsProvider: () -> PlaylistsApi,
    settingsProvider: () -> SettingsApi,
    uiExtensionProvider: () -> UiExtensionApi
): PluginContext = SandboxedPluginContext(
    appContext = appContext,
    pluginId = pluginId,
    grantedCapabilities = grantedCapabilities,
    playerControllerProvider = playerControllerProvider,
    queueProvider = queueProvider,
    libraryProvider = libraryProvider,
    playlistsProvider = playlistsProvider,
    settingsProvider = settingsProvider,
    uiExtensionProvider = uiExtensionProvider
)

private class SandboxedPluginContext(
    override val appContext: Context,
    override val pluginId: String,
    private val grantedCapabilities: Set<PluginCapability>,
    private val playerControllerProvider: () -> PlayerApi,
    private val queueProvider: () -> QueueApi,
    private val libraryProvider: () -> LibraryApi,
    private val playlistsProvider: () -> PlaylistsApi,
    private val settingsProvider: () -> SettingsApi,
    private val uiExtensionProvider: () -> UiExtensionApi
) : PluginContext {

    override fun hasCapability(capability: PluginCapability): Boolean {
        return grantedCapabilities.contains(capability)
    }

    private fun checkAny(vararg capabilities: PluginCapability) {
        if (capabilities.none { grantedCapabilities.contains(it) }) {
            val names = capabilities.joinToString { it.name }
            throw PluginSecurityException("Плагин '$pluginId' не имеет разрешения: требуется одно из [$names]")
        }
    }

    override val player: PlayerApi = object : PlayerApi {
        override fun play() {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.PLAY)
            playerControllerProvider().play()
        }

        override fun pause() {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.PAUSE)
            playerControllerProvider().pause()
        }

        override fun stop() {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.STOP)
            playerControllerProvider().stop()
        }

        override fun skipToNext() {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.NEXT)
            playerControllerProvider().skipToNext()
        }

        override fun skipToPrevious() {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.PREVIOUS)
            playerControllerProvider().skipToPrevious()
        }

        override fun seekTo(positionMs: Long) {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.SEEK)
            playerControllerProvider().seekTo(positionMs)
        }

        override fun setSpeed(speed: Float) {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.CHANGE_SPEED, PluginCapability.CONTROL_SPEED)
            playerControllerProvider().setSpeed(speed)
        }

        override fun setPitch(pitchSemitones: Float) {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.CHANGE_PITCH, PluginCapability.CONTROL_PITCH)
            playerControllerProvider().setPitch(pitchSemitones)
        }

        override fun setVolume(volume: Float) {
            checkAny(PluginCapability.CONTROL_PLAYER, PluginCapability.CONTROL_AUDIO_EFFECTS)
            playerControllerProvider().setVolume(volume)
        }

        override fun getPlaybackState(): PlaybackState {
            checkAny(PluginCapability.READ_PLAYBACK_STATE, PluginCapability.CONTROL_PLAYER)
            return playerControllerProvider().getPlaybackState()
        }
    }

    override val queue: QueueApi = object : QueueApi {
        override fun getQueue(): List<Song> {
            checkAny(PluginCapability.READ_QUEUE)
            return queueProvider().getQueue()
        }

        override fun addToQueue(song: Song) {
            checkAny(PluginCapability.ADD_TO_QUEUE, PluginCapability.MODIFY_QUEUE)
            queueProvider().addToQueue(song)
        }

        override fun removeFromQueue(index: Int) {
            checkAny(PluginCapability.REMOVE_FROM_QUEUE, PluginCapability.MODIFY_QUEUE)
            queueProvider().removeFromQueue(index)
        }

        override fun clearQueue() {
            checkAny(PluginCapability.CLEAR_QUEUE, PluginCapability.MODIFY_QUEUE)
            queueProvider().clearQueue()
        }
    }

    override val library: LibraryApi = object : LibraryApi {
        override fun getAllSongs(): List<Song> {
            checkAny(PluginCapability.READ_LIBRARY, PluginCapability.READ_AUDIO_FILES)
            return libraryProvider().getAllSongs()
        }

        override fun getFavoriteSongs(): List<Song> {
            checkAny(PluginCapability.READ_FAVORITES, PluginCapability.READ_LIBRARY)
            return libraryProvider().getFavoriteSongs()
        }

        override fun getSongById(id: Long): Song? {
            checkAny(PluginCapability.READ_LIBRARY)
            return libraryProvider().getSongById(id)
        }
    }

    override val playlists: PlaylistsApi = object : PlaylistsApi {
        override fun getAllPlaylists(): List<Playlist> {
            checkAny(PluginCapability.READ_PLAYLISTS)
            return playlistsProvider().getAllPlaylists()
        }

        override fun addSongToPlaylist(playlistId: Long, songId: Long) {
            checkAny(PluginCapability.ADD_TO_PLAYLISTS, PluginCapability.MODIFY_PLAYLISTS)
            playlistsProvider().addSongToPlaylist(playlistId, songId)
        }

        override fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
            checkAny(PluginCapability.REMOVE_FROM_PLAYLISTS, PluginCapability.MODIFY_PLAYLISTS)
            playlistsProvider().removeSongFromPlaylist(playlistId, songId)
        }
    }

    override val settings: SettingsApi = object : SettingsApi {
        override fun getString(key: String, defaultValue: String): String {
            checkAny(PluginCapability.READ_SETTINGS)
            return settingsProvider().getString(key, defaultValue)
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            checkAny(PluginCapability.READ_SETTINGS)
            return settingsProvider().getBoolean(key, defaultValue)
        }

        override fun getFloat(key: String, defaultValue: Float): Float {
            checkAny(PluginCapability.READ_SETTINGS)
            return settingsProvider().getFloat(key, defaultValue)
        }

        override fun getInt(key: String, defaultValue: Int): Int {
            checkAny(PluginCapability.READ_SETTINGS)
            return settingsProvider().getInt(key, defaultValue)
        }

        override fun setString(key: String, value: String) {
            checkAny(PluginCapability.MODIFY_SETTINGS)
            settingsProvider().setString(key, value)
        }

        override fun setBoolean(key: String, value: Boolean) {
            checkAny(PluginCapability.MODIFY_SETTINGS)
            settingsProvider().setBoolean(key, value)
        }

        override fun setFloat(key: String, value: Float) {
            checkAny(PluginCapability.MODIFY_SETTINGS)
            settingsProvider().setFloat(key, value)
        }

        override fun setInt(key: String, value: Int) {
            checkAny(PluginCapability.MODIFY_SETTINGS)
            settingsProvider().setInt(key, value)
        }
    }

    override val ui: UiExtensionApi = object : UiExtensionApi {
        override fun registerAction(action: PluginAction) {
            checkAny(PluginCapability.UI_EXTENSION, PluginCapability.ADD_ACTION, PluginCapability.ADD_PLAYER_ACTION, PluginCapability.ADD_MENU_ITEM)
            uiExtensionProvider().registerAction(action)
        }

        override fun unregisterAction(actionId: String) {
            checkAny(PluginCapability.UI_EXTENSION, PluginCapability.ADD_ACTION, PluginCapability.ADD_PLAYER_ACTION, PluginCapability.ADD_MENU_ITEM)
            uiExtensionProvider().unregisterAction(actionId)
        }
    }

    override val notifications: NotificationApi = object : NotificationApi {
        override fun getNotificationState(): Boolean {
            checkAny(PluginCapability.READ_NOTIFICATION_STATE)
            return true
        }

        override fun addNotificationAction(action: PluginAction) {
            checkAny(PluginCapability.ADD_NOTIFICATION_ACTION, PluginCapability.MODIFY_PLUGIN_NOTIFICATION_ACTIONS)
            uiExtensionProvider().registerAction(action.copy(target = PluginActionTarget.PLAYER))
        }

        override fun removeNotificationAction(actionId: String) {
            checkAny(PluginCapability.ADD_NOTIFICATION_ACTION, PluginCapability.MODIFY_PLUGIN_NOTIFICATION_ACTIONS)
            uiExtensionProvider().unregisterAction(actionId)
        }
    }

    private val eventListeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    override val events: EventBusApi = object : EventBusApi {
        override fun subscribe(eventType: String, listener: (Any?) -> Unit) {
            when (eventType) {
                "playback" -> checkAny(PluginCapability.RECEIVE_PLAYBACK_EVENTS)
                "track" -> checkAny(PluginCapability.RECEIVE_TRACK_EVENTS)
                "queue" -> checkAny(PluginCapability.RECEIVE_QUEUE_EVENTS)
                "library" -> checkAny(PluginCapability.RECEIVE_LIBRARY_EVENTS)
                "playlist" -> checkAny(PluginCapability.RECEIVE_PLAYLIST_EVENTS)
                "settings" -> checkAny(PluginCapability.RECEIVE_SETTINGS_EVENTS)
                else -> checkAny(PluginCapability.RECEIVE_PLAYBACK_EVENTS)
            }
            val list = eventListeners.getOrPut(eventType) { mutableListOf() }
            list.add(listener)
        }

        override fun unsubscribe(eventType: String, listener: (Any?) -> Unit) {
            eventListeners[eventType]?.remove(listener)
        }
    }

    private val pluginPrefs by lazy {
        appContext.getSharedPreferences("plugin_storage_$pluginId", Context.MODE_PRIVATE)
    }

    private val pluginStorageDir by lazy {
        File(appContext.filesDir, "plugin_data/$pluginId").apply {
            if (!exists()) mkdirs()
        }
    }

    override val storage: PluginStorageApi = object : PluginStorageApi {
        override fun getString(key: String, defaultValue: String): String {
            checkAny(PluginCapability.READ_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            return pluginPrefs.getString(key, defaultValue) ?: defaultValue
        }

        override fun putString(key: String, value: String) {
            checkAny(PluginCapability.WRITE_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            pluginPrefs.edit().putString(key, value).apply()
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            checkAny(PluginCapability.READ_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            return pluginPrefs.getBoolean(key, defaultValue)
        }

        override fun putBoolean(key: String, value: Boolean) {
            checkAny(PluginCapability.WRITE_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            pluginPrefs.edit().putBoolean(key, value).apply()
        }

        override fun getInt(key: String, defaultValue: Int): Int {
            checkAny(PluginCapability.READ_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            return pluginPrefs.getInt(key, defaultValue)
        }

        override fun putInt(key: String, value: Int) {
            checkAny(PluginCapability.WRITE_PLUGIN_STORAGE, PluginCapability.STORE_PLUGIN_SETTINGS)
            pluginPrefs.edit().putInt(key, value).apply()
        }

        override fun getStorageDir(): File {
            checkAny(PluginCapability.READ_PLUGIN_STORAGE, PluginCapability.WRITE_PLUGIN_STORAGE)
            return pluginStorageDir
        }
    }
}
