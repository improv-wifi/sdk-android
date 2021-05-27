package com.improv.wifi

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import com.wifi.improv.ImprovDevice
import java.util.UUID
import kotlin.text.StringBuilder

// https://punchthrough.com/android-ble-guide/
class ImprovManager(
    private val context: Context,
    private val callback: ImprovManagerCallback
) {

    companion object {
        private const val TAG = "ImprovManager"

        val ENABLE_BT_INTENT = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        private val UUID_SERVICE_PROVISION: UUID = UUID.fromString("00467768-6228-2272-4663-277478268000")
        private val UUID_CHAR_CURRENT_STATE: UUID = UUID.fromString("00467768-6228-2272-4663-277478268001")
        private val UUID_CHAR_ERROR_STATE: UUID = UUID.fromString("00467768-6228-2272-4663-277478268002")
        private val UUID_CHAR_RPC: UUID = UUID.fromString("00467768-6228-2272-4663-277478268003")
    }

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val scanner = bluetoothManager.adapter.bluetoothLeScanner
    private val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_SERVICE_PROVISION)).build()
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i(TAG, "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                // TODO: get state and error state
                callback.onDeviceFound(ImprovDevice(name, address, this))
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Successfully connected to $deviceAddress, discovering services.")
                    bluetoothGatt = gatt
                    callback.onConnectionStateChange(true)

                    gatt.discoverServices()

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    bluetoothGatt = null
                    callback.onConnectionStateChange(false)
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
                bluetoothGatt = null
                callback.onConnectionStateChange(false)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toUByte()
            when(characteristic.uuid){
                UUID_CHAR_CURRENT_STATE -> {
                    Log.i(TAG, "Current State has changed to $value.")
                    val deviceState = DeviceState.values().firstOrNull { it.value == value }
                    if (deviceState != null)
                        callback.onStateChange(deviceState)
                    else
                        Log.e(TAG, "Unable to determine Current State")

                    // Can't call too quick so I'll do them in sequence.
                    val service = gatt.getService(UUID_SERVICE_PROVISION)
                    val errorStateChar = service.getCharacteristic(UUID_CHAR_ERROR_STATE)
                    gatt.readCharacteristic(errorStateChar)
                }
                UUID_CHAR_ERROR_STATE -> {
                    Log.i(TAG, "Error State has changed to $value.")
                    val errorState = ErrorState.values().firstOrNull { it.value == value }
                    if (errorState != null)
                        callback.onErrorStateChange(errorState)
                    else
                        Log.e(TAG, "Unable to determine Error State")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Char ${characteristic.uuid} write complete")
            } else {
                Log.e(TAG, "Char ${characteristic.uuid} not written!!")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Char ${characteristic.uuid} read complete: ${characteristic.value}")
                onCharacteristicChanged(gatt, characteristic)
            } else {
                Log.e(TAG, "Char ${characteristic.uuid} not read!!")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Desc ${descriptor.uuid} write complete")
            } else {
                Log.e(TAG, "Desc ${descriptor.uuid} not written!!")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            for (service in gatt.services){
                val sb = StringBuilder("Found service: ${service.uuid}")
                for (char in service.characteristics){
                    sb.append("\n\tChar: ${char.uuid}, Value: ${char.value}")
                }
                Log.i(TAG, sb.toString())
            }
            if (gatt.services.isEmpty())
                Log.e(TAG, "No Services Found!!")

            val service = gatt.getService(UUID_SERVICE_PROVISION)
            val currentStateChar = service.getCharacteristic(UUID_CHAR_CURRENT_STATE)
            gatt.readCharacteristic(currentStateChar)
            if(gatt.setCharacteristicNotification(currentStateChar, true)) {
                Log.i(TAG, "Registered for Current State Notifications, descriptors: ${currentStateChar.descriptors }}")
                currentStateChar.descriptors.firstOrNull()?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            } else
                Log.e(TAG, "Unable to register for Current State Notifications")

            val errorStateChar = service.getCharacteristic(UUID_CHAR_ERROR_STATE)
            if(gatt.setCharacteristicNotification(errorStateChar, true)) {
                Log.i(TAG, "Registered for Error State Notifications")
                errorStateChar.descriptors.firstOrNull()?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            } else
                Log.e(TAG, "Unable to register for Error State Notifications")
        }
    }

    private var isScanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    fun stopScan(){
        if (isScanning){
            scanner.stopScan(scanCallback)
        }
        isScanning = false
    }

    fun findDevices(){
        Log.i(TAG, "Find Devices")
        isScanning = true
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    fun connectToDevice(device: ImprovDevice){
        Log.i(TAG, "Connect to Device ${device.address}")
        stopScan()
        Thread.sleep(100)
        device.btDevice.connectGatt(context, true, gattCallback)
    }

    fun identifyDevice(){
        if(bluetoothGatt == null) {
            error("Not Connected to a Device!")
        }
        bluetoothGatt?.let {
            val rpc = it.getService(UUID_SERVICE_PROVISION)?.getCharacteristic(UUID_CHAR_RPC)
            if (rpc != null) {
                sendRpc(rpc, RpcCommand.IDENTIFY, arrayOf())
            }
        }

    }

    fun sendWifi(ssid: String, password: String){
        Log.i(TAG, "Send Wifi")
        if(bluetoothGatt == null){
            error("Not Connected to a Device!")
        }
        bluetoothGatt?.let {
            val rpc = it.getService(UUID_SERVICE_PROVISION)?.getCharacteristic(UUID_CHAR_RPC)
            if (rpc != null) {
                val encodedSsid = ssid.encodeToByteArray()
                val encodedPassword = password.encodeToByteArray()
                val data = arrayOf(encodedSsid.size.toUByte()) + encodedSsid.toUByteArray() + encodedPassword.size.toUByte() + encodedPassword.toUByteArray()
                sendRpc(rpc, RpcCommand.SEND_WIFI, data)
            }
        }
    }

    private fun sendRpc(rpc: BluetoothGattCharacteristic, command: RpcCommand, data: Array<UByte>){
        val payload = arrayOf(command.value, data.size.toUByte()) + data + 0.toUByte()
        payload[payload.size - 1] = payload.reduce { sum, cur -> (sum + cur).toUByte() }
        rpc.value = payload.toUByteArray().toByteArray()

        Log.d(TAG, "Sending ${payload.map { it.toString() }.toList()}")
        bluetoothGatt!!.writeCharacteristic(rpc)
    }

}
