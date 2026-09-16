package com.example.dayowl.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dayowl.MainActivity
import com.example.dayowl.R
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.audio.AudioPlayer
import com.example.dayowl.network.UdpEndpoint
import com.example.dayowl.repository.ConnectionState
import com.example.dayowl.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ClientService : Service() {

    private val audioPlayer: AudioPlayer by inject()
    private val sessionRepository: SessionRepository by inject()

    // A Service is not a LifecycleOwner, so it needs its own scope.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wifiLock: WifiManager.WifiLock? = null
    private var isPlaying = false

    @Volatile
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        // Subscribed here, not in onStartCommand: a second START_LISTEN reaches this same instance
        // and would double-subscribe.
        serviceScope.launch {
            sessionRepository.connectionState.collect { state ->
                // lastStartId == 0 means onStartCommand has not run yet; the re-check at the end
                // of it covers that window, so ignoring IDLE here is safe and avoids stopSelf(0).
                if (state == ConnectionState.IDLE && lastStartId != 0) {
                    Log.i(TAG, "Connection is IDLE, stopping")
                    // stopSelf(id), never the no-arg form: that one destroys the service even when
                    // a newer start command is already queued, which is exactly the rejoin kill.
                    stopSelf(lastStartId)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId

        // startForeground FIRST. Binding the socket used to run ahead of it, so a BindException on
        // a rapid leave/rejoin killed the service before it ever went foreground, which the system
        // reports as ForegroundServiceDidNotStartInTimeException.
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Day Owl Client")
            .setContentText("Receiving audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // The session may already have been torn down between joinSession and getting here.
        if (sessionRepository.connectionState.value == ConnectionState.IDLE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START_LISTEN) startListening()

        // NOT sticky: a sticky restart delivers a null intent, which skips startListening() and
        // leaves a foreground notification attached to nothing.
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (isPlaying) return

        acquireWifiLock()

        val endpoint = UdpEndpoint()
        try {
            endpoint.open(AudioConfig.UDP_PORT_AUDIO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind audio socket", e)
            stopSelf(lastStartId)
            return
        }

        // AudioPlayer owns the receive loop and the endpoint from here on.
        if (!audioPlayer.start(endpoint)) {
            endpoint.close()
            stopSelf(lastStartId)
            return
        }
        isPlaying = true
    }

    private fun acquireWifiLock() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = runCatching {
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "dayowl:client").apply { acquire() }
        }.getOrNull()
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        serviceScope.cancel()
        audioPlayer.stop() // closes the endpoint, which is what unblocks the receive loop
        wifiLock?.runCatching { if (isHeld) release() }
        wifiLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Client Service Channel", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ClientService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "client_service_channel"
        const val ACTION_START_LISTEN = "com.example.dayowl.START_LISTEN"
    }
}
