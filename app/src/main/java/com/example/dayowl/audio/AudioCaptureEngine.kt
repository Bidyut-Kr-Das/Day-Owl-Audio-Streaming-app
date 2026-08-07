package com.example.dayowl.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import com.example.dayowl.model.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

@SuppressLint("MissingPermission")
class AudioCaptureEngine(
    private val mediaProjection: MediaProjection,
    private val statsManager: com.example.dayowl.util.StatsManager? = null
) : AudioFrameProducer {

    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var sequenceNumber = 0L
    
    private val _audioFrames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 64)
    override val audioFrames: SharedFlow<AudioFrame> = _audioFrames.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Buffer pool to reduce GC pressure
    private val bufferPool = ArrayDeque<ByteArray>()
    private fun getBuffer(): ByteArray = synchronized(bufferPool) {
        bufferPool.removeFirstOrNull() ?: ByteArray(AudioConfig.FRAME_SIZE_BYTES)
    }
    private fun releaseBuffer(buffer: ByteArray) = synchronized(bufferPool) {
        if (bufferPool.size < 100) bufferPool.addLast(buffer)
    }

    override fun startProducer() {
        if (isRunning) return
        
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioConfig.AUDIO_FORMAT
        )
        // Log.d("AudioCaptureEngine", "minBufferSize: $minBufferSize")

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
            Log.e("AudioCaptureEngine", "Failed to build AudioRecord", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioCaptureEngine", "AudioRecord not initialized")
            return
        }

        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            // Log.e("AudioCaptureEngine", "AudioRecord failed to start recording")
            return
        }
        
        // Log.d("AudioCaptureEngine", "AudioRecord started successfully")
        isRunning = true

        scope.launch {
            val recordBuffer = ByteArray(AudioConfig.FRAME_SIZE_BYTES)
            while (isRunning) {
                val readStartTime = android.os.SystemClock.elapsedRealtimeNanos()
                val read = audioRecord?.read(recordBuffer, 0, recordBuffer.size) ?: 0
                val readEndTime = android.os.SystemClock.elapsedRealtimeNanos()
                
                if (read > 0) {
                    statsManager?.recordT1(readEndTime - readStartTime)
                    val frameData = getBuffer()
                    System.arraycopy(recordBuffer, 0, frameData, 0, read)
                    
                    val frame = AudioFrame(
                        sequenceNumber = sequenceNumber++,
                        timestamp = System.currentTimeMillis(),
                        data = if (read == recordBuffer.size) frameData else frameData.copyOfRange(0, read),
                        capturedAtNanos = readEndTime
                    )
                    _audioFrames.tryEmit(frame)
                    statsManager?.incrementCaptured()
                    
                    // We don't release the buffer here because it's being used by the flow subscribers.
                    // In a production app, we would use a more sophisticated reference counting system.
                    // For now, we'll let GC handle it or implement a release mechanism later.
                }
            }
        }
    }

    override fun stopProducer() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    // Helper to start the capture engine - maintained for backward compatibility during refactor
    fun start(sampleRate: Int, onAudioData: (ByteArray) -> Unit) {
        startProducer()
    }

    fun stop() {
        stopProducer()
    }
}
