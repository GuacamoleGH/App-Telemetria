package com.telemetria.app.core.obd.transport

import com.telemetria.app.core.obd.model.ObdConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interfaz de transporte de bajo nivel para comunicación con adaptadores OBD-II / ELM327.
 * Permite implementar transportes BLE (Veepeak), Bluetooth Clásico SPP o Wi-Fi sin alterar la lógica de protocolo.
 */
interface ObdTransport {

    val connectionState: StateFlow<ObdConnectionState>

    val isConnected: Boolean

    /**
     * Conecta con el adaptador especificado (ej. dirección MAC en BLE o IP:Puerto en Wi-Fi).
     */
    suspend fun connect(target: String): Boolean

    /**
     * Envía un comando de texto al adaptador (ej. "010C\r") y espera la respuesta completa hasta el prompt '>'.
     */
    suspend fun sendCommand(command: String, timeoutMillis: Long = 2500L): String

    /**
     * Desconecta y libera recursos.
     */
    fun disconnect()
}

