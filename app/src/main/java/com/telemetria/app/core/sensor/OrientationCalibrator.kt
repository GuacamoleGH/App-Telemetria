package com.telemetria.app.core.sensor

import com.telemetria.app.core.sensor.model.CalibratedGForces
import com.telemetria.app.core.sensor.model.InertialSnapshot
import kotlin.math.sqrt

/**
 * Calibrador de orientación del smartphone montado en el vehículo.
 * Transforma las aceleraciones del sistema de coordenadas del móvil
 * al sistema de coordenadas de dinámica del vehículo:
 * - Eje longitudinal: Aceleración / Frenada
 * - Eje lateral: Curvas / Fuerza centrífuga
 * - Eje vertical: Baches / Compresión
 */
class OrientationCalibrator {

    companion object {
        const val STANDARD_GRAVITY = 9.80665f
    }

    // Vectores unitarios del vehículo expresados en el marco del teléfono
    private var upVector = floatArrayOf(0f, 1f, 0f)       // Vertical
    private var forwardVector = floatArrayOf(0f, 0f, -1f) // Hacia adelante del vehículo
    private var rightVector = floatArrayOf(1f, 0f, 0f)    // Hacia la derecha del vehículo

    var isCalibrated: Boolean = false
        private set

    // Acumulador para calibración estática (promedio de lecturas en reposo)
    private val staticGravitySamples = mutableListOf<FloatArray>()
    private val maxCalibrationSamples = 40

    /**
     * Inicia una calibración estática (el vehículo debe estar detenido en superficie razonablemente nivelada).
     */
    fun startCalibration() {
        staticGravitySamples.clear()
        isCalibrated = false
    }

    /**
     * Invalida la calibración previa cuando el teléfono es manipulado o cambia de orientación.
     */
    fun invalidateCalibration() {
        isCalibrated = false
        staticGravitySamples.clear()
    }

    /**
     * Comprueba si el vector gravedad actual se ha desviado significativamente (>15°) del vector UP calibrado.
     */
    fun isOrientationChanged(rawX: Float, rawY: Float, rawZ: Float): Boolean {
        if (!isCalibrated) return false
        val norm = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        if (norm < 7.0f || norm > 13.0f) return false
        val currentUpX = rawX / norm
        val currentUpY = rawY / norm
        val currentUpZ = rawZ / norm
        val dot = currentUpX * upVector[0] + currentUpY * upVector[1] + currentUpZ * upVector[2]
        // cos(15°) ≈ 0.966. Si dot < 0.966, la orientación ha rotado más de 15 grados respecto a la calibrada
        return dot < 0.966f
    }

    /**
     * Procesa una muestra de gravedad/acelerómetro durante el proceso de calibración.
     */
    fun addCalibrationSample(rawX: Float, rawY: Float, rawZ: Float): Boolean {
        if (staticGravitySamples.size < maxCalibrationSamples) {
            staticGravitySamples.add(floatArrayOf(rawX, rawY, rawZ))
        }

        if (staticGravitySamples.size >= maxCalibrationSamples) {
            computeCalibration()
            return true
        }
        return false
    }

    private fun computeCalibration() {
        if (staticGravitySamples.isEmpty()) return

        // 1. Promediar vector gravedad (Vertical del vehículo)
        var avgX = 0f
        var avgY = 0f
        var avgZ = 0f
        for (s in staticGravitySamples) {
            avgX += s[0]
            avgY += s[1]
            avgZ += s[2]
        }
        avgX /= staticGravitySamples.size
        avgY /= staticGravitySamples.size
        avgZ /= staticGravitySamples.size

        // Normalizar para obtener vector UP
        val normUp = sqrt(avgX * avgX + avgY * avgY + avgZ * avgZ)
        if (normUp > 0.001f) {
            upVector = floatArrayOf(avgX / normUp, avgY / normUp, avgZ / normUp)
        }

        // 2. Determinar vector FORWARD tentativo perpendicular a UP
        // Asumiendo montaje típico en salpicadero donde la pantalla mira al habitáculo:
        // El vector Y del teléfono suele proyectar hacia arriba y -Z hacia adelante de la marcha
        val tempForward = floatArrayOf(0f, 0f, -1f)
        
        // Proyectar tempForward sobre el plano perpendicular a upVector: F_proj = temp - (temp . up) * up
        val dot = dotProduct(tempForward, upVector)
        var projX = tempForward[0] - dot * upVector[0]
        var projY = tempForward[1] - dot * upVector[1]
        var projZ = tempForward[2] - dot * upVector[2]
        var normF = sqrt(projX * projX + projY * projY + projZ * projZ)

        // Si el teléfono está en un ángulo inusual (ej. totalmente plano mirando al techo)
        if (normF < 0.1f) {
            // Usar eje Y como referencia forward
            val altTemp = floatArrayOf(0f, 1f, 0f)
            val dotAlt = dotProduct(altTemp, upVector)
            projX = altTemp[0] - dotAlt * upVector[0]
            projY = altTemp[1] - dotAlt * upVector[1]
            projZ = altTemp[2] - dotAlt * upVector[2]
            normF = sqrt(projX * projX + projY * projY + projZ * projZ)
        }

        if (normF > 0.001f) {
            forwardVector = floatArrayOf(projX / normF, projY / normF, projZ / normF)
        }

        // 3. Vector RIGHT (derecha) = forward x up
        rightVector = crossProduct(forwardVector, upVector)
        val normR = sqrt(rightVector[0] * rightVector[0] + rightVector[1] * rightVector[1] + rightVector[2] * rightVector[2])
        if (normR > 0.001f) {
            rightVector = floatArrayOf(rightVector[0] / normR, rightVector[1] / normR, rightVector[2] / normR)
        }

        isCalibrated = true
    }

    /**
     * Transforma una muestra inercial al sistema de fuerzas G del vehículo.
     */
    fun transformToVehicleG(snapshot: InertialSnapshot): CalibratedGForces {
        val linX = snapshot.linearAccelX
        val linY = snapshot.linearAccelY
        val linZ = snapshot.linearAccelZ

        val linVector = floatArrayOf(linX, linY, linZ)

        // Producto escalar con los ejes del vehículo
        val longMps2 = dotProduct(linVector, forwardVector)
        val latMps2 = dotProduct(linVector, rightVector)
        val vertMps2 = dotProduct(linVector, upVector)

        // Conversión a unidades G
        return CalibratedGForces(
            longitudinalG = longMps2 / STANDARD_GRAVITY,
            lateralG = latMps2 / STANDARD_GRAVITY,
            verticalG = vertMps2 / STANDARD_GRAVITY,
            isCalibrated = isCalibrated
        )
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }

    private fun crossProduct(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    }
}

