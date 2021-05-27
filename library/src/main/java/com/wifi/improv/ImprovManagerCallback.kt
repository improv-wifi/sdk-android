package com.improv.wifi

import com.wifi.improv.ImprovDevice

interface ImprovManagerCallback {

    fun onDeviceFound(device: ImprovDevice)

    fun onConnectionStateChange(device: ImprovDevice?)

    fun onStateChange(state: DeviceState)

    fun onErrorStateChange(errorState: ErrorState)
}