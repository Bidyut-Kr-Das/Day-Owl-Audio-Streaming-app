package com.example.dayowl.util

import com.example.dayowl.model.StreamStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class StatsManager {
    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private var capCount = 0
    private var sendCount = 0
    private var recvCount = 0
    private var playCount = 0
    
    private var lastReportTime = System.currentTimeMillis()
    
    private var capFps = 0
    private var sendFps = 0
    private var recvFps = 0
    private var playFps = 0
    
    private var latestRxSeq = 0L
    private var latestPlaySeq = 0L
    private var expectedSeq = 0L
    private var currentQueue = 0

    // Pipeline Timings (Aggregators)
    private var t1Sum = 0L; private var t1Count = 0
    private var t2Sum = 0L; private var t2Count = 0
    private var t4Sum = 0L; private var t4Count = 0
    private var t5Sum = 0L; private var t5Count = 0
    private var t6Sum = 0L; private var t6Count = 0

    // Maintained for backward compatibility
    private val _bitrate = MutableStateFlow(0L)
    val bitrate: StateFlow<Long> = _bitrate.asStateFlow()

    fun updateQueueDepth(depth: Int) {
        currentQueue = depth
        _stats.update { it.copy(queueDepth = depth) }
    }

    fun incrementCaptured() {
        capCount++
    }

    fun incrementSent() {
        sendCount++
        _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
    }

    fun incrementReceived(seq: Long) {
        recvCount++
        latestRxSeq = seq
        _stats.update { it.copy(packetsReceived = it.packetsReceived + 1) }
    }

    fun incrementPlayed(seq: Long) {
        playCount++
        latestPlaySeq = seq
    }

    fun updateExpectedSeq(seq: Long) {
        expectedSeq = seq
    }

    fun updateBitrate(bytes: Long) {
        _bitrate.value = bytes * 8
        _stats.update { it.copy(bitrateBps = bytes * 8) }
    }

    fun reportLoss() {
        _stats.update { it.copy(packetLossCount = it.packetLossCount + 1) }
    }

    fun reportOutOfOrder() {
        _stats.update { it.copy(outOfOrderCount = it.outOfOrderCount + 1) }
    }

    fun updateJitter(ms: Double) {
        _stats.update { it.copy(jitterMs = ms) }
    }

    fun updateLatency(ms: Long) {
        _stats.update { it.copy(averageLatencyMs = ms) }
    }

    fun recordT1(nanos: Long) { t1Sum += nanos; t1Count++ }
    fun recordT2(nanos: Long) { t2Sum += nanos; t2Count++ }
    fun recordT4(nanos: Long) { t4Sum += nanos; t4Count++ }
    fun recordT5(nanos: Long) { t5Sum += nanos; t5Count++ }
    fun recordT6(nanos: Long) { t6Sum += nanos; t6Count++ }

    fun getDiagnosticReport(): String {
        val now = System.currentTimeMillis()
        val delta = (now - lastReportTime) / 1000.0
        
        val avgT1 = if (t1Count > 0) (t1Sum / t1Count) / 1_000_000.0 else 0.0
        val avgT2 = if (t2Count > 0) (t2Sum / t2Count) / 1_000_000.0 else 0.0
        val avgT4 = if (t4Count > 0) (t4Sum / t4Count) / 1_000_000.0 else 0.0
        val avgT5 = if (t5Count > 0) (t5Sum / t5Count) / 1_000_000.0 else 0.0
        val avgT6 = if (t6Count > 0) (t6Sum / t6Count) / 1_000_000.0 else 0.0

        if (delta >= 1.0) {
            capFps = (capCount / delta).toInt()
            sendFps = (sendCount / delta).toInt()
            recvFps = (recvCount / delta).toInt()
            playFps = (playCount / delta).toInt()
            
            capCount = 0
            sendCount = 0
            recvCount = 0
            playCount = 0
            
            t1Sum = 0; t1Count = 0
            t2Sum = 0; t2Count = 0
            t4Sum = 0; t4Count = 0
            t5Sum = 0; t5Count = 0
            t6Sum = 0; t6Count = 0
            
            lastReportTime = now
        }

        return """
            ==========================
            Capture FPS : $capFps
            Send FPS : $sendFps
            Receive FPS : $recvFps
            Playback FPS : $playFps
            Current Queue : $currentQueue
            Expected Seq : $expectedSeq
            Latest RX Seq : $latestRxSeq
            Latest PLAY Seq : $latestPlaySeq
            Average Latency : ${_stats.value.averageLatencyMs} ms
            
            PIPELINE STAGES (avg ms):
            1. Capture Read: ${"%.2f".format(avgT1)}
            2. Cap -> Send : ${"%.2f".format(avgT2)}
            4. Recv -> Buff: ${"%.2f".format(avgT4)}
            5. Buffer Wait: ${"%.2f".format(avgT5)}
            6. Track Write: ${"%.2f".format(avgT6)}
            ==========================
        """.trimIndent()
    }
}
