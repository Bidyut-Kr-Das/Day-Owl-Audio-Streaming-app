package com.example.dayowl.audio

import android.media.AudioFormat

object AudioConfig {
    // 2: Opus frames with the previous frame carried as redundancy. v1 (raw PCM) peers reject it.
    const val PROTOCOL_VERSION: Byte = 2

    const val SAMPLE_RATE = 48000
    const val CHANNELS = 1 // Mono
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 10ms is the shortest frame Opus codes at full quality, and the lowest-latency choice here.
    const val FRAME_DURATION_MS = 10
    const val SAMPLES_PER_FRAME = SAMPLE_RATE * CHANNELS * FRAME_DURATION_MS / 1000 // 480

    // 48000 samples/sec * 1 channel * 2 bytes/sample * 0.010 sec = 960 bytes of decoded PCM
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE

    // ~160 bytes per 10ms frame at 128 kbps. The cap makes the encoder squeeze a spike to fit, so
    // two frames plus the header always stay under the 1500 byte MTU.
    const val OPUS_BITRATE = 128_000
    const val OPUS_COMPLEXITY = 5
    const val MAX_OPUS_FRAME_BYTES = 600

    // Only the control port is fixed. The joiner's audio port is ephemeral and travels in the
    // JOIN_REQUEST - a hardcoded one can be already taken, and then the session is silent.
    const val UDP_PORT_CONTROL = 5000

    // Joiners listen here for the host's periodic HOST_INFO announce, so a host is found even
    // when the joiner's own DISCOVER probe never reaches it.
    const val UDP_PORT_DISCOVERY = 5002

    const val PACKET_TYPE_AUDIO: Byte = 0x01
    const val PACKET_TYPE_CONTROL: Byte = 0x02

    // Jitter ring. Power of two so the index is (seq and size-1).
    const val RING_SIZE = 64

    // Adaptive play-out depth, in 10ms frames. Starts low, grows by one per underrun, and gives one
    // back after TARGET_DECAY_FRAMES of clean play-out - so it settles at what this link needs.
    const val TARGET_MIN_FRAMES = 2
    const val TARGET_START_FRAMES = 3
    const val TARGET_MAX_FRAMES = 15
    const val TARGET_DECAY_FRAMES = 1000 // 10s
    const val DEPTH_HYSTERESIS = 2

    // Client re-sends JOIN_REQUEST on this interval; it doubles as the heartbeat.
    const val HEARTBEAT_INTERVAL_MS = 2000L
    const val CLIENT_TIMEOUT_MS = 6000L

    // Client-side host-lost timeout. Deliberately NOT CLIENT_TIMEOUT_MS: with a 2s heartbeat that
    // would declare the host dead after only 3 consecutive losses, which WiFi produces routinely.
    const val HOST_TIMEOUT_MS = 8000L

    const val SOCKET_BUFFER_BYTES = 256 * 1024
    const val DSCP_EF = 0xB8 // maps to WMM AC_VO on WiFi
}
