package com.example.dayowl.audio

import android.media.AudioFormat

object AudioConfig {
    const val PROTOCOL_VERSION: Byte = 1
    
    const val SAMPLE_RATE = 48000
    const val CHANNELS = 1 // Mono
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    const val FRAME_DURATION_MS = 20
    
    // 48000 samples/sec * 1 channel * 2 bytes/sample * 0.020 sec = 1920 bytes
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_SIZE_BYTES = (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE * FRAME_DURATION_MS) / 1000
    
    const val UDP_PORT_AUDIO = 5001
    const val UDP_PORT_CONTROL = 5000
    
    const val JITTER_BUFFER_SIZE = 50 // Max frames to store
    const val JITTER_BUFFER_MIN_DELAY = 2 // Frames (40ms)
    const val JITTER_BUFFER_MAX_DELAY = 15 // Frames (300ms)
    const val JITTER_BUFFER_TARGET_ADAPT_RATE = 0.05 // How fast target delay adapts
    
    const val CLOCK_DRIFT_COMPENSATION_RATE = 0.001 // Adjust playback speed slightly if needed (future) or just timing
    
    const val PACKET_TYPE_AUDIO: Byte = 0x01
    const val PACKET_TYPE_CONTROL: Byte = 0x02
    
    // Latency Controller
    const val LATENCY_TARGET_MS = 80L
    const val LATENCY_MAX_THRESHOLD_MS = 160L
    const val LATENCY_CATCHUP_FRAMES = 8 // If behind by 8 frames (160ms), jump forward
}
