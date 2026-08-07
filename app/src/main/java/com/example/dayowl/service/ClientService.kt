package com.example.dayowl.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.dayowl.MainActivity
import com.example.dayowl.R
import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.audio.AudioPlayer
import com.example.dayowl.network.AudioPacketizer
import com.example.dayowl.network.UdpReceiver
import com.example.dayowl.util.StatsManager
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class ClientService : Service() {

    private val udpReceiver: UdpReceiver by inject()
    private val audioPlayer: AudioPlayer by inject()
    private val statsManager: StatsManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isPlaying = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_LISTEN) {
            startListening()
        }

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

        return START_STICKY
    }

    private fun startListening() {
        if (isPlaying) return
        isPlaying = true

        udpReceiver.init(AudioConfig.UDP_PORT_AUDIO)
        audioPlayer.start()

        serviceScope.launch {
            while (isActive && isPlaying) {
                // Buffer size should be larger than FRAME_SIZE_BYTES + HEADER
                val data = udpReceiver.receive(4096)
                if (data != null) {
                    try {
                        val frame = AudioPacketizer.depacketize(data)
                        if (frame != null) {
                            statsManager.recordT4(android.os.SystemClock.elapsedRealtimeNanos() - frame.receivedAtNanos)
                            statsManager.incrementReceived(frame.sequenceNumber)
                            audioPlayer.consumeFrame(frame)
                            statsManager.updateBitrate(data.size.toLong())
                        }
                    } catch (e: Exception) {
                        Log.e("ClientService", "Playback failed", e)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isPlaying = false
        udpReceiver.close()
        audioPlayer.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Client Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "client_service_channel"
        const val ACTION_START_LISTEN = "com.example.dayowl.START_LISTEN"
    }
}
