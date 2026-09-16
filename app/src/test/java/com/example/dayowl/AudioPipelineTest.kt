package com.example.dayowl

import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.audio.FrameRing
import com.example.dayowl.network.AudioPacketizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPipelineTest {

    // ---- wire format ----

    @Test
    fun `header round trips and packet fits the MTU`() {
        val buf = ByteArray(AudioPacketizer.PACKET_SIZE)
        val payload = ByteArray(AudioConfig.FRAME_SIZE_BYTES) { (it % 251).toByte() }
        System.arraycopy(payload, 0, buf, AudioPacketizer.HEADER_SIZE, payload.size)
        AudioPacketizer.writeHeader(buf, seq = 123456789L, payloadLen = payload.size)

        assertEquals(payload.size, AudioPacketizer.payloadLen(buf, buf.size))
        assertEquals(123456789L, AudioPacketizer.seqOf(buf))
        assertArrayEquals(
            payload,
            buf.copyOfRange(AudioPacketizer.HEADER_SIZE, AudioPacketizer.HEADER_SIZE + payload.size)
        )

        // The whole point of the 10ms frame: one datagram, no IP fragmentation.
        assertEquals(980, AudioPacketizer.PACKET_SIZE)
    }

    @Test
    fun `malformed packets are rejected`() {
        val buf = ByteArray(AudioPacketizer.PACKET_SIZE)
        AudioPacketizer.writeHeader(buf, 1L, AudioConfig.FRAME_SIZE_BYTES)

        assertEquals(-1, AudioPacketizer.payloadLen(buf, AudioPacketizer.HEADER_SIZE - 1)) // too short
        assertEquals(-1, AudioPacketizer.payloadLen(buf, AudioPacketizer.PACKET_SIZE - 1)) // truncated payload

        val badVersion = buf.copyOf().also { it[0] = 99 }
        assertEquals(-1, AudioPacketizer.payloadLen(badVersion, badVersion.size))

        val badType = buf.copyOf().also { it[1] = AudioConfig.PACKET_TYPE_CONTROL }
        assertEquals(-1, AudioPacketizer.payloadLen(badType, badType.size))
    }

    // ---- ring storage ----

    private fun ring() = FrameRing(size = 8, frameBytes = 4, target = 2, hysteresis = 1, maxPlcRepeats = 2)

    private fun FrameRing.feed(seq: Long, marker: Byte) =
        put(seq, byteArrayOf(marker, marker, marker, marker), 0, 4)

    @Test
    fun `slots read back by sequence and a wrap invalidates the old one`() {
        val r = ring()
        r.feed(3L, 33)
        assertArrayEquals(byteArrayOf(33, 33, 33, 33), r.get(3L))
        assertNull(r.get(4L))

        // seq 11 lands in the same slot as 3 (11 and 7 == 3) and must not read back as a hit for 3.
        r.feed(11L, 11)
        assertNull(r.get(3L))
        assertArrayEquals(byteArrayOf(11, 11, 11, 11), r.get(11L))
    }

    @Test
    fun `out of order arrival still reads back correctly`() {
        val r = ring()
        r.feed(2L, 2)
        r.feed(1L, 1)
        r.feed(0L, 0)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), r.get(0L))
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), r.get(1L))
        assertArrayEquals(byteArrayOf(2, 2, 2, 2), r.get(2L))
    }

    // ---- play-out control: the logic most likely to be wrong ----

    @Test
    fun `holds until primed then starts target frames behind`() {
        val r = ring()
        assertNull(r.next()) // nothing received at all

        r.feed(0L, 0)
        assertNull(r.next())
        r.feed(1L, 1)
        assertNull(r.next()) // only 1 frame of lead, target is 2

        r.feed(2L, 2)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), r.next()) // starts at latest - target = 0
        assertEquals(1L, r.playoutSeq)
    }

    @Test
    fun `advances exactly one frame per call while depth is in band`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, s.toByte())
        assertNotNull(r.next())

        for (s in 3L..7L) {
            r.feed(s, s.toByte())
            val before = r.playoutSeq
            r.next()
            assertEquals(before + 1, r.playoutSeq)
        }
        assertEquals(0L, r.droppedCount)
        assertEquals(0L, r.concealedCount)
    }

    @Test
    fun `drops a frame when depth exceeds target plus hysteresis`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, s.toByte())
        r.next() // primed, playoutSeq == 1

        // Push depth to 5, well past target(2) + hysteresis(1).
        for (s in 3L..6L) r.feed(s, s.toByte())
        assertEquals(5L, r.depth)

        val before = r.playoutSeq
        r.next()
        assertEquals(before + 2, r.playoutSeq) // one frame skipped
        assertEquals(1L, r.droppedCount)
    }

    @Test
    fun `repeats the last good frame when the sender stops then falls silent`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, 7)

        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 0, real
        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 1, real
        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 2, real
        // Nothing more arrives. Play-out keeps going - the device must never be starved.
        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // conceal 1
        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // conceal 2
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), r.next()) // past maxPlcRepeats, silence
        assertEquals(3L, r.concealedCount)
    }

    @Test
    fun `conceals a single lost frame and resumes real audio after it`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, 7)
        r.next() // seq 0
        r.next() // seq 1

        r.feed(4L, 9) // seq 3 is lost in transit, seq 4 arrives

        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 2, real
        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 3, concealed by repeat
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), r.next()) // seq 4, real again
        assertEquals(1L, r.concealedCount)
        assertEquals(0L, r.droppedCount)
    }

    @Test
    fun `resyncs when the sender restarts its sequence`() {
        val r = ring()
        for (s in 100L..102L) r.feed(s, 1)
        r.next()
        val stale = r.playoutSeq

        // Host restarted: sequence numbers begin again at 0, far below where we are playing.
        r.feed(0L, 5)
        r.next()

        assertEquals(1L, r.resyncCount)
        assert(r.playoutSeq < stale) { "expected re-anchor below " + stale + ", got " + r.playoutSeq }
        assert(r.playoutSeq >= 0L) { "play-out sequence must never go negative" }
    }

    @Test
    fun `play-out never moves backwards during normal streaming`() {
        val r = ring()
        var last = -1L
        for (s in 0L..200L) {
            r.feed(s, (s % 127).toByte())
            if (r.next() != null) {
                assert(r.playoutSeq > last) { "play-out went backwards at seq " + s }
                last = r.playoutSeq
            }
        }
        assertEquals(0L, r.resyncCount)
    }
}
