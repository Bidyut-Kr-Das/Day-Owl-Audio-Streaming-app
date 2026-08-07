package com.example.dayowl.network

import android.util.Log
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.AudioFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

class PacedSender(
    private val udpSender: UdpSender,
    private val statsManager: com.example.dayowl.util.StatsManager? = null
) {
    private val frameQueue = Channel<AudioFrame>(Channel.UNLIMITED)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val targetClients = ConcurrentHashMap.newKeySet<String>()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        
        job = scope.launch {
            var nextSendTime = System.currentTimeMillis()
            
            for (frame in frameQueue) {
                val data = AudioPacketizer.packetize(frame)
                
                val clients = targetClients.toList()
                clients.forEach { ip ->
                    udpSender.send(data, ip, AudioConfig.UDP_PORT_AUDIO)
                }
                
                statsManager?.recordT2(android.os.SystemClock.elapsedRealtimeNanos() - frame.capturedAtNanos)
                statsManager?.incrementSent()
                
                nextSendTime += AudioConfig.FRAME_DURATION_MS
                val delayTime = nextSendTime - System.currentTimeMillis()
                if (delayTime > 0) {
                    delay(delayTime)
                } else if (delayTime < -AudioConfig.FRAME_DURATION_MS) {
                    // We are falling behind, reset timing
                    nextSendTime = System.currentTimeMillis()
                }
            }
        }
    }

    fun addClient(ip: String) {
        targetClients.add(ip)
    }

    fun removeClient(ip: String) {
        targetClients.remove(ip)
    }

    fun send(frame: AudioFrame) {
        frameQueue.trySend(frame)
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        targetClients.clear()
    }
}
