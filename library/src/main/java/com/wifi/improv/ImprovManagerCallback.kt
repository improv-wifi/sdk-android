package com.wifi.improv

interface ImprovManagerCallback {

    fun onScanningStateChange(scanning: Boolean)

    fun onDeviceFound(device: ImprovDevice)

    fun onConnectionStateChange(device: ImprovDevice?)

    fun onStateChange(state: DeviceState)

    fun onErrorStateChange(errorState: ErrorState)
}