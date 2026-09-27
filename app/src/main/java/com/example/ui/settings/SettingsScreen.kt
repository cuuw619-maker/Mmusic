package com.example.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.PreferencesManager
import com.example.ui.components.OvalTabItem
import com.example.ui.components.OvalTabRow
import com.example.ui.player.SpeedPitchBottomSheet
import com.example.ui.util.HapticFeedbackType
import com.example.ui.util.rememberHapticHelper
import com.example.ui.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val amoledDark by viewModel.amoledDark.collectAsStateWithLifecycle()
    val resumePlayback by viewModel.resumePlayback.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val pitchSemitones by viewModel.pitchSemitones.collectAsStateWithLifecycle()
    val pitchCents by viewModel.pitchCents.collectAsStateWithLifecycle()

    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val userPlugins by viewModel.userPlugins.collectAsStateWithLifecycle()

    val audioEngineMode by viewModel.audioEngineMode.collectAsStateWithLifecycle()
    val antiCrackleBuffer by viewModel.antiCrackleBuffer.collectAsStateWithLifecycle()
    val playerSwipeDirection by viewModel.playerSwipeDirection.collectAsStateWithLifecycle()
    val doubleTapSeekSeconds by viewModel.doubleTapSeekSeconds.collectAsStateWithLifecycle()
    val volumeGestureEnabled by viewModel.volumeGestureEnabled.collectAsStateWithLifecycle()
    val filterShortAudio by viewModel.filterShortAudio.collectAsStateWithLifecycle()

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showPluginSheet by remember { mutableStateOf(false) }
    var showSwipeDirDialog by remember { mutableStateOf(false) }
    var showDoubleTapSeekDialog by remember { mutableStateOf(false) }

    val haptic = rememberHapticHelper()

    val categoryTabs = remember {
        listOf(
            OvalTabItem("Звук", Icons.Rounded.GraphicEq),
            OvalTabItem("Вид", Icons.Rounded.ColorLens),
            OvalTabItem("Жесты", Icons.Rounded.TouchApp),
            OvalTabItem("Медиатека", Icons.Rounded.Folder),
            OvalTabItem("Модули", Icons.Rounded.Extension)
        )
    }

    if (showSpeedPitchSheet) {
        SpeedPitchBottomSheet(
            currentSpeed = playbackSpeed,
            currentPitchSemitones = pitchSemitones,
            currentPitchCents = pitchCents,
            onSpeedChanged = { viewModel.setPlaybackSpeed(it) },
            onPitchChanged = { viewModel.setPitchSemitones(it) },
            onPitchCentsChanged = { viewModel.setPitchCents(it) },
            onDismiss = { showSpeedPitchSheet = false }
        )
    }

    if (showPluginSheet) {
        PluginManagerSheet(
            plugins = plugins,
            userProjects = userPlugins,
            onTogglePlugin = { id, enabled -> viewModel.setPluginEnabled(id, enabled) },
            onSaveUserPlugin = { project -> viewModel.saveUserPlugin(project) },
            onDeleteUserPlugin = { id -> viewModel.deleteUserPlugin(id) },
            onDismiss = { showPluginSheet = false }
        )
    }

    if (showThemeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выбор темы оформления") },
            text = {
                Column {
                    PreferencesManager.ThemeMode.values().forEach { mode ->
                        val label = when (mode) {
                            PreferencesManager.ThemeMode.SYSTEM -> "Как в системе"
                            PreferencesManager.ThemeMode.LIGHT -> "Светлая тема"
                            PreferencesManager.ThemeMode.DARK -> "Тёмная тема"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showSwipeDirDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSwipeDirDialog = false },
            title = { Text("Направление свайпа в плеере") },
            text = {
                Column {
                    val options = listOf(
                        "RIGHT_NEXT_LEFT_PREV" to "Свайп влево: Предыдущий • Вправо: Следующий",
                        "LEFT_NEXT_RIGHT_PREV" to "Свайп влево: Следующий • Вправо: Предыдущий"
                    )
                    options.forEach { (key, title) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setPlayerSwipeDirection(key)
                                    showSwipeDirDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playerSwipeDirection == key,
                                onClick = {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setPlayerSwipeDirection(key)
                                    showSwipeDirDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSwipeDirDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showDoubleTapSeekDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDoubleTapSeekDialog = false },
            title = { Text("Шаг перемотки по обложке") },
            text = {
                Column {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setDoubleTapSeekSeconds(sec)
                                    showDoubleTapSeekDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = doubleTapSeekSeconds == sec,
                                onClick = {
                                    haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                                    viewModel.setDoubleTapSeekSeconds(sec)
                                    showDoubleTapSeekDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "$sec секунд", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDoubleTapSeekDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Top Header
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Oval-in-Oval Category Selector
        OvalTabRow(
            tabs = categoryTabs,
            selectedIndex = selectedCategoryIndex,
            onTabSelected = { selectedCategoryIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            containerHeightDp = 46
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Animated Category Contents
        AnimatedContent(
            targetState = selectedCategoryIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(200, easing = LinearOutSlowInEasing)) { it / 4 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(160, easing = FastOutLinearInEasing)) { -it / 4 } + fadeOut(tween(140)))
                } else {
                    (slideInHorizontally(tween(200, easing = LinearOutSlowInEasing)) { -it / 4 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(160, easing = FastOutLinearInEasing)) { it / 4 } + fadeOut(tween(140)))
                }
            },
            label = "settings_category_transition"
        ) { categoryIndex ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 6.dp)
            ) {
                when (categoryIndex) {
                    0 -> {
                        // ==========================================
                        // CATEGORY 0: SOUND & DSP ENGINE
                        // ==========================================
                        item {
                            SectionTitle(title = "Студийный движок обработки звука (DSP)")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    SettingsRow(
                                        icon = Icons.Rounded.GraphicEq,
                                        title = "Режим аудиодвижка",
                                        subtitle = if (audioEngineMode == "STUDIO") "Studio Master Native (аппаратный sinc-интерполятор 64-bit)" else "Software Sonic (Exo)",
                                        onClick = {
                                            val newMode = if (audioEngineMode == "STUDIO") "SONIC" else "STUDIO"
                                            viewModel.setAudioEngineMode(newMode)
                                        }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.Headphones,
                                        title = "Студийный 4x буфер против треска",
                                        subtitle = "Предотвращает щелчки и 'репение' звука при сильном замедлении (до 0.5x)",
                                        checked = antiCrackleBuffer,
                                        onCheckedChange = { viewModel.setAntiCrackleBuffer(it) }
                                    )

                                    SettingsRow(
                                        icon = Icons.Rounded.Speed,
                                        title = "Скорость и Тональность",
                                        subtitle = "Скорость: %.2fx • Тон: %+d st • Микро: %+d центов".format(playbackSpeed, pitchSemitones, pitchCents),
                                        onClick = { showSpeedPitchSheet = true }
                                    )

                                    SettingsRow(
                                        icon = Icons.Rounded.Equalizer,
                                        title = "Эквалайзер и полосы частот",
                                        subtitle = "Настройка тембра, усиление басов Bass Boost и Virtualizer",
                                        onClick = { viewModel.setEqualizerSheetVisible(true) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            SectionTitle(title = "Бесшовный переход (Crossfade & Gapless)")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.VolumeUp,
                                        title = "Плавный переход (Crossfade)",
                                        subtitle = if (crossfadeEnabled) "Длительность: %.1f сек (Equal-Power)" else "Выключен",
                                        checked = crossfadeEnabled,
                                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                                    )

                                    if (crossfadeEnabled) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Длительность перехода",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "%.1f с".format(crossfadeDuration),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Slider(
                                                value = crossfadeDuration.coerceIn(0.1f, 1.5f),
                                                onValueChange = {
                                                    val rounded = (it * 10).roundToInt() / 10f
                                                    viewModel.setCrossfadeDuration(rounded)
                                                },
                                                valueRange = 0.1f..1.5f,
                                                steps = 13
                                            )
                                        }
                                    }

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.Waves,
                                        title = "Устранение микропауз",
                                        subtitle = "Предварительная буферизация двух движков исключает тишину и разрывы",
                                        checked = true,
                                        onCheckedChange = { /* Always active in 2.0 */ }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    1 -> {
                        // ==========================================
                        // CATEGORY 1: APPEARANCE
                        // ==========================================
                        item {
                            SectionTitle(title = "Цвета и темы")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    val themeLabel = when (themeMode) {
                                        PreferencesManager.ThemeMode.SYSTEM -> "Как в системе"
                                        PreferencesManager.ThemeMode.LIGHT -> "Светлая"
                                        PreferencesManager.ThemeMode.DARK -> "Тёмная"
                                    }
                                    SettingsRow(
                                        icon = when (themeMode) {
                                            PreferencesManager.ThemeMode.SYSTEM -> Icons.Rounded.Smartphone
                                            PreferencesManager.ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                            PreferencesManager.ThemeMode.DARK -> Icons.Rounded.DarkMode
                                        },
                                        title = "Тема оформления",
                                        subtitle = themeLabel,
                                        onClick = { showThemeDialog = true }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.DarkMode,
                                        title = "AMOLED Тёмная тема",
                                        subtitle = "Глубокий чёрный цвет фона для экранов OLED",
                                        checked = amoledDark,
                                        onCheckedChange = { viewModel.setAmoledDark(it) }
                                    )

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        SettingsSwitchRow(
                                            icon = Icons.Rounded.ColorLens,
                                            title = "Динамические цвета (Material You)",
                                            subtitle = "Палитра на основе системных обоев устройства",
                                            checked = dynamicColor,
                                            onCheckedChange = { viewModel.setDynamicColor(it) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            SectionTitle(title = "Анимации интерфейса")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.MotionPhotosOn,
                                        title = "Уменьшение движения (Reduced Motion)",
                                        subtitle = "Отключает пружинные и инерционные эффекты",
                                        checked = reducedMotion,
                                        onCheckedChange = { viewModel.setReducedMotion(it) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    2 -> {
                        // ==========================================
                        // CATEGORY 2: GESTURES & UX
                        // ==========================================
                        item {
                            SectionTitle(title = "Жесты полноэкранного плеера")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    SettingsRow(
                                        icon = Icons.Rounded.Swipe,
                                        title = "Направление свайпа по обложке",
                                        subtitle = if (playerSwipeDirection == "RIGHT_NEXT_LEFT_PREV") "Свайп влево: Предыдущий • Вправо: Следующий" else "Свайп влево: Следующий • Вправо: Предыдущий",
                                        onClick = { showSwipeDirDialog = true }
                                    )

                                    SettingsRow(
                                        icon = Icons.Rounded.TouchApp,
                                        title = "Двойное касание по краям обложки",
                                        subtitle = "Перемотка назад / вперед на $doubleTapSeekSeconds сек",
                                        onClick = { showDoubleTapSeekDialog = true }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.VolumeDown,
                                        title = "Жест громкости на экране",
                                        subtitle = "Вертикальное скольжение по экрану меняет громкость",
                                        checked = volumeGestureEnabled,
                                        onCheckedChange = { viewModel.setVolumeGestureEnabled(it) }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.Vibration,
                                        title = "Тактильный отклик (Haptic Feedback)",
                                        subtitle = "Вибрация при свайпах, переключении треков и табов",
                                        checked = hapticEnabled,
                                        onCheckedChange = { viewModel.setHapticEnabled(it) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    3 -> {
                        // ==========================================
                        // CATEGORY 3: LIBRARY & SCANNER
                        // ==========================================
                        item {
                            SectionTitle(title = "Медиатека и поиск")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    SettingsRow(
                                        icon = Icons.Rounded.Refresh,
                                        title = "Сканировать аудиофайлы",
                                        subtitle = if (isScanning) "Идет сканирование..." else "В библиотеке ${allSongs.size} треков",
                                        onClick = { viewModel.scanLibrary() }
                                    )

                                    SettingsRow(
                                        icon = Icons.Rounded.Tune,
                                        title = "Поведение при смене сортировки",
                                        subtitle = "Всегда закреплять список вверху (без прыжков к треку)",
                                        onClick = { /* Informational */ }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.MusicNote,
                                        title = "Скрывать короткие аудиозаписи (< 30с)",
                                        subtitle = "Исключать системные звуки, рингтоны и диктофон",
                                        checked = filterShortAudio,
                                        onCheckedChange = { viewModel.setFilterShortAudio(it) }
                                    )

                                    SettingsSwitchRow(
                                        icon = Icons.Rounded.Replay,
                                        title = "Возобновлять воспроизведение",
                                        subtitle = "Запоминать последний трек и позицию при старте",
                                        checked = resumePlayback,
                                        onCheckedChange = { viewModel.setResumePlayback(it) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    4 -> {
                        // ==========================================
                        // CATEGORY 4: PLUGINS & LAB
                        // ==========================================
                        item {
                            SectionTitle(title = "Расширения и модули")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    val activeCount = plugins.count { it.isEnabled }
                                    SettingsRow(
                                        icon = Icons.Rounded.Extension,
                                        title = "Менеджер плагинов",
                                        subtitle = "Активно: $activeCount из ${plugins.size} модулей",
                                        onClick = { showPluginSheet = true }
                                    )

                                    SettingsRow(
                                        icon = Icons.Rounded.Science,
                                        title = "Экспериментальная лаборатория",
                                        subtitle = "Пользовательские скрипты и DSP фильтры",
                                        onClick = { showPluginSheet = true }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            SectionTitle(title = "О приложении")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "imux player",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Версия 2.2.0 • Studio Master DSP 2026",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val haptic = rememberHapticHelper()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHaptic(HapticFeedbackType.LIGHT_TICK)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = rememberHapticHelper()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHaptic(HapticFeedbackType.TOGGLE)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHaptic(HapticFeedbackType.TOGGLE)
                onCheckedChange(it)
            }
        )
    }
}
