package com.qorvhex.qobearvpn.model

enum class BypassRegion(
    val id: String,
    val flag: String,
    val cidrAsset: String?,
    val relayProfile: RelayProfile?,
) {
    IRAN(
        id = "iran",
        flag = "🇮🇷",
        cidrAsset = "geoip/ir.cidr",
        relayProfile = RelayProfile(
            splitBytes = 6 * 1024,
            minPiece = 3,
            maxPiece = 29,
            pauseMinMs = 2,
            pauseSpreadMs = 6,
            clampWindow = 1024,
            unclampBytes = 12 * 1024L,
            unclampMs = 20_000L,
        ),
    ),
    CHINA(
        id = "china",
        flag = "🇨🇳",
        cidrAsset = "geoip/cn.cidr",
        relayProfile = RelayProfile(
            splitBytes = 8 * 1024,
            minPiece = 2,
            maxPiece = 19,
            pauseMinMs = 3,
            pauseSpreadMs = 9,
            clampWindow = 768,
            unclampBytes = 16 * 1024L,
            unclampMs = 22_000L,
        ),
    ),
    RUSSIA(
        id = "russia",
        flag = "🇷🇺",
        cidrAsset = "geoip/ru.cidr",
        relayProfile = RelayProfile(
            splitBytes = 6 * 1024,
            minPiece = 4,
            maxPiece = 32,
            pauseMinMs = 2,
            pauseSpreadMs = 6,
            clampWindow = 1024,
            unclampBytes = 12 * 1024L,
            unclampMs = 20_000L,
        ),
    ),
    GLOBAL(
        id = "global",
        flag = "🌐",
        cidrAsset = null,
        relayProfile = null,
    );

    companion object {
        fun fromId(id: String?): BypassRegion =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IRAN
    }
}

data class RelayProfile(
    val splitBytes: Int,
    val minPiece: Int,
    val maxPiece: Int,
    val pauseMinMs: Int,
    val pauseSpreadMs: Int,
    val clampWindow: Int,
    val unclampBytes: Long,
    val unclampMs: Long,
)

enum class DnsPreset(val id: String, val servers: List<String>) {
    TUNNELBEAR("tunnelbear", emptyList()),
    CLOUDFLARE("cloudflare", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("google", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("quad9", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("adguard", listOf("94.140.14.14", "94.140.15.15")),
    CUSTOM("custom", emptyList());

    companion object {
        fun fromId(id: String?): DnsPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLOUDFLARE
    }
}

enum class SplitTunnelMode(val id: String) {
    ALL("all"),
    INCLUDE("include"),
    EXCLUDE("exclude");

    companion object {
        fun fromId(id: String?): SplitTunnelMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
    }
}

enum class AppThemeMode(val id: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DARK
    }
}

enum class AppLanguage(val id: String) {
    FA("fa"),
    EN("en");

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FA
    }
}
