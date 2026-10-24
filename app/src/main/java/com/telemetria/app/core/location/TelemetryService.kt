package com.telemetria.app.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.telemetria.app.MainActivity
import com.telemetria.app.core.database.TelemetryDatabase
import com.telemetria.app.core.sensor.InertialSensorManager
import com.telemetria.app.core.session.SessionManager
import com.telemetria.app.core.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TelemetryService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationJob: Job? = null
    private var sensorJob: Job? = null
    private var statusObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var gpsTracker: GpsTracker
    private lateinit var inertialSensorManager: InertialSensorManager
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        val db = TelemetryDatabase.getInstance(this)
        sessionManager = SessionManager.getInstance(db)
        gpsTracker = GpsTracker(this)
        inertialSensorManager = InertialSensorManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SERVICE -> {
                startForegroundTracking()
            }
            else -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val initialNotification = buildNotification("Iniciando servicio de telemetría...", "Esperando señal GPS y sensores...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startGpsCollection()
        startSensorCollection()
        observeLiveState()
    }

    private fun startGpsCollection() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            try {
                gpsTracker.observeLocation(intervalMillis = 1000L).collect { snapshot ->
                    sessionManager.onGpsReceived(snapshot)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startSensorCollection() {
        sensorJob?.cancel()
        sensorJob = serviceScope.launch {
            try {
                inertialSensorManager.observeInertialSensors().collect { snapshot ->
                    sessionManager.onInertialReceived(snapshot)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeLiveState() {
        statusObserverJob?.cancel()
        statusObserverJob = serviceScope.launch {
            sessionManager.liveState.collectLatest { state ->
                val title = when (state.status) {
                    com.telemetria.app.core.model.SessionStatus.ACTIVE -> "Grabando Telemetría"
                    com.telemetria.app.core.model.SessionStatus.PAUSED -> "Telemetría en Pausa"
                    com.telemetria.app.core.model.SessionStatus.COMPLETED -> "Telemetría Lista"
                }

                val speedStr = FormatUtils.formatSpeed(state.currentSpeedKmh)
                val distStr = FormatUtils.formatDistanceKm(state.distanceMeters)
                val timeStr = FormatUtils.formatDuration(state.elapsedSeconds)
                val gStr = String.format(java.util.Locale.US, "%.2fG Lat", state.lateralG)
                val content = "$speedStr km/h | $distStr | $gStr"

                notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
            }
        }
    }

    private fun stopForegroundTracking() {
        locationJob?.cancel()
        sensorJob?.cancel()
        statusObserverJob?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Telemetría Automovilística",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el registro de datos GPS y sensores activo en segundo plano."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Telemetria::TrackingWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopForegroundTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "telemetry_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.telemetria.app.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.telemetria.app.STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, TelemetryService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelemetryService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
