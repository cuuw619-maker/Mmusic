package com.example.plugin

import com.example.model.PlaybackState
import com.example.model.Song

interface MusicPlayerPlugin {
    val id: String
    val name: String
    val version: String
    val author: String
    val description: String
    val requiredCapabilities: Set<PluginCapability>

    fun onLoad(context: PluginContext)
    fun onEnable()
    fun onDisable()

    // Plugin Event Hooks
    fun onPlaybackStateChanged(state: PlaybackState) {}
    fun onTrackChanged(song: Song?) {}
    fun onPlaybackStarted() {}
    fun onPlaybackPaused() {}
    fun onPlaybackStopped() {}
    fun onPlaybackCompleted() {}
    fun onQueueChanged(queue: List<Song>) {}
    fun onFavoriteChanged(songId: Long, isFavorite: Boolean) {}
    fun onPlaylistChanged(playlistId: Long) {}
    fun onSettingsChanged(key: String, value: Any?) {}
}
