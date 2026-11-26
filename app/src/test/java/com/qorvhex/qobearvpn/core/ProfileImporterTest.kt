package com.qorvhex.qobearvpn.core

import com.qorvhex.qobearvpn.model.ProfileImportException
import com.qorvhex.qobearvpn.service.OpenVpnRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileImporterTest {
    private val sampleTunnelBear = """
        client
        remote de.lazerpenguin.com 7011 tcp-client
        dev tun
        proto tcp-client
        nobind
        remote-cert-tls server
        persist-key
        persist-tun
        reneg-sec 0
        dhcp-option DNS 8.8.8.8
        dhcp-option DNS 8.8.4.4
        redirect-gateway
        verb 5
        auth-user-pass
        setenv CLIENT_CERT 0
        <ca>
        -----BEGIN CERTIFICATE-----
        MIICPTCCAcOgAwIBAgIQfs/kxYEHK0ojKgXA1FrgFjAKBggqhkjOPQQDAjBgMQsw
        CQYDVQQGEwJDQTEUMBIGA1UECgwLTWNBZmVlLCBMTEMxDDAKBgNVBAsMA1ZQTjEt
        MCsGA1UEAwwkTWNBZmVlIE9wZW5WUE4gQ2VydGlmaWNhdGUgQXV0aG9yaXR5MB4X
        DTI0MDgwNjIwMzM0N1oXDTM0MDgwNjIxMzI0N1owYDELMAkGA1UEBhMCQ0ExFDAS
        BgNVBAoMC01jQWZlZSwgTExDMQwwCgYDVQQLDANWUE4xLTArBgNVBAMMJE1jQWZl
        ZSBPcGVuVlBOIENlcnRpZmljYXRlIEF1dGhvcml0eTB2MBAGByqGSM49AgEGBSuB
        BAAiA2IABNJps+fTiqQfpGzgpq9yAPM0rLzVZ1qscVxqag3ESsclEp/uk+HCAwK1
        EiLER8xXXweW9jVcYEHLuUkmBL+0FjocD5lI6zbrwaY8gWOz8vAP0fjolhXQgHfH
        TqrYC9unIqNCMEAwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUwa+4wbD5vocC
        xKeVNouzvsSPLekwDgYDVR0PAQH/BAQDAgGGMAoGCCqGSM49BAMCA2gAMGUCMGIM
        dRbutNNzP8GIyGHKtPd+7CSOlpqeBOUBsGLkj4F1y7/yqv7hIchtTIZQymmthAIx
        APY7ZiCKYW7L0mLVgowDRSY95Qxrs9NjsyQxlqRdMKcQfrojIH8Dh931M5Sj7EqR
        eg==
        -----END CERTIFICATE-----
        </ca>
        data-ciphers AES-256-CBC
        cipher AES-256-CBC
        auth SHA256
    """.trimIndent()

    @Test
    fun parsesAllBundledProfilesFromAssetsDirectory() {
        val dir = listOf(
            File("src/main/assets/profiles"),
            File("app/src/main/assets/profiles"),
        ).firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Bundled assets/profiles directory missing", dir)

        val files = dir!!.listFiles { f -> f.extension.equals("ovpn", true) }.orEmpty()
        assertEquals(47, files.size)
        files.forEach { file ->
            val p = ProfileImporter.parse(file.name, file.readText())
            assertTrue(ProfileRuntime.isTunnelBear(p))
            assertTrue(ProfileRuntime.supportsTcpRelay(p))
            assertEquals(ProfileRuntime.TUNNELBEAR_TCP_PORT, p.remotes.first().port)
            assertFalse(p.selfContained)
        }
    }

    @Test
    fun stripsDns6PrependsPullFiltersAndPreparesRelayConfig() {
        val custom = sampleTunnelBear + "\ndhcp-option DNS6 2001:4860:4860::8888\npull-filter accept \"dhcp-option DNS\""
        val profile = ProfileImporter.parse("TunnelBear-Germany-TCP-connect-fix.ovpn", custom)
        assertFalse("setenv" in profile.config)
        assertTrue("cipher AES-256-CBC" in profile.config)

        val relayCfg = ProfileRuntime.effective(
            profile = profile,
            forceTcp = true,
            relayPort = 43210,
            customDns = listOf("1.1.1.1", "1.0.0.1"),
        )
        assertTrue(relayCfg.startsWith("pull-filter ignore \"dhcp-option DNS\""))
        assertTrue("remote 127.0.0.1 43210 tcp-client" in relayCfg)
        assertTrue("dhcp-option DNS 1.1.1.1" in relayCfg)
        assertFalse("DNS6" in relayCfg)
        assertFalse("8.8.8.8" in relayCfg)
    }

    @Test
    fun mixedAttackerAndTunnelBearRemotesAreNotTreatedAsTunnelBear() {
        val mixed = sampleTunnelBear.replace(
            "remote de.lazerpenguin.com 7011 tcp-client",
            "remote evil.example.com 1194 udp\nremote de.lazerpenguin.com 7011 tcp-client"
        )
        val profile = ProfileImporter.parse("mixed.ovpn", mixed, isCustom = true)
        assertFalse(ProfileRuntime.isTunnelBear(profile))
        assertFalse(ProfileRuntime.supportsTcpRelay(profile))
    }

    @Test(expected = ProfileImportException::class)
    fun rejectsMissingCaCertificate() {
        ProfileImporter.parse("bad.ovpn", "client\nremote example.com 1194 udp\ndev tun\n")
    }

    @Test(expected = ProfileImportException::class)
    fun rejectsWeakNoneCipher() {
        ProfileImporter.parse("weak.ovpn", sampleTunnelBear.replace("cipher AES-256-CBC", "cipher none"))
    }

    @Test
    fun customNonTunnelBearBareAuthUserPassRequiresInteractivePrompt() {
        val customConfig = sampleTunnelBear.replace(
            "remote de.lazerpenguin.com 7011 tcp-client",
            "remote custom.vpn.example.org 1194 udp"
        )
        val parsed = ProfileImporter.parse("personal.ovpn", customConfig, isCustom = true)
        assertTrue(parsed.isCustom)
        assertFalse(ProfileRuntime.isTunnelBear(parsed))
        assertFalse(parsed.selfContained)
        assertFalse(parsed.hasSavedCredentials)
    }

    @Test
    fun importedTunnelBearLookalikeDoesNotReceiveTunnelBearCredentials() {
        val crafted = sampleTunnelBear.replace(
            "remote de.lazerpenguin.com 7011 tcp-client",
            "remote rogue.lazerpenguin.com 7011 tcp-client"
        )
        val parsed = ProfileImporter.parse("crafted.ovpn", crafted, isCustom = true)
        assertTrue(parsed.isCustom)
        assertFalse(ProfileRuntime.isTunnelBear(parsed))
        assertNull(OpenVpnRuntime.resolveAccountId(parsed, "tb-account"))
    }
}
