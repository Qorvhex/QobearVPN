package com.qorvhex.qobearvpn.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnProbePacketTest {
    @Test
    fun buildsValidHardResetAndParsesServerReply() {
        val clientSession = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val reset = OpenVpnProbePacket.request(clientSession)
        assertEquals(14, reset.size)
        assertEquals(0x38, reset[0].toInt() and 0xFF)

        val serverSession = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)
        // P_CONTROL_HARD_RESET_SERVER_V2 (0x40), ack_count = 1, ack_id = 0, remote_session = clientSession, packet_id = 42
        val reply = byteArrayOf(0x40) +
            serverSession +
            byteArrayOf(1) +
            byteArrayOf(0, 0, 0, 0) +
            clientSession +
            byteArrayOf(0, 0, 0, 42)

        assertTrue(OpenVpnProbePacket.accepts(reply, clientSession))
        assertArrayEquals(serverSession, OpenVpnProbePacket.serverSession(reply))
        assertEquals(42L, OpenVpnProbePacket.packetId(reply))

        val wrongSession = byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9)
        assertFalse(OpenVpnProbePacket.accepts(reply, wrongSession))
    }

    @Test
    fun extractsControlPayloadFromPControlV1() {
        val payload = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x05)
        val pkt = OpenVpnProbePacket.control(
            session = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            ackId = 0L,
            remoteSession = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1),
            packetId = 1L,
            payload = payload,
        )
        assertEquals(4, OpenVpnProbePacket.opcode(pkt))
        assertArrayEquals(payload, OpenVpnProbePacket.controlPayload(pkt))
    }
}
