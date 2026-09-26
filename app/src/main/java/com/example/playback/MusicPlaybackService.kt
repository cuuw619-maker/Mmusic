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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.MusicApplication
import com.example.R
import com.google.common.collect.ImmutableList
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
        const val ACTION_TOGGLE_SHUFFLE = "com.example.action.TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.example.action.TOGGLE_REPEAT"
        var instance: MusicPlaybackService? = null
            private set
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
        instance = this

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

        // Custom commands for notification & lockscreen: Shuffle, Repeat
        // Using clean vector icons
        val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Перемешать")
            .setIconResId(R.drawable.ic_shuffle_custom)
            .setSessionCommand(shuffleCommand)
            .build()

        val repeatCommand = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
        val repeatButton = CommandButton.Builder()
            .setDisplayName("Повтор")
            .setIconResId(R.drawable.ic_repeat_custom)
            .setSessionCommand(repeatCommand)
            .build()

        // Exact layout: Shuffle, Repeat + MediaSession handles Previous, Play/Pause, Next
        val customLayout = listOf(shuffleButton, repeatButton)

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(shuffleCommand)
                    .add(repeatCommand)
                    .build()

                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
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
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .setCustomLayout(customLayout)
            .build()

        val notificationProvider = object : DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: Player.Commands,
                customLayout: ImmutableList<CommandButton>,
                showWhenCompact: Boolean
            ): ImmutableList<CommandButton> {
                val prevBtn = CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .setIconResId(R.drawable.ic_notification_prev)
                    .setDisplayName("Предыдущий")
                    .build()

                val nextBtn = CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .setIconResId(R.drawable.ic_notification_next)
                    .setDisplayName("Следующий")
                    .build()

                val playPauseBtn = CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setIconResId(
                        if (session.player.isPlaying) R.drawable.ic_notification_pause
                        else R.drawable.ic_notification_play
                    )
                    .setDisplayName(if (session.player.isPlaying) "Пауза" else "Воспроизведение")
                    .build()

                val shuffleBtn = customLayout.firstOrNull { it.sessionCommand?.customAction == ACTION_TOGGLE_SHUFFLE }
                    ?: shuffleButton

                val repeatBtn = customLayout.firstOrNull { it.sessionCommand?.customAction == ACTION_TOGGLE_REPEAT }
                    ?: repeatButton

                return if (showWhenCompact) {
                    ImmutableList.of(prevBtn, playPauseBtn, nextBtn)
                } else {
                    ImmutableList.of(prevBtn, shuffleBtn, playPauseBtn, nextBtn, repeatBtn)
                }
            }
        }
        setMediaNotificationProvider(notificationProvider)
    }

    fun crossfadeTo(targetIndex: Int, crossfadeMs: Long) {
        if (targetIndex !in 0 until player.mediaItemCount) return
        val targetItem = player.getMediaItemAt(targetIndex)
        val originalVolume = 1.0f

        crossfadeJob?.cancel()

        val secPlayer = crossfadePlayer ?: run {
            player.seekToDefaultPosition(targetIndex)
            player.play()
            return
        }

        // Secondary player starts target track smoothly from 0 volume
        secPlayer.stop()
        secPlayer.clearMediaItems()
        secPlayer.setMediaItem(targetItem, 0L)
        secPlayer.volume = 0f
        secPlayer.prepare()
        secPlayer.play()

        crossfadeJob = serviceScope.launch {
            // Wait briefly for secondary player to finish buffering so audio is ready
            var waitCount = 0
            while (secPlayer.playbackState == Player.STATE_BUFFERING && waitCount < 15) {
                delay(10L)
                waitCount++
            }

            // Crossfade: player fades down 1 -> 0, secPlayer fades up 0 -> 1
            val steps = 25
            val stepDelay = (crossfadeMs / steps).coerceAtLeast(10L)
            for (i in 1..steps) {
                val fraction = i / steps.toFloat()
                player.volume = ((1f - fraction) * originalVolume).coerceIn(0f, 1f)
                secPlayer.volume = (fraction * originalVolume).coerceIn(0f, 1f)
                delay(stepDelay)
            }

            // At this point, player.volume is 0f and secPlayer is playing smoothly at 1.0f
            player.volume = 0f
            secPlayer.volume = originalVolume

            // Pause player briefly so it does not trigger automatic track change or buffer conflict
            player.pause()

            // Prepare player on the target track at secPlayer's current position
            val handoverPos = secPlayer.currentPosition.coerceAtLeast(0L)
            player.seekTo(targetIndex, handoverPos)
            player.volume = 0f
            player.play()

            // Wait until primary player is ready and actively playing to guarantee zero audio gap
            var readyWait = 0
            while ((!player.isPlaying || player.playbackState != Player.STATE_READY) && readyWait < 30) {
                delay(10L)
                readyWait++
            }

            // Smooth 30ms micro-crossfade handover: ramp player up, ramp secPlayer down
            val microSteps = 3
            for (step in 1..microSteps) {
                val factor = step / microSteps.toFloat()
                player.volume = (factor * originalVolume).coerceIn(0f, 1f)
                secPlayer.volume = ((1f - factor) * originalVolume).coerceIn(0f, 1f)
                delay(10L)
            }

            player.volume = originalVolume
            secPlayer.stop()
            secPlayer.clearMediaItems()
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
        if (instance === this) {
            instance = null
        }
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
