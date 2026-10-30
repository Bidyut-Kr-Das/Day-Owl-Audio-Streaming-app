package com.example.dayowl.network

import com.example.dayowl.audio.AudioConfig

/**
 * Wire format: version(1) type(1) seq(8) curLen(2) prevLen(2) cur(curLen) prev(prevLen) = 14 byte header.
 *
 * `cur` is the Opus frame for `seq`; `prev` is the frame for `seq - 1` again. Every frame therefore
 * travels twice, so any single lost datagram is recovered bit-exact by the next one - at ~160 bytes
 * a frame that redundancy costs far less airtime than the raw PCM it replaced.
 *
 * Operates in place on caller-owned buffers so the hot path allocates nothing. Pure JVM
 * (no android imports) so it is unit-testable.
 */
object AudioPacketizer {
    const val HEADER_SIZE = 14
    const val MAX_PACKET_SIZE = HEADER_SIZE + 2 * AudioConfig.MAX_OPUS_FRAME_BYTES

    /** Writes a whole packet into [into] and returns its length. [prevLen] 0 means no previous frame. */
    fun write(into: ByteArray, seq: Long, cur: ByteArray, curLen: Int, prev: ByteArray, prevLen: Int): Int {
        into[0] = AudioConfig.PROTOCOL_VERSION
        into[1] = AudioConfig.PACKET_TYPE_AUDIO
        for (i in 0 until 8) into[2 + i] = (seq ushr (56 - 8 * i)).toByte()
        putShort(into, 10, curLen)
        putShort(into, 12, prevLen)
        System.arraycopy(cur, 0, into, HEADER_SIZE, curLen)
        System.arraycopy(prev, 0, into, HEADER_SIZE + curLen, prevLen)
        return HEADER_SIZE + curLen + prevLen
    }

    /** Length of the current frame, or -1 if the first [len] bytes of [buf] are not a valid audio packet. */
    fun payloadLen(buf: ByteArray, len: Int): Int {
        if (len < HEADER_SIZE) return -1
        if (buf[0] != AudioConfig.PROTOCOL_VERSION) return -1
        if (buf[1] != AudioConfig.PACKET_TYPE_AUDIO) return -1
        val cur = getShort(buf, 10)
        return if (cur == 0 || len < HEADER_SIZE + cur + getShort(buf, 12)) -1 else cur
    }

    /** Length of the redundant previous frame, which starts at HEADER_SIZE + payloadLen. Only valid after [payloadLen]. */
    fun prevLen(buf: ByteArray): Int = getShort(buf, 12)

    fun seqOf(buf: ByteArray): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (buf[2 + i].toLong() and 0xFF)
        return v
    }

    private fun putShort(b: ByteArray, off: Int, v: Int) {
        b[off] = (v ushr 8).toByte()
        b[off + 1] = v.toByte()
    }

    private fun getShort(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
}
