package com.telemetria.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.location.TelemetryService
import com.telemetria.app.core.model.Session
import com.telemetria.app.core.model.SessionStatus
import com.telemetria.app.core.session.SessionLiveState
import com.telemetria.app.core.session.SessionManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TelemetryDatabase.getInstance(application)
    private val sessionManager = SessionManager.getInstance(db)

    val liveState: StateFlow<SessionLiveState> = sessionManager.liveState

    fun onStartClicked(title: String = "") {
        viewModelScope.launch {
            // Iniciar foreground service
            TelemetryService.startService(getApplication())
            sessionManager.startNewSession(title)
        }
    }

    fun onPauseClicked() {
        sessionManager.pauseSession()
    }

    fun onResumeClicked() {
        sessionManager.resumeSession()
    }

    fun onStopClicked(onSessionSaved: (Session) -> Unit = {}) {
        viewModelScope.launch {
            val session = sessionManager.stopSession()
            TelemetryService.stopService(getApplication())
            if (session != null) {
                onSessionSaved(session)
            }
        }
    }
}

