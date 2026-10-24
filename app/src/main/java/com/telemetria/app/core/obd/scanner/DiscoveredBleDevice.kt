package com.telemetria.app.core.obd.scanner

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

