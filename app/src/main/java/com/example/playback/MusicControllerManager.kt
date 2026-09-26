package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.preferences.PreferencesManager
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.plugin.PluginManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.random.Random

class MusicControllerManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    val equalizerManager: EqualizerManager,
    private val onSongPlayed: (Long) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    var pluginManager: PluginManager? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var originalQueue = mutableListOf<Song>()
    private var currentQueueSongs = mutableListOf<Song>()
    private var positionUpdateJob: Job? = null
    private var volumeFadeJob: Job? = null
    private var isCrossfading = false
    private var pendingRestore: Triple<Song, List<Song>, Long>? = null
    var playbackService: MusicPlaybackService? = null
        private set

    fun attachService(service: MusicPlaybackService) {
        playbackService = service
    }

    fun detachService() {
        playbackService = null
    }

    private fun getService(): MusicPlaybackService? =
        playbackService ?: MusicPlaybackService.instance

    init {
        initController()
    }

    private fun initController() {
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, MusicPlaybackService::class.java)
            )
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    setupPlayerListener()
                    applyPlaybackParameters()
                    val pending = pendingRestore
                    if (pending != null) {
                        pendingRestore = null
                        restoreLastPlayedSong(pending.first, pending.second, pending.third)
                    }
                    updatePlaybackState()
                } catch (e: Exception) {
                    Log.e("MusicControllerManager", "Failed to connect to MediaController", e)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Throwable) {
            Log.e("MusicControllerManager", "Failed to initialize MediaController", e)
        }
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                if (isPlaying) {
                    startPositionUpdates()
                    pluginManager?.dispatchPlaybackStarted()
                } else {
                    stopPositionUpdates()
                    pluginManager?.dispatchPlaybackPaused()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState()
                if (state == Player.STATE_ENDED) {
                    stopPositionUpdates()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlaybackState()
                val currentSong = _playbackState.value.currentSong
                if (currentSong != null) {
                    onSongPlayed(currentSong.id)
                    preferencesManager.setLastPlayedSong(currentSong.id, 0L)
                    pluginManager?.dispatchTrackChanged(currentSong)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlaybackState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicControllerManager", "Player error: ${error.message}", error)
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Ошибка воспроизведения: ${error.localizedMessage ?: "Не удалось воспроизвести файл"}"
                )
            }

            @OptIn(UnstableApi::class)
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    equalizerManager.bindAudioSession(audioSessionId)
                }
            }
        })

        val savedShuffle = preferencesManager.shuffleEnabled.value
        controller.shuffleModeEnabled = savedShuffle

        val savedRepeat = when (preferencesManager.repeatMode.value) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = savedRepeat
        controller.volume = preferencesManager.playerVolume.value
    }

    fun applyPlaybackParameters() {
        val controller = mediaController ?: return
        val speed = preferencesManager.playbackSpeed.value.coerceIn(0.5f, 2.0f)
        val semitones = preferencesManager.pitchSemitones.value.coerceIn(-12, 12)

        // Standard semitone pitch calculation: 2^(semitones / 12)
        // With semitones = 0 (default), pitchMultiplier is exactly 1.0f:
        // Speed changes tempo cleanly via time-stretch without shifting voice or musical pitch.
        val pitchMultiplier = 2.0f.pow(semitones / 12.0f)

        try {
            controller.playbackParameters = PlaybackParameters(speed, pitchMultiplier)
        } catch (e: Exception) {
            Log.e("MusicControllerManager", "Failed to apply playback parameters", e)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        preferencesManager.setPlaybackSpeed(clamped)
        applyPlaybackParameters()
    }

    fun setPitchSemitones(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        preferencesManager.setPitchSemitones(clamped)
        applyPlaybackParameters()
    }

    fun setPreservePitch(preserve: Boolean) {
        preferencesManager.setPreservePitch(preserve)
        applyPlaybackParameters()
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val currentMediaItem = controller.currentMediaItem
        val currentSong = currentMediaItem?.let { item ->
            val id = item.mediaId.toLongOrNull() ?: -1L
            currentQueueSongs.find { it.id == id } ?: songFromMediaItem(item)
        } ?: _playbackState.value.currentSong

        val repeat = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        val duration = if (controller.duration > 0) controller.duration else (currentSong?.durationMs ?: 0L)

        val newState = _playbackState.value.copy(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = controller.bufferedPosition,
            shuffleModeEnabled = controller.shuffleModeEnabled,
            repeatMode = repeat,
            queue = currentQueueSongs.toList(),
            currentQueueIndex = controller.currentMediaItemIndex,
            errorMessage = null
        )
        _playbackState.value = newState
        pluginManager?.dispatchPlaybackStateChanged(newState)
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = if (controller.duration > 0) controller.duration else (_playbackState.value.currentSong?.durationMs ?: 0L)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        bufferedPositionMs = controller.bufferedPosition
                    )

                    // Auto-crossfade nearing track end
                    val crossfadeEnabled = preferencesManager.crossfadeEnabled.value
                    val crossfadeDurationSec = preferencesManager.crossfadeDuration.value
                    val crossfadeMs = (crossfadeDurationSec * 1000).toLong()

                    if (crossfadeEnabled && crossfadeMs > 0 && dur > crossfadeMs * 2) {
                        val remaining = dur - pos
                        if (remaining in 1..(crossfadeMs + 60L) && !isCrossfading) {
                            val nextIndex = controller.nextMediaItemIndex
                            val targetIdx = if (nextIndex != C.INDEX_UNSET) {
                                nextIndex
                            } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0) {
                                0
                            } else {
                                C.INDEX_UNSET
                            }

                            if (targetIdx != C.INDEX_UNSET) {
                                isCrossfading = true
                                if (playbackService != null) {
                                    playbackService?.crossfadeTo(targetIdx, crossfadeMs)
                                } else {
                                    performCrossfadeNext(crossfadeMs)
                                }
                                scope.launch {
                                    delay(crossfadeMs + 200L)
                                    isCrossfading = false
                                }
                            }
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun performCrossfadeNext(durationMs: Long) {
        val controller = mediaController ?: return
        val nextIdx = controller.nextMediaItemIndex
        if (nextIdx != C.INDEX_UNSET && playbackService != null) {
            playbackService?.crossfadeTo(nextIdx, durationMs)
            return
        }
        scope.launch {
            fadeVolume(from = 1.0f, to = 0.0f, durationMs = durationMs / 2)
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            }
            fadeVolume(from = 0.0f, to = 1.0f, durationMs = durationMs / 2)
            isCrossfading = false
        }
    }

    private suspend fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        val controller = mediaController ?: return
        val steps = 15
        val stepDelay = (durationMs / steps).coerceAtLeast(10L)
        val delta = (to - from) / steps

        for (i in 0..steps) {
            val volume = (from + delta * i).coerceIn(0.0f, 1.0f)
            controller.volume = volume
            delay(stepDelay)
        }
        controller.volume = to
    }

    // Playback control APIs
    fun restoreLastPlayedSong(song: Song, queue: List<Song> = listOf(song), positionMs: Long = 0L) {
        val controller = mediaController ?: return
        if (controller.currentMediaItem != null || _playbackState.value.currentSong != null) return
        originalQueue = queue.toMutableList()
        currentQueueSongs.clear()
        currentQueueSongs.addAll(queue)

        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val mediaItems = queue.map { songToMediaItem(it) }

        controller.setMediaItems(mediaItems, startIndex, positionMs.coerceAtLeast(0L))
        controller.prepare()

        val newState = _playbackState.value.copy(
            currentSong = song,
            isPlaying = false,
            currentPositionMs = positionMs.coerceAtLeast(0L),
            durationMs = song.durationMs,
            queue = currentQueueSongs.toList(),
            currentQueueIndex = startIndex
        )
        _playbackState.value = newState
        pluginManager?.dispatchQueueChanged(currentQueueSongs)
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        val controller = mediaController ?: return
        originalQueue = queue.toMutableList()
        currentQueueSongs.clear()
        currentQueueSongs.addAll(queue)

        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val mediaItems = queue.map { songToMediaItem(it) }

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()

        if (preferencesManager.fadeInOnStart.value) {
            scope.launch {
                controller.volume = 0f
                controller.play()
                fadeVolume(0f, 1f, 350L)
            }
        } else {
            controller.volume = 1f
            controller.play()
        }
        updatePlaybackState()
        pluginManager?.dispatchQueueChanged(currentQueueSongs)
    }

    fun playQueue(queue: List<Song>, startIndex: Int = 0) {
        if (queue.isEmpty()) return
        val controller = mediaController ?: return
        originalQueue = queue.toMutableList()
        currentQueueSongs.clear()
        currentQueueSongs.addAll(queue)

        val mediaItems = queue.map { songToMediaItem(it) }
        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, queue.size - 1), 0L)
        controller.prepare()
        controller.play()
        updatePlaybackState()
        pluginManager?.dispatchQueueChanged(currentQueueSongs)
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.mediaItemCount == 0) {
                if (currentQueueSongs.isNotEmpty()) {
                    controller.setMediaItems(currentQueueSongs.map { songToMediaItem(it) }, 0, 0L)
                    controller.prepare()
                }
            }
            controller.play()
        }
        updatePlaybackState()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        val crossfadeEnabled = preferencesManager.crossfadeEnabled.value
        val crossfadeDurationSec = preferencesManager.crossfadeDuration.value
        val crossfadeMs = (crossfadeDurationSec * 1000).toLong()

        val nextIndex = controller.nextMediaItemIndex
        val targetIdx = if (nextIndex != C.INDEX_UNSET) {
            nextIndex
        } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0) {
            0
        } else {
            C.INDEX_UNSET
        }

        if (targetIdx != C.INDEX_UNSET) {
            if (crossfadeEnabled && crossfadeMs > 0 && controller.isPlaying && playbackService != null) {
                playbackService?.crossfadeTo(targetIdx, crossfadeMs)
            } else {
                controller.seekTo(targetIdx, 0L)
                controller.play()
            }
        }
        updatePlaybackState()
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else {
            val prevIndex = controller.previousMediaItemIndex
            val targetIdx = if (prevIndex != C.INDEX_UNSET) {
                prevIndex
            } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0) {
                controller.mediaItemCount - 1
            } else {
                C.INDEX_UNSET
            }

            if (targetIdx != C.INDEX_UNSET) {
                val crossfadeEnabled = preferencesManager.crossfadeEnabled.value
                val crossfadeDurationSec = preferencesManager.crossfadeDuration.value
                val crossfadeMs = (crossfadeDurationSec * 1000).toLong()
                if (crossfadeEnabled && crossfadeMs > 0 && controller.isPlaying && playbackService != null) {
                    playbackService?.crossfadeTo(targetIdx, crossfadeMs)
                } else {
                    controller.seekTo(targetIdx, 0L)
                    controller.play()
                }
            } else {
                controller.seekTo(0L)
            }
        }
        updatePlaybackState()
    }

    fun rewind10() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition - 10000).coerceAtLeast(0)
        controller.seekTo(newPos)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = newPos)
    }

    fun forward10() {
        val controller = mediaController ?: return
        val newPos = (controller.currentPosition + 10000).coerceAtMost(controller.duration)
        controller.seekTo(newPos)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = newPos)
    }

    /**
     * Seamless shuffle toggle:
     * Toggles ExoPlayer native shuffle mode without stopping or resetting audio buffers.
     */
    fun toggleShuffle() {
        val controller = mediaController ?: return
        val enableShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = enableShuffle
        preferencesManager.setShuffleEnabled(enableShuffle)
        updatePlaybackState()
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val newMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = newMode
        val stateRepeat = when (newMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        preferencesManager.setRepeatMode(stateRepeat)
        _playbackState.value = _playbackState.value.copy(repeatMode = stateRepeat)
        updatePlaybackState()
    }

    fun stop() {
        val controller = mediaController ?: return
        controller.stop()
        updatePlaybackState()
    }

    fun getQueue(): List<Song> = currentQueueSongs.toList()

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        currentQueueSongs.add(song)
        originalQueue.add(song)
        controller.addMediaItem(songToMediaItem(song))
        updatePlaybackState()
        pluginManager?.dispatchQueueChanged(currentQueueSongs)
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in currentQueueSongs.indices) {
            val removedSong = currentQueueSongs.removeAt(index)
            originalQueue.removeAll { it.id == removedSong.id }
            controller.removeMediaItem(index)
            updatePlaybackState()
            pluginManager?.dispatchQueueChanged(currentQueueSongs)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in currentQueueSongs.indices && toIndex in currentQueueSongs.indices) {
            val item = currentQueueSongs.removeAt(fromIndex)
            currentQueueSongs.add(toIndex, item)
            controller.moveMediaItem(fromIndex, toIndex)
            updatePlaybackState()
            pluginManager?.dispatchQueueChanged(currentQueueSongs)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        moveQueueItem(fromIndex, toIndex)
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in currentQueueSongs.indices) {
            controller.seekTo(index, 0L)
            controller.play()
            updatePlaybackState()
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        currentQueueSongs.clear()
        originalQueue.clear()
        controller.clearMediaItems()
        updatePlaybackState()
        pluginManager?.dispatchQueueChanged(emptyList())
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUriString?.let { Uri.parse(it) })
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(Uri.parse(song.contentUriString))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun songFromMediaItem(item: MediaItem): Song {
        val meta = item.mediaMetadata
        return Song(
            id = item.mediaId.toLongOrNull() ?: 0L,
            title = meta.title?.toString() ?: "Неизвестный трек",
            artist = meta.artist?.toString() ?: "Неизвестный исполнитель",
            album = meta.albumTitle?.toString() ?: "",
            albumId = 0L,
            durationMs = 0L,
            contentUriString = item.requestMetadata.mediaUri?.toString() ?: "",
            albumArtUriString = meta.artworkUri?.toString()
        )
    }

    fun release() {
        stopPositionUpdates()
        equalizerManager.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
