package com.wifi.improv.demo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.improv.wifi.DeviceState
import com.improv.wifi.ErrorState
import com.wifi.improv.ImprovDevice
import com.improv.wifi.ImprovManagerCallback
import kotlinx.coroutines.launch

class ImprovViewModel: ViewModel(), ImprovManagerCallback {

    private val _improvState = MutableLiveData<ImprovScreenState>()
    val improvState: LiveData<ImprovScreenState> = _improvState

    val devices = mutableSetOf<ImprovDevice>()
    var connected: Boolean = false
    var deviceState: DeviceState? = null
    var errorState: ErrorState? = null

    private fun update(){
        viewModelScope.launch {
            _improvState.value = ImprovScreenState(
                devices,
                "",
                connected,
                deviceState.toString(),
                errorState.toString()
            )
        }
    }

    override fun onDeviceFound(device: ImprovDevice) {
        devices.add(device)
        update()
    }

    override fun onConnectionStateChange(connected: Boolean) {
        this.connected = connected
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
    val devices: Set<ImprovDevice>,
    val address: String,
    val btConnected: Boolean,
    val deviceState: String,
    val errorState: String
)