package com.focustracker.detector

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * 基于 UsageStatsManager 的前台应用分类监控。
 * 开销极小（无需摄像头），用于「设备工作模式」下的专注判定。
 */
class AppUsageMonitor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** 是否已授予「使用情况访问权限」 */
    val hasPermission: Boolean
        get() {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

    /** 每 [intervalMs] 轮询一次前台应用，返回其分类，仅分类变化时发射 */
    fun foregroundCategoryFlow(intervalMs: Long = 2000L): Flow<AppCategory> = flow {
        while (true) {
            emit(classifyForeground())
            delay(intervalMs)
        }
    }.distinctUntilChanged()

    private fun classifyForeground(): AppCategory {
        if (!hasPermission) return AppCategory.NEUTRAL
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000,
            now,
        )
        val current = stats?.maxByOrNull { it.lastTimeUsed }
        return AppClassifier.classify(current?.packageName)
    }
}
