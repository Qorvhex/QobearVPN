package com.qorvhex.qobearvpn.core

import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.ProfileImportException
import com.qorvhex.qobearvpn.model.Remote
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Locale

object ProfileImporter {
    const val MAX_BYTES = 1_048_576

    private val fileDirectives = setOf(
        "ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2", "extra-certs"
    )

    private val allowedDirectives = setOf(
        "client", "dev", "dev-type", "proto", "remote", "port", "rport", "nobind",
        "remote-cert-tls", "verify-x509-name", "peer-fingerprint", "persist-key", "persist-tun",
        "reneg-sec", "dhcp-option", "redirect-gateway", "redirect-private", "route", "route-ipv6",
        "route-metric", "route-nopull", "route-delay", "route-gateway", "verb", "mute",
        "auth-user-pass", "auth-nocache", "data-ciphers", "cipher", "data-ciphers-fallback",
        "auth", "tls-version-min", "tls-version-max", "tls-cipher", "tls-ciphersuites",
        "tls-client", "key-direction", "resolv-retry", "connect-retry", "connect-retry-max",
        "connect-timeout", "server-poll-timeout", "remote-random", "remote-random-hostname",
        "explicit-exit-notify", "tun-mtu", "mssfix", "sndbuf", "rcvbuf", "ping",
        "ping-restart", "keepalive", "pull", "topology", "ifconfig", "ifconfig-ipv6", "auth-retry"
    ) + fileDirectives

    private val weakCiphers = setOf("none", "null", "rc4", "des-cbc", "des-ede-cbc", "des-ede3-cbc", "bf-cbc")
    private val weakAuths = setOf("none", "null", "md5", "sha1")

    fun parse(
        name: String,
        text: String,
        companions: Map<String, String> = emptyMap(),
        isCustom: Boolean = false,
    ): Profile {
        if (text.toByteArray().size > MAX_BYTES || '\u0000' in text) {
            throw ProfileImportException("Profile too large or contains invalid characters")
        }
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.toList()
        val out = mutableListOf<String>()
        val directives = mutableListOf<List<String>>()
        var block: String? = null
        val blockContent = StringBuilder()
        var skipping: String? = null
        var hasCa = false
        var controlAuth = false
        var inlineAuth = false

        for (line in lines) {
            if (skipping != null) {
                if (line == "</$skipping>") skipping = null
                continue
            }
            if (block != null) {
                out += line
                if (line == "</$block>") {
                    if (block == "ca") {
                        validateX509Certificate(blockContent.toString())
                    }
                    blockContent.clear()
                    block = null
                } else {
                    blockContent.append(line).append('\n')
                }
                continue
            }
            if (line.isBlank() || line.startsWith('#') || line.startsWith(';')) continue
            if (line.startsWith('<')) {
                val tag = line.removePrefix("<").removeSuffix(">")
                if (line != "<$tag>" || tag.startsWith('/')) {
                    throw ProfileImportException("Malformed inline XML tag: $line")
                }
                if (tag == "auth-user-pass") {
                    inlineAuth = true
                    block = tag
                    out += line
                    continue
                }
                if (tag !in fileDirectives) {
                    skipping = tag
                    continue
                }
                if (tag == "ca") hasCa = true
                if (tag.startsWith("tls-")) controlAuth = true
                block = tag
                blockContent.clear()
                out += line
                continue
            }

            val words = tokenize(line)
            if (words.isEmpty()) continue
            val key = words[0].removePrefix("--").lowercase(Locale.ROOT)
            if (key !in allowedDirectives) continue

            val parts = if (key == "auth-user-pass") listOf(key) else listOf(key) + words.drop(1)
            validateSecurityPolicy(key, parts)

            if (key in fileDirectives) {
                if (parts.size !in 2..3) throw ProfileImportException("Invalid certificate reference")
                val ref = parts[1]
                if (ref.contains('/') || ref.contains('\\') || ref.contains(':') || ref == "..") {
                    throw ProfileImportException("Invalid companion reference: $ref")
                }
                val content = companions[ref]
                    ?: throw ProfileImportException("Missing companion file: $ref", ref)
                if (content.toByteArray().size > MAX_BYTES || '\u0000' in content || content.contains("</$key>")) {
                    throw ProfileImportException("Invalid companion content: $ref")
                }
                if (key == "ca") {
                    validateX509Certificate(content)
                    hasCa = true
                }
                if (key.startsWith("tls-")) controlAuth = true
                val canonical = content.trim().lineSequence().joinToString("\n") { it.trim() }
                out += "<$key>\n$canonical\n</$key>"
                if (parts.size == 3) {
                    if (key != "tls-auth" || parts[2] !in listOf("0", "1")) {
                        throw ProfileImportException("Invalid key-direction")
                    }
                    out += "key-direction ${parts[2]}"
                }
            } else {
                directives += parts
                if (key != "verb" && key != "mute" && key != "auth-nocache") {
                    out += parts.joinToString(" ") { quote(it) }
                }
            }
        }

        if (block != null || skipping != null) {
            throw ProfileImportException("Unclosed inline section")
        }
        if (inlineAuth) {
            val auth = Profile.inlineCredentials(out.joinToString("\n"))
            if (auth == null || auth.first.isBlank()) {
                throw ProfileImportException("Invalid inline credentials")
            }
        }
        if (!hasCa && directives.none { it[0] == "peer-fingerprint" }) {
            throw ProfileImportException("A CA certificate or peer-fingerprint is required")
        }
        if (directives.none { it[0] == "remote-cert-tls" && it.getOrNull(1) == "server" } &&
            directives.none { it[0] == "peer-fingerprint" }
        ) {
            out += "remote-cert-tls server"
        }

        val defaultProto = directives.lastOrNull { it[0] == "proto" }?.getOrNull(1) ?: "udp"
        val defaultPort = directives.lastOrNull { it[0] in setOf("port", "rport") }?.getOrNull(1) ?: "1194"
        val remotes = directives.filter { it[0] == "remote" }.map { d ->
            val host = d.getOrNull(1) ?: throw ProfileImportException("Missing remote host")
            val port = (d.getOrNull(2) ?: defaultPort).toIntOrNull()
                ?: throw ProfileImportException("Invalid remote port")
            val proto = d.getOrNull(3) ?: defaultProto
            if (port !in 1..65535 || host.any(Char::isWhitespace) ||
                proto !in setOf("udp", "udp4", "udp6", "tcp-client", "tcp4-client", "tcp6-client", "tcp")
            ) {
                throw ProfileImportException("Invalid remote directive")
            }
            Remote(host, port, proto)
        }
        if (remotes.isEmpty()) throw ProfileImportException("No remote server defined")

        val config = out.joinToString("\n") + "\nverb 3\n"
        if (config.toByteArray().size > MAX_BYTES) throw ProfileImportException("Expanded profile exceeds limit")

        val id = MessageDigest.getInstance("SHA-256")
            .digest(config.toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        val cleanName = name
            .removeSuffix(".txt")
            .removeSuffix(".ovpn")
            .removeSuffix("-TCP-connect-fix")
            .removeSuffix("-connect-fix")
            .take(120)

        return Profile(
            id = id,
            name = cleanName,
            config = config,
            remotes = remotes,
            authenticatedControl = controlAuth,
            isCustom = isCustom,
        )
    }

    private fun validateSecurityPolicy(key: String, parts: List<String>) {
        when (key) {
            "dev" -> if (parts.getOrNull(1)?.startsWith("tun") != true) {
                throw ProfileImportException("Only TUN profiles are supported")
            }
            "cipher", "data-ciphers-fallback" -> {
                val c = parts.getOrNull(1)?.lowercase(Locale.ROOT)
                if (c in weakCiphers) throw ProfileImportException("Insecure cipher rejected: $c")
            }
            "data-ciphers" -> {
                val ciphers = parts.getOrNull(1)?.lowercase(Locale.ROOT)?.split(':').orEmpty()
                if (ciphers.any { it.trim() in weakCiphers }) {
                    throw ProfileImportException("Insecure data-ciphers entry rejected")
                }
            }
            "auth" -> {
                val a = parts.getOrNull(1)?.lowercase(Locale.ROOT)
                if (a in weakAuths) throw ProfileImportException("Insecure HMAC auth rejected: $a")
            }
            "tls-version-min" -> {
                val v = parts.getOrNull(1)?.toDoubleOrNull()
                if (v != null && v < 1.2) throw ProfileImportException("TLS < 1.2 is not permitted")
            }
            "remote-cert-tls" -> {
                if (parts.getOrNull(1)?.lowercase(Locale.ROOT) != "server") {
                    throw ProfileImportException("remote-cert-tls must require server")
                }
            }
        }
    }

    private fun validateX509Certificate(pem: String) {
        try {
            val certs = CertificateFactory.getInstance("X.509")
                .generateCertificates(pem.trim().byteInputStream())
            require(certs.isNotEmpty())
        } catch (_: Exception) {
            throw ProfileImportException("Invalid CA certificate")
        }
    }

    private fun quote(s: String): String =
        if (s.any { it.isWhitespace() || it in "'\"\\" } || s.startsWith('#') || s.startsWith(';')) {
            "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            s
        }

    private fun tokenize(line: String): List<String> {
        val result = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (c in line) {
            when {
                escaped -> {
                    word.append(c)
                    escaped = false
                }
                c == '\\' && quote != '\'' -> escaped = true
                quote != null -> if (c == quote) quote = null else word.append(c)
                c == '"' || c == '\'' -> quote = c
                c == '#' || c == ';' -> break
                c.isWhitespace() -> if (word.isNotEmpty()) {
                    result += word.toString()
                    word.clear()
                }
                else -> word.append(c)
            }
        }
        if (word.isNotEmpty()) result += word.toString()
        return result
    }
}
