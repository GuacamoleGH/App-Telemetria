package com.telemetria.app.ui.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import com.telemetria.app.ui.theme.CockpitBlack
import com.telemetria.app.ui.theme.CockpitCardBg
import com.telemetria.app.ui.theme.NeonAmber
import com.telemetria.app.ui.theme.NeonCyan
import com.telemetria.app.ui.theme.TextMuted
import com.telemetria.app.ui.theme.TextPrimary
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
    var isOfflineOnly by remember { mutableStateOf(false) }

    // Configurar osmdroid User-Agent para cumplir políticas de uso de OpenStreetMap
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

    LaunchedEffect(isOfflineOnly) {
        mapView.setUseDataConnection(!isOfflineOnly)
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
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

        // Pill conmutador de Red / Modo Offline de OpenStreetMap
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CockpitBlack.copy(alpha = 0.85f))
                .clickable { isOfflineOnly = !isOfflineOnly }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOfflineOnly) Icons.Default.CloudOff else Icons.Default.Public,
                contentDescription = null,
                tint = if (isOfflineOnly) NeonAmber else NeonCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isOfflineOnly) " Solo Caché (Offline)" else " OSM En Línea",
                fontSize = 10.sp,
                color = if (isOfflineOnly) NeonAmber else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Subtítulo informativo de privacidad de teselas en la parte inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CockpitBlack.copy(alpha = 0.70f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "© OpenStreetMap • Solo descarga teselas visuales",
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
