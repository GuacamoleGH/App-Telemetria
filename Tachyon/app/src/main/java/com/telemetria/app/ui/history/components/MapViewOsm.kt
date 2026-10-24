package com.telemetria.app.ui.history.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapViewOsm(
    points: List<TelemetryPointEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Configurar osmdroid User-Agent para cumplir políticas de OpenStreetMap
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = context.filesDir
        Configuration.getInstance().osmdroidTileCache = context.cacheDir
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()

            val geoPoints = points
                .filter { it.latitude != null && it.longitude != null }
                .map { GeoPoint(it.latitude!!, it.longitude!!) }

            if (geoPoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                }
                map.overlays.add(polyline)

                // Centrar en el primer punto
                map.controller.setZoom(16.0)
                map.controller.setCenter(geoPoints.first())
            }

            map.invalidate()
        }
    )
}

