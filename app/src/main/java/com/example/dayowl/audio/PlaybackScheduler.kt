package com.example.dayowl.audio

import android.os.SystemClock
import android.util.Log
import com.example.dayowl.model.AudioFrame
import com.example.dayowl.util.StatsManager

class PlaybackScheduler(
    private val jitterBuffer: JitterBuffer,
    private val statsManager: StatsManager? = null
) {
    private var nextExpectedSeq = -1L
    private var startSeq = -1L
    private var startTimeNanos = 0L
    private var lastFrame: AudioFrame? = null
    
    private var isBuffering = true
    private var consecutiveMissingCount = 0
    
    // Clock drift compensation
    private var driftCompensationNanos = 0L

    fun getNextFrameToPlay(): AudioFrame? {
        val now = SystemClock.elapsedRealtimeNanos()
        
        checkLatency(now)
        
        if (isBuffering) {
            val target = jitterBuffer.targetDelay
            if (jitterBuffer.size >= target) {
                val lowestSeq = jitterBuffer.peekLowestSequence()
                if (lowestSeq != null) {
                    val frame = jitterBuffer.getFrame(lowestSeq)!!
                    startSeq = lowestSeq
                    nextExpectedSeq = lowestSeq + 1
                    startTimeNanos = now
                    lastFrame = frame
                    isBuffering = false
                    consecutiveMissingCount = 0
                    statsManager?.updateExpectedSeq(nextExpectedSeq)
                    // Log.i("PlaybackScheduler", "Buffering complete. Target delay: $target. Starting at Seq: $startSeq")
                    return frame
                }
            }
            return null
        }

        // Calculate when the NEXT frame should play
        val seqOffset = nextExpectedSeq - startSeq
        val targetPlayTimeNanos = startTimeNanos + (seqOffset * AudioConfig.FRAME_DURATION_MS * 1_000_000L) + driftCompensationNanos
        
        // Wait one extra frame duration before declaring loss
        val lossThresholdNanos = targetPlayTimeNanos + (AudioConfig.FRAME_DURATION_MS * 1_000_000L)

        if (now < targetPlayTimeNanos) {
            // Not time yet
            return null
        }

        // Try to get frame from buffer
        val frame = jitterBuffer.getFrame(nextExpectedSeq)
        
        if (frame != null) {
            // Packet found!
            statsManager?.recordT5(now - frame.receivedAtNanos)
            adjustDrift(frame, targetPlayTimeNanos, now)
            
            lastFrame = frame
            nextExpectedSeq++
            consecutiveMissingCount = 0
            statsManager?.updateExpectedSeq(nextExpectedSeq)
            
            // Latency measurement: relative to capture timestamp
            // Note: cross-device clock sync is required for absolute latency.
            // For now we track internal pipeline latency.
            val playLatency = (now - targetPlayTimeNanos) / 1_000_000L
            statsManager?.updateLatency(playLatency)
            
            return frame
        } else if (now > lossThresholdNanos) {
            // Packet loss declared after 1-frame grace period
            return handlePacketLoss()
        }
        
        return null // Within grace period, wait a bit longer
    }

    private fun handlePacketLoss(): AudioFrame? {
        consecutiveMissingCount++
        statsManager?.reportLoss()
        
        // PLC: Repeat last frame
        val plcFrame = lastFrame?.copy(
            sequenceNumber = nextExpectedSeq,
            timestamp = System.currentTimeMillis() // Current local time for metadata
        )
        
        nextExpectedSeq++
        statsManager?.updateExpectedSeq(nextExpectedSeq)
        
        // If we have too many losses or buffer is empty, re-buffer
        if (consecutiveMissingCount >= 10 || jitterBuffer.size == 0) {
            // Log.w("PlaybackScheduler", "Loss threshold reached ($consecutiveMissingCount). Re-buffering...")
            isBuffering = true
        }
        
        return plcFrame
    }

    private fun adjustDrift(frame: AudioFrame, targetTime: Long, actualTime: Long) {
        // Slow clock drift compensation
        // Compare capture timestamp delta vs local clock delta
        val tsDeltaNanos = (frame.timestamp - (lastFrame?.timestamp ?: frame.timestamp)) * 1_000_000L
        val localDeltaNanos = actualTime - (lastFrame?.receivedAtNanos ?: actualTime)
        
        if (tsDeltaNanos > 0 && localDeltaNanos > 0) {
            val drift = localDeltaNanos - tsDeltaNanos
            // Apply a tiny portion of drift to compensation
            driftCompensationNanos += (drift * AudioConfig.CLOCK_DRIFT_COMPENSATION_RATE).toLong()
        }
    }

    private fun checkLatency(nowNanos: Long) {
        if (isBuffering || startSeq == -1L) return
        
        val latestReceived = jitterBuffer.peekHighestSequence() ?: return
        val sequenceGap = latestReceived - nextExpectedSeq
        
        // Calculate current playback delay (how late we are for the next frame)
        val seqOffset = nextExpectedSeq - startSeq
        val scheduledTimeNanos = startTimeNanos + (seqOffset * AudioConfig.FRAME_DURATION_MS * 1_000_000L) + driftCompensationNanos
        val currentDelayMs = (nowNanos - scheduledTimeNanos) / 1_000_000L
        
        val targetDelay = jitterBuffer.targetDelay
        
        // JUMP condition: Behind by many frames OR clock is significantly behind target
        if (sequenceGap > AudioConfig.LATENCY_CATCHUP_FRAMES || currentDelayMs > AudioConfig.LATENCY_MAX_THRESHOLD_MS) {
            val skipTo = latestReceived - targetDelay + 1
            if (skipTo > nextExpectedSeq) {
                val droppedFrames = skipTo - nextExpectedSeq
                // Log.w("PlaybackScheduler", "Latency too high (gap: $sequenceGap, delay: ${currentDelayMs}ms). " +
                //         "Jumping to $skipTo (dropped $droppedFrames frames)")
                
                jitterBuffer.dropOlderThan(skipTo)
                nextExpectedSeq = skipTo
                
                // Align timing so skipTo frame is due exactly now - targetDelay (approx)
                // Actually, if we want SKIP_TO to be played NOW, we set startTimeNanos such that its scheduled time is NOW.
                val newSeqOffset = nextExpectedSeq - startSeq
                startTimeNanos = nowNanos - (newSeqOffset * AudioConfig.FRAME_DURATION_MS * 1_000_000L) - driftCompensationNanos
                
                consecutiveMissingCount = 0
                statsManager?.updateExpectedSeq(nextExpectedSeq)
            }
        }
    }

    fun reset() {
        isBuffering = true
        nextExpectedSeq = -1L
        startSeq = -1L
        startTimeNanos = 0L
        lastFrame = null
        consecutiveMissingCount = 0
        driftCompensationNanos = 0L
    }
}
