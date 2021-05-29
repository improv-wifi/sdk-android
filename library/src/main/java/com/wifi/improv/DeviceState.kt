package com.wifi.improv

enum class DeviceState(val value: UByte) {
    AUTHORIZATION_REQUIRED(1.toUByte()),
    AUTHORIZED(2.toUByte()),
    PROVISIONING(3.toUByte()),
    PROVISIONED(4.toUByte())
}
