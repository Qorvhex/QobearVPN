package com.qorvhex.qobearvpn.data

import android.content.Context
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.AppThemeMode
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.DnsPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val bypassRegion: BypassRegion = BypassRegion.IRAN,
    val language: AppLanguage = AppLanguage.FA,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dnsPreset: DnsPreset = DnsPreset.CLOUDFLARE,
    val customDns: String = "",
    val lanBypass: Boolean = true,
    val autoReconnect: Boolean = true,
    val globalAutoFallbackRelay: Boolean = true,
    val autoConnectBestServer: Boolean = true,
) {
    fun effectiveDnsServers(): List<String> = when (dnsPreset) {
        DnsPreset.TUNNELBEAR -> emptyList()
        DnsPreset.CUSTOM -> parseValidIpv4List(customDns).orEmpty().ifEmpty { DnsPreset.CLOUDFLARE.servers }
        else -> dnsPreset.servers
    }

    companion object {
        private val STRICT_IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
        )

        /**
         * Returns null if any non-empty token is invalid (so callers can surface exact validation errors),
         * or a validated list of 1..2 non-loopback/non-unspecified IPv4 resolvers.
         */
        fun parseValidIpv4List(input: String): List<String>? {
            val tokens = input.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty() || tokens.size > 2) return null
            val out = mutableListOf<String>()
            for (token in tokens) {
                if (!STRICT_IPV4_REGEX.matches(token)) return null
                val firstOctet = token.substringBefore('.').toIntOrNull() ?: return null
                if (firstOctet == 0 || firstOctet == 127 || firstOctet >= 224) return null
                val valid = runCatching { InetAddress.getByName(token) is Inet4Address }.getOrDefault(false)
                if (!valid) return null
                if (token !in out) out += token
            }
            return out
        }
    }
}

class SettingsStore private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("qobear_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDED, false),
        bypassRegion = BypassRegion.fromId(prefs.getString(KEY_BYPASS_REGION, BypassRegion.IRAN.id)),
        language = AppLanguage.fromId(prefs.getString(KEY_LANGUAGE, AppLanguage.FA.id)),
        themeMode = AppThemeMode.fromId(prefs.getString(KEY_THEME, AppThemeMode.DARK.id)),
        dnsPreset = DnsPreset.fromId(prefs.getString(KEY_DNS_PRESET, DnsPreset.CLOUDFLARE.id)),
        customDns = prefs.getString(KEY_CUSTOM_DNS, "").orEmpty(),
        lanBypass = prefs.getBoolean(KEY_LAN_BYPASS, true),
        autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
        globalAutoFallbackRelay = prefs.getBoolean(KEY_GLOBAL_FALLBACK, true),
        autoConnectBestServer = prefs.getBoolean(KEY_AUTO_SERVER, true),
    )

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_ONBOARDED, next.onboardingCompleted)
            .putString(KEY_BYPASS_REGION, next.bypassRegion.id)
            .putString(KEY_LANGUAGE, next.language.id)
            .putString(KEY_THEME, next.themeMode.id)
            .putString(KEY_DNS_PRESET, next.dnsPreset.id)
            .putString(KEY_CUSTOM_DNS, next.customDns)
            .putBoolean(KEY_LAN_BYPASS, next.lanBypass)
            .putBoolean(KEY_AUTO_RECONNECT, next.autoReconnect)
            .putBoolean(KEY_GLOBAL_FALLBACK, next.globalAutoFallbackRelay)
            .putBoolean(KEY_AUTO_SERVER, next.autoConnectBestServer)
            .apply()
        _settings.value = next
    }

    companion object {
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_BYPASS_REGION = "bypass_region"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_DNS_PRESET = "dns_preset"
        private const val KEY_CUSTOM_DNS = "custom_dns"
        private const val KEY_LAN_BYPASS = "lan_bypass"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_GLOBAL_FALLBACK = "global_fallback_relay"
        private const val KEY_AUTO_SERVER = "auto_server"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
