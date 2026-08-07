package com.example.dayowl.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dayowl.MainActivity
import com.example.dayowl.R
import com.example.dayowl.audio.AudioCaptureEngine
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.network.*
import com.example.dayowl.repository.SessionRepository
import com.example.dayowl.util.StatsManager
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class HostService : Service() {

    private val discoveryManager: DiscoveryManager by inject()
    private val udpSender: UdpSender by inject()
    private val udpReceiver: UdpReceiver by inject()
    private val pacedSender: PacedSender by inject()
    private val statsManager: StatsManager by inject()
    private val sessionRepository: SessionRepository by inject()

    private var captureEngine: AudioCaptureEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = mutableSetOf<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_BROADCAST) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Day Owl Host")
            .setContentText("Broadcasting audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent?.action == ACTION_START_BROADCAST) {
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            
            if (resultData != null) {
                Handler(Looper.getMainLooper()).post {
                    startBroadcasting(resultData)
                }
            }
        }

        return START_STICKY
    }

    private fun startBroadcasting(resultData: Intent) {
        val mpManager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, resultData) ?: return

        discoveryManager.startAdvertising("Bidyut's Session", AudioConfig.UDP_PORT_CONTROL)
        udpSender.init()
        udpReceiver.init(AudioConfig.UDP_PORT_CONTROL)
        sessionRepository.setBroadcasting(true)

        startControlListener()

        pacedSender.start()

        captureEngine = AudioCaptureEngine(mediaProjection, statsManager)
        captureEngine?.startProducer()
        
        serviceScope.launch {
            captureEngine?.audioFrames?.collect { frame ->
                pacedSender.send(frame)
                statsManager.updateBitrate(frame.data.size.toLong())
                statsManager.incrementSent()
            }
        }
    }

    private fun startControlListener() {
        serviceScope.launch {
            while (isActive) {
                val packet = udpReceiver.receivePacket(1024)
                if (packet != null) {
                    try {
                        val data = packet.data
                        if (data.isEmpty()) continue
                        
                        val jsonStr = if (data[0] == AudioConfig.PACKET_TYPE_CONTROL) {
                            String(data.copyOfRange(1, data.size))
                        } else {
                            String(data)
                        }
                        
                        val control = Json.decodeFromString<ControlPacket>(jsonStr)
                        when (control.type) {
                            PacketType.JOIN_REQUEST -> {
                                synchronized(clients) { clients.add(packet.address) }
                                pacedSender.addClient(packet.address)
                                // Log.d("HostService", "Client joined: ${packet.address}")
                                val response = ControlPacket(PacketType.JOIN_ACCEPT)
                                val responseData = (byteArrayOf(AudioConfig.PACKET_TYPE_CONTROL) + Json.encodeToString(response).toByteArray())
                                udpSender.send(responseData, packet.address, AudioConfig.UDP_PORT_CONTROL)
                            }
                            PacketType.LEAVE -> {
                                synchronized(clients) { clients.remove(packet.address) }
                                pacedSender.removeClient(packet.address)
                                // Log.d("HostService", "Client left: ${packet.address}")
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e("HostService", "Control packet error", e)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureEngine?.stopProducer()
        pacedSender.stop()
        discoveryManager.stopAdvertising()
        udpSender.close()
        udpReceiver.close()
        sessionRepository.setBroadcasting(false)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Host Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "host_service_channel"
        const val ACTION_START_BROADCAST = "com.example.dayowl.START_BROADCAST"
        const val ACTION_STOP_BROADCAST = "com.example.dayowl.STOP_BROADCAST"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
