package com.improv.wifi

enum class ErrorState(val value: UByte) {
    NO_ERROR(0.toUByte()),
    INVALID_RPC_PACKET(1.toUByte()),
    UNKNOWN_COMMAND(2.toUByte()),
    UNABLE_TO_CONNECT(3.toUByte()),
    NOT_AUTHORIZED(4.toUByte()),
    UNKNOWN(255.toUByte())
}