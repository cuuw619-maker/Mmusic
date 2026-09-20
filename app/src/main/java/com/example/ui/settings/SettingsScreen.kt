package com.example.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val preservePitch by viewModel.preservePitch.collectAsStateWithLifecycle()

    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showPluginSheet by remember { mutableStateOf(false) }

    val haptic = rememberHapticHelper()

    if (showSpeedPitchSheet) {
        SpeedPitchBottomSheet(
            currentSpeed = playbackSpeed,
            currentPitchSemitones = pitchSemitones,
            preservePitch = preservePitch,
            onSpeedChanged = { viewModel.setPlaybackSpeed(it) },
            onPitchChanged = { viewModel.setPitchSemitones(it) },
            onPreservePitchChanged = { viewModel.setPreservePitch(it) },
            onDismiss = { showSpeedPitchSheet = false }
        )
    }

    if (showPluginSheet) {
        PluginManagerSheet(
            plugins = plugins,
            onTogglePlugin = { id, enabled -> viewModel.setPluginEnabled(id, enabled) },
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
                androidx.compose.material3.TextButton(onClick = { showThemeDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        // Appearance Section
        item {
            SectionTitle(title = "Внешний вид")
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
                        subtitle = "Глубокий чёрный цвет фона для экономии батареи",
                        checked = amoledDark,
                        onCheckedChange = { viewModel.setAmoledDark(it) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitchRow(
                            icon = Icons.Rounded.ColorLens,
                            title = "Динамические цвета (Material You)",
                            subtitle = "Цветовая палитра на основе системных обоев",
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Audio & Playback Section
        item {
            SectionTitle(title = "Воспроизведение и звук")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Rounded.Equalizer,
                        title = "Эквалайзер и эффекты",
                        subtitle = "Настройка частотных полос, Bass Boost и громкости",
                        onClick = { viewModel.setEqualizerSheetVisible(true) }
                    )

                    SettingsRow(
                        icon = Icons.Rounded.Speed,
                        title = "Скорость и Тональность",
                        subtitle = "Скорость: %.2fx • Тон: %+d st".format(playbackSpeed, pitchSemitones),
                        onClick = { showSpeedPitchSheet = true }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = "Плавный переход (Crossfade)",
                        subtitle = if (crossfadeEnabled) "Длительность: %.1f сек".format(crossfadeDuration) else "Выключен",
                        checked = crossfadeEnabled,
                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                    )

                    if (crossfadeEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Время кроссфейда",
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
                                value = crossfadeDuration,
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

        // Motion & Haptics Section
        item {
            SectionTitle(title = "Анимации и отклик")
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
                        title = "Уменьшение движения",
                        subtitle = "Отключает пружинные и инерционные эффекты для чувствительных пользователей",
                        checked = reducedMotion,
                        onCheckedChange = { viewModel.setReducedMotion(it) }
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = "Тактильный отклик (Haptics)",
                        subtitle = "Вибрация при нажатии кнопок, жестах перелистывания и смене табов",
                        checked = hapticEnabled,
                        onCheckedChange = { viewModel.setHapticEnabled(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Plugin Extensions Section
        item {
            SectionTitle(title = "Расширения и плагины")
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
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Library & Scanner Section
        item {
            SectionTitle(title = "Медиатека")
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
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // About Section
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
                                text = "Музыкальный плеер",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Версия 2.0.0 • Material 3 Expressive 2026",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Высокоточный музыкальный плеер нового поколения: архитектура Media3/ExoPlayer, истинный Crossfade, регулятор скорости и высоты тона (Sonic DSP), изоляция аудиоэффектов, расширяемая архитектура плагинов и адаптивный интерфейс.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
