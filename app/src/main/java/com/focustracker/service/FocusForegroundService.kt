package com.focustracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.focustracker.FocusTracker
import com.focustracker.MainActivity
import com.focustracker.R
import com.focustracker.alert.DistractionAlerter
import com.focustracker.data.AppDatabase
import com.focustracker.data.FocusSession
import com.focustracker.detector.AppUsageMonitor
import com.focustracker.detector.DetectionConfig
import com.focustracker.detector.FocusEngine
import com.focustracker.detector.FocusMode
import com.focustracker.detector.FocusState
import com.focustracker.detector.HeadPoseDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 前台服务：负责计时、摄像头/应用分类检测编排、专注时长累计与分心提醒。
 */
class FocusForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.focustracker.action.START"
        const val ACTION_STOP = "com.focustracker.action.STOP"
        const val EXTRA_CATEGORY_ID = "extra_category_id"

        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 100
    }

    private val database by lazy { AppDatabase.get(this) }

    private lateinit var alerter: DistractionAlerter
    private lateinit var appUsageMonitor: AppUsageMonitor
    private lateinit var headPoseDetector: HeadPoseDetector
    private lateinit var engine: FocusEngine

    private var activeSession: FocusSession? = null
    private var sessionStartMs = 0L
    private var focusedMs = 0L
    private var focusedSince = 0L
    private var distractCount = 0

    override fun onCreate() {
        super.onCreate()
        alerter = DistractionAlerter(this)
        appUsageMonitor = AppUsageMonitor(this)
        headPoseDetector = HeadPoseDetector(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val raw = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
                startSession(raw.takeIf { it >= 0 })
            }
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession(categoryId: Long?) {
        startForegroundCompat()
        lifecycleScope.launch {
            val session = FocusSession(startTime = System.currentTimeMillis(), categoryId = categoryId)
            val id = database.focusSessionDao().insert(session)
            activeSession = database.focusSessionDao().getSessionById(id)

            sessionStartMs = System.currentTimeMillis()
            focusedMs = 0L
            focusedSince = 0L
            distractCount = 0

            FocusTracker.reset()
            FocusTracker.isRunning.value = true
            beginMonitoring()
        }
    }

    private fun beginMonitoring() {
        headPoseDetector.start()

        engine = FocusEngine(
            facePoseFlow = headPoseDetector.facePose,
            appCategoryFlow = appUsageMonitor.foregroundCategoryFlow(),
            onDistraction = {
                distractCount++
                alerter.alert()
            },
        )
        engine.start()

        // 计时器
        lifecycleScope.launch {
            while (true) {
                FocusTracker.elapsedMs.value = System.currentTimeMillis() - sessionStartMs
                delay(500)
            }
        }

        // 模式 -> 摄像头开关
        lifecycleScope.launch {
            engine.mode.collect { mode ->
                when (mode) {
                    FocusMode.MONITORING -> if (!headPoseDetector.running.value) headPoseDetector.start()
                    FocusMode.DEVICE_WORK, FocusMode.IDLE -> headPoseDetector.stop()
                }
                FocusTracker.mode.value = mode
            }
        }

        // 状态 + 姿态 -> 自适应帧率 + 累计专注时长
        lifecycleScope.launch {
            combine(engine.focusState, headPoseDetector.facePose) { state, face ->
                state to face
            }.collect { (state, face) ->
                FocusTracker.focusState.value = state

                val fps = when {
                    state == FocusState.FOCUSED -> DetectionConfig.FPS_STABLE
                    face?.hasFace == false -> DetectionConfig.FPS_IDLE
                    else -> DetectionConfig.FPS_ACTIVE
                }
                headPoseDetector.setFrameRate(fps)

                val now = System.currentTimeMillis()
                if (state == FocusState.FOCUSED) {
                    if (focusedSince == 0L) focusedSince = now
                } else {
                    if (focusedSince != 0L) {
                        focusedMs += now - focusedSince
                        focusedSince = 0L
                    }
                }
            }
        }

        // 评分镜像到 UI
        lifecycleScope.launch {
            engine.score.collect { FocusTracker.score.value = it }
        }
    }

    private fun stopSession() {
        lifecycleScope.launch {
            if (focusedSince != 0L) {
                focusedMs += System.currentTimeMillis() - focusedSince
                focusedSince = 0L
            }

            headPoseDetector.stop()
            if (::engine.isInitialized) engine.stop()

            activeSession?.let { s ->
                database.focusSessionDao().update(
                    s.copy(
                        endTime = System.currentTimeMillis(),
                        focusedDurationMs = focusedMs,
                        distractedCount = distractCount,
                    )
                )
            }

            activeSession = null
            FocusTracker.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        headPoseDetector.stop()
        if (::engine.isInitialized) engine.stop()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "专注计时",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注进行中")
            .setContentText("正在记录专注时长")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
