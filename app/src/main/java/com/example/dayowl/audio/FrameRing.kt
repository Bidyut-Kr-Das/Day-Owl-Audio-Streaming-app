package com.example.dayowl.audio

import java.util.concurrent.atomic.AtomicLongArray

/**
 * Lock-free jitter ring of encoded Opus frames: one producer (network RX thread), one consumer
 * (playback thread).
 *
 * A slot is valid only when its recorded sequence equals the one being asked for, so a stale
 * slot left behind by a wrap reads as a miss - no eviction pass, no scanning. The sequence is
 * published *after* the payload copy and AtomicLongArray.set is a release store, so a consumer
 * that sees the sequence is guaranteed to see the payload bytes.
 *
 * Depth adapts: an underrun raises the drop threshold by one frame, a long clean run lowers it.
 * The buffer itself grows on an underrun because play-out holds its position (the decoder
 * conceals) instead of skipping ahead, so depth tracks the jitter this link actually has.
 *
 * Pure JVM (no android imports) so [next] - the only nontrivial logic here - is unit-testable.
 */
class FrameRing(
    private val size: Int = AudioConfig.RING_SIZE,
    private val frameBytes: Int = AudioConfig.MAX_OPUS_FRAME_BYTES,
    private val minTarget: Int = AudioConfig.TARGET_MIN_FRAMES,
    private val startTarget: Int = AudioConfig.TARGET_START_FRAMES,
    private val maxTarget: Int = AudioConfig.TARGET_MAX_FRAMES,
    private val hysteresis: Int = AudioConfig.DEPTH_HYSTERESIS,
    private val decayFrames: Int = AudioConfig.TARGET_DECAY_FRAMES
) {
    private val mask = (size - 1).toLong()
    private val slots = Array(size) { ByteArray(frameBytes) }
    private val lens = IntArray(size)
    private val seqs = AtomicLongArray(size).also { for (i in 0 until size) it.set(i, -1L) }

    /** Sequence of the most recently received packet. Plain volatile, not a running max, so a
     *  sender restart (seq back to 0) is visible to the resync branch in [next]. */
    @Volatile private var latest = -1L
    @Volatile private var firstSeq = -1L

    /** Consumer-thread state. */
    var playoutSeq = -1L
        private set
    var target = startTarget
        private set
    /** Byte length of the frame [next] just returned. */
    var frameLen = 0
        private set
    private var inMiss = false
    private var cleanRun = 0

    var concealedCount = 0L
        private set
    var droppedCount = 0L
        private set
    var resyncCount = 0L
        private set

    val depth: Long get() = if (playoutSeq < 0) 0L else latest - playoutSeq

    /** Producer side: the frame a packet was sent for. */
    fun put(seq: Long, src: ByteArray, srcOff: Int, len: Int) {
        store(seq, src, srcOff, len)
        if (firstSeq < 0L) firstSeq = seq
        latest = seq
    }

    /** Producer side: the redundant copy of `seq` carried by packet `seq + 1`. Fills a gap only. */
    fun backfill(seq: Long, src: ByteArray, srcOff: Int, len: Int) {
        if (seq < 0L || seqs.get((seq and mask).toInt()) == seq) return
        store(seq, src, srcOff, len)
    }

    private fun store(seq: Long, src: ByteArray, srcOff: Int, len: Int) {
        val idx = (seq and mask).toInt()
        val n = if (len < frameBytes) len else frameBytes
        System.arraycopy(src, srcOff, slots[idx], 0, n)
        lens[idx] = n
        seqs.set(idx, seq) // release store: publishes the payload copied above
    }

    /**
     * Consumer side. Returns the encoded frame to decode ([frameLen] bytes), [LOST] when the
     * decoder must conceal, or null while still priming.
     *
     * Sender and device clocks drift apart; the drop branch absorbs that as an emergent property
     * - there is deliberately no separate drift-correction code.
     */
    fun next(): ByteArray? {
        val l = latest
        if (l < 0L) return null

        if (playoutSeq < 0L) {
            if (l - firstSeq < target) return null // still filling the initial buffer
            playoutSeq = l - target
        } else if (l - playoutSeq < -size) {
            // Sender restarted its sequence, or we fell absurdly far behind. Re-anchor.
            playoutSeq = maxOf(0L, l - target)
            inMiss = false
            resyncCount++
        } else if (l - playoutSeq > target + hysteresis) {
            playoutSeq++ // skip one frame; the advance below makes it two, so we catch up by one
            droppedCount++
        }

        if (++cleanRun >= decayFrames) {
            cleanRun = 0
            if (target > minTarget) target--
        }

        val hit = get(playoutSeq)
        if (hit != null) {
            inMiss = false
            frameLen = lens[(playoutSeq and mask).toInt()]
            playoutSeq++
            return hit
        }

        concealedCount++
        cleanRun = 0
        // One raise per run of misses: a 200ms outage is one event, not twenty.
        if (!inMiss && target < maxTarget) target++
        inMiss = true
        // Not arrived yet (underrun): hold position so the buffer deepens. Lost for good: move on.
        if (playoutSeq <= l) playoutSeq++
        return LOST
    }

    /**
     * The frame stored for [seq], or null if that slot holds something else - which is how a
     * stale slot left behind by a wrap reads as a miss rather than as ancient audio.
     *
     * ponytail: hands back the slot itself rather than a copy, so the producer could in principle
     * overwrite it mid-decode. At 64 slots and a working depth of a few frames that is ~50 frames
     * of separation; if RING_SIZE is ever cut below ~32, copy out here instead.
     */
    fun get(seq: Long): ByteArray? {
        if (seq < 0L) return null
        val idx = (seq and mask).toInt()
        return if (seqs.get(idx) == seq) slots[idx] else null
    }

    fun reset() {
        for (i in 0 until size) seqs.set(i, -1L)
        latest = -1L
        firstSeq = -1L
        playoutSeq = -1L
        target = startTarget
        frameLen = 0
        inMiss = false
        cleanRun = 0
        concealedCount = 0L
        droppedCount = 0L
        resyncCount = 0L
    }

    companion object {
        /** Returned by [next] when the frame is missing and the decoder must conceal it. */
        val LOST = ByteArray(0)
    }
}
