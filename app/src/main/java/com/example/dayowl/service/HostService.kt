package com.example.dayowl.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dayowl.MainActivity
import com.example.dayowl.R
import com.example.dayowl.audio.AudioCaptureEngine
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.datastore.DataStoreManager
import com.example.dayowl.network.*
import com.example.dayowl.repository.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import java.util.concurrent.ConcurrentHashMap

class HostService : Service() {

    private val discoveryManager: DiscoveryManager by inject()
    private val sessionRepository: SessionRepository by inject()
    private val dataStoreManager: DataStoreManager by inject()

    /** Bound to the control port; audio goes out on its own ephemeral socket. */
    private val control = UdpEndpoint()
    private val audio = UdpEndpoint()

    private var captureEngine: AudioCaptureEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** ip -> last time we heard from it. A client's repeated JOIN_REQUEST is its heartbeat. */
    private val lastSeen = ConcurrentHashMap<String, Long>()

    private var wifiLock: WifiManager.WifiLock? = null

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
                Handler(Looper.getMainLooper()).post { startBroadcasting(resultData) }
            }
        }

        return START_STICKY
    }

    private fun startBroadcasting(resultData: Intent) {
        val mpManager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, resultData) ?: return

        acquireWifiLock()

        try {
            control.open(AudioConfig.UDP_PORT_CONTROL)
            audio.open()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open sockets", e)
            stopSelf()
            return
        }

        val engine = AudioCaptureEngine(mediaProjection, audio) { stopSelf() }
        captureEngine = engine
        if (!engine.start()) {
            Log.e(TAG, "Capture failed to start")
            stopSelf()
            return
        }

        sessionRepository.setBroadcasting(true)
        startControlListener()
        startEvictionTicker()

        serviceScope.launch {
            val name = runCatching { dataStoreManager.usernameFlow.first() }.getOrDefault("Day Owl")
            discoveryManager.startAdvertising(name + " Session", AudioConfig.UDP_PORT_CONTROL)
        }
    }

    private fun startControlListener() {
        serviceScope.launch {
            while (isActive) {
                val packet = control.receivePacket(1024)
                if (packet == null) {
                    if (!control.isOpen) break
                    continue
                }
                try {
                    val data = packet.data
                    if (data.isEmpty()) continue

                    val jsonStr = if (data[0] == AudioConfig.PACKET_TYPE_CONTROL) {
                        String(data, 1, data.size - 1)
                    } else {
                        String(data)
                    }

                    when (Json.decodeFromString<ControlPacket>(jsonStr).type) {
                        PacketType.JOIN_REQUEST -> {
                            val fresh = lastSeen.put(packet.address, SystemClock.elapsedRealtime()) == null
                            if (fresh) {
                                captureEngine?.addClient(packet.address)
                                Log.i(TAG, "Client joined: " + packet.address)
                            }
                            // Reply to the port the request came FROM. Replying to a fixed control
                            // port sent the accept to a socket that nobody was ever reading.
                            val response = byteArrayOf(AudioConfig.PACKET_TYPE_CONTROL) +
                                Json.encodeToString(ControlPacket(PacketType.JOIN_ACCEPT)).toByteArray()
                            control.send(response, response.size, packet.address, packet.port)
                        }
                        PacketType.LEAVE -> dropClient(packet.address)
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Control packet error", e)
                }
            }
        }
    }

    /** A client that stops re-sending JOIN_REQUEST has gone; stop burning airtime on it. */
    private fun startEvictionTicker() {
        serviceScope.launch {
            while (isActive) {
                delay(2000)
                val now = SystemClock.elapsedRealtime()
                lastSeen.entries
                    .filter { now - it.value > AudioConfig.CLIENT_TIMEOUT_MS }
                    .forEach { dropClient(it.key) }
            }
        }
    }

    private fun dropClient(ip: String) {
        if (lastSeen.remove(ip) != null) {
            captureEngine?.removeClient(ip)
            Log.i(TAG, "Client dropped: " + ip)
        }
    }

    private fun acquireWifiLock() {
        // Only effective while foreground with the screen on, but that is exactly the hosting case,
        // and it removes the WiFi power-save polling that shows up as 100ms+ jitter spikes.
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = runCatching {
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "dayowl:host").apply { acquire() }
        }.getOrNull()
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stopAdvertising()
        serviceScope.cancel()
        captureEngine?.stop()
        captureEngine = null
        control.close()
        audio.close()
        lastSeen.clear()
        wifiLock?.runCatching { if (isHeld) release() }
        wifiLock = null
        sessionRepository.setBroadcasting(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Host Service Channel", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "HostService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "host_service_channel"
        const val ACTION_START_BROADCAST = "com.example.dayowl.START_BROADCAST"
        const val ACTION_STOP_BROADCAST = "com.example.dayowl.STOP_BROADCAST"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
