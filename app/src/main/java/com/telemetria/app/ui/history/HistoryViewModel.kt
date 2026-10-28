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

data class ZipExportProgress(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val sessionTitle: String = ""
)

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

    private val _promptDeleteRawVideoAfterZip = MutableSharedFlow<SessionEntity>()
    val promptDeleteRawVideoAfterZip: SharedFlow<SessionEntity> = _promptDeleteRawVideoAfterZip.asSharedFlow()

    private val _zipExportProgress = MutableStateFlow(ZipExportProgress())
    val zipExportProgress: StateFlow<ZipExportProgress> = _zipExportProgress.asStateFlow()

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

    fun exportZip(session: SessionEntity, trimDistanceMeters: Double = 0.0) {
        _zipExportProgress.value = ZipExportProgress(
            isExporting = true,
            progress = 0.05f,
            statusText = "Iniciando empaquetado...",
            sessionTitle = session.title
        )
        viewModelScope.launch(Dispatchers.IO) {
            val rawPoints = _selectedPoints.value.ifEmpty {
                db.telemetryPointDao().getPointsForSession(session.id)
            }
            val points = if (trimDistanceMeters > 0.0) {
                com.telemetria.app.core.util.PrivacyTrimmer.trimPointsByDistance(rawPoints, trimDistanceMeters)
            } else {
                rawPoints
            }
            val zipFile = getZipFileForSession(session)
            val tempDir = File(getApplication<Application>().cacheDir, "zip_temp_${session.id.take(8)}")
            
            val vehicleConfig = com.telemetria.app.core.preferences.VehiclePreferences.getInstance(getApplication()).vehicleConfig.value
            val success = com.telemetria.app.core.export.ZipSessionExporter.exportSessionToZip(
                session = session,
                points = points,
                outputZipFile = zipFile,
                tempDir = tempDir,
                vehicleConfig = vehicleConfig,
                onProgress = { progress, statusText ->
                    _zipExportProgress.value = ZipExportProgress(
                        isExporting = true,
                        progress = progress,
                        statusText = statusText,
                        sessionTitle = session.title
                    )
                }
            )
            withContext(Dispatchers.Main) {
                _zipExportProgress.value = ZipExportProgress(isExporting = false)
                if (success) {
                    _exportMessage.emit("Paquete ZIP exportado en: ${zipFile.absolutePath}")
                    if (doesRawVideoExist(session)) {
                        _promptDeleteRawVideoAfterZip.emit(session)
                    }
                } else {
                    _exportMessage.emit("Error empaquetando ZIP")
                }
            }
        }
    }

    /**
     * Genera un nombre de archivo descriptivo y seguro para el paquete ZIP,
     * incorporando la fecha y hora de la ruta (YYYY-MM-DD_HH-mm) y el ID corto.
     * Sanitiza caracteres prohibidos en sistemas de archivos de Windows (\ / : * ? " < > |).
     */
    fun getSafeZipFilename(session: SessionEntity): String {
        return com.telemetria.app.core.util.FormatUtils.formatZipFilename(session.startTimeMillis, session.id)
    }

    fun getZipFileForSession(session: SessionEntity): File {
        val exportDir = getExportDirectory()
        val shortId = session.id.take(8)
        // Soporte retrocompatible: si ya existe un zip para esta sesión (nuevo formato o formato antiguo sesion_xxxx_bundle.zip), devolverlo
        val existing = exportDir.listFiles()?.firstOrNull { file ->
            file.isFile && file.extension.equals("zip", ignoreCase = true) && file.name.contains(shortId)
        }
        return existing ?: File(exportDir, getSafeZipFilename(session))
    }

    fun isZipExported(session: SessionEntity): Boolean {
        val file = getZipFileForSession(session)
        return file.exists() && file.length() > 0
    }

    fun doesRawVideoExist(session: SessionEntity): Boolean {
        session.videoFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > 0) return true
        }
        val videoDir = File(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: getApplication<Application>().filesDir,
            "telemetria_videos"
        )
        return videoDir.exists() && videoDir.listFiles()?.any {
            it.isFile && (it.name.contains(session.id.take(8)) || it.name.contains(session.id))
        } == true
    }

    fun getRawVideoSizeMb(session: SessionEntity): Float {
        var bytes = 0L
        session.videoFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) bytes += file.length()
        }
        if (bytes == 0L) {
            val videoDir = File(
                getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: getApplication<Application>().filesDir,
                "telemetria_videos"
            )
            videoDir.listFiles()?.forEach {
                if (it.isFile && (it.name.contains(session.id.take(8)) || it.name.contains(session.id))) {
                    bytes += it.length()
                }
            }
        }
        return bytes / (1024f * 1024f)
    }

    fun deleteRawVideo(session: SessionEntity, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytesFreed = SessionFileCleaner.deleteRawVideoOnly(getApplication(), session)
            val updated = db.sessionDao().getSessionById(session.id)
            _selectedSession.value = updated

            withContext(Dispatchers.Main) {
                val mbFreed = bytesFreed / (1024f * 1024f)
                val msg = if (bytesFreed > 0) {
                    String.format(
                        java.util.Locale.US,
                        "Vídeo original eliminado (%.1f MB liberados). Se conserva dentro del archivo .zip en Download/Telemetria",
                        mbFreed
                    )
                } else {
                    "El vídeo original ya no existe en el almacenamiento interno."
                }
                _exportMessage.emit(msg)
                onDeleted?.invoke()
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

