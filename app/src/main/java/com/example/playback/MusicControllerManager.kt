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
    var playbackService: MusicPlaybackService? = null
        private set

    fun attachService(service: MusicPlaybackService) {
        playbackService = service
    }

    fun detachService() {
        playbackService = null
    }

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

        controller.shuffleModeEnabled = false
        controller.repeatMode = Player.REPEAT_MODE_OFF
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
        }

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
                        if (remaining in 1..crossfadeMs && !isCrossfading && controller.hasNextMediaItem()) {
                            isCrossfading = true
                            val nextIndex = controller.nextMediaItemIndex
                            if (nextIndex != C.INDEX_UNSET) {
                                if (playbackService != null) {
                                    playbackService?.crossfadeTo(nextIndex, crossfadeMs)
                                } else {
                                    performCrossfadeNext(crossfadeMs)
                                }
                            }
                            scope.launch {
                                delay(crossfadeMs + 300L)
                                isCrossfading = false
                            }
                        }
                    }
                }
                delay(200)
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
        if (nextIndex != C.INDEX_UNSET) {
            if (crossfadeEnabled && crossfadeMs > 0 && controller.isPlaying && playbackService != null) {
                playbackService?.crossfadeTo(nextIndex, crossfadeMs)
            } else {
                controller.seekToNextMediaItem()
            }
        } else if (controller.mediaItemCount > 0) {
            if (crossfadeEnabled && crossfadeMs > 0 && controller.isPlaying && playbackService != null) {
                playbackService?.crossfadeTo(0, crossfadeMs)
            } else {
                controller.seekTo(0, 0L)
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
            if (prevIndex != C.INDEX_UNSET) {
                val crossfadeEnabled = preferencesManager.crossfadeEnabled.value
                val crossfadeDurationSec = preferencesManager.crossfadeDuration.value
                val crossfadeMs = (crossfadeDurationSec * 1000).toLong()
                if (crossfadeEnabled && crossfadeMs > 0 && controller.isPlaying && playbackService != null) {
                    playbackService?.crossfadeTo(prevIndex, crossfadeMs)
                } else {
                    controller.seekToPreviousMediaItem()
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
     * Real stable Fisher-Yates shuffle algorithm.
     * When enabling: keeps current song playing at current position, shuffles remaining items.
     * When disabling: smoothly restores original un-shuffled queue order.
     */
    fun toggleShuffle() {
        val controller = mediaController ?: return
        val enableShuffle = !controller.shuffleModeEnabled

        if (currentQueueSongs.isEmpty()) {
            controller.shuffleModeEnabled = enableShuffle
            _playbackState.value = _playbackState.value.copy(shuffleModeEnabled = enableShuffle)
            return
        }

        val currentSong = _playbackState.value.currentSong
        val currentPosition = controller.currentPosition
        val isPlaying = controller.isPlaying

        if (enableShuffle) {
            // Build stable shuffled list with current song staying in place
            val remaining = originalQueue.filter { it.id != currentSong?.id }.toMutableList()
            // Fisher-Yates shuffle
            val rng = Random(System.currentTimeMillis())
            for (i in remaining.indices.reversed()) {
                val j = rng.nextInt(i + 1)
                val temp = remaining[i]
                remaining[i] = remaining[j]
                remaining[j] = temp
            }

            val newQueue = mutableListOf<Song>()
            if (currentSong != null) {
                newQueue.add(currentSong)
            }
            newQueue.addAll(remaining)

            currentQueueSongs = newQueue
            controller.setMediaItems(
                newQueue.map { songToMediaItem(it) },
                0,
                currentPosition
            )
            controller.shuffleModeEnabled = true
        } else {
            // Restore original queue order
            val restoreIndex = if (currentSong != null) {
                originalQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            } else 0

            currentQueueSongs = originalQueue.toMutableList()
            controller.setMediaItems(
                currentQueueSongs.map { songToMediaItem(it) },
                restoreIndex,
                currentPosition
            )
            controller.shuffleModeEnabled = false
        }

        if (isPlaying) {
            controller.play()
        }
        updatePlaybackState()
        pluginManager?.dispatchQueueChanged(currentQueueSongs)
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
        _playbackState.value = _playbackState.value.copy(repeatMode = stateRepeat)
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
