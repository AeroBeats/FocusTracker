package com.focustracker.detector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 专注引擎：融合「摄像头姿态」与「前台应用分类」两个信号，
 * 输出工作模式、专注状态与专注度评分，并做滞回防抖。
 */
class FocusEngine(
    private val facePoseFlow: Flow<FacePose?>,
    private val appCategoryFlow: Flow<AppCategory>,
    private val onDistraction: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _mode = MutableStateFlow(FocusMode.MONITORING)
    val mode: StateFlow<FocusMode> = _mode

    private val _focusState = MutableStateFlow(FocusState.UNCERTAIN)
    val focusState: StateFlow<FocusState> = _focusState

    private val _score = MutableStateFlow(0f)
    val score: StateFlow<Float> = _score

    private var distractSince = 0L
    private var focusSince = 0L

    fun start() {
        scope.launch {
            combine(facePoseFlow, appCategoryFlow) { face, app ->
                face to app
            }.collect { (face, app) ->
                update(face, app)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private fun update(face: FacePose?, app: AppCategory) {
        // 模式切换：自己在前台 -> 监护模式；其他应用在前台 -> 设备工作模式
        val newMode = when (app) {
            AppCategory.SELF -> FocusMode.MONITORING
            else -> FocusMode.DEVICE_WORK
        }
        if (newMode != _mode.value) _mode.value = newMode

        val s = when (_mode.value) {
            FocusMode.MONITORING -> scoreFromFace(face)
            FocusMode.DEVICE_WORK -> scoreFromApp(app)
            FocusMode.IDLE -> 0f
        }
        _score.value = s
        applyHysteresis(s)
    }

    private fun scoreFromFace(face: FacePose?): Float {
        if (face == null || !face.hasFace) return -1.5f // 离座/人脸丢失
        return when {
            face.pitchDeg <= DetectionConfig.PITCH_DOWN_DEG -> 1f      // 低头专注
            face.pitchDeg >= DetectionConfig.PITCH_UP_DEG -> -1f       // 抬头
            abs(face.yawDeg) >= DetectionConfig.YAW_AWAY_DEG -> -1f    // 左右张望
            else -> 0f
        }
    }

    private fun scoreFromApp(app: AppCategory): Float = when (app) {
        AppCategory.FOCUS -> 1f
        AppCategory.DISTRACT -> -1f
        AppCategory.NEUTRAL -> 0f
        AppCategory.SELF -> 1f
    }

    private fun applyHysteresis(s: Float) {
        val now = System.currentTimeMillis()
        when {
            s <= DetectionConfig.SCORE_DISTRACT -> {
                focusSince = 0L
                if (distractSince == 0L) distractSince = now
                if (now - distractSince >= DetectionConfig.DISTRACT_SUSTAIN_MS &&
                    _focusState.value != FocusState.DISTRACTED
                ) {
                    _focusState.value = FocusState.DISTRACTED
                    onDistraction()
                }
            }
            s >= DetectionConfig.SCORE_FOCUS -> {
                distractSince = 0L
                if (focusSince == 0L) focusSince = now
                if (now - focusSince >= DetectionConfig.FOCUS_SUSTAIN_MS &&
                    _focusState.value != FocusState.FOCUSED
                ) {
                    _focusState.value = FocusState.FOCUSED
                }
            }
            else -> {
                distractSince = 0L
                focusSince = 0L
                _focusState.value = FocusState.UNCERTAIN
            }
        }
    }
}
