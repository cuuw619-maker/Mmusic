package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.data.preferences.PreferencesManager
import com.example.model.EqualizerBand
import com.example.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EqualizerManager(private val preferencesManager: PreferencesManager) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = 0

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    companion object {
        val STANDARD_PRESETS = listOf(
            "Обычный", "Flat", "Bass Boost", "Rock", "Pop", "Classical", "Jazz", "Vocal", "Electronic", "Custom"
        )
    }

    @Synchronized
    fun bindAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        release()
        currentAudioSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val bb = try { BassBoost(0, audioSessionId) } catch (e: Exception) { null }
            bassBoost = bb

            // Virtualizer causes comb-filtering and reverberant echo when active; keep isolated
            val virt = try { Virtualizer(0, audioSessionId) } catch (e: Exception) { null }
            virtualizer = virt

            val le = try { LoudnessEnhancer(audioSessionId) } catch (e: Exception) { null }
            loudnessEnhancer = le

            val isEnabled = preferencesManager.equalizerEnabled.value
            val savedBb = preferencesManager.bassBoostLevel.value
            val savedVirt = preferencesManager.virtualizerStrength.value
            val savedLe = preferencesManager.loudnessGain.value

            eq.enabled = isEnabled
            bb?.enabled = isEnabled && savedBb > 0
            virt?.enabled = isEnabled && savedVirt > 0
            le?.enabled = isEnabled && savedLe > 0

            // Band levels
            val numBands = eq.numberOfBands
            val bandLevelRange = eq.bandLevelRange
            val minLevel = bandLevelRange[0]
            val maxLevel = bandLevelRange[1]

            val bands = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val bandIndex = i.toShort()
                val centerFreq = eq.getCenterFreq(bandIndex) / 1000
                val savedLevel = preferencesManager.getBandLevel(bandIndex, 0.toShort())
                try {
                    eq.setBandLevel(bandIndex, savedLevel.coerceIn(minLevel, maxLevel))
                } catch (e: Exception) {
                    Log.w("EqualizerManager", "Could not set band level $bandIndex", e)
                }
                bands.add(
                    EqualizerBand(
                        bandIndex = bandIndex,
                        centerFreqHz = centerFreq,
                        minLevelMilliBels = minLevel,
                        maxLevelMilliBels = maxLevel,
                        currentLevelMilliBels = savedLevel
                    )
                )
            }

            if (savedBb > 0) {
                try { bb?.setStrength(savedBb.coerceIn(0, 1000)) } catch (e: Exception) {}
            }
            if (savedVirt > 0) {
                try { virt?.setStrength(savedVirt.coerceIn(0, 1000)) } catch (e: Exception) {}
            }
            if (savedLe > 0) {
                try { le?.setTargetGain(savedLe) } catch (e: Exception) {}
            }

            // Presets
            val presetsList = mutableListOf<String>()
            presetsList.addAll(STANDARD_PRESETS)

            val hwPresetCount = eq.numberOfPresets
            for (p in 0 until hwPresetCount) {
                val name = eq.getPresetName(p.toShort())
                if (!presetsList.contains(name)) {
                    presetsList.add(name)
                }
            }

            _equalizerState.value = EqualizerState(
                isAvailable = true,
                isEnabled = isEnabled,
                bands = bands,
                bassBoostStrength = savedBb,
                virtualizerStrength = savedVirt,
                loudnessEnhancerStrength = savedLe,
                presets = presetsList,
                currentPresetIndex = 0
            )
        } catch (e: Throwable) {
            Log.e("EqualizerManager", "Equalizer not supported on this device/session", e)
            _equalizerState.value = EqualizerState(isAvailable = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferencesManager.setEqualizerEnabled(enabled)
        try {
            equalizer?.enabled = enabled
            val state = _equalizerState.value
            bassBoost?.enabled = enabled && state.bassBoostStrength > 0
            virtualizer?.enabled = enabled && state.virtualizerStrength > 0
            loudnessEnhancer?.enabled = enabled && state.loudnessEnhancerStrength > 0
            _equalizerState.value = _equalizerState.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error toggling equalizer", e)
        }
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, level)
            preferencesManager.setBandLevel(bandIndex, level)
            val updatedBands = _equalizerState.value.bands.map {
                if (it.bandIndex == bandIndex) it.copy(currentLevelMilliBels = level) else it
            }
            _equalizerState.value = _equalizerState.value.copy(
                bands = updatedBands,
                currentPresetIndex = (_equalizerState.value.presets.indexOf("Custom").coerceAtLeast(0)).toShort()
            )
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting band level", e)
        }
    }

    fun setBassBoost(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.enabled = clamped > 0 && _equalizerState.value.isEnabled
            bassBoost?.setStrength(clamped)
            preferencesManager.setBassBoost(clamped)
            _equalizerState.value = _equalizerState.value.copy(bassBoostStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting bass boost", e)
        }
    }

    fun setVirtualizer(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            virtualizer?.enabled = clamped > 0 && _equalizerState.value.isEnabled
            virtualizer?.setStrength(clamped)
            preferencesManager.setVirtualizer(clamped)
            _equalizerState.value = _equalizerState.value.copy(virtualizerStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting virtualizer", e)
        }
    }

    fun setLoudnessEnhancer(targetGainmB: Int) {
        try {
            loudnessEnhancer?.enabled = targetGainmB > 0 && _equalizerState.value.isEnabled
            loudnessEnhancer?.setTargetGain(targetGainmB)
            preferencesManager.setLoudnessGain(targetGainmB)
            _equalizerState.value = _equalizerState.value.copy(loudnessEnhancerStrength = targetGainmB)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting loudness enhancer", e)
        }
    }

    fun usePreset(presetIndex: Short) {
        val presets = _equalizerState.value.presets
        if (presetIndex in presets.indices) {
            applyPreset(presets[presetIndex.toInt()])
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        val currentBands = _equalizerState.value.bands
        if (currentBands.isEmpty()) return

        val minL = currentBands.first().minLevelMilliBels.toInt()
        val maxL = currentBands.first().maxLevelMilliBels.toInt()
        val scale = (maxL - minL) / 20.0f // mapping from dB to mB

        // Relative dB curves for standard presets
        val dbCurve = when (presetName) {
            "Flat", "Обычный" -> listOf(0f, 0f, 0f, 0f, 0f)
            "Bass Boost" -> listOf(6f, 4f, 1f, 0f, -1f)
            "Rock" -> listOf(4.5f, 2f, -1.5f, 2.5f, 4.5f)
            "Pop" -> listOf(-1.5f, 1.5f, 3.5f, 2f, -1f)
            "Classical" -> listOf(4f, 2.5f, 0f, 2f, 3.5f)
            "Jazz" -> listOf(3f, 1.5f, -1f, 1.5f, 3f)
            "Vocal" -> listOf(-2f, 2f, 5f, 3f, 0f)
            "Electronic" -> listOf(5f, 3f, 0f, 2f, 4f)
            else -> null
        }

        if (dbCurve != null) {
            val updatedBands = currentBands.mapIndexed { index, band ->
                val curveIndex = (index * dbCurve.size / currentBands.size).coerceIn(0, dbCurve.size - 1)
                val targetDb = dbCurve[curveIndex]
                val targetMilliBels = (targetDb * scale).toInt().coerceIn(minL, maxL).toShort()
                try {
                    eq.setBandLevel(band.bandIndex, targetMilliBels)
                    preferencesManager.setBandLevel(band.bandIndex, targetMilliBels)
                } catch (e: Exception) {}
                band.copy(currentLevelMilliBels = targetMilliBels)
            }
            val presetIdx = _equalizerState.value.presets.indexOf(presetName).coerceAtLeast(0)
            _equalizerState.value = _equalizerState.value.copy(
                bands = updatedBands,
                currentPresetIndex = presetIdx.toShort()
            )
        } else {
            // Check hardware presets
            val hwIdx = (0 until eq.numberOfPresets).find { eq.getPresetName(it.toShort()) == presetName }
            if (hwIdx != null) {
                try {
                    eq.usePreset(hwIdx.toShort())
                    val updatedBands = currentBands.map {
                        val lvl = eq.getBandLevel(it.bandIndex)
                        preferencesManager.setBandLevel(it.bandIndex, lvl)
                        it.copy(currentLevelMilliBels = lvl)
                    }
                    val pIdx = _equalizerState.value.presets.indexOf(presetName).coerceAtLeast(0)
                    _equalizerState.value = _equalizerState.value.copy(
                        bands = updatedBands,
                        currentPresetIndex = pIdx.toShort()
                    )
                } catch (e: Exception) {}
            }
        }
    }

    @Synchronized
    fun release() {
        try { equalizer?.release() } catch (e: Exception) {}
        try { bassBoost?.release() } catch (e: Exception) {}
        try { virtualizer?.release() } catch (e: Exception) {}
        try { loudnessEnhancer?.release() } catch (e: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentAudioSessionId = 0
    }
}
