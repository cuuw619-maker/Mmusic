package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.MusicApplication
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.example.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.example.action.TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.example.action.TOGGLE_REPEAT"
        const val ACTION_REWIND = "com.example.action.REWIND"
        const val ACTION_FORWARD = "com.example.action.FORWARD"
    }

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    var crossfadePlayer: ExoPlayer? = null
        private set

    private var crossfadeJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // High quality Sonic time-stretching engine (disable platform AudioTrack params to avoid stutter/repeats)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        val crossfadeRenderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }

        crossfadePlayer = ExoPlayer.Builder(this, crossfadeRenderersFactory)
            .setAudioAttributes(audioAttributes, false)
            .build()

        // Bind equalizer directly to player session
        val app = applicationContext as? MusicApplication
        if (app != null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            app.equalizerManager.bindAudioSession(player.audioSessionId)
        }

        app?.musicControllerManager?.attachService(this)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    app?.equalizerManager?.bindAudioSession(audioSessionId)
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Custom commands for notification & lockscreen: Shuffle, Repeat, Rewind 10s, Fast Forward 10s
        val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Перемешать")
            .setIconResId(android.R.drawable.ic_menu_rotate)
            .setSessionCommand(shuffleCommand)
            .build()

        val repeatCommand = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
        val repeatButton = CommandButton.Builder()
            .setDisplayName("Повтор")
            .setIconResId(android.R.drawable.ic_menu_revert)
            .setSessionCommand(repeatCommand)
            .build()

        val rewindCommand = SessionCommand(ACTION_REWIND, Bundle.EMPTY)
        val rewindButton = CommandButton.Builder()
            .setDisplayName("Назад 10с")
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(rewindCommand)
            .build()

        val forwardCommand = SessionCommand(ACTION_FORWARD, Bundle.EMPTY)
        val forwardButton = CommandButton.Builder()
            .setDisplayName("Вперед 10с")
            .setIconResId(android.R.drawable.ic_media_ff)
            .setSessionCommand(forwardCommand)
            .build()

        val customLayout = listOf(rewindButton, forwardButton, shuffleButton, repeatButton)

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(shuffleCommand)
                    .add(repeatCommand)
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()

                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .setCustomLayout(customLayout)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                val appInst = applicationContext as? MusicApplication
                when (customCommand.customAction) {
                    ACTION_TOGGLE_SHUFFLE -> {
                        appInst?.musicControllerManager?.toggleShuffle()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_TOGGLE_REPEAT -> {
                        appInst?.musicControllerManager?.cycleRepeatMode()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_REWIND -> {
                        appInst?.musicControllerManager?.rewind10() ?: player.seekBack()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_FORWARD -> {
                        appInst?.musicControllerManager?.forward10() ?: player.seekForward()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .setCustomLayout(customLayout)
            .build()
    }

    fun crossfadeTo(targetIndex: Int, crossfadeMs: Long) {
        val currentItem = player.currentMediaItem ?: run {
            player.seekToDefaultPosition(targetIndex)
            player.play()
            return
        }

        val currentPosition = player.currentPosition
        val originalVolume = 1.0f

        crossfadeJob?.cancel()

        // Prepare secondary player with the current song's remainder to fade out
        crossfadePlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(currentItem, currentPosition)
            prepare()
            volume = originalVolume
            play()
        }

        // Switch main player to target track immediately and start at 0 volume
        player.seekToDefaultPosition(targetIndex)
        player.volume = 0f
        player.play()

        crossfadeJob = serviceScope.launch {
            val steps = 25
            val stepDelay = (crossfadeMs / steps).coerceAtLeast(12L)
            for (i in 1..steps) {
                val fraction = i / steps.toFloat()
                crossfadePlayer?.volume = ((1f - fraction) * originalVolume).coerceIn(0f, 1f)
                player.volume = (fraction * originalVolume).coerceIn(0f, 1f)
                delay(stepDelay)
            }
            crossfadePlayer?.stop()
            crossfadePlayer?.clearMediaItems()
            player.volume = originalVolume
            crossfadeJob = null
        }
    }

    fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        crossfadePlayer?.stop()
        crossfadePlayer?.clearMediaItems()
        player.volume = 1.0f
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cancelCrossfade()
        val app = applicationContext as? MusicApplication
        app?.musicControllerManager?.detachService()

        crossfadePlayer?.release()
        crossfadePlayer = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
