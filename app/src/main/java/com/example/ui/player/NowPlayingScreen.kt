package com.example.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.ui.animation.FluidMotionConstants
import com.example.ui.animation.HapticType
import com.example.ui.animation.calculateRubberBandOffset
import com.example.ui.animation.fluidPress
import com.example.ui.animation.performHapticFeedback
import com.example.ui.components.AppIcons
import com.example.ui.components.InteractiveProgressBar
import com.example.ui.components.MorphingPlayPauseButton
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onOpenSleepTimer: () -> Unit = {},
    currentSpeed: Float = 1.0f,
    currentPitchSemitones: Int = 0,
    preservePitch: Boolean = true,
    onSpeedChanged: (Float) -> Unit = {},
    onPitchChanged: (Int) -> Unit = {},
    onPreservePitchChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCollapse)

    val song = playbackState.currentSong ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var showMoreMenu by remember { mutableStateOf(false) }
    var showTrackDetailsDialog by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }

    // Physical gesture drag offsets for Album Art with rubber-banding and rotation
    val artOffsetX = remember { Animatable(0f) }
    val artOffsetY = remember { Animatable(0f) }
    val screenOffsetY = remember { Animatable(0f) }

    // Track previous song id to determine forward / backward transition direction
    var previousSongId by remember { mutableLongStateOf(song.id) }
    var isForwardTransition by remember { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        if (previousSongId != song.id) {
            isForwardTransition = true
            previousSongId = song.id
        }
    }

    if (showTrackDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showTrackDetailsDialog = false },
            title = { Text("Свойства трека") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Название: ${song.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("Исполнитель: ${song.artist}", style = MaterialTheme.typography.bodyMedium)
                    Text("Альбом: ${song.album}", style = MaterialTheme.typography.bodyMedium)
                    Text("Формат: ${song.format}", style = MaterialTheme.typography.bodyMedium)
                    if (song.bitrateKbps > 0) {
                        Text("Битрейт: ${song.bitrateKbps} kbps", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (song.sizeFormatted.isNotEmpty()) {
                        Text("Размер файла: ${song.sizeFormatted}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (song.year > 0) {
                        Text("Год: ${song.year}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (song.folderName.isNotEmpty()) {
                        Text("Папка: ${song.folderName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (song.dataPath.isNotEmpty()) {
                        Text("Путь: ${song.dataPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackDetailsDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, screenOffsetY.value.roundToInt()) }
            .testTag("now_playing_screen"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            scope.launch {
                                artOffsetX.snapTo(calculateRubberBandOffset(totalDragX, 0.18f))
                                artOffsetY.snapTo(calculateRubberBandOffset(totalDragY, 0.18f))
                                if (totalDragY > 0) {
                                    screenOffsetY.snapTo(calculateRubberBandOffset(totalDragY, 0.5f))
                                }
                            }
                        },
                        onDragEnd = {
                            val threshold = 140f
                            if (totalDragY > threshold && abs(totalDragY) > abs(totalDragX)) {
                                // Swipe Down -> Dismiss Now Playing
                                performHapticFeedback(view, HapticType.MEDIUM)
                                onCollapse()
                            } else if (totalDragY < -threshold && abs(totalDragY) > abs(totalDragX)) {
                                // Swipe Up -> Open Queue
                                performHapticFeedback(view, HapticType.MEDIUM)
                                onOpenQueue()
                            } else if (totalDragX < -threshold) {
                                // Swipe Left -> Next Track
                                isForwardTransition = true
                                performHapticFeedback(view, HapticType.LIGHT)
                                onSkipNext()
                            } else if (totalDragX > threshold) {
                                // Swipe Right -> Previous Track
                                isForwardTransition = false
                                performHapticFeedback(view, HapticType.LIGHT)
                                onSkipPrevious()
                            }

                            scope.launch {
                                artOffsetX.animateTo(0f, FluidMotionConstants.dragSpring())
                            }
                            scope.launch {
                                artOffsetY.animateTo(0f, FluidMotionConstants.dragSpring())
                            }
                            scope.launch {
                                screenOffsetY.animateTo(0f, FluidMotionConstants.dragSpring())
                            }
                        },
                        onDragCancel = {
                            scope.launch { artOffsetX.animateTo(0f) }
                            scope.launch { artOffsetY.animateTo(0f) }
                            scope.launch { screenOffsetY.animateTo(0f) }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.92f,
                                hapticType = HapticType.LIGHT,
                                onClick = onCollapse
                            )
                            .testTag("now_playing_collapse_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Свернуть",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "СЕЙЧАС ИГРАЕТ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (song.album.isNotBlank()) {
                            Text(
                                text = song.album,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .fluidPress(
                                    scaleDown = 0.92f,
                                    hapticType = HapticType.LIGHT,
                                    onClick = { showMoreMenu = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Меню",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Эквалайзер") },
                                leadingIcon = { Icon(AppIcons.Equalizer, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenEqualizer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Скорость и тональность") },
                                leadingIcon = { Icon(AppIcons.Speed, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showSpeedPitchSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Таймер сна") },
                                leadingIcon = { Icon(AppIcons.SleepTimer, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenSleepTimer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Свойства трека") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showTrackDetailsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Поделиться") },
                                leadingIcon = { Icon(AppIcons.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Слушаю ${song.title} — ${song.artist}")
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Поделиться треком"))
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Physical Album Art Container with directional Motion & Tilt
                val rotationAngle = (artOffsetX.value * 0.08f).coerceIn(-8f, 8f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .offset { IntOffset(artOffsetX.value.roundToInt(), artOffsetY.value.roundToInt()) }
                        .graphicsLayer {
                            rotationZ = rotationAngle
                        }
                        .shadow(16.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(song.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    performHapticFeedback(view, HapticType.TOGGLE)
                                    onToggleFavorite(song.id)
                                },
                                onLongPress = {
                                    performHapticFeedback(view, HapticType.HEAVY)
                                    showTrackDetailsDialog = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            if (isForwardTransition) {
                                (slideInHorizontally(FluidMotionConstants.artworkSpring()) { width -> (width * 0.3f).toInt() } + fadeIn(FluidMotionConstants.pressSpring())) togetherWith
                                (slideOutHorizontally(FluidMotionConstants.artworkSpring()) { width -> (-width * 0.3f).toInt() } + fadeOut(FluidMotionConstants.pressSpring()))
                            } else {
                                (slideInHorizontally(FluidMotionConstants.artworkSpring()) { width -> (-width * 0.3f).toInt() } + fadeIn(FluidMotionConstants.pressSpring())) togetherWith
                                (slideOutHorizontally(FluidMotionConstants.artworkSpring()) { width -> (width * 0.3f).toInt() } + fadeOut(FluidMotionConstants.pressSpring()))
                            }
                        },
                        label = "album_art_motion"
                    ) { currentSong ->
                        if (!currentSong.albumArtUriString.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentSong.albumArtUriString)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.Library,
                                contentDescription = null,
                                modifier = Modifier.size(108.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Title, Artist, Quality Badge, and Favorite with Directional Slide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = song,
                            transitionSpec = {
                                (fadeIn(FluidMotionConstants.pressSpring()) + slideInHorizontally(FluidMotionConstants.pressSpring()) { if (isForwardTransition) 30 else -30 }) togetherWith
                                (fadeOut(FluidMotionConstants.pressSpring()) + slideOutHorizontally(FluidMotionConstants.pressSpring()) { if (isForwardTransition) -30 else 30 })
                            },
                            label = "track_title_motion"
                        ) { currentSong ->
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = song.qualityBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Favorite Button with Haptic
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.88f,
                                hapticType = HapticType.TOGGLE,
                                onClick = { onToggleFavorite(song.id) }
                            )
                            .testTag("now_playing_favorite_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) AppIcons.FavoriteFilled else AppIcons.FavoriteOutlined,
                            contentDescription = if (song.isFavorite) "В избранном" else "В избранное",
                            tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Physics Progress Bar
                InteractiveProgressBar(
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Quick utility chips (Speed, Pitch, EQ, Sleep)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fluidPress(
                            scaleDown = 0.94f,
                            hapticType = HapticType.LIGHT,
                            onClick = { showSpeedPitchSheet = true }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "%.2fx".format(currentSpeed),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (currentPitchSemitones != 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fluidPress(
                                scaleDown = 0.94f,
                                hapticType = HapticType.LIGHT,
                                onClick = { showSpeedPitchSheet = true }
                            )
                        ) {
                            Text(
                                text = "%+d st".format(currentPitchSemitones),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fluidPress(
                            scaleDown = 0.94f,
                            hapticType = HapticType.LIGHT,
                            onClick = onOpenEqualizer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Equalizer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EQ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fluidPress(
                            scaleDown = 0.94f,
                            hapticType = HapticType.LIGHT,
                            onClick = onOpenSleepTimer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.SleepTimer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Сон",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.TOGGLE,
                                onClick = onToggleShuffle
                            )
                            .testTag("now_playing_shuffle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Shuffle,
                            contentDescription = "Перемешать",
                            tint = if (playbackState.shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    // 10s Rewind
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = onRewind10
                            )
                            .testTag("now_playing_rewind_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Rewind,
                            contentDescription = "Назад на 10 сек",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Previous Track
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = {
                                    isForwardTransition = false
                                    onSkipPrevious()
                                }
                            )
                            .testTag("now_playing_prev_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Previous,
                            contentDescription = "Предыдущий трек",
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Morphing Play/Pause Button
                    MorphingPlayPauseButton(
                        isPlaying = playbackState.isPlaying,
                        onClick = onPlayPause,
                        size = 72.dp,
                        iconSize = 36.dp
                    )

                    // Next Track
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = {
                                    isForwardTransition = true
                                    onSkipNext()
                                }
                            )
                            .testTag("now_playing_next_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Next,
                            contentDescription = "Следующий трек",
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 10s Forward
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = onForward10
                            )
                            .testTag("now_playing_forward_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Forward,
                            contentDescription = "Вперед на 10 сек",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Repeat Mode
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.TOGGLE,
                                onClick = onCycleRepeatMode
                            )
                            .testTag("now_playing_repeat_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        val (icon, tint) = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> AppIcons.Repeat to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            RepeatMode.ALL -> AppIcons.Repeat to MaterialTheme.colorScheme.primary
                            RepeatMode.ONE -> AppIcons.RepeatOne to MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = playbackState.repeatMode.label,
                            tint = tint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom actions: Add to playlist, Queue button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = { onAddToPlaylist(song) }
                            )
                            .testTag("now_playing_add_playlist_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.PlaylistAdd,
                            contentDescription = "Добавить в плейлист",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .fluidPress(
                                scaleDown = 0.90f,
                                hapticType = HapticType.LIGHT,
                                onClick = onOpenQueue
                            )
                            .testTag("now_playing_queue_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Queue,
                            contentDescription = "Очередь воспроизведения",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showSpeedPitchSheet) {
        SpeedPitchBottomSheet(
            currentSpeed = currentSpeed,
            currentPitchSemitones = currentPitchSemitones,
            preservePitch = preservePitch,
            onSpeedChanged = onSpeedChanged,
            onPitchChanged = onPitchChanged,
            onPreservePitchChanged = onPreservePitchChanged,
            onDismiss = { showSpeedPitchSheet = false }
        )
    }
}
