package com.qorvhex.qobearvpn.core

/** OpenVPN v2 control-channel packet framing for TLS handshake latency probes. */
object OpenVpnProbePacket {
    fun request(session: ByteArray): ByteArray {
        require(session.size == 8)
        return byteArrayOf(0x38) + session + byteArrayOf(0, 0, 0, 0, 0)
    }

    fun serverSession(reply: ByteArray): ByteArray = reply.copyOfRange(1, 9)

    fun packetId(reply: ByteArray): Long {
        val count = reply[9].toInt() and 0xFF
        val at = 10 + count * 4 + 8
        return (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (reply[at + i].toLong() and 0xFF) }
    }

    fun control(
        session: ByteArray,
        ackId: Long,
        remoteSession: ByteArray,
        packetId: Long,
        payload: ByteArray,
    ): ByteArray {
        fun u32(v: Long) = byteArrayOf(
            (v ushr 24).toByte(),
            (v ushr 16).toByte(),
            (v ushr 8).toByte(),
            v.toByte(),
        )
        return byteArrayOf(0x20) + session + byteArrayOf(1) + u32(ackId) + remoteSession + u32(packetId) + payload
    }

    fun opcode(packet: ByteArray): Int = if (packet.isEmpty()) -1 else (packet[0].toInt() and 0xFF) ushr 3

    fun controlPayload(packet: ByteArray): ByteArray? {
        if (opcode(packet) != 4 || packet.size < 14) return null
        val count = packet[9].toInt() and 0xFF
        val at = 10 + count * 4 + (if (count > 0) 8 else 0) + 4
        return if (at <= packet.size) packet.copyOfRange(at, packet.size) else null
    }

    fun accepts(packet: ByteArray, session: ByteArray): Boolean {
        if (session.size != 8 || packet.size < 26 || (packet[0].toInt() and 0xFF) != 0x40) return false
        val count = packet[9].toInt() and 0xFF
        if (count !in 1..32 || packet.size < 10 + count * 4 + 12) return false
        val offset = 10 + count * 4
        return packet.copyOfRange(offset, offset + 8).contentEquals(session) &&
            (0 until count).any { i -> (10 + i * 4 until 14 + i * 4).all { packet[it] == 0.toByte() } }
    }
}
