package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _resumePlayback = MutableStateFlow(prefs.getBoolean(KEY_RESUME_PLAYBACK, true))
    val resumePlayback: StateFlow<Boolean> = _resumePlayback.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQ_ENABLED, false))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(prefs.getInt(KEY_BASS_BOOST, 0).toShort())
    val bassBoostLevel: StateFlow<Short> = _bassBoostLevel.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(prefs.getInt(KEY_VIRTUALIZER, 0).toShort())
    val virtualizerStrength: StateFlow<Short> = _virtualizerStrength.asStateFlow()

    private val _amoledDark = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_DARK, false))
    val amoledDark: StateFlow<Boolean> = _amoledDark.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _pitchSemitones = MutableStateFlow(prefs.getInt(KEY_PITCH_SEMITONES, 0))
    val pitchSemitones: StateFlow<Int> = _pitchSemitones.asStateFlow()

    private val _preservePitch = MutableStateFlow(prefs.getBoolean(KEY_PRESERVE_PITCH, true))
    val preservePitch: StateFlow<Boolean> = _preservePitch.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 0.3f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    private val _loudnessGain = MutableStateFlow(prefs.getInt(KEY_LOUDNESS_GAIN, 0))
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow()

    private val _volumeNormalize = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORMALIZE, false))
    val volumeNormalize: StateFlow<Boolean> = _volumeNormalize.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean(KEY_GAPLESS, true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _fadeInOnStart = MutableStateFlow(prefs.getBoolean(KEY_FADE_IN, false))
    val fadeInOnStart: StateFlow<Boolean> = _fadeInOnStart.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true))
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _animationScale = MutableStateFlow(prefs.getFloat(KEY_ANIMATION_SCALE, 1.0f))
    val animationScale: StateFlow<Float> = _animationScale.asStateFlow()

    private val _reducedMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCED_MOTION, false))
    val reducedMotion: StateFlow<Boolean> = _reducedMotion.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_ENABLED, true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setResumePlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, enabled).apply()
        _resumePlayback.value = enabled
    }

    fun setLastPlayedSong(songId: Long, positionMs: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SONG_ID, songId)
            .putLong(KEY_LAST_POSITION_MS, positionMs)
            .apply()
    }

    fun getLastSongId(): Long = prefs.getLong(KEY_LAST_SONG_ID, -1L)
    fun getLastPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        _equalizerEnabled.value = enabled
    }

    fun setBassBoost(level: Short) {
        prefs.edit().putInt(KEY_BASS_BOOST, level.toInt()).apply()
        _bassBoostLevel.value = level
    }

    fun setVirtualizer(strength: Short) {
        prefs.edit().putInt(KEY_VIRTUALIZER, strength.toInt()).apply()
        _virtualizerStrength.value = strength
    }

    fun setBandLevel(band: Short, level: Short) {
        prefs.edit().putInt("${KEY_EQ_BAND_PREFIX}$band", level.toInt()).apply()
    }

    fun getBandLevel(band: Short, defaultLevel: Short): Short {
        val stored = prefs.getInt("${KEY_EQ_BAND_PREFIX}$band", Integer.MIN_VALUE)
        return if (stored == Integer.MIN_VALUE) defaultLevel else stored.toShort()
    }

    fun setAmoledDark(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_DARK, enabled).apply()
        _amoledDark.value = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _playbackSpeed.value = speed
    }

    fun setPitchSemitones(semitones: Int) {
        prefs.edit().putInt(KEY_PITCH_SEMITONES, semitones).apply()
        _pitchSemitones.value = semitones
    }

    fun setPreservePitch(preserve: Boolean) {
        prefs.edit().putBoolean(KEY_PRESERVE_PITCH, preserve).apply()
        _preservePitch.value = preserve
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()
        _crossfadeEnabled.value = enabled
    }

    fun setCrossfadeDuration(durationSeconds: Float) {
        val clamped = durationSeconds.coerceIn(0.1f, 1.5f)
        prefs.edit().putFloat(KEY_CROSSFADE_DURATION, clamped).apply()
        _crossfadeDuration.value = clamped
    }

    fun setLoudnessGain(gainmB: Int) {
        prefs.edit().putInt(KEY_LOUDNESS_GAIN, gainmB).apply()
        _loudnessGain.value = gainmB
    }

    fun setVolumeNormalize(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_NORMALIZE, enabled).apply()
        _volumeNormalize.value = enabled
    }

    fun setGaplessPlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply()
        _gaplessPlayback.value = enabled
    }

    fun setFadeInOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FADE_IN, enabled).apply()
        _fadeInOnStart.value = enabled
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, enabled).apply()
        _animationsEnabled.value = enabled
    }

    fun setAnimationScale(scale: Float) {
        prefs.edit().putFloat(KEY_ANIMATION_SCALE, scale).apply()
        _animationScale.value = scale
    }

    fun setReducedMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply()
        _reducedMotion.value = enabled
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        _hapticEnabled.value = enabled
    }

    fun getCustomString(key: String, defaultValue: String = ""): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    fun getCustomBoolean(key: String, defaultValue: Boolean = false): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun getCustomFloat(key: String, defaultValue: Float = 0f): Float =
        prefs.getFloat(key, defaultValue)

    fun getCustomInt(key: String, defaultValue: Int = 0): Int =
        prefs.getInt(key, defaultValue)

    fun setCustomString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun setCustomBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setCustomFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun setCustomInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_RESUME_PLAYBACK = "key_resume_playback"
        private const val KEY_LAST_SONG_ID = "key_last_song_id"
        private const val KEY_LAST_POSITION_MS = "key_last_pos_ms"
        private const val KEY_EQ_ENABLED = "key_eq_enabled"
        private const val KEY_BASS_BOOST = "key_bass_boost"
        private const val KEY_VIRTUALIZER = "key_virtualizer"
        private const val KEY_EQ_BAND_PREFIX = "key_eq_band_"
        private const val KEY_AMOLED_DARK = "key_amoled_dark"
        private const val KEY_PLAYBACK_SPEED = "key_playback_speed"
        private const val KEY_PITCH_SEMITONES = "key_pitch_semitones"
        private const val KEY_PRESERVE_PITCH = "key_preserve_pitch"
        private const val KEY_CROSSFADE_ENABLED = "key_crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "key_crossfade_duration"
        private const val KEY_LOUDNESS_GAIN = "key_loudness_gain"
        private const val KEY_VOLUME_NORMALIZE = "key_volume_normalize"
        private const val KEY_GAPLESS = "key_gapless"
        private const val KEY_FADE_IN = "key_fade_in"
        private const val KEY_ANIMATIONS_ENABLED = "key_animations_enabled"
        private const val KEY_ANIMATION_SCALE = "key_animation_scale"
        private const val KEY_REDUCED_MOTION = "key_reduced_motion"
        private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"
    }
}
