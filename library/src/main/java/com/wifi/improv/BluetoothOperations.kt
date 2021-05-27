package com.wifi.improv

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor


sealed class BleOperationType

data class Connect(val device: BluetoothDevice) : BleOperationType()
data class Disconnect(val device: BluetoothDevice) : BleOperationType()

data class CharacteristicRead(val char: BluetoothGattCharacteristic ): BleOperationType()
data class CharacteristicWrite(val char: BluetoothGattCharacteristic): BleOperationType()

data class DescriptorRead(val desc: BluetoothGattDescriptor): BleOperationType()
data class DescriptorWrite(val desc: BluetoothGattDescriptor): BleOperationType()
