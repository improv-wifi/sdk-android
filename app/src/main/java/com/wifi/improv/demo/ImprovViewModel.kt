package com.wifi.improv.demo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import com.wifi.improv.ImprovManagerCallback
import kotlinx.coroutines.launch

class ImprovViewModel : ViewModel(), ImprovManagerCallback {

    private val _improvState = MutableLiveData<ImprovScreenState>()
    val improvState: LiveData<ImprovScreenState> = _improvState

    var scanning = false
    val devices = mutableSetOf<ImprovDevice>()
    var connectedDevice: ImprovDevice? = null
    var deviceState: DeviceState? = null
    var errorState: ErrorState? = null

    private fun update() {
        viewModelScope.launch {
            _improvState.value = ImprovScreenState(
                scanning,
                devices.toList(),
                connectedDevice?.name ?: "",
                connectedDevice?.address ?: "",
                connectedDevice != null,
                deviceState.toString(),
                errorState.toString()
            )
        }
    }

    override fun onScanningStateChange(scanning: Boolean) {
        this.scanning = scanning
        update()
    }

    override fun onDeviceFound(device: ImprovDevice) {
        devices.add(device)
        update()
    }

    override fun onConnectionStateChange(device: ImprovDevice?) {
        this.connectedDevice = device
        if (connectedDevice == null) {
            deviceState = null
            errorState = null
        }
        update()
    }

    override fun onStateChange(state: DeviceState) {
        deviceState = state
        update()
    }

    override fun onErrorStateChange(errorState: ErrorState) {
        this.errorState = errorState
        update()
    }
}

@Immutable
data class ImprovScreenState(
    val scanning: Boolean,
    val devices: List<ImprovDevice>,
    val name: String,
    val address: String,
    val btConnected: Boolean,
    val deviceState: String,
    val errorState: String
)