package com.example.dayowl.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UdpPacket(
    val data: ByteArray,
    val address: String,
    val port: Int
)

class UdpReceiver {
    private var socket: DatagramSocket? = null

    fun init(port: Int) {
        socket = DatagramSocket(port)
    }

    suspend fun receivePacket(bufferSize: Int): UdpPacket? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            socket?.receive(packet)
            UdpPacket(
                data = packet.data.copyOfRange(0, packet.length),
                address = packet.address.hostAddress ?: "",
                port = packet.port
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun receive(bufferSize: Int): ByteArray? = receivePacket(bufferSize)?.data

    fun close() {
        socket?.close()
        socket = null
    }
}
