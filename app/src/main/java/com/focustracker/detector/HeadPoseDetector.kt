package com.focustracker.detector

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前置摄像头 + ML Kit 人脸检测，输出头部姿态（低头/抬头/张望）。
 *
 * 省电要点：
 *  - 压低分析分辨率（480x360）
 *  - KEEP_ONLY_LATEST 背压，只处理最新帧
 *  - 自适应帧率节流（setFrameRate）
 */
@OptIn(ExperimentalGetImage::class)
class HeadPoseDetector(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val _facePose = MutableStateFlow<FacePose?>(null)
    val facePose: StateFlow<FacePose?> = _facePose

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var provider: ProcessCameraProvider? = null
    private var frameIntervalMs: Long = (1000f / DetectionConfig.FPS_ACTIVE).toLong()
    private var lastFrameAt = 0L
    private val processing = AtomicBoolean(false)

    /** 设置目标帧率（自适应降频） */
    fun setFrameRate(fps: Float) {
        frameIntervalMs = (1000f / fps.coerceAtLeast(1f)).toLong()
    }

    fun start() {
        if (_running.value) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            provider = cameraProvider

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(
                    Size(DetectionConfig.TARGET_WIDTH, DetectionConfig.TARGET_HEIGHT)
                )
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyze) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis,
                )
                _running.value = true
            } catch (e: Exception) {
                _running.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val tooSoon = now - lastFrameAt < frameIntervalMs
        if (tooSoon || !processing.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        lastFrameAt = now

        val mediaImage = proxy.image
        if (mediaImage == null) {
            processing.set(false)
            proxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                _facePose.value = if (faces.isEmpty()) {
                    FacePose(hasFace = false)
                } else {
                    val f = faces[0]
                    FacePose(hasFace = true, pitchDeg = f.headEulerAngleX, yawDeg = f.headEulerAngleY)
                }
                processing.set(false)
                proxy.close()
            }
            .addOnFailureListener {
                processing.set(false)
                proxy.close()
            }
    }

    fun stop() {
        provider?.unbindAll()
        provider = null
        _running.value = false
        _facePose.value = null
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
        detector.close()
    }
}
