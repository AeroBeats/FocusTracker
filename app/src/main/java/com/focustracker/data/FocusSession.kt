package com.focustracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一次专注会话 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val startTime: Long,
    val endTime: Long? = null,
    /** 累计专注时长（毫秒） */
    val focusedDurationMs: Long = 0,
    /** 分心提醒次数 */
    val distractedCount: Int = 0,
)
