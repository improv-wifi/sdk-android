package com.wifi.improv

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class ImprovManager(
    private val context: Context,
    private val callback: ImprovManagerCallback
) {

    companion object {
        private const val TAG = "ImprovManager"

        private val UUID_SERVICE_PROVISION: UUID =
            UUID.fromString("00467768-6228-2272-4663-277478268000")
        private val UUID_CHAR_CURRENT_STATE: UUID =
            UUID.fromString("00467768-6228-2272-4663-277478268001")
        private val UUID_CHAR_ERROR_STATE: UUID =
            UUID.fromString("00467768-6228-2272-4663-277478268002")
        private val UUID_CHAR_RPC: UUID =
            UUID.fromString("00467768-6228-2272-4663-277478268003")
        private val UUID_CHAR_RPC_RESULT: UUID =
            UUID.fromString("00467768-6228-2272-4663-277478268004")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val scanner = bluetoothManager.adapter.bluetoothLeScanner
    private val scanFilter =
        ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_SERVICE_PROVISION)).build()
    private val scanSettings =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    private val foundDevices = mutableMapOf<String, BluetoothDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i(TAG, "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                foundDevices[address] = this
                callback.onDeviceFound(ImprovDevice(name, address))
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
                    callback.onConnectionStateChange(
                        ImprovDevice(
                            gatt.device.name,
                            gatt.device.address
                        )
                    )

                    operationQueue.add(RequestLargeMtu)
                    operationQueue.add(DiscoverServices)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    bluetoothGatt = null
                    callback.onConnectionStateChange(null)
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
                bluetoothGatt = null
                callback.onConnectionStateChange(null)
            }

            if (pendingOperation is Connect)
                signalEndOfOperation()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                UUID_CHAR_CURRENT_STATE -> {
                    val value =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toUByte()
                    Log.i(TAG, "Current State has changed to $value.")
                    val deviceState = DeviceState.values().firstOrNull { it.value == value }
                    if (deviceState != null)
                        callback.onStateChange(deviceState)
                    else
                        Log.e(TAG, "Unable to determine Current State")
                }
                UUID_CHAR_ERROR_STATE -> {
                    val value =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toUByte()
                    Log.i(TAG, "Error State has changed to $value.")
                    val errorState = ErrorState.values().firstOrNull { it.value == value }
                    if (errorState != null)
                        callback.onErrorStateChange(errorState)
                    else
                        Log.e(TAG, "Unable to determine Error State")
                }
                UUID_CHAR_RPC_RESULT -> {
                    Log.i(TAG, "RPC Result has changed to ${characteristic.value.joinToString()}.")
                    val result = extractResultStrings(characteristic.value)
                    if (result != null)
                        callback.onRpcResult(result)
                    else
                        Log.w(TAG, "Received empty RPC Result")
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
            if (pendingOperation is CharacteristicWrite)
                signalEndOfOperation()
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
            if (pendingOperation is CharacteristicRead)
                signalEndOfOperation()
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
            if (pendingOperation is DescriptorWrite)
                signalEndOfOperation()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            for (service in gatt.services) {
                val sb = StringBuilder("Found service: ${service.uuid}")
                for (char in service.characteristics) {
                    sb.append("\n\tChar: ${char.uuid}, Value: ${char.value}")
                }
                Log.i(TAG, sb.toString())
            }
            if (gatt.services.isEmpty())
                Log.e(TAG, "No Services Found!!")

            val service = gatt.getService(UUID_SERVICE_PROVISION)
            val currentStateChar = service.getCharacteristic(UUID_CHAR_CURRENT_STATE)
            enqueueOperation(CharacteristicRead(currentStateChar))
            if (gatt.setCharacteristicNotification(currentStateChar, true)) {
                Log.i(
                    TAG,
                    "Registered for Current State Notifications, descriptors: ${currentStateChar.descriptors}}"
                )
                currentStateChar.descriptors.firstOrNull()?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    enqueueOperation(DescriptorWrite(it))
                }
            } else
                Log.e(TAG, "Unable to register for Current State Notifications")

            val errorStateChar = service.getCharacteristic(UUID_CHAR_ERROR_STATE)
            enqueueOperation(CharacteristicRead(errorStateChar))
            if (gatt.setCharacteristicNotification(errorStateChar, true)) {
                Log.i(TAG, "Registered for Error State Notifications")
                errorStateChar.descriptors.firstOrNull()?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    enqueueOperation(DescriptorWrite(it))
                }
            } else
                Log.e(TAG, "Unable to register for Error State Notifications")

            val rpcResultChar = service.getCharacteristic(UUID_CHAR_RPC_RESULT)
            enqueueOperation(CharacteristicRead(rpcResultChar))
            if (gatt.setCharacteristicNotification(rpcResultChar, true)) {
                Log.i(TAG, "Registered for RPC Result Notifications")
                rpcResultChar.descriptors.firstOrNull()?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    enqueueOperation(DescriptorWrite(it))
                }
            } else
                Log.e(TAG, "Unable to register for RPC Result Notifications")

            if (pendingOperation is DiscoverServices)
                signalEndOfOperation()
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "MTU change to $mtu returned status: $status")
            if (pendingOperation is RequestLargeMtu)
                signalEndOfOperation()
        }
    }

    private var isScanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    fun stopScan() {
        if (isScanning) {
            scanner.stopScan(scanCallback)
            callback.onScanningStateChange(false)
        }
        isScanning = false
    }

    fun findDevices() {
        Log.i(TAG, "Find Devices")
        isScanning = true
        callback.onScanningStateChange(true)
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    fun connectToDevice(device: ImprovDevice) {
        Log.i(TAG, "Connect to Device ${device.address}")
        stopScan()
        if (foundDevices.containsKey(device.address)) {
            enqueueOperation(Connect(foundDevices[device.address]!!))
        } else {
            Log.e(TAG, "Tried to connect to a device we didn't find?")
        }
    }

    fun identifyDevice() {
        if (bluetoothGatt == null) {
            error("Not Connected to a Device!")
        }
        bluetoothGatt?.let {
            val rpc = it.getService(UUID_SERVICE_PROVISION)?.getCharacteristic(UUID_CHAR_RPC)
            if (rpc != null) {
                sendRpc(rpc, RpcCommand.IDENTIFY, arrayOf())
            }
        }

    }

    fun sendWifi(ssid: String, password: String) {
        Log.i(TAG, "Send Wifi")
        if (bluetoothGatt == null) {
            error("Not Connected to a Device!")
        }
        bluetoothGatt?.let {
            val rpc = it.getService(UUID_SERVICE_PROVISION)?.getCharacteristic(UUID_CHAR_RPC)
            if (rpc != null) {
                val encodedSsid = ssid.encodeToByteArray()
                val encodedPassword = password.encodeToByteArray()
                val data =
                    arrayOf(encodedSsid.size.toUByte()) + encodedSsid.toUByteArray() + encodedPassword.size.toUByte() + encodedPassword.toUByteArray()
                sendRpc(rpc, RpcCommand.SEND_WIFI, data)
            }
        }
    }

    private fun sendRpc(rpc: BluetoothGattCharacteristic, command: RpcCommand, data: Array<UByte>) {
        val payload = arrayOf(command.value, data.size.toUByte()) + data + 0.toUByte()
        payload[payload.size - 1] = payload.reduce { sum, cur -> (sum + cur).toUByte() }
        rpc.value = payload.toUByteArray().toByteArray()

        Log.d(TAG, "Sending ${payload.map { it.toString() }.toList()}")
        enqueueOperation(CharacteristicWrite(rpc))
    }

    private fun extractResultStrings(data: ByteArray): List<String>? {
        // Ensure the data is at least 3 bytes long to read the first string length
        if (data.size < 3) return null

        val strings = mutableListOf<String>()
        var currentIndex = 2 // Start after the first two bytes

        while (currentIndex < data.size) {
            // Get the length of the current string
            val stringLength = data[currentIndex].toInt()
            currentIndex++

            // Ensure there are enough bytes left for the current string
            if (currentIndex + stringLength > data.size) return strings

            // Extract the string and add it to the list
            try {
                val string = data.decodeToString(currentIndex, currentIndex + stringLength, throwOnInvalidSequence = true)
                currentIndex += stringLength
                strings += string
            } catch (e: Exception) {
                Log.e(TAG, "Invalid string encoding, returning strings previously decoded")
                return strings
            }
        }

        return strings
    }

    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: BleOperationType? = null

    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            doNextOperation()
        }
    }

    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Log.e(TAG, "doNextOperation() called when an operation is pending! Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Log.v(TAG, "Operation queue empty, returning")
            return
        }
        pendingOperation = operation

        when (operation) {
            is Connect -> {
                operation.device.connectGatt(context, true, gattCallback)
            }
            is Disconnect -> {
                // Noop?
            }
            is DiscoverServices -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt!!.discoverServices()
                } else {
                    Log.e(TAG, "Tried to discover services without device connected.")
                }
            }
            is CharacteristicWrite -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt!!.writeCharacteristic(operation.char)
                } else {
                    Log.e(TAG, "Tried writing characteristic without device connected.")
                }
            }
            is CharacteristicRead -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt!!.readCharacteristic(operation.char)
                } else {
                    Log.e(TAG, "Tried reading characteristic without device connected.")
                }
            }
            is DescriptorWrite -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt!!.writeDescriptor(operation.desc)
                } else {
                    Log.e(TAG, "Tried writing descriptor without device connected.")
                }
            }
            is RequestLargeMtu -> {
                if (bluetoothGatt != null) {
                    bluetoothGatt!!.requestMtu(517)
                } else {
                    Log.e(TAG, "Tried requesting MTU without device connected.")
                }
            }
            else -> {
                error("Unhandled Operation!")
            }
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Log.d(TAG, "End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            doNextOperation()
        }
    }
}
