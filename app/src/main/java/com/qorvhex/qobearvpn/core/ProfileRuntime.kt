package com.qorvhex.qobearvpn.core

import com.qorvhex.qobearvpn.model.Profile

object ProfileRuntime {
    const val TUNNELBEAR_TCP_PORT = 7011

    fun isTunnelBear(profile: Profile): Boolean =
        !profile.isCustom &&
            profile.remotes.isNotEmpty() &&
            profile.remotes.all { it.host.endsWith(".lazerpenguin.com", ignoreCase = true) }

    fun supportsTcpRelay(profile: Profile): Boolean =
        isTunnelBear(profile) ||
            profile.remotes.firstOrNull()?.protocol?.startsWith("tcp", ignoreCase = true) == true

    /**
     * Prepares the runtime profile string fed to OpenVPN 3:
     * - Strips inline `<auth-user-pass>` blocks while preserving `auth-user-pass`.
     * - Ensures a `cipher` line exists alongside `data-ciphers`.
     * - Rewrites `remote` to loopback `127.0.0.1 <relayPort> tcp-client` when SplitRelay is active.
     * - Prepends `pull-filter ignore` before custom DNS directives so server DNS cannot override or leak via DNS6.
     */
    fun effective(
        profile: Profile,
        forceTcp: Boolean,
        relayPort: Int? = null,
        customDns: List<String> = emptyList(),
    ): String {
        val raw = profile.config.replace("\r\n", "\n").split("\n")
        val lines = mutableListOf<String>()
        var inAuth = false
        for (line in raw) {
            val trimmed = line.trim()
            if (trimmed == "<auth-user-pass>") {
                inAuth = true
                lines += "auth-user-pass"
                continue
            }
            if (inAuth) {
                if (trimmed == "</auth-user-pass>") inAuth = false
                continue
            }
            if (customDns.isNotEmpty() && isDnsDirective(trimmed)) {
                continue
            }
            if (customDns.isNotEmpty() && trimmed.startsWith("pull-filter", ignoreCase = true) &&
                trimmed.contains("dns", ignoreCase = true)
            ) {
                continue
            }
            lines += line
        }

        val directive = { l: String -> l.trim().substringBefore(' ').lowercase() }

        if (lines.none { directive(it) == "cipher" }) {
            val firstCipher = lines.firstOrNull { directive(it) == "data-ciphers" }
                ?.trim()
                ?.substringAfter(' ')
                ?.split(':')
                ?.firstOrNull()
                ?.trim()
            if (!firstCipher.isNullOrBlank()) {
                lines.add("cipher $firstCipher")
            }
        }

        val moveToTcp = (forceTcp || relayPort != null) && isTunnelBear(profile)
        if (moveToTcp) {
            for (i in lines.indices) {
                val t = lines[i].trim()
                when (directive(t)) {
                    "proto" -> lines[i] = "proto tcp-client"
                    "remote" -> {
                        val parts = t.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            lines[i] = if (relayPort != null) {
                                "remote 127.0.0.1 $relayPort tcp-client"
                            } else {
                                "remote ${parts[1]} $TUNNELBEAR_TCP_PORT tcp-client"
                            }
                        }
                    }
                    "explicit-exit-notify" -> lines[i] = ""
                }
            }
        } else if (relayPort != null) {
            for (i in lines.indices) {
                val t = lines[i].trim()
                when (directive(t)) {
                    "proto" -> lines[i] = "proto tcp-client"
                    "remote" -> lines[i] = "remote 127.0.0.1 $relayPort tcp-client"
                    "explicit-exit-notify" -> lines[i] = ""
                }
            }
        }

        val finalLines = mutableListOf<String>()
        if (customDns.isNotEmpty()) {
            // Prepend ignore filters before any profile directives (first-match-wins in OpenVPN 3).
            // Note: `pull-filter ignore "dhcp-option DNS"` prefix-matches both DNS and DNS6 in OpenVPN 3 optfilt.hpp.
            finalLines += "pull-filter ignore \"dhcp-option DNS\""
            finalLines += "pull-filter ignore \"dns\""
        }
        finalLines += lines.filter { it.isNotBlank() }
        if (customDns.isNotEmpty()) {
            customDns.forEach { dns -> finalLines += "dhcp-option DNS $dns" }
        }

        return finalLines.joinToString("\n")
    }

    private fun isDnsDirective(trimmedLine: String): Boolean {
        val lower = trimmedLine.lowercase()
        return lower.startsWith("dhcp-option dns") || lower.startsWith("dhcp-option dns6")
    }
}
