package com.example.dayowl.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioFrame(
    val sequenceNumber: Long,
    val timestamp: Long,
    val data: ByteArray,
    val capturedAtNanos: Long = 0L,
    val receivedAtNanos: Long = 0L
)

@Serializable
data class StreamStats(
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetLossCount: Long = 0,
    val outOfOrderCount: Long = 0,
    val jitterMs: Double = 0.0,
    val averageLatencyMs: Long = 0,
    val queueDepth: Int = 0,
    val bitrateBps: Long = 0
)

@Serializable
data class SessionInfo(
    val hostName: String,
    val ipAddress: String,
    val port: Int,
    val sessionName: String
)
