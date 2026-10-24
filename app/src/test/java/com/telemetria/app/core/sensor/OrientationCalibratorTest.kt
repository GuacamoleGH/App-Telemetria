package com.telemetria.app.core.sensor

import com.telemetria.app.core.sensor.model.InertialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrientationCalibratorTest {

    @Test
    fun calibrator_beforeCalibration_transformsWithDefaultVectors() {
        val calibrator = OrientationCalibrator()
        assertTrue(!calibrator.isCalibrated)

        // Snapshot con aceleración hacia adelante (-Z en coordenadas del teléfono con pantalla mirando al conductor)
        val snapshot = InertialSnapshot(
            timestampMillis = 1000L,
            monotonicNanos = 1000000L,
            linearAccelX = 0f,
            linearAccelY = 0f,
            linearAccelZ = -4.903325f, // 0.5 G hacia adelante
            rawAccelX = 0f,
            rawAccelY = 9.80665f, // gravedad vertical
            rawAccelZ = 0f,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f
        )

        val gForces = calibrator.transformToVehicleG(snapshot)
        // Con vector forward por defecto (0, 0, -1), dot([0, 0, -4.9], [0, 0, -1]) = +4.9 m/s² -> +0.5 G
        assertEquals(0.5f, gForces.longitudinalG, 0.05f)
        assertEquals(0.0f, gForces.lateralG, 0.05f)
    }

    @Test
    fun calibrator_staticSamples_calibratesCorrectly() {
        val calibrator = OrientationCalibrator()
        calibrator.startCalibration()

        // Móvil montado verticalmente en el salpicadero (gravedad medida principalmente en eje +Y)
        for (i in 0 until 40) {
            val finished = calibrator.addCalibrationSample(0.1f, 9.8f, 0.2f)
            if (i == 39) {
                assertTrue("Calibration should complete on 40th sample", finished)
            }
        }

        assertTrue(calibrator.isCalibrated)

        // Viraje a la derecha que genera aceleración lateral en el móvil (eje +X)
        val snapshotTurn = InertialSnapshot(
            timestampMillis = 2000L,
            monotonicNanos = 2000000L,
            linearAccelX = 4.903325f, // ~0.5 G lateral
            linearAccelY = 0f,
            linearAccelZ = 0f,
            rawAccelX = 4.9f,
            rawAccelY = 9.8f,
            rawAccelZ = 0f,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0.2f
        )

        val gForces = calibrator.transformToVehicleG(snapshotTurn)
        assertTrue("Lateral G magnitude should be around 0.5G", abs(gForces.lateralG) in 0.4f..0.6f)
    }
}

