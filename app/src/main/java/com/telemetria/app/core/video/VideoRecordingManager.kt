package com.telemetria.app.core.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
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
    private var preview: Preview? = null
    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var orientationEventListener: OrientationEventListener? = null

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
     * Si la cámara ya está activa o grabando, reconecta la superficie sin romper la grabación
     * (crucial para evitar cortes al rotar entre vertical y horizontal).
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onReady: () -> Unit = {}
    ) {
        val existingCapture = videoCapture
        val existingPreview = preview
        val existingProvider = cameraProvider

        // Si ya está vinculado y estamos grabando (o ya configurado), solo actualizamos el SurfaceProvider
        // sin llamar a unbindAll(), lo que destruiría la grabación activa durante la rotación de pantalla.
        if (existingCapture != null && existingPreview != null && existingProvider != null &&
            (existingProvider.isBound(existingCapture) || isRecording)
        ) {
            surfaceProvider?.let { existingPreview.setSurfaceProvider(it) }
            updateRotations()
            onReady()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val rotation = getDisplayRotation()

            val newPreview = Preview.Builder().build()
            newPreview.targetRotation = rotation
            preview = newPreview
            surfaceProvider?.let { newPreview.setSurfaceProvider(it) }

            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            val capture = VideoCapture.withOutput(recorder)
            capture.targetRotation = rotation
            videoCapture = capture

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                if (surfaceProvider != null) {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, newPreview, capture)
                } else {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, capture)
                }
                startOrientationListener()
                onReady()
            } catch (e: Exception) {
                Log.e("VideoRecordingManager", "Error al vincular lifecycle de cámara: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startOrientationListener() {
        if (orientationEventListener != null) return
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                videoCapture?.targetRotation = rotation
                preview?.targetRotation = rotation
            }
        }
        if (orientationEventListener?.canDetectOrientation() == true) {
            orientationEventListener?.enable()
        }
    }

    private fun updateRotations() {
        val rotation = getDisplayRotation()
        videoCapture?.targetRotation = rotation
        preview?.targetRotation = rotation
    }

    private fun getDisplayRotation(): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
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

        updateRotations()

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
                        Log.e("VideoRecordingManager", "CameraX VideoRecordEvent.Finalize con error: ${event.error}")
                        onFinalized(false, "Error CameraX: ${event.error}")
                    } else {
                        Log.i("VideoRecordingManager", "CameraX VideoRecordEvent.Finalize exitoso")
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
        orientationEventListener?.disable()
        orientationEventListener = null
        stopRecording()
        cameraProvider?.unbindAll()
    }
}
