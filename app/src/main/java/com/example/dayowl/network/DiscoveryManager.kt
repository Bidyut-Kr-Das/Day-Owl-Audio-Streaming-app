package com.example.dayowl.network

import android.os.SystemClock
import android.util.Log
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Discovery by UDP broadcast on the control port, not mDNS.
 *
 * NSD advertised whatever address the responder felt like - on a phone hotspot the host has more
 * than one interface and the resolved address was regularly one nothing could reach. A HOST_INFO
 * reply cannot lie: the address is the datagram's source, so it is by construction the host's
 * address on the interface that carried the probe.
 *
 * The probe repeats, so it doubles as the liveness check: an entry that stops answering ages out.
 */
class DiscoveryManager(private val sessionRepository: SessionRepository) {

    private var scope: CoroutineScope? = null

    @Volatile
    private var endpoint: UdpEndpoint? = null

    /** ip -> (what to show, when we last heard from it). */
    private val seen = LinkedHashMap<String, Pair<SessionInfo, Long>>()

    fun startDiscovery() {
        stopDiscovery()

        val ep = UdpEndpoint()
        try {
            ep.open(broadcast = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open discovery socket", e)
            return
        }
        endpoint = ep

        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s

        s.launch {
            while (isActive) {
                val packet = ep.receivePacket(512)
                if (endpoint !== ep) return@launch
                if (packet == null) {
                    if (!ep.isOpen) break
                    continue
                }
                val control = Control.decode(packet.data) ?: continue
                if (control.type != PacketType.HOST_INFO) continue

                val name = control.data?.takeIf { it.isNotBlank() } ?: packet.address
                record(packet.address, name, packet.port)
            }
        }

        s.launch {
            val probe = Control.encode(PacketType.DISCOVER)
            while (isActive) {
                ep.send(probe, probe.size, BROADCAST_ADDRESS, AudioConfig.UDP_PORT_CONTROL)
                if (endpoint !== ep) return@launch
                expire()
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    fun stopDiscovery() {
        scope?.cancel()
        scope = null
        endpoint?.close()
        endpoint = null
        synchronized(seen) { seen.clear() }
        sessionRepository.updateDiscoveredSessions(emptyList())
    }

    private fun record(ip: String, name: String, port: Int) {
        val session = SessionInfo(hostName = name, ipAddress = ip, port = port, sessionName = name)
        synchronized(seen) { seen[ip] = session to SystemClock.elapsedRealtime() }
        publish()
    }

    /** A host that stops answering disappears; the old NSD list never refreshed at all. */
    private fun expire() {
        val now = SystemClock.elapsedRealtime()
        val changed = synchronized(seen) {
            seen.entries.removeAll { now - it.value.second > HOST_TTL_MS }
        }
        if (changed) publish()
    }

    private fun publish() {
        val sessions = synchronized(seen) { seen.values.map { it.first } }
        sessionRepository.updateDiscoveredSessions(sessions)
    }

    private companion object {
        const val TAG = "DiscoveryManager"
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val PROBE_INTERVAL_MS = 1000L
        const val HOST_TTL_MS = 5000L
    }
}
