package com.example.dayowl.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UdpSender {
    private var socket: DatagramSocket? = null

    fun init() {
        if (socket == null || socket!!.isClosed) {
            socket = DatagramSocket()
        }
    }

    suspend fun send(data: ByteArray, address: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            if (socket == null || socket!!.isClosed) {
                socket = DatagramSocket()
            }
            val inetAddress = InetAddress.getByName(address)
            val packet = DatagramPacket(data, data.size, inetAddress, port)
            socket?.send(packet)
        } catch (e: Exception) {
            // Log.e("UdpSender", "Error sending packet to $address:$port", e)
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
