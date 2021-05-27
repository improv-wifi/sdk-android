package com.improv.wifi

import com.wifi.improv.ImprovDevice

interface ImprovManagerCallback {

    fun onDeviceFound(device: ImprovDevice)

    fun onConnectionStateChange(connected: Boolean)

    fun onStateChange(state: DeviceState)

    fun onErrorStateChange(errorState: ErrorState)
}