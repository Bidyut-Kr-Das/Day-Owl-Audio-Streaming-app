package com.example.dayowl.network

import android.os.SystemClock
import android.util.Log
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.repository.ConnectionState
import com.example.dayowl.repository.SessionRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side control plane and the owner of [ConnectionState].
 *
 * Sends and receives on ONE socket, which is what makes the handshake work at all: the request used
 * to leave from an ephemeral socket nobody read, so JOIN_ACCEPT could never arrive.
 *
 * The retry loop doubles as the heartbeat - re-sending JOIN_REQUEST forever is how the host knows
 * we are still here, and the host's client map makes a repeat idempotent. The host replies
 * JOIN_ACCEPT to every one of them, so that same traffic is also our liveness signal for the host.
 * No extra message type, no extra timer.
 */
class SessionManager(private val sessionRepository: SessionRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Also the generation token. Cancellation is fire-and-forget and neither `receivePacket` nor
     * `send` is a suspension point, so a stale loop can wake up after a new join has started.
     * Every state publish is gated on the captured endpoint still being this one.
     */
    @Volatile
    private var endpoint: UdpEndpoint? = null

    /**
     * Opened here rather than in ClientService so the JOIN_REQUEST can carry its real port: the
     * host used to aim audio at a hardcoded 5001 that nothing was guaranteed to be listening on,
     * and a failed bind there left the UI saying CONNECTED over silence.
     */
    @Volatile
    private var audio: UdpEndpoint? = null

    /** The socket audio arrives on. ClientService hands it to AudioPlayer, which owns it. */
    val audioEndpoint: UdpEndpoint? get() = audio

    private val jobs = mutableListOf<Job>()

    fun joinSession(session: SessionInfo) {
        // Re-tapping a row we are already connected to would run the whole stop/reopen dance for
        // nothing. JoinScreen has two clickables that both land here, so guard at the funnel.
        if (sessionRepository.connectionState.value == ConnectionState.CONNECTED &&
            sessionRepository.activeSession.value == session
        ) return

        // Close the previous generation WITHOUT publishing IDLE - that would make ClientService
        // stop itself a moment before we ask it to serve the new session.
        closeGeneration(null)

        val ep = UdpEndpoint()
        val audioEp = UdpEndpoint()
        try {
            ep.open()
            audioEp.open()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open sockets", e)
            ep.close()
            audioEp.close()
            teardown(null)
            return
        }
        endpoint = ep
        audio = audioEp
        sessionRepository.setConnectionState(ConnectionState.CONNECTING)

        val lastAccept = AtomicLong(SystemClock.elapsedRealtime())

        jobs += scope.launch {
            while (isActive) {
                val packet = ep.receivePacket(1024)
                if (endpoint !== ep) return@launch
                if (packet == null) {
                    if (!ep.isOpen) break
                    continue
                }
                if (Control.decode(packet.data)?.type == PacketType.JOIN_ACCEPT) {
                    lastAccept.set(SystemClock.elapsedRealtime())
                    if (sessionRepository.connectionState.value != ConnectionState.CONNECTED) {
                        sessionRepository.setActiveSession(session)
                        sessionRepository.setConnectionState(ConnectionState.CONNECTED)
                        Log.i(TAG, "Join accepted by " + packet.address)
                    }
                }
            }
        }

        jobs += scope.launch {
            // The payload is the port we listen for audio on. The host used to ignore it entirely.
            val request = Control.encode(PacketType.JOIN_REQUEST, audioEp.localPort.toString())
            var attempts = 0

            while (isActive) {
                ep.send(request, request.size, session.ipAddress, session.port)
                if (endpoint !== ep) return@launch

                val connected = sessionRepository.connectionState.value == ConnectionState.CONNECTED
                if (connected) {
                    if (SystemClock.elapsedRealtime() - lastAccept.get() > AudioConfig.HOST_TIMEOUT_MS) {
                        Log.i(TAG, "Host stopped answering, tearing down")
                        teardown(ep)
                        return@launch
                    }
                } else if (++attempts >= JOIN_MAX_ATTEMPTS) {
                    Log.i(TAG, "Join timed out after " + attempts + " attempts")
                    teardown(ep)
                    return@launch
                }

                delay(if (connected) AudioConfig.HEARTBEAT_INTERVAL_MS else JOIN_RETRY_MS)
            }
        }
    }

    fun leaveSession() {
        val active = sessionRepository.activeSession.value
        val ep = endpoint
        if (ep == null || active == null) {
            teardown(null)
            return
        }

        // Publish immediately so the UI and ClientService react without waiting on the network.
        sessionRepository.setActiveSession(null)
        sessionRepository.setConnectionState(ConnectionState.IDLE)

        // The send must leave the main thread: DatagramSocket.send there throws
        // NetworkOnMainThreadException, which UdpEndpoint.send swallows - the LEAVE would look
        // sent and silently never go out.
        scope.launch {
            val bye = Control.encode(PacketType.LEAVE)
            ep.send(bye, bye.size, active.ipAddress, active.port)
            closeGeneration(ep)
        }
    }

    fun stop() = teardown(null)

    /** Cancels and closes [generation] only if it is still current. Publishes nothing. */
    @Synchronized
    private fun closeGeneration(generation: UdpEndpoint?): Boolean {
        if (generation != null && endpoint !== generation) return false
        jobs.forEach { it.cancel() }
        jobs.clear()
        endpoint?.close()
        endpoint = null
        // AudioPlayer closes this too once it has it; close is idempotent.
        audio?.close()
        audio = null
        return true
    }

    @Synchronized
    private fun teardown(generation: UdpEndpoint?) {
        if (!closeGeneration(generation)) return
        sessionRepository.setActiveSession(null)
        sessionRepository.setConnectionState(ConnectionState.IDLE)
    }

    private companion object {
        const val TAG = "SessionManager"
        const val JOIN_RETRY_MS = 500L
        const val JOIN_MAX_ATTEMPTS = 10 // ~5s before giving up
    }
}
