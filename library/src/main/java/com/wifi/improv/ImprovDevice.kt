package com.wifi.improv

data class ImprovDevice(
    val name: String?,
    val address: String
) {
    override fun equals(other: Any?): Boolean {
        return if (other is ImprovDevice) address == other.address else false
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}
