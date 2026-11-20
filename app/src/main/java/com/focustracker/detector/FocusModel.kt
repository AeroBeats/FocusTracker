package com.focustracker.detector

/** 工作模式 */
enum class FocusMode {
    /** 监护模式：不碰设备，靠摄像头判定低头/抬头/离座 */
    MONITORING,

    /** 设备工作模式：正在用设备做其他事，靠前台应用分类 + 可选摄像头 */
    DEVICE_WORK,

    /** 空闲（未开始计时） */
    IDLE,
}

/** 专注状态 */
enum class FocusState {
    FOCUSED,
    DISTRACTED,
    UNCERTAIN,
}

/** 应用分类结果 */
enum class AppCategory {
    FOCUS,
    DISTRACT,
    NEUTRAL,
    SELF,
}

/** 一帧人脸姿态（来自 ML Kit） */
data class FacePose(
    val hasFace: Boolean,
    /** 俯仰角 headEulerAngleX（度） */
    val pitchDeg: Float = 0f,
    /** 偏航角 headEulerAngleY（度） */
    val yawDeg: Float = 0f,
)

/** 一次专注度采样输入 */
data class FocusSample(
    val appCategory: AppCategory = AppCategory.NEUTRAL,
    val facePose: FacePose? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
