package com.example.dayowl.audio

import android.util.Log
import com.example.dayowl.model.AudioFrame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

class JitterBuffer(
    private val statsManager: com.example.dayowl.util.StatsManager? = null
) {
    private val frames = ConcurrentHashMap<Long, AudioFrame>()
    
    // Adaptive target delay
    private var targetDelayFrames = AudioConfig.JITTER_BUFFER_MIN_DELAY.toDouble()
    
    // Jitter estimation
    private var lastArrivalNanos = 0L
    private var averageJitterNanos = 0.0

    fun addFrame(frame: AudioFrame) {
        val seq = frame.sequenceNumber
        
        // Update jitter estimation
        val now = frame.receivedAtNanos
        if (lastArrivalNanos != 0L) {
            val delta = now - lastArrivalNanos
            val jitter = Math.abs(delta - (AudioConfig.FRAME_DURATION_MS * 1_000_000L))
            
            // Exponential moving average for jitter
            averageJitterNanos = averageJitterNanos * 0.9 + jitter * 0.1
            statsManager?.updateJitter(averageJitterNanos / 1_000_000.0)
            
            // Adjust target delay based on jitter
            // Target delay = average jitter + safety margin (converted to frames)
            val jitterInFrames = averageJitterNanos / (AudioConfig.FRAME_DURATION_MS * 1_000_000.0)
            val newTarget = min(
                AudioConfig.JITTER_BUFFER_MAX_DELAY.toDouble(),
                max(AudioConfig.JITTER_BUFFER_MIN_DELAY.toDouble(), jitterInFrames + 1.5)
            )
            
            // Slow adaptation
            targetDelayFrames = targetDelayFrames * (1.0 - AudioConfig.JITTER_BUFFER_TARGET_ADAPT_RATE) + 
                               newTarget * AudioConfig.JITTER_BUFFER_TARGET_ADAPT_RATE
        }
        lastArrivalNanos = now

        frames[seq] = frame
        
        // Bound growth. Drop oldest if exceeding bufferSize.
        if (frames.size > AudioConfig.JITTER_BUFFER_SIZE) {
            val oldestSeq = frames.keys().asSequence().minOrNull()
            if (oldestSeq != null) {
                frames.remove(oldestSeq)
            }
        }
    }

    fun getFrame(sequenceNumber: Long): AudioFrame? {
        return frames.remove(sequenceNumber)
    }

    fun hasFrame(sequenceNumber: Long): Boolean {
        return frames.containsKey(sequenceNumber)
    }

    fun peekHighestSequence(): Long? {
        return frames.keys().asSequence().maxOrNull()
    }

    fun peekLowestSequence(): Long? {
        return frames.keys().asSequence().minOrNull()
    }

    fun dropOlderThan(sequenceNumber: Long) {
        val keys = frames.keys().asSequence()
        for (k in keys) {
            if (k < sequenceNumber) {
                frames.remove(k)
            }
        }
    }

    fun clear() {
        frames.clear()
        lastArrivalNanos = 0L
        averageJitterNanos = 0.0
        targetDelayFrames = AudioConfig.JITTER_BUFFER_MIN_DELAY.toDouble()
    }

    val size: Int get() = frames.size
    val targetDelay: Int get() = targetDelayFrames.toInt()
}
