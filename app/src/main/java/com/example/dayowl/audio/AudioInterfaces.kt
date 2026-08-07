package com.example.dayowl.audio

import com.example.dayowl.model.AudioFrame
import kotlinx.coroutines.flow.SharedFlow

interface AudioFrameProducer {
    val audioFrames: SharedFlow<AudioFrame>
    fun startProducer()
    fun stopProducer()
}

interface AudioFrameConsumer {
    fun consumeFrame(frame: AudioFrame)
}
