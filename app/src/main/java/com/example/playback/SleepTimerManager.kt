package com.example.playback

import com.example.model.SleepTimerOption
import com.example.model.SleepTimerState
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

class SleepTimerManager(
    private val onTimeExpired: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun setTimer(option: SleepTimerOption) {
        timerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            _state.value = SleepTimerState()
            return
        }

        if (option == SleepTimerOption.END_OF_TRACK) {
            _state.value = SleepTimerState(
                isActive = true,
                totalMinutes = 0,
                secondsRemaining = 0,
                stopAtEndOfTrack = true
            )
            return
        }

        val totalSeconds = option.minutes * 60
        _state.value = SleepTimerState(
            isActive = true,
            totalMinutes = option.minutes,
            secondsRemaining = totalSeconds,
            stopAtEndOfTrack = false
        )

        timerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                remaining--
                _state.value = _state.value.copy(secondsRemaining = remaining)
            }
            if (isActive && remaining <= 0) {
                _state.value = SleepTimerState()
                onTimeExpired()
            }
        }
    }

    fun onTrackEnded() {
        if (_state.value.isActive && _state.value.stopAtEndOfTrack) {
            _state.value = SleepTimerState()
            onTimeExpired()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state.value = SleepTimerState()
    }
}
