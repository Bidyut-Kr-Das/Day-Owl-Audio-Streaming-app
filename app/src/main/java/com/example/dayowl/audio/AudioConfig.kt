package com.example.dayowl.audio

import android.media.AudioFormat

object AudioConfig {
    const val PROTOCOL_VERSION: Byte = 1

    const val SAMPLE_RATE = 48000
    const val CHANNELS = 1 // Mono
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 10ms keeps the datagram (20 byte header + 960 byte payload = 980) under the
    // 1500 byte MTU. At 20ms the payload alone was 1920 and every packet IP-fragmented.
    const val FRAME_DURATION_MS = 10

    // 48000 samples/sec * 1 channel * 2 bytes/sample * 0.010 sec = 960 bytes
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_SIZE_BYTES = (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE * FRAME_DURATION_MS) / 1000

    const val UDP_PORT_AUDIO = 5001
    const val UDP_PORT_CONTROL = 5000

    const val PACKET_TYPE_AUDIO: Byte = 0x01
    const val PACKET_TYPE_CONTROL: Byte = 0x02

    // Jitter ring. Power of two so the index is (seq and size-1).
    const val RING_SIZE = 64

    // Play-out depth control, in frames.
    const val TARGET_DEPTH_FRAMES = 5 // 50ms of jitter budget
    const val DEPTH_HYSTERESIS = 2
    const val MAX_PLC_REPEATS = 3

    // Client re-sends JOIN_REQUEST on this interval; it doubles as the heartbeat.
    const val HEARTBEAT_INTERVAL_MS = 2000L
    const val CLIENT_TIMEOUT_MS = 6000L

    // Client-side host-lost timeout. Deliberately NOT CLIENT_TIMEOUT_MS: with a 2s heartbeat that
    // would declare the host dead after only 3 consecutive losses, which WiFi produces routinely.
    const val HOST_TIMEOUT_MS = 8000L

    const val SOCKET_BUFFER_BYTES = 256 * 1024
    const val DSCP_EF = 0xB8 // maps to WMM AC_VO on WiFi
}
