package com.qorvhex.qobearvpn.data

import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.ProbeResult
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.Remote
import com.qorvhex.qobearvpn.model.ServerPolicy
import com.qorvhex.qobearvpn.service.OpenVpnRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun validatesCustomIpv4DnsStrictly() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            AppSettings.parseValidIpv4List("1.1.1.1, 8.8.8.8"),
        )
        assertNull(AppSettings.parseValidIpv4List("1.1.1.1, 999.0.0.1"))
        assertNull(AppSettings.parseValidIpv4List("010.001.001.001"))
        assertNull(AppSettings.parseValidIpv4List("127.0.0.1"))
        assertNull(AppSettings.parseValidIpv4List("0.0.0.0"))
        assertNull(AppSettings.parseValidIpv4List("not-an-ip"))
    }

    @Test
    fun serverPolicySelectsFastestReachableFreshProfile() {
        val now = 1_000_000L
        val fastBlocked = Profile(
            id = "a", name = "A", config = "",
            remotes = listOf(Remote("a.lazerpenguin.com", 7011, "tcp-client")),
            probe = ProbeResult(40L, now - 1000, "OPENVPN_DPI_TLS", error = "TLS_BLOCKED"),
        )
        val slowOk = Profile(
            id = "b", name = "B", config = "",
            remotes = listOf(Remote("b.lazerpenguin.com", 7011, "tcp-client")),
            probe = ProbeResult(210L, now - 1000, "OPENVPN_DPI_TLS"),
        )
        val fastOk = Profile(
            id = "c", name = "C", config = "",
            remotes = listOf(Remote("c.lazerpenguin.com", 7011, "tcp-client")),
            probe = ProbeResult(95L, now - 1000, "OPENVPN_DPI_TLS"),
        )
        val staleFast = Profile(
            id = "d", name = "D", config = "",
            remotes = listOf(Remote("d.lazerpenguin.com", 7011, "tcp-client")),
            probe = ProbeResult(30L, now - 900_000L, "OPENVPN_DPI_TLS"),
        )

        val chosen = ServerPolicy.fastest(listOf(fastBlocked, slowOk, fastOk, staleFast), now)
        assertEquals("c", chosen?.id)

        val chosenAllowStale = ServerPolicy.fastest(listOf(staleFast), now, allowStale = true)
        assertEquals("d", chosenAllowStale?.id)
        assertNull(ServerPolicy.fastest(listOf(fastBlocked), now))
    }

    @Test
    fun bypassRegionsDefineExpectedRelayProfiles() {
        assertNotNull(BypassRegion.IRAN.relayProfile)
        assertNotNull(BypassRegion.CHINA.relayProfile)
        assertNotNull(BypassRegion.RUSSIA.relayProfile)
        assertNull(BypassRegion.GLOBAL.relayProfile)
    }

    @Test
    fun dpiBypassSeparatedFromGeoRegionAndDisabledByDefault() {
        val defaults = AppSettings()
        assertFalse(defaults.dpiBypassEnabled)

        BypassRegion.entries.forEach { region ->
            assertNull(region.effectiveDpiRelay(false))
            assertNotNull(region.effectiveDpiRelay(true))
        }
    }

    @Test
    fun autoConnectBestServerEnabledByDefault() {
        assertTrue(AppSettings().autoConnectBestServer)
    }

    @Test
    fun openVpnRuntimeResolvesCustomAndTunnelBearCredentialsStrictly() {
        val selfCustom = Profile(
            id = "self-custom",
            name = "SelfCustom",
            config = "client\ndev tun\nremote vpn.example.net 1194 udp\n",
            remotes = listOf(Remote("vpn.example.net", 1194, "udp")),
            isCustom = true,
        )
        val authPromptCustom = Profile(
            id = "auth-custom",
            name = "AuthCustom",
            config = "client\ndev tun\nremote vpn.example.net 1194 udp\nauth-user-pass\n",
            remotes = listOf(Remote("vpn.example.net", 1194, "udp")),
            isCustom = true,
            hasSavedCredentials = false,
        )
        val savedCredCustom = authPromptCustom.copy(id = "saved-custom", hasSavedCredentials = true)
        val tunnelBear = Profile(
            id = "tb-de",
            name = "Germany",
            config = "client\ndev tun\nremote de.lazerpenguin.com 7011 tcp-client\nauth-user-pass\n",
            remotes = listOf(Remote("de.lazerpenguin.com", 7011, "tcp-client")),
        )

        assertEquals(OpenVpnRuntime.SELF_CREDENTIAL, OpenVpnRuntime.resolveAccountId(selfCustom, "tb-1"))
        assertEquals(OpenVpnRuntime.SELF_CREDENTIAL, OpenVpnRuntime.resolveAccountId(selfCustom, null))

        // Must NEVER fall back to TunnelBear credentials when a custom profile requires auth-user-pass
        assertNull(OpenVpnRuntime.resolveAccountId(authPromptCustom, "tb-1"))
        assertNull(OpenVpnRuntime.resolveAccountId(authPromptCustom, null))

        assertEquals(OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL, OpenVpnRuntime.resolveAccountId(savedCredCustom, null))

        try {
            OpenVpnRuntime.setTransientProfileCredentials(authPromptCustom.id, "alice", "s3cr3t")
            assertEquals(
                OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL,
                OpenVpnRuntime.resolveAccountId(authPromptCustom, null),
            )
            assertEquals("alice" to "s3cr3t", OpenVpnRuntime.transientProfileCredentials(authPromptCustom.id))
        } finally {
            OpenVpnRuntime.clearTransientProfileCredentials(authPromptCustom.id)
        }
        assertNull(OpenVpnRuntime.resolveAccountId(authPromptCustom, null))

        assertEquals("tb-1", OpenVpnRuntime.resolveAccountId(tunnelBear, "tb-1"))
        assertNull(OpenVpnRuntime.resolveAccountId(tunnelBear, null))
    }
}
