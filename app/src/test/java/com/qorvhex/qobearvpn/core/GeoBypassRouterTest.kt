package com.qorvhex.qobearvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

class GeoBypassRouterTest {
    @Test
    fun complementRoutesExcludeEverySelectedPrefixAndStayWithinCap() {
        val excluded = listOf(
            "10.0.0.0/8",
            "192.168.0.0/16",
            "5.22.0.0/16",
            "2.144.0.0/14",
        ).mapNotNull { CidrPrefix.parse(it) }

        val complement = GeoBypassRouter.buildLegacyComplementRoutes(
            countryCidrs = excluded,
            lanBypass = true,
            maxOutputRoutes = GeoBypassRouter.MAX_LEGACY_OUTPUT_ROUTES,
        )

        assertTrue(complement.isNotEmpty())
        assertTrue(complement.size <= GeoBypassRouter.MAX_LEGACY_OUTPUT_ROUTES)

        val bypassedSamples = listOf("10.1.2.3", "192.168.1.1", "5.22.10.20", "2.145.0.5", "172.16.4.1")
        for (ip in bypassedSamples) {
            val ipLong = CidrPrefix.ipv4ToLong(InetAddress.getByName(ip) as Inet4Address)
            assertFalse("Bypassed IP $ip leaked into tunnel routes", complement.any { it.contains(ipLong) })
        }

        val tunneledSamples = listOf("1.1.1.1", "8.8.8.8", "140.82.121.4", "151.101.1.69")
        for (ip in tunneledSamples) {
            val ipLong = CidrPrefix.ipv4ToLong(InetAddress.getByName(ip) as Inet4Address)
            val matchCount = complement.count { it.contains(ipLong) }
            assertEquals("Public IP $ip should match exactly 1 complement route", 1, matchCount)
        }
    }

    @Test
    fun bundledGeoIpFilesParseAndPreserveAll1200OnApi33() {
        val geoDir = listOf(
            File("src/main/assets/geoip"),
            File("app/src/main/assets/geoip"),
        ).firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Bundled assets/geoip directory missing", geoDir)

        for (fileName in listOf("ir.cidr", "cn.cidr", "ru.cidr")) {
            val file = File(geoDir, fileName)
            assertTrue("Missing $fileName", file.exists())
            val cidrs = file.readLines().mapNotNull { CidrPrefix.parse(it) }
            assertEquals("$fileName expected 1200 CIDRs", 1200, cidrs.size)

            val api33 = GeoBypassRouter.buildApi33Exclusions(cidrs, lanBypass = true)
            assertEquals(1205, api33.size)
            assertTrue(api33.size <= GeoBypassRouter.MAX_API33_EXCLUDES)

            val legacy = GeoBypassRouter.buildLegacyComplementRoutes(cidrs, lanBypass = true)
            assertTrue(legacy.size <= GeoBypassRouter.MAX_LEGACY_OUTPUT_ROUTES)
        }
    }
}
