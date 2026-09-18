package com.example.dayowl.network

import com.example.dayowl.audio.AudioConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class PacketType {
    /** Joiner -> broadcast: who is hosting? */
    DISCOVER,

    /** Host -> joiner: the reply to DISCOVER. [ControlPacket.data] is the session name. */
    HOST_INFO,

    /** Joiner -> host. [ControlPacket.data] is the port the joiner wants audio on. */
    JOIN_REQUEST,
    JOIN_ACCEPT,
    LEAVE
}

@Serializable
data class ControlPacket(
    val type: PacketType,
    val data: String? = null
)

/**
 * Wire helpers for the control plane: one type byte, then JSON.
 *
 * Pure JVM (no android imports) so it is unit-testable, and in one place so the host and the
 * joiner cannot drift apart - they used to carry the same decode block copy-pasted.
 */
object Control {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(type: PacketType, data: String? = null): ByteArray =
        byteArrayOf(AudioConfig.PACKET_TYPE_CONTROL) +
            json.encodeToString(ControlPacket(type, data)).toByteArray()

    /**
     * Parses the first [len] bytes of [buf], tolerating a body sent without the type byte.
     * Null for anything that is not a control packet - an audio frame included.
     */
    fun decode(buf: ByteArray, len: Int = buf.size): ControlPacket? {
        if (len <= 0) return null
        val body = if (buf[0] == AudioConfig.PACKET_TYPE_CONTROL) {
            String(buf, 1, len - 1)
        } else {
            String(buf, 0, len)
        }
        return runCatching { json.decodeFromString<ControlPacket>(body) }.getOrNull()
    }
}
