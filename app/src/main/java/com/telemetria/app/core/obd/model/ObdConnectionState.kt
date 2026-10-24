package com.telemetria.app.core.obd.model

sealed class ObdConnectionState {
    object Disconnected : ObdConnectionState()
    object Scanning : ObdConnectionState()
    data class Connecting(val deviceName: String, val address: String) : ObdConnectionState()
    data class DiscoveringServices(val info: String) : ObdConnectionState()
    object InitializingElm327 : ObdConnectionState()
    data class Connected(val protocolName: String, val supportedPidsCount: Int) : ObdConnectionState()
    data class Error(val message: String) : ObdConnectionState()
}

