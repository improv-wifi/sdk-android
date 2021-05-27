package com.wifi.improv

import android.bluetooth.BluetoothDevice

data class ImprovDevice(
    val name: String,
    val address: String,
    val btDevice: BluetoothDevice
){
    override fun equals(other: Any?): Boolean {
        return if(other is ImprovDevice) address == other.address else false
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}
