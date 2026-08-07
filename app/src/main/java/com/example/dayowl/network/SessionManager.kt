package com.example.dayowl.network

import android.util.Log
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionManager(
    private val udpSender: UdpSender,
    private val udpReceiver: UdpReceiver,
    private val sessionRepository: SessionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createSession(name: String, port: Int) {
        udpReceiver.init(port)
        udpSender.init()
        // Advertise via Nsd is separate, but we track state here
    }

    fun joinSession(session: SessionInfo) {
        // Log.d("SessionManager", "joinSession: Sending JOIN_REQUEST to ${session.ipAddress}:${session.port}")
        scope.launch {
            val packet = ControlPacket(PacketType.JOIN_REQUEST, Json.encodeToString(session))
            val data = Json.encodeToString(packet).toByteArray()
            val prefixedData = byteArrayOf(AudioConfig.PACKET_TYPE_CONTROL) + data
            udpSender.send(prefixedData, session.ipAddress, session.port)
            // Log.d("SessionManager", "joinSession: Packet sent")
            // Wait for JOIN_ACCEPT
        }
    }

    fun leaveSession() {
        val active = sessionRepository.activeSession.value ?: return
        scope.launch {
            val packet = ControlPacket(PacketType.LEAVE)
            val data = Json.encodeToString(packet).toByteArray()
            val prefixedData = byteArrayOf(AudioConfig.PACKET_TYPE_CONTROL) + data
            udpSender.send(prefixedData, active.ipAddress, active.port)
            sessionRepository.setActiveSession(null)
        }
    }

    fun stop() {
        udpSender.close()
        udpReceiver.close()
    }
}
