package com.example.dayowl.network

import com.example.dayowl.audio.AudioConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

data class UdpPacket(val data: ByteArray, val address: String, val port: Int)

/**
 * One socket that both sends and receives.
 *
 * Same-socket send+receive is what makes a request/reply exchange (JOIN_REQUEST -> JOIN_ACCEPT)
 * possible at all - the previous split UdpSender/UdpReceiver pair sent from an ephemeral socket
 * that nothing ever read, so the accept could never arrive.
 *
 * Blocking, not suspending: every caller runs on its own dedicated thread.
 * receiveInto/receivePacket are unblocked by [close], not by interrupt.
 */
class UdpEndpoint {

    private var socket: DatagramSocket? = null
    private val sendPacket = DatagramPacket(ByteArray(0), 0)
    private val recvPacket = DatagramPacket(ByteArray(0), 0)

    /** [port] 0 leaves the socket on an OS-assigned ephemeral port. */
    fun open(port: Int = 0) {
        close()
        socket = DatagramSocket(null).apply {
            reuseAddress = true // survives a rapid leave/rejoin without BindException
            runCatching { sendBufferSize = AudioConfig.SOCKET_BUFFER_BYTES }
            runCatching { receiveBufferSize = AudioConfig.SOCKET_BUFFER_BYTES }
            runCatching { trafficClass = AudioConfig.DSCP_EF } // WMM AC_VO priority on WiFi
            bind(InetSocketAddress(port))
        }
    }

    val isOpen: Boolean get() = socket?.isClosed == false

    /**
     * Sends [len] bytes of [buf]. Synchronized because a DatagramPacket must not be shared
     * across threads; uncontended in practice since each endpoint is sent on by one thread.
     */
    @Synchronized
    fun send(buf: ByteArray, len: Int, dest: InetSocketAddress) {
        val s = socket ?: return
        try {
            sendPacket.setData(buf, 0, len)
            sendPacket.socketAddress = dest
            s.send(sendPacket)
        } catch (_: Exception) {
            // Unreachable peers surface as silently-ignored ICMP on an unconnected socket.
            // Never connect() - it would turn one absent client into a failure for all of them.
        }
    }

    fun send(buf: ByteArray, len: Int, address: String, port: Int) =
        send(buf, len, InetSocketAddress(address, port))

    /** Blocks until a datagram arrives. Returns its length, or -1 once the socket is closed. */
    fun receiveInto(buf: ByteArray): Int {
        val s = socket ?: return -1
        return try {
            recvPacket.setData(buf, 0, buf.size)
            s.receive(recvPacket)
            recvPacket.length
        } catch (_: Exception) {
            -1
        }
    }

    /** Control-plane receive: allocates, but runs at a few packets per second. */
    fun receivePacket(bufferSize: Int): UdpPacket? {
        val s = socket ?: return null
        return try {
            val buf = ByteArray(bufferSize)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            UdpPacket(buf.copyOfRange(0, p.length), p.address.hostAddress ?: "", p.port)
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
