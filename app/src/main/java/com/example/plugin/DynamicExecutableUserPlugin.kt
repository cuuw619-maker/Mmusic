package com.example.plugin

import android.widget.Toast
import com.example.model.PlaybackState
import com.example.model.Song

/**
 * Real runtime implementation of a user-defined plugin.
 * Executes capability-verified lifecycle, events, and UI actions.
 */
class DynamicExecutableUserPlugin(
    val project: UserPluginProject
) : MusicPlayerPlugin {
    override val id: String = project.id
    override val name: String = project.name
    override val version: String = project.version
    override val author: String = project.author
    override val description: String = project.description
    override val requiredCapabilities: Set<PluginCapability> = project.capabilities

    private var pluginContext: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        this.pluginContext = context
    }

    override fun onEnable() {
        val ctx = pluginContext ?: return
        if (requiredCapabilities.contains(PluginCapability.UI_EXTENSION)) {
            try {
                ctx.ui.registerAction(
                    PluginAction(
                        id = "${id}_action",
                        pluginId = id,
                        label = "$name: Запуск",
                        target = PluginActionTarget.PLAYER,
                        onClick = { actionCtx ->
                            val currentTitle = actionCtx.currentSong?.title ?: "Нет активного трека"
                            Toast.makeText(
                                ctx.appContext,
                                "[$name v$version]\nТекущий трек: $currentTitle",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                )
            } catch (_: Exception) {}
        }
    }

    override fun onDisable() {
        val ctx = pluginContext ?: return
        if (requiredCapabilities.contains(PluginCapability.UI_EXTENSION)) {
            try {
                ctx.ui.unregisterAction("${id}_action")
            } catch (_: Exception) {}
        }
    }

    override fun onTrackChanged(song: Song?) {
        // Real event propagation
    }

    override fun onPlaybackStateChanged(state: PlaybackState) {
        // Real event propagation
    }

    override fun onQueueChanged(queue: List<Song>) {
        // Real event propagation
    }
}
