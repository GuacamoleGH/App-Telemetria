package com.telemetria.app.ui.history

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.core.export.CsvExporter
import com.telemetria.app.core.export.GpxExporter
import com.telemetria.app.core.util.SessionFileCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TelemetryDatabase.getInstance(application)

    val sessions: StateFlow<List<SessionEntity>> = db.sessionDao().observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedSession = MutableStateFlow<SessionEntity?>(null)
    val selectedSession: StateFlow<SessionEntity?> = _selectedSession.asStateFlow()

    private val _selectedPoints = MutableStateFlow<List<TelemetryPointEntity>>(emptyList())
    val selectedPoints: StateFlow<List<TelemetryPointEntity>> = _selectedPoints.asStateFlow()

    private val _exportMessage = MutableSharedFlow<String>()
    val exportMessage: SharedFlow<String> = _exportMessage.asSharedFlow()

    fun loadSessionDetails(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = db.sessionDao().getSessionById(sessionId)
            val points = db.telemetryPointDao().getPointsForSession(sessionId)
            _selectedSession.value = session
            _selectedPoints.value = points
        }
    }

    fun exportGpx(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = _selectedPoints.value.ifEmpty {
                db.telemetryPointDao().getPointsForSession(session.id)
            }
            val exportDir = getExportDirectory()
            val file = File(exportDir, "sesion_${session.id.take(8)}.gpx")
            val success = GpxExporter.exportToGpx(session, points, file)
            withContext(Dispatchers.Main) {
                if (success) {
                    _exportMessage.emit("GPX exportado en: ${file.absolutePath}")
                } else {
                    _exportMessage.emit("Error exportando GPX")
                }
            }
        }
    }

    fun exportCsv(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = _selectedPoints.value.ifEmpty {
                db.telemetryPointDao().getPointsForSession(session.id)
            }
            val exportDir = getExportDirectory()
            val file = File(exportDir, "telemetria_${session.id.take(8)}.csv")
            val success = CsvExporter.exportToCsv(session, points, file)
            withContext(Dispatchers.Main) {
                if (success) {
                    _exportMessage.emit("CSV exportado en: ${file.absolutePath}")
                } else {
                    _exportMessage.emit("Error exportando CSV")
                }
            }
        }
    }

    fun exportZip(session: SessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = _selectedPoints.value.ifEmpty {
                db.telemetryPointDao().getPointsForSession(session.id)
            }
            val exportDir = getExportDirectory()
            val zipFile = File(exportDir, "sesion_${session.id.take(8)}_bundle.zip")
            val tempDir = File(getApplication<Application>().cacheDir, "zip_temp_${session.id.take(8)}")
            
            val vehicleConfig = com.telemetria.app.core.preferences.VehiclePreferences.getInstance(getApplication()).vehicleConfig.value
            val success = com.telemetria.app.core.export.ZipSessionExporter.exportSessionToZip(
                session = session,
                points = points,
                outputZipFile = zipFile,
                tempDir = tempDir,
                vehicleConfig = vehicleConfig
            )
            withContext(Dispatchers.Main) {
                if (success) {
                    _exportMessage.emit("Paquete ZIP exportado en: ${zipFile.absolutePath}")
                } else {
                    _exportMessage.emit("Error empaquetando ZIP")
                }
            }
        }
    }

    fun deleteSession(sessionId: String, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = db.sessionDao().getSessionById(sessionId)

            // Eliminar todos los archivos físicos generados (vídeo, zip, csv, gpx, temporales)
            val deletedFiles = SessionFileCleaner.deleteSessionFiles(getApplication(), session, sessionId)

            // Eliminar registros de la base de datos local
            db.telemetryPointDao().deletePointsForSession(sessionId)
            db.sessionDao().deleteSession(sessionId)

            if (_selectedSession.value?.id == sessionId) {
                _selectedSession.value = null
                _selectedPoints.value = emptyList()
            }

            withContext(Dispatchers.Main) {
                val count = deletedFiles.size
                val msg = if (count > 0) {
                    "Sesión y $count archivo(s) asociados eliminados (vídeo, zip, csv, gpx)"
                } else {
                    "Sesión eliminada del historial"
                }
                _exportMessage.emit(msg)
                onDeleted?.invoke()
            }
        }
    }

    private fun getExportDirectory(): File {
        val app = getApplication<Application>()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloads, "Telemetria")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return if (exportDir.canWrite()) exportDir else app.getExternalFilesDir(null) ?: app.filesDir
    }
}

