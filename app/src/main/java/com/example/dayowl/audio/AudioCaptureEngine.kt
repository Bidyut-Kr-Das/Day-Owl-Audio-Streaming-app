package com.example.dayowl.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import com.example.dayowl.network.AudioPacketizer
import com.example.dayowl.network.UdpEndpoint
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * The entire host send path: capture -> packetize -> fan out, on one thread, with no queue.
 *
 * AudioRecord.read() is the pacer. It returns exactly once per frame of real audio, off the
 * audio HAL clock. Nothing else may pace - the old PacedSender re-paced these frames off
 * System.currentTimeMillis() through an unbounded Channel, so the drift between the two clocks
 * accumulated in the queue and latency grew without bound for as long as a session ran.
 */
@SuppressLint("MissingPermission")
class AudioCaptureEngine(
    private val mediaProjection: MediaProjection,
    private val endpoint: UdpEndpoint,
    /** Called when the capture loop dies on its own (dead AudioRecord, revoked projection). */
    private val onDied: () -> Unit = {}
) {
    /** "ip:port" -> where to send. Keyed on both, so two joiners behind one IP stay separate. */
    private val clients = ConcurrentHashMap<String, InetSocketAddress>()

    /** Snapshot of [clients], rebuilt only on join/leave so the send loop allocates no iterator. */
    @Volatile private var dests: Array<InetSocketAddress> = emptyArray()

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /** [port] is the one the joiner told us it bound, not an assumed constant. */
    fun addClient(ip: String, port: Int) {
        // Resolved once here, never on the send path.
        runCatching { InetSocketAddress(ip, port) }
            .onSuccess { clients[clientKey(ip, port)] = it; dests = clients.values.toTypedArray() }
    }

    fun removeClient(key: String) {
        if (clients.remove(key) != null) dests = clients.values.toTypedArray()
    }

    fun start(): Boolean {
        if (running) return true

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // Playback capture routes through the mixer, so there is no fast capture path and
        // setPerformanceMode would be silently ignored. minBufferSize at 48k mono is already
        // several frames deep at 10ms - leave it alone.
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioConfig.AUDIO_FORMAT
        )

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioConfig.AUDIO_FORMAT)
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(minBufferSize)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build AudioRecord", e)
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            release()
            return false
        }

        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "AudioRecord failed to start recording")
            release()
            return false
        }

        running = true
        thread = Thread(::captureLoop, "dayowl-capture").apply { start() }
        return true
    }

    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val packet = ByteArray(AudioPacketizer.PACKET_SIZE)
        var seq = 0L
        var frames = 0
        var lastLog = System.currentTimeMillis()

        while (running) {
            val n = audioRecord?.read(packet, AudioPacketizer.HEADER_SIZE, AudioConfig.FRAME_SIZE_BYTES) ?: -1
            if (n <= 0) {
                if (n < 0) {
                    // ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT. Do not spin, and do not fail
                    // silently: the host would keep accepting joiners with no audio to give them.
                    Log.e(TAG, "AudioRecord.read failed with " + n + ", capture is over")
                    if (running) { running = false; onDied() }
                    break
                }
                continue
            }

            AudioPacketizer.writeHeader(packet, seq++, n)

            val targets = dests
            val len = AudioPacketizer.HEADER_SIZE + n
            for (i in targets.indices) endpoint.send(packet, len, targets[i])

            frames++
            val now = System.currentTimeMillis()
            if (now - lastLog >= 1000) {
                // Must read ~100 at 10ms frames. Sustained lower means the send loop is overrunning.
                Log.i(TAG, "captureFps=$frames clients=${targets.size} seq=$seq")
                frames = 0
                lastLog = now
            }
        }
    }

    fun stop() {
        running = false
        // AudioRecord.read is a native blocking call and ignores interrupt; stop() releases it.
        audioRecord?.runCatching { stop() }
        thread?.join(500)
        thread = null
        release()
        clients.clear()
        dests = emptyArray()
    }

    private fun release() {
        audioRecord?.runCatching { release() }
        audioRecord = null
    }

    companion object {
        private const val TAG = "AudioCaptureEngine"

        /** The one definition of a client's identity; HostService ages the same key out. */
        fun clientKey(ip: String, port: Int) = ip + ":" + port
    }
}
