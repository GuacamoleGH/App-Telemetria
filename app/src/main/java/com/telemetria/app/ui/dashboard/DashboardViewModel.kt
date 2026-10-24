package com.telemetria.app.ui.dashboard

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.location.TelemetryService
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.session.SessionLiveState
import com.telemetria.app.core.session.SessionManager
import com.telemetria.app.core.video.CameraPermissionHelper
import com.telemetria.app.core.video.VideoRecordingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TelemetryDatabase.getInstance(application)
    private val sessionManager = SessionManager.getInstance(db)
    val videoRecordingManager = VideoRecordingManager(application)

    val liveState: StateFlow<SessionLiveState> = sessionManager.liveState

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    fun toggleVideoEnabled() {
        _isVideoEnabled.update { !it }
    }

    fun onStartClicked(title: String = "") {
        viewModelScope.launch {
            TelemetryService.startService(getApplication())
            val session = sessionManager.startNewSession(title)

            if (_isVideoEnabled.value && CameraPermissionHelper.hasCameraPermission(getApplication())) {
                startVideoForSession(session.id)
            }
        }
    }

    private fun startVideoForSession(sessionId: String) {
        val app = getApplication<Application>()
        val videoDir = File(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: app.filesDir, "telemetria_videos")
        videoDir.mkdirs()
        val videoFile = File(videoDir, "video_${sessionId.take(8)}.mp4")

        videoRecordingManager.startRecording(
            outputFile = videoFile,
            onStartSync = { startMonoNanos, startEpochMillis ->
                sessionManager.attachVideoRecording(
                    filePath = videoFile.absolutePath,
                    startMonoNanos = startMonoNanos,
                    startEpochMillis = startEpochMillis
                )
            },
            onStatusUpdate = { durationSec, _ ->
                sessionManager.updateVideoStatus(isRecording = true, durationSec = durationSec)
            },
            onFinalized = { success, errorMsg ->
                sessionManager.updateVideoStatus(isRecording = false, durationSec = 0)
            }
        )
    }

    fun onPauseClicked() {
        sessionManager.pauseSession()
        if (videoRecordingManager.isRecording) {
            videoRecordingManager.pauseRecording()
        }
    }

    fun onResumeClicked() {
        sessionManager.resumeSession()
        if (videoRecordingManager.isRecording && videoRecordingManager.isPaused) {
            videoRecordingManager.resumeRecording()
        }
    }

    fun onStopClicked(onSessionSaved: (Session) -> Unit = {}) {
        viewModelScope.launch {
            if (videoRecordingManager.isRecording) {
                videoRecordingManager.stopRecording()
            }
            val session = sessionManager.stopSession()
            TelemetryService.stopService(getApplication())
            if (session != null) {
                onSessionSaved(session)
            }
        }
    }

    fun onCalibrateSensorsClicked() {
        sessionManager.startOrientationCalibration()
    }

    override fun onCleared() {
        super.onCleared()
        videoRecordingManager.release()
    }
}
