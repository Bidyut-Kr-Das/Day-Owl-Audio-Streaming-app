package com.example.dayowl.audio

import java.util.concurrent.atomic.AtomicLongArray

/**
 * Lock-free jitter ring: one producer (network RX thread), one consumer (playback thread).
 *
 * A slot is valid only when its recorded sequence equals the one being asked for, so a stale
 * slot left behind by a wrap reads as a miss - no eviction pass, no scanning. The sequence is
 * published *after* the payload copy and AtomicLongArray.set is a release store, so a consumer
 * that sees the sequence is guaranteed to see the payload bytes.
 *
 * Pure JVM (no android imports) so [next] - the only nontrivial logic here - is unit-testable.
 */
class FrameRing(
    private val size: Int = AudioConfig.RING_SIZE,
    private val frameBytes: Int = AudioConfig.FRAME_SIZE_BYTES,
    private val target: Int = AudioConfig.TARGET_DEPTH_FRAMES,
    private val hysteresis: Int = AudioConfig.DEPTH_HYSTERESIS,
    private val maxPlcRepeats: Int = AudioConfig.MAX_PLC_REPEATS
) {
    private val mask = (size - 1).toLong()
    private val slots = Array(size) { ByteArray(frameBytes) }
    private val seqs = AtomicLongArray(size).also { for (i in 0 until size) it.set(i, -1L) }
    private val silence = ByteArray(frameBytes)

    /** Sequence of the most recently received packet. Plain volatile, not a running max, so a
     *  sender restart (seq back to 0) is visible to the resync branch in [next]. */
    @Volatile private var latest = -1L
    @Volatile private var firstSeq = -1L

    /** Consumer-thread state. */
    var playoutSeq = -1L
        private set
    private var lastGoodSeq = -1L
    private var concealRun = 0

    var concealedCount = 0L
        private set
    var droppedCount = 0L
        private set
    var resyncCount = 0L
        private set

    val depth: Long get() = if (playoutSeq < 0) 0L else latest - playoutSeq

    /** Producer side. */
    fun put(seq: Long, src: ByteArray, srcOff: Int, len: Int) {
        val idx = (seq and mask).toInt()
        System.arraycopy(src, srcOff, slots[idx], 0, if (len < frameBytes) len else frameBytes)
        seqs.set(idx, seq) // release store: publishes the payload copied above
        if (firstSeq < 0L) firstSeq = seq
        latest = seq
    }

    /**
     * Consumer side. Returns the frame to hand to the audio device, or null while still priming.
     *
     * Play-out always advances by one frame per call; the sender's pace and the device's pace are
     * reconciled purely by the drop branch. Crystal drift between the two is absorbed by that
     * branch as an emergent property - there is deliberately no separate drift-correction code.
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
            concealRun = 0
            resyncCount++
        } else if (l - playoutSeq > target + hysteresis) {
            playoutSeq++ // skip one frame; the advance below makes it two, so we catch up by one
            droppedCount++
        }

        val hit = get(playoutSeq)
        val out: ByteArray = if (hit != null) {
            lastGoodSeq = playoutSeq
            concealRun = 0
            hit
        } else {
            concealedCount++
            concealRun++
            (if (concealRun <= maxPlcRepeats) get(lastGoodSeq) else null) ?: silence
        }
        playoutSeq++
        return out
    }

    /**
     * The frame stored for [seq], or null if that slot holds something else - which is how a
     * stale slot left behind by a wrap reads as a miss rather than as ancient audio.
     *
     * ponytail: hands back the slot itself rather than a copy, so the producer could in principle
     * overwrite it mid-write. At 64 slots and a working depth of ~5 that is 59 frames of
     * separation; if RING_SIZE is ever cut below ~16, copy out here instead.
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
        lastGoodSeq = -1L
        concealRun = 0
        concealedCount = 0L
        droppedCount = 0L
        resyncCount = 0L
    }
}
