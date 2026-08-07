package com.example.dayowl.network

import com.example.dayowl.audio.AudioConfig
import com.example.dayowl.model.AudioFrame
import android.util.Log
import java.nio.ByteBuffer

object AudioPacketizer {
    private const val HEADER_SIZE = 20 // 1+1+8+8+2

    fun packetize(frame: AudioFrame): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + frame.data.size)
        buffer.put(AudioConfig.PROTOCOL_VERSION)
        buffer.put(AudioConfig.PACKET_TYPE_AUDIO)
        buffer.putLong(frame.sequenceNumber)
        buffer.putLong(frame.timestamp)
        buffer.putShort(frame.data.size.toShort())
        buffer.put(frame.data)
        val data = buffer.array()
        
        // Log.v("AudioPacketizer", "Packetized: Seq=${frame.sequenceNumber} TS=${frame.timestamp} Size=${frame.data.size}")
        
        return data
    }

    fun depacketize(data: ByteArray): AudioFrame? {
        if (data.size < HEADER_SIZE) return null
        
        val buffer = ByteBuffer.wrap(data)
        val version = buffer.get()
        if (version != AudioConfig.PROTOCOL_VERSION) return null
        
        val type = buffer.get()
        if (type != AudioConfig.PACKET_TYPE_AUDIO) return null
        
        val sequenceNumber = buffer.getLong()
        val timestamp = buffer.getLong()
        val payloadSize = buffer.getShort().toInt()
        
        if (data.size < HEADER_SIZE + payloadSize) return null
        
        val payload = ByteArray(payloadSize)
        buffer.get(payload)
        
        val frame = AudioFrame(sequenceNumber, timestamp, payload, 0L, android.os.SystemClock.elapsedRealtimeNanos())
        // Log.v("AudioPacketizer", "Depacketized: Seq=${frame.sequenceNumber} TS=${frame.timestamp} Size=${frame.data.size}")
        
        return frame
    }
}
