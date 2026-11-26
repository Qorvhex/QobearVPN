package com.qorvhex.qobearvpn.model

import java.util.Locale

data class CountryPlace(
    val isoCode: String?,
    val flag: String,
    val nameFa: String,
    val nameEn: String,
) {
    fun localizedName(fa: Boolean): String = if (fa) nameFa else nameEn
}

object CountryGeoMapper {
    private data class CountryEntry(val iso: String, val fa: String, val en: String)

    private val ENTRIES: Map<String, CountryEntry> = listOf(
        CountryEntry("AR", "آرژانتین", "Argentina"),
        CountryEntry("AU", "استرالیا", "Australia"),
        CountryEntry("AT", "اتریش", "Austria"),
        CountryEntry("BE", "بلژیک", "Belgium"),
        CountryEntry("BR", "برزیل", "Brazil"),
        CountryEntry("BG", "بلغارستان", "Bulgaria"),
        CountryEntry("CA", "کانادا", "Canada"),
        CountryEntry("CL", "شیلی", "Chile"),
        CountryEntry("CO", "کلمبیا", "Colombia"),
        CountryEntry("CY", "قبرس", "Cyprus"),
        CountryEntry("CZ", "جمهوری چک", "Czech Republic"),
        CountryEntry("DK", "دانمارک", "Denmark"),
        CountryEntry("FI", "فنلاند", "Finland"),
        CountryEntry("FR", "فرانسه", "France"),
        CountryEntry("DE", "آلمان", "Germany"),
        CountryEntry("GR", "یونان", "Greece"),
        CountryEntry("HU", "مجارستان", "Hungary"),
        CountryEntry("IE", "ایرلند", "Ireland"),
        CountryEntry("IT", "ایتالیا", "Italy"),
        CountryEntry("JP", "ژاپن", "Japan"),
        CountryEntry("KE", "کنیا", "Kenya"),
        CountryEntry("LV", "لتونی", "Latvia"),
        CountryEntry("LT", "لیتوانی", "Lithuania"),
        CountryEntry("MY", "مالزی", "Malaysia"),
        CountryEntry("MX", "مکزیک", "Mexico"),
        CountryEntry("MD", "مولداوی", "Moldova"),
        CountryEntry("NL", "هلند", "Netherlands"),
        CountryEntry("NZ", "نیوزیلند", "New Zealand"),
        CountryEntry("NG", "نیجریه", "Nigeria"),
        CountryEntry("NO", "نروژ", "Norway"),
        CountryEntry("PE", "پرو", "Peru"),
        CountryEntry("PL", "لهستان", "Poland"),
        CountryEntry("PT", "پرتغال", "Portugal"),
        CountryEntry("RO", "رومانی", "Romania"),
        CountryEntry("RS", "صربستان", "Serbia"),
        CountryEntry("SG", "سنگاپور", "Singapore"),
        CountryEntry("SI", "اسلوونی", "Slovenia"),
        CountryEntry("ZA", "آفریقای جنوبی", "South Africa"),
        CountryEntry("KR", "کره جنوبی", "South Korea"),
        CountryEntry("ES", "اسپانیا", "Spain"),
        CountryEntry("SE", "سوئد", "Sweden"),
        CountryEntry("CH", "سوئیس", "Switzerland"),
        CountryEntry("TW", "تایوان", "Taiwan"),
        CountryEntry("GB", "بریتانیا", "United Kingdom"),
        CountryEntry("US", "آمریکا", "United States"),
        CountryEntry("TR", "ترکیه", "Turkey"),
        CountryEntry("AE", "امارات", "UAE"),
        CountryEntry("HK", "هنگ‌کنگ", "Hong Kong"),
        CountryEntry("IN", "هند", "India"),
        CountryEntry("RU", "روسیه", "Russia"),
        CountryEntry("UA", "اوکراین", "Ukraine"),
    ).associateBy { it.iso }

    private val HOST_ALIASES = mapOf(
        "uk" to "GB",
        "us-central" to "US",
        "us-east" to "US",
        "us-west" to "US",
    )

    fun flagFromIso(isoCode: String?): String {
        val code = isoCode?.trim()?.uppercase(Locale.ROOT) ?: return "🌐"
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "🌐"
        val first = 0x1F1E6 + (code[0] - 'A')
        val second = 0x1F1E6 + (code[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun resolve(profile: Profile): CountryPlace {
        val host = profile.remotes.firstOrNull()?.host?.lowercase(Locale.ROOT).orEmpty()
        val hostPrefix = host.substringBefore('.')
        val isoFromHost = HOST_ALIASES[hostPrefix]
            ?: hostPrefix.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)

        val cleanedName = profile.name
            .replace(Regex("(?i)^tunnelbear[-_\\s]*"), "")
            .replace(Regex("(?i)[-_\\s]*tcp[-_\\s]*connect[-_\\s]*fix$"), "")
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()

        val iso = isoFromHost ?: matchIsoFromName(cleanedName)
        val entry = iso?.let { ENTRIES[it] }
        val usSuffix = resolveUsRegion(cleanedName, hostPrefix)

        return if (entry != null) {
            val faName = if (usSuffix != null) "${entry.fa} (${usSuffix.first})" else entry.fa
            val enName = if (usSuffix != null) "${entry.en} · ${usSuffix.second}" else entry.en
            CountryPlace(entry.iso, flagFromIso(entry.iso), faName, enName)
        } else if (iso != null) {
            val localeEn = Locale("", iso).getDisplayCountry(Locale.ENGLISH).ifBlank { cleanedName }
            val localeFa = Locale("", iso).getDisplayCountry(Locale("fa")).ifBlank { localeEn }
            CountryPlace(iso, flagFromIso(iso), localeFa, localeEn)
        } else {
            val fallback = cleanedName.ifBlank { profile.name }
            CountryPlace(null, "🌐", fallback, fallback)
        }
    }

    private fun matchIsoFromName(name: String): String? {
        val lower = name.lowercase(Locale.ROOT)
        if ("united kingdom" in lower || "england" in lower) return "GB"
        if ("united states" in lower) return "US"
        return ENTRIES.values.firstOrNull { entry ->
            lower.contains(entry.en.lowercase(Locale.ROOT))
        }?.iso
    }

    private fun resolveUsRegion(cleanedName: String, hostPrefix: String): Pair<String, String>? {
        val lower = cleanedName.lowercase(Locale.ROOT)
        return when {
            hostPrefix == "us-east" || lower.endsWith("east") -> "شرق" to "East"
            hostPrefix == "us-west" || lower.endsWith("west") -> "غرب" to "West"
            hostPrefix == "us-central" || lower.endsWith("central") -> "مرکز" to "Central"
            else -> null
        }
    }
}
