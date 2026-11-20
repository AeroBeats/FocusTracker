package com.focustracker

import com.focustracker.detector.FocusMode
import com.focustracker.detector.FocusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局可观测状态，前台服务负责写入，UI 负责读取。
 */
object FocusTracker {
    val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val elapsedMs: MutableStateFlow<Long> = MutableStateFlow(0L)
    val focusState: MutableStateFlow<FocusState> = MutableStateFlow(FocusState.UNCERTAIN)
    val score: MutableStateFlow<Float> = MutableStateFlow(0f)
    val mode: MutableStateFlow<FocusMode> = MutableStateFlow(FocusMode.IDLE)

    fun reset() {
        isRunning.value = false
        elapsedMs.value = 0L
        focusState.value = FocusState.UNCERTAIN
        score.value = 0f
        mode.value = FocusMode.IDLE
    }
}
