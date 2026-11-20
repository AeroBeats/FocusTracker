package com.focustracker.detector

/**
 * 检测相关可调参数。
 *
 * 注意：ML Kit FaceDetection 的 headEulerAngleX 为俯仰角（pitch），
 * 符号约定通常为「仰头为正、低头为负」，但以真机实测为准，
 * 若方向相反，只需把 PITCH_DOWN_DEG / PITCH_UP_DEG 对调符号即可。
 */
object DetectionConfig {
    /** 低头判定阈值（度），负值 = 低头 */
    const val PITCH_DOWN_DEG = -25f

    /** 抬头判定阈值（度） */
    const val PITCH_UP_DEG = 25f

    /** 左右张望（偏航角 yaw）判定阈值（度） */
    const val YAW_AWAY_DEG = 30f

    /** 状态滞回：持续超过该时长才判定为分心，避免抖动误报 */
    const val DISTRACT_SUSTAIN_MS = 3000L

    /** 持续超过该时长才判定为专注 */
    const val FOCUS_SUSTAIN_MS = 3000L

    /** 专注度评分：低于该值判为分心 */
    const val SCORE_DISTRACT = -0.8f

    /** 专注度评分：高于该值判为专注 */
    const val SCORE_FOCUS = 0.3f

    // 自适应帧率（帧/秒）
    const val FPS_ACTIVE = 10f   // 状态多变，需要细看
    const val FPS_STABLE = 4f    // 稳定低头学习
    const val FPS_IDLE = 1f      // 无人/离座，仅轮询检测回归

    // 摄像头分析分辨率（压低以省电）
    const val TARGET_WIDTH = 480
    const val TARGET_HEIGHT = 360
}
