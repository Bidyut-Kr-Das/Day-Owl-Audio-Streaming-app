package com.example.dayowl

import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.audio.FrameRing
import com.example.dayowl.network.AudioPacketizer
import com.example.dayowl.network.Control
import com.example.dayowl.network.PacketType
import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.concentus.OpusEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioPipelineTest {

    // ---- codec ----

    @Test
    fun `opus round trips a tone and conceals a lost frame`() {
        val n = AudioConfig.SAMPLES_PER_FRAME
        val enc = OpusEncoder(AudioConfig.SAMPLE_RATE, AudioConfig.CHANNELS, OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY)
        enc.setBitrate(AudioConfig.OPUS_BITRATE)
        val dec = OpusDecoder(AudioConfig.SAMPLE_RATE, AudioConfig.CHANNELS)
        val pcm = ShortArray(n)
        val out = ShortArray(n)
        val packet = ByteArray(AudioConfig.MAX_OPUS_FRAME_BYTES)
        var t = 0
        var energy = 0.0
        repeat(20) {
            for (i in 0 until n) pcm[i] = (8000 * sin(2 * PI * 440 * t++ / AudioConfig.SAMPLE_RATE)).toInt().toShort()
            val len = enc.encode(pcm, 0, n, packet, 0, packet.size)
            assertTrue("frame too big: " + len, len in 1..AudioConfig.MAX_OPUS_FRAME_BYTES)
            assertEquals(n, dec.decode(packet, 0, len, out, 0, n, false))
            energy = out.sumOf { it.toDouble() * it }
        }
        assertTrue("decoded tone is silent", energy > 0.0)
        assertEquals(n, dec.decode(null, 0, 0, out, 0, n, false)) // PLC yields a full frame
    }

    // ---- wire format ----

    @Test
    fun `packet carries current and previous frame and fits the MTU`() {
        val cur = ByteArray(5) { 1 }
        val prev = ByteArray(3) { 2 }
        val buf = ByteArray(AudioPacketizer.MAX_PACKET_SIZE)
        val len = AudioPacketizer.write(buf, 123456789L, cur, cur.size, prev, prev.size)

        assertEquals(AudioPacketizer.HEADER_SIZE + 8, len)
        assertEquals(5, AudioPacketizer.payloadLen(buf, len))
        assertEquals(3, AudioPacketizer.prevLen(buf))
        assertEquals(123456789L, AudioPacketizer.seqOf(buf))
        val h = AudioPacketizer.HEADER_SIZE
        assertArrayEquals(cur, buf.copyOfRange(h, h + 5))
        assertArrayEquals(prev, buf.copyOfRange(h + 5, h + 8))

        // Two maximum frames still make one datagram, no IP fragmentation.
        assertTrue(AudioPacketizer.MAX_PACKET_SIZE <= 1472)
    }

    @Test
    fun `malformed packets are rejected`() {
        val buf = ByteArray(AudioPacketizer.MAX_PACKET_SIZE)
        val len = AudioPacketizer.write(buf, 1L, ByteArray(4), 4, ByteArray(4), 4)

        assertEquals(-1, AudioPacketizer.payloadLen(buf, AudioPacketizer.HEADER_SIZE - 1)) // too short
        assertEquals(-1, AudioPacketizer.payloadLen(buf, len - 1)) // truncated redundant frame

        val badVersion = buf.copyOf().also { it[0] = 1 } // v1 raw PCM peer
        assertEquals(-1, AudioPacketizer.payloadLen(badVersion, len))

        val badType = buf.copyOf().also { it[1] = AudioConfig.PACKET_TYPE_CONTROL }
        assertEquals(-1, AudioPacketizer.payloadLen(badType, len))
    }

    // ---- ring storage ----

    private fun ring(decay: Int = 1000) = FrameRing(
        size = 8, frameBytes = 4, minTarget = 1, startTarget = 2, maxTarget = 4, hysteresis = 1, decayFrames = decay
    )

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
    fun `backfill fills a gap but never overwrites`() {
        val r = ring()
        r.feed(2L, 2)
        r.backfill(1L, byteArrayOf(9, 9, 9, 9), 0, 4)
        r.backfill(2L, byteArrayOf(9, 9, 9, 9), 0, 4)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), r.get(1L))
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
        assertEquals(4, r.frameLen)
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
    fun `a lost frame is concealed and play-out moves past it`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, 7)
        r.next() // seq 0
        r.next() // seq 1

        r.feed(4L, 9) // seq 3 is lost in transit (both copies), seq 4 arrives

        assertArrayEquals(byteArrayOf(7, 7, 7, 7), r.next()) // seq 2, real
        assertSame(FrameRing.LOST, r.next()) // seq 3, decoder conceals
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), r.next()) // seq 4, real again
        assertEquals(1L, r.concealedCount)
        assertEquals(0L, r.droppedCount)
    }

    @Test
    fun `an underrun holds position and raises the target once per run`() {
        val r = ring()
        for (s in 0L..2L) r.feed(s, 7)
        repeat(3) { r.next() } // seq 0..2 played, nothing newer has arrived
        val held = r.playoutSeq

        repeat(3) { assertSame(FrameRing.LOST, r.next()) }
        assertEquals(held, r.playoutSeq) // waits for seq 3 instead of skipping it
        assertEquals(3, r.target) // one raise for the whole run, not three

        r.feed(3L, 3)
        assertArrayEquals(byteArrayOf(3, 3, 3, 3), r.next()) // nothing skipped
    }

    @Test
    fun `target decays after a clean run`() {
        val r = ring(decay = 5)
        for (s in 0L..2L) r.feed(s, 1)
        for (s in 3L..12L) {
            r.next()
            r.feed(s, 1)
        }
        assertEquals(1, r.target) // 2 -> 1 after 5 clean frames, floored at minTarget
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
                assert(r.playoutSeq >= last) { "play-out went backwards at seq " + s }
                last = r.playoutSeq
            }
        }
        assertEquals(0L, r.resyncCount)
    }

    // ---- control plane ----

    @Test
    fun `join request carries the audio port the joiner bound`() {
        val encoded = Control.encode(PacketType.JOIN_REQUEST, "41234")
        val decoded = Control.decode(encoded)

        assertEquals(PacketType.JOIN_REQUEST, decoded?.type)
        assertEquals(41234, decoded?.data?.toIntOrNull())
    }

    @Test
    fun `host info carries the session name`() {
        val decoded = Control.decode(Control.encode(PacketType.HOST_INFO, "Kim's Session"))

        assertEquals(PacketType.HOST_INFO, decoded?.type)
        assertEquals("Kim's Session", decoded?.data)
    }

    @Test
    fun `discover needs no payload and an audio frame is not control`() {
        assertNull(Control.decode(ByteArray(0)))
        assertEquals(PacketType.DISCOVER, Control.decode(Control.encode(PacketType.DISCOVER))?.type)

        val audio = ByteArray(AudioPacketizer.MAX_PACKET_SIZE)
        val len = AudioPacketizer.write(audio, 7L, ByteArray(160) { 3 }, 160, ByteArray(160), 160)
        assertNull(Control.decode(audio, len))
    }
}
