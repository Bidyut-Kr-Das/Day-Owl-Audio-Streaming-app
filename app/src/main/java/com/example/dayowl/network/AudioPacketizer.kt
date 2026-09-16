package com.example.dayowl.network

import com.example.dayowl.audio.AudioConfig

/**
 * Wire format: version(1) type(1) seq(8) timestamp(8) payloadLen(2) payload(n) = 20 byte header.
 *
 * Operates in place on caller-owned buffers so the hot path allocates nothing. Pure JVM
 * (no android imports) so it is unit-testable.
 */
object AudioPacketizer {
    const val HEADER_SIZE = 20
    const val PACKET_SIZE = HEADER_SIZE + AudioConfig.FRAME_SIZE_BYTES

    /**
     * Stamps the header ahead of a payload already sitting at [HEADER_SIZE] in [into].
     * Bytes 10-17 are reserved and left zero - play-out is clocked by the audio device, so no
     * consumer reads a sender timestamp.
     */
    fun writeHeader(into: ByteArray, seq: Long, payloadLen: Int) {
        into[0] = AudioConfig.PROTOCOL_VERSION
        into[1] = AudioConfig.PACKET_TYPE_AUDIO
        putLong(into, 2, seq)
        into[18] = (payloadLen ushr 8).toByte()
        into[19] = payloadLen.toByte()
    }

    /** Payload length, or -1 if the first [len] bytes of [buf] are not a valid audio packet. */
    fun payloadLen(buf: ByteArray, len: Int): Int {
        if (len < HEADER_SIZE) return -1
        if (buf[0] != AudioConfig.PROTOCOL_VERSION) return -1
        if (buf[1] != AudioConfig.PACKET_TYPE_AUDIO) return -1
        val n = ((buf[18].toInt() and 0xFF) shl 8) or (buf[19].toInt() and 0xFF)
        return if (len < HEADER_SIZE + n) -1 else n
    }

    fun seqOf(buf: ByteArray): Long = getLong(buf, 2)

    private fun putLong(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) b[off + i] = (v ushr (56 - 8 * i)).toByte()
    }

    private fun getLong(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }
}
