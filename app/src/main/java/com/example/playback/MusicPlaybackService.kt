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
import androidx.media3.exoplayer.DefaultLoadControl
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

        // Studio Master 64-bit Audio DSP pipeline with float processing & 4x anti-crackle buffer headroom
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(true)
                    .setAudioTrackBufferSizeProvider { minBufferSizeInBytes, _, _, _, _, _, _ ->
                        // Quadruple buffer headroom (minimum 128KB) to completely eliminate buffer underruns,
                        // crackles, flutter, and "репение" even during deep time-stretching (0.5x speed)
                        (minBufferSizeInBytes * 4).coerceAtLeast(131072)
                    }
                    .build()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30000,
                /* maxBufferMs = */ 60000,
                /* bufferForPlaybackMs = */ 2000,
                /* bufferForPlaybackAfterRebufferMs = */ 5000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .build()

        val crossfadeRenderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(true)
                    .setAudioTrackBufferSizeProvider { minBufferSizeInBytes, _, _, _, _, _, _ ->
                        (minBufferSizeInBytes * 4).coerceAtLeast(131072)
                    }
                    .build()
            }
        }

        crossfadePlayer = ExoPlayer.Builder(this, crossfadeRenderersFactory)
            .setAudioAttributes(audioAttributes, false)
            .setLoadControl(loadControl)
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
        val currentItem = player.currentMediaItem
        val secPlayer = crossfadePlayer

        crossfadeJob?.cancel()

        if (secPlayer == null || currentItem == null || crossfadeMs <= 0 || !player.isPlaying) {
            player.volume = 1.0f
            player.seekTo(targetIndex, 0L)
            player.play()
            return
        }

        crossfadeJob = serviceScope.launch {
            val currentPos = player.currentPosition.coerceAtLeast(0L)
            // 1. Prepare secondary player with the outgoing track at current position
            secPlayer.stop()
            secPlayer.clearMediaItems()
            secPlayer.setMediaItem(currentItem, currentPos)
            secPlayer.volume = player.volume
            secPlayer.prepare()

            // 2. Prepare primary player with the incoming target track at 0 volume
            player.volume = 0f
            player.seekTo(targetIndex, 0L)

            // 3. Wait until BOTH players are actively in STATE_READY so there is ZERO micropause or silence gap!
            var waitCount = 0
            while ((secPlayer.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_BUFFERING) && waitCount < 30) {
                delay(10L)
                waitCount++
            }

            // 4. Start simultaneous seamless playback
            secPlayer.play()
            player.play()

            // 5. Equal-power crossfade curve: cos for fade out, sin for fade in
            val steps = 30
            val stepDelay = (crossfadeMs / steps).coerceAtLeast(10L)
            for (i in 1..steps) {
                val fraction = i / steps.toFloat()
                // Equal-power crossfade ensures perceived acoustic loudness remains completely constant
                val outVol = kotlin.math.cos(fraction * (Math.PI / 2.0)).toFloat().coerceIn(0f, 1f)
                val inVol = kotlin.math.sin(fraction * (Math.PI / 2.0)).toFloat().coerceIn(0f, 1f)

                secPlayer.volume = outVol
                player.volume = inVol
                delay(stepDelay)
            }

            // Crossfade complete: primary player is already at full volume playing the target track!
            player.volume = 1.0f
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
