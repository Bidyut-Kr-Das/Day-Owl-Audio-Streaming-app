package com.example.dayowl.network

import kotlinx.serialization.Serializable

@Serializable
enum class PacketType {
    HELLO,
    JOIN_REQUEST,
    JOIN_ACCEPT,
    LEAVE,
    HEARTBEAT,
    HOST_SHUTDOWN
}

@Serializable
data class ControlPacket(
    val type: PacketType,
    val data: String? = null
)
