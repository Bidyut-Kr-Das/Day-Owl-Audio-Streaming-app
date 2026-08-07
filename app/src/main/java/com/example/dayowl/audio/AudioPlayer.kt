package com.example.dayowl.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.dayowl.model.AudioFrame
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlayer(
    private val statsManager: com.example.dayowl.util.StatsManager? = null
) : AudioFrameConsumer {

    private var audioTrack: AudioTrack? = null
    private val jitterBuffer = JitterBuffer(statsManager)
    private val scheduler = PlaybackScheduler(jitterBuffer, statsManager)
    private val isRunning = AtomicBoolean(false)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null

    fun start() {
        synchronized(this) {
            if (isRunning.get()) {
                // Log.w("AudioPlayer", "AudioPlayer already running")
                return
            }
            isRunning.set(true)
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioConfig.AUDIO_FORMAT
        )

        audioTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        playbackJob = scope.launch {
            // Log.i("AudioPlayer", "Playback Loop Started. Thread ID: ${Thread.currentThread().id}")
            var lastReport = System.currentTimeMillis()
            
            try {
                while (isRunning.get()) {
                    // Log.d("AudioPlayer", "PLAY Sequence=${frame.sequenceNumber} QueueSize=${jitterBuffer.size} WriteResult=$result WriteDuration=${duration}ms")
                    
                    // Pull from scheduler instead of jitter buffer directly
                    val frame = scheduler.getNextFrameToPlay()
                    
                    if (frame != null) {
                        val startTime = android.os.SystemClock.elapsedRealtimeNanos()
                        val result = audioTrack?.write(frame.data, 0, frame.data.size, AudioTrack.WRITE_BLOCKING) ?: -1
                        val endTime = android.os.SystemClock.elapsedRealtimeNanos()
                        statsManager?.recordT6(endTime - startTime)
                        
                        // Update stats
                        statsManager?.incrementPlayed(frame.sequenceNumber)
                        statsManager?.updateQueueDepth(jitterBuffer.size)
                        
                        // If write was extremely fast, we might need to yield
                        if (endTime - startTime < 1_000_000L) yield()
                    } else {
                        // Scheduler says wait: use a very small delay for precision
                        delay(2)
                    }
                    
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 1000) {
                        val report = statsManager?.getDiagnosticReport()
                        if (report != null) {
                            Log.i("AudioPlayer", report)
                        }
                        lastReport = now
                    }
                }
            } finally {
                // Log.i("AudioPlayer", "Playback Loop Stopped. Thread ID: ${Thread.currentThread().id}")
            }
        }
    }

    override fun consumeFrame(frame: AudioFrame) {
        jitterBuffer.addFrame(frame)
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        
        runBlocking {
            playbackJob?.cancelAndJoin()
        }
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        jitterBuffer.clear()
        scheduler.reset()
    }

    // Maintain for backward compatibility during refactor
    fun start(sampleRate: Int) {
        start()
    }

    fun write(data: ByteArray) {
    }
}
