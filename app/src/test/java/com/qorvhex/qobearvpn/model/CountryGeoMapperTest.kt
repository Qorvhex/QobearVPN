package com.qorvhex.qobearvpn.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryGeoMapperTest {
    private val expectedTunnelBearHosts = listOf(
        "TunnelBear-Argentina" to "ar.lazerpenguin.com",
        "TunnelBear-Australia" to "au.lazerpenguin.com",
        "TunnelBear-Austria" to "at.lazerpenguin.com",
        "TunnelBear-Belgium" to "be.lazerpenguin.com",
        "TunnelBear-Brazil" to "br.lazerpenguin.com",
        "TunnelBear-Bulgaria" to "bg.lazerpenguin.com",
        "TunnelBear-Canada" to "ca.lazerpenguin.com",
        "TunnelBear-Chile" to "cl.lazerpenguin.com",
        "TunnelBear-Colombia" to "co.lazerpenguin.com",
        "TunnelBear-Cyprus" to "cy.lazerpenguin.com",
        "TunnelBear-Czech-Republic" to "cz.lazerpenguin.com",
        "TunnelBear-Denmark" to "dk.lazerpenguin.com",
        "TunnelBear-Finland" to "fi.lazerpenguin.com",
        "TunnelBear-France" to "fr.lazerpenguin.com",
        "TunnelBear-Germany" to "de.lazerpenguin.com",
        "TunnelBear-Greece" to "gr.lazerpenguin.com",
        "TunnelBear-Hungary" to "hu.lazerpenguin.com",
        "TunnelBear-Ireland" to "ie.lazerpenguin.com",
        "TunnelBear-Italy" to "it.lazerpenguin.com",
        "TunnelBear-Japan" to "jp.lazerpenguin.com",
        "TunnelBear-Kenya" to "ke.lazerpenguin.com",
        "TunnelBear-Latvia" to "lv.lazerpenguin.com",
        "TunnelBear-Lithuania" to "lt.lazerpenguin.com",
        "TunnelBear-Malaysia" to "my.lazerpenguin.com",
        "TunnelBear-Mexico" to "mx.lazerpenguin.com",
        "TunnelBear-Moldova" to "md.lazerpenguin.com",
        "TunnelBear-Netherlands" to "nl.lazerpenguin.com",
        "TunnelBear-New-Zealand" to "nz.lazerpenguin.com",
        "TunnelBear-Nigeria" to "ng.lazerpenguin.com",
        "TunnelBear-Norway" to "no.lazerpenguin.com",
        "TunnelBear-Peru" to "pe.lazerpenguin.com",
        "TunnelBear-Poland" to "pl.lazerpenguin.com",
        "TunnelBear-Portugal" to "pt.lazerpenguin.com",
        "TunnelBear-Romania" to "ro.lazerpenguin.com",
        "TunnelBear-Serbia" to "rs.lazerpenguin.com",
        "TunnelBear-Singapore" to "sg.lazerpenguin.com",
        "TunnelBear-Slovenia" to "si.lazerpenguin.com",
        "TunnelBear-South-Africa" to "za.lazerpenguin.com",
        "TunnelBear-South-Korea" to "kr.lazerpenguin.com",
        "TunnelBear-Spain" to "es.lazerpenguin.com",
        "TunnelBear-Sweden" to "se.lazerpenguin.com",
        "TunnelBear-Switzerland" to "ch.lazerpenguin.com",
        "TunnelBear-Taiwan" to "tw.lazerpenguin.com",
        "TunnelBear-United-Kingdom" to "uk.lazerpenguin.com",
        "TunnelBear-United-States-Central" to "us-central.lazerpenguin.com",
        "TunnelBear-United-States-East" to "us-east.lazerpenguin.com",
        "TunnelBear-United-States-West" to "us-west.lazerpenguin.com",
    )

    @Test
    fun all47ServersHaveDistinctCountryFlagsAndLocalizedNames() {
        assertEquals(47, expectedTunnelBearHosts.size)
        expectedTunnelBearHosts.forEach { (name, host) ->
            val p = Profile(
                id = host,
                name = name,
                config = "",
                remotes = listOf(Remote(host, 7011, "tcp-client")),
            )
            val place = CountryGeoMapper.resolve(p)
            assertNotEquals("Missing flag for $name", "🌐", place.flag)
            assertTrue("Missing Persian name for $name", place.nameFa.isNotBlank())
            assertTrue("Missing English name for $name", place.nameEn.isNotBlank())
        }
    }

    @Test
    fun mapsUnitedKingdomAndUsRegionsProperly() {
        val uk = CountryGeoMapper.resolve(
            Profile("uk", "TunnelBear-United-Kingdom", "", listOf(Remote("uk.lazerpenguin.com", 7011, "tcp-client")))
        )
        assertEquals("GB", uk.isoCode)
        assertEquals(CountryGeoMapper.flagFromIso("GB"), uk.flag)

        val usWest = CountryGeoMapper.resolve(
            Profile("usw", "TunnelBear-United-States-West", "", listOf(Remote("us-west.lazerpenguin.com", 7011, "tcp-client")))
        )
        assertEquals("US", usWest.isoCode)
        assertTrue(usWest.nameFa.contains("غرب"))
        assertTrue(usWest.nameEn.contains("West"))
    }
}
