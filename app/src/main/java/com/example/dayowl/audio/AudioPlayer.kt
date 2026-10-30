package com.example.dayowl.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.example.dayowl.network.AudioPacketizer
import com.example.dayowl.network.UdpEndpoint
import org.concentus.OpusDecoder
import org.concentus.OpusException

/**
 * The entire client receive path: two dedicated threads either side of a [FrameRing] of Opus
 * frames. Decoding happens on the play thread, so the decoder needs no locking.
 *
 * AudioTrack.write(WRITE_BLOCKING) is the clock. It blocks until the data is queued, which paces
 * the play-out loop at exactly the DAC rate for free. The old PlaybackScheduler computed its own
 * play-out timestamps and the loop spun on delay(2) whenever the scheduler said "not yet", which
 * drained the track buffer and produced the dropouts.
 */
class AudioPlayer {

    private val ring = FrameRing()
    private var audioTrack: AudioTrack? = null
    private var endpoint: UdpEndpoint? = null
    private var rxThread: Thread? = null
    private var playThread: Thread? = null
    @Volatile private var running = false

    /** When the last audio datagram landed. Zero packets is the failure this had no words for. */
    @Volatile private var lastRxMs = 0L

    /** Takes ownership of [endpoint]: [stop] closes it to unblock the receive loop. */
    fun start(endpoint: UdpEndpoint): Boolean {
        // Restart, not no-op: ClientService can be recreated before the old one is destroyed,
        // and returning early there would keep the dead endpoint and leak the new socket.
        if (running) stop()
        this.endpoint = endpoint
        ring.reset()
        lastRxMs = System.currentTimeMillis()
        if (!buildTrack()) return false
        running = true
        rxThread = Thread(::rxLoop, "dayowl-rx").apply { start() }
        playThread = Thread(::playLoop, "dayowl-play").apply { start() }
        return true
    }

    private fun buildTrack(): Boolean {
        val min = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioConfig.AUDIO_FORMAT
        )
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioConfig.AUDIO_FORMAT)
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(min, 3 * AudioConfig.FRAME_SIZE_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Honored only when the rate matches the device's native output rate; silently
                // falls back to the mixer otherwise, which costs ~20ms. Not worth a resampler.
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build AudioTrack", e)
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized")
            track.runCatching { release() }
            return false
        }
        track.play()
        audioTrack = track
        Log.i(TAG, "AudioTrack buf=${maxOf(min, 3 * AudioConfig.FRAME_SIZE_BYTES)} perfMode=${track.performanceMode}")
        return true
    }

    private fun rxLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val ep = endpoint ?: return
        val buf = ByteArray(AudioPacketizer.MAX_PACKET_SIZE)
        while (running) {
            val n = ep.receiveInto(buf)
            if (n < 0) break // socket closed
            val cur = AudioPacketizer.payloadLen(buf, n)
            if (cur <= 0) continue
            lastRxMs = System.currentTimeMillis()
            val seq = AudioPacketizer.seqOf(buf)
            // The redundant copy first, so put() below leaves `latest` on this packet's own frame.
            val prev = AudioPacketizer.prevLen(buf)
            if (prev > 0) ring.backfill(seq - 1, buf, AudioPacketizer.HEADER_SIZE + cur, prev)
            ring.put(seq, buf, AudioPacketizer.HEADER_SIZE, cur)
        }
    }

    private fun playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val decoder = try {
            OpusDecoder(AudioConfig.SAMPLE_RATE, AudioConfig.CHANNELS)
        } catch (e: OpusException) {
            Log.e(TAG, "Failed to create Opus decoder", e)
            return
        }
        val pcm = ShortArray(AudioConfig.SAMPLES_PER_FRAME)
        var lastLog = System.currentTimeMillis()
        var logNow = false

        while (running) {
            // Logged before the ring is consulted, so a session receiving nothing still reports.
            // Silence used to look exactly like health: the log below only ran once a frame played.
            val tick = System.currentTimeMillis()
            if (tick - lastLog >= 1000) {
                val silentMs = tick - lastRxMs
                if (silentMs >= NO_AUDIO_WARN_MS) {
                    Log.w(TAG, "no audio for ${silentMs}ms - the host is not reaching this socket")
                }
                lastLog = tick
                logNow = true
            }

            val frame = ring.next()
            if (frame == null) {
                logNow = false
                // Still priming. The only sleep in this loop - once primed, the blocking write paces it.
                try { Thread.sleep(2) } catch (_: InterruptedException) { break }
                continue
            }

            // A null packet asks Opus for its own loss concealment, which fades smoothly instead of
            // the buzz the old repeat-last-frame produced.
            val samples = try {
                if (frame === FrameRing.LOST) {
                    decoder.decode(null, 0, 0, pcm, 0, AudioConfig.SAMPLES_PER_FRAME, false)
                } else {
                    decoder.decode(frame, 0, ring.frameLen, pcm, 0, AudioConfig.SAMPLES_PER_FRAME, false)
                }
            } catch (e: OpusException) {
                Log.w(TAG, "Opus decode failed", e)
                pcm.fill(0)
                AudioConfig.SAMPLES_PER_FRAME
            }

            val track = audioTrack ?: break
            val n = track.write(pcm, 0, samples, AudioTrack.WRITE_BLOCKING)
            if (n <= 0) {
                // ERROR_DEAD_OBJECT / ERROR_INVALID_OPERATION. Without this branch the loop,
                // having no sleep, would spin at 100% CPU forever.
                Log.w(TAG, "AudioTrack.write returned $n, rebuilding track")
                releaseTrack()
                if (!running || !buildTrack()) break
                continue
            }

            if (logNow) {
                logNow = false
                Log.i(
                    TAG,
                    "depth=${ring.depth} target=${ring.target} concealed=${ring.concealedCount} " +
                        "dropped=${ring.droppedCount} resync=${ring.resyncCount} " +
                        "underruns=${track.underrunCount} seq=${ring.playoutSeq}"
                )
            }
        }
    }

    fun stop() {
        running = false
        // Neither AudioTrack.write nor DatagramSocket.receive is interruptible.
        // pause()+flush() releases a pending blocking write; closing the socket releases the receive.
        audioTrack?.runCatching { pause(); flush() }
        endpoint?.close()
        rxThread?.join(500)
        playThread?.join(500)
        rxThread = null
        playThread = null
        releaseTrack()
        endpoint = null
        ring.reset()
    }

    private fun releaseTrack() {
        audioTrack?.runCatching { stop(); release() }
        audioTrack = null
    }

    private companion object {
        const val TAG = "AudioPlayer"
        const val NO_AUDIO_WARN_MS = 5000L
    }
}
