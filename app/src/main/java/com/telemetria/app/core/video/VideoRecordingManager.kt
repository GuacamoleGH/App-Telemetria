package com.telemetria.app.core.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class VideoRecordingManager(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    var isRecording: Boolean = false
        private set
    var isPaused: Boolean = false
        private set

    var videoStartMonotonicNanos: Long = 0L
        private set
    var videoStartEpochMillis: Long = 0L
        private set

    /**
     * Vincula la cámara al ciclo de vida y a un SurfaceProvider de previsualización (opcional).
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build()
            surfaceProvider?.let { preview.setSurfaceProvider(it) }

            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                if (surfaceProvider != null) {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
                } else {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, capture)
                }
                onReady()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Inicia la grabación del archivo de vídeo limpio y notifica los timestamps monotónicos y UTC en el instante exacto del arranque del stream.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        outputFile: File,
        onStartSync: (startMonoNanos: Long, startEpochMillis: Long) -> Unit,
        onStatusUpdate: (durationSec: Long, bytesRecorded: Long) -> Unit,
        onFinalized: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        val capture = videoCapture ?: run {
            onFinalized(false, "VideoCapture no inicializado")
            return
        }

        outputFile.parentFile?.mkdirs()

        // Verificar política de espacio en disco (mínimo 500 MB)
        if (!StorageSpaceHelper.hasSufficientSpaceForRecording(outputFile.parentFile ?: context.filesDir)) {
            onFinalized(false, "Espacio de almacenamiento insuficiente (se requieren al menos 500 MB)")
            return
        }

        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        val pendingRecording = capture.output.prepareRecording(context, outputOptions)

        if (CameraPermissionHelper.hasAudioPermission(context)) {
            pendingRecording.withAudioEnabled()
        }

        val recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    isPaused = false
                    videoStartMonotonicNanos = SystemClock.elapsedRealtimeNanos()
                    videoStartEpochMillis = System.currentTimeMillis()
                    onStartSync(videoStartMonotonicNanos, videoStartEpochMillis)
                }
                is VideoRecordEvent.Status -> {
                    val durationSec = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                    val bytes = event.recordingStats.numBytesRecorded
                    onStatusUpdate(durationSec, bytes)
                }
                is VideoRecordEvent.Pause -> {
                    isPaused = true
                }
                is VideoRecordEvent.Resume -> {
                    isPaused = false
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    isPaused = false
                    activeRecording = null
                    if (event.hasError()) {
                        onFinalized(false, "Error CameraX: ${event.error}")
                    } else {
                        onFinalized(true, null)
                    }
                }
            }
        }

        activeRecording = recording
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            activeRecording?.pause()
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            activeRecording?.resume()
        }
    }

    fun stopRecording() {
        if (isRecording) {
            activeRecording?.stop()
            activeRecording = null
        }
    }

    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
    }
}
