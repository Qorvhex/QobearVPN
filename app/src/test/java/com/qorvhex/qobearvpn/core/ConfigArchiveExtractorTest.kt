package com.qorvhex.qobearvpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ConfigArchiveExtractorTest {
    private val sampleOvpn = """
        client
        dev tun
        proto tcp-client
        remote example.com 443
        cipher AES-256-GCM
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent()

    @Test
    fun extractsZipWithSubfoldersSanitizesTraversalAndSkipsNonOvpn() {
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("TUNNELBEAR/"))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("TUNNELBEAR/Canada.ovpn"))
                zos.write(sampleOvpn.toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("../../etc/German.conf"))
                zos.write(sampleOvpn.toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("TUNNELBEAR/README-fa.txt"))
                zos.write("Not an openvpn file".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("__MACOSX/._Canada.ovpn"))
                zos.write(sampleOvpn.toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val collected = ConfigArchiveExtractor.collectFromInputs(
            listOf(
                "bundle.zip" to zipBytes,
                "single.ovpn" to sampleOvpn.toByteArray(),
            )
        )

        assertEquals(3, collected.size)
        assertTrue("Canada.ovpn" in collected)
        assertTrue("German.ovpn" in collected)
        assertTrue("single.ovpn" in collected)
        assertFalse("README-fa.txt" in collected)
        assertFalse(collected.keys.any { ".." in it || "/" in it || "__MACOSX" in it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedZipEntry() {
        val huge = ByteArray(ConfigArchiveExtractor.MAX_ENTRY_BYTES + 1024) { 'a'.code.toByte() }
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("huge.ovpn"))
                zos.write(huge)
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        ConfigArchiveExtractor.collectFromInputs(listOf("bomb.zip" to zipBytes))
    }
}
