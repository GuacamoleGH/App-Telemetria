package com.telemetria.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.telemetria.app.core.model.GpsSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GpsTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun observeLocation(intervalMillis: Long = 100L): Flow<GpsSnapshot> = callbackFlow {
        if (!LocationPermissionHelper.hasLocationPermissions(context)) {
            close(SecurityException("Missing location permissions"))
            return@callbackFlow
        }

        // Configuración de máxima frecuencia y precisión para telemetría deportiva (hasta 10 Hz)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val snapshot = locationToSnapshot(location)
                    trySend(snapshot)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun locationToSnapshot(location: Location): GpsSnapshot {
        val monotonicNanos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.elapsedRealtimeNanos
        } else {
            SystemClock.elapsedRealtimeNanos()
        }

        // Convert speed from m/s to km/h
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
        val altitude = if (location.hasAltitude()) location.altitude else null
        val bearing = if (location.hasBearing()) location.bearing else null
        val accuracy = if (location.hasAccuracy()) location.accuracy else null

        return GpsSnapshot(
            timestampMillis = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            monotonicNanos = monotonicNanos,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude,
            speedKmh = speedKmh,
            bearingDegrees = bearing,
            accuracyMeters = accuracy,
            isRealFix = true
        )
    }
}
