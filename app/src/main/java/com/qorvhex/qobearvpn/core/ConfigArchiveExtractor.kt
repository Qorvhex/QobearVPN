package com.qorvhex.qobearvpn.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Normalizes batch-selected config inputs and `.zip` archives into safe, bounded filename -> text maps.
 * Enforces strict basename sanitization (no path traversal) and bounded extraction (Zip-Bomb defense).
 */
object ConfigArchiveExtractor {
    const val MAX_SELECTED_ITEMS = 120
    const val MAX_ARCHIVE_ENTRIES = 200
    const val MAX_ENTRY_BYTES = ProfileImporter.MAX_BYTES
    const val MAX_TOTAL_BYTES = 16 * 1024 * 1024

    private val companionExtensions = setOf("crt", "key", "pem")
    private val ovpnRemoteRegex = Regex("(?m)^\\s*remote\\s+\\S+")
    private val ovpnClientRegex = Regex("(?m)^\\s*client\\s*$")

    fun collectFromInputs(inputs: List<Pair<String, ByteArray>>): Map<String, String> {
        require(inputs.size <= MAX_SELECTED_ITEMS) { "Too many files selected" }
        val result = linkedMapOf<String, String>()
        var totalBytes = 0
        var archiveEntriesSeen = 0

        for ((rawName, bytes) in inputs) {
            if (isZipPayload(rawName, bytes)) {
                ZipInputStream(bytes.inputStream()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        archiveEntriesSeen++
                        require(archiveEntriesSeen <= MAX_ARCHIVE_ENTRIES) { "Archive contains too many entries" }
                        try {
                            if (entry.isDirectory) continue
                            val entryPath = entry.name.replace('\\', '/')
                            if (entryPath.startsWith("__MACOSX/", ignoreCase = true)) continue
                            val base = safeBasename(entryPath) ?: continue
                            if (base.startsWith(".")) continue

                            val lower = base.lowercase()
                            val ext = lower.substringAfterLast('.', "")
                            val candidateProfile = lower.endsWith(".ovpn") ||
                                lower.endsWith(".ovpn.txt") ||
                                ext == "conf"
                            val candidateCompanion = ext in companionExtensions
                            if (!candidateProfile && !candidateCompanion && ext != "txt") continue

                            val extracted = readBoundedEntry(zis, MAX_ENTRY_BYTES)
                            totalBytes += extracted.size
                            require(totalBytes <= MAX_TOTAL_BYTES) { "Extracted import size limit exceeded" }

                            val text = extracted.toString(Charsets.UTF_8)
                            if (candidateCompanion) {
                                addUnique(result, base, text)
                            } else if (candidateProfile || looksLikeOvpn(text)) {
                                addUnique(result, normalizeOvpnName(base, text), text)
                            }
                        } finally {
                            zis.closeEntry()
                        }
                    }
                }
            } else {
                totalBytes += bytes.size
                require(totalBytes <= MAX_TOTAL_BYTES) { "Import size limit exceeded" }
                val base = safeBasename(rawName) ?: "profile_${result.size + 1}.ovpn"
                val text = bytes.toString(Charsets.UTF_8)
                val finalName = if (looksLikeOvpn(text) && !isKnownOvpnExtension(base)) {
                    normalizeOvpnName(base, text)
                } else {
                    base
                }
                addUnique(result, finalName, text)
            }
        }
        return result
    }

    private fun isZipPayload(name: String, bytes: ByteArray): Boolean {
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte())
        ) {
            return true
        }
        return name.trim().lowercase().endsWith(".zip")
    }

    private fun readBoundedEntry(input: ZipInputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            require(out.size() + n <= maxBytes) { "Archive entry exceeds maximum profile size" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun safeBasename(raw: String): String? {
        val base = raw.replace('\\', '/')
            .substringAfterLast('/')
            .trim()
            .take(160)
        if (base.isEmpty() || base == "." || base == "..") return null
        if ('\u0000' in base) return null
        return base
    }

    private fun looksLikeOvpn(text: String): Boolean =
        ovpnRemoteRegex.containsMatchIn(text) &&
            (ovpnClientRegex.containsMatchIn(text) || text.contains("<ca>"))

    private fun isKnownOvpnExtension(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".ovpn") || lower.endsWith(".ovpn.txt")
    }

    private fun normalizeOvpnName(name: String, text: String): String {
        if (isKnownOvpnExtension(name)) return name
        if (!looksLikeOvpn(text) && !name.lowercase().endsWith(".conf")) return name
        val stem = name.substringBeforeLast('.').ifEmpty { name }
        return "$stem.ovpn".take(160)
    }

    private fun addUnique(target: MutableMap<String, String>, preferredName: String, content: String) {
        var key = preferredName
        var counter = 2
        while (key in target) {
            val stem = preferredName.substringBeforeLast('.')
            val ext = preferredName.substringAfterLast('.', "")
            key = if (ext.isNotEmpty() && '.' in preferredName) {
                "${stem}_$counter.$ext".take(160)
            } else {
                "${preferredName}_$counter".take(160)
            }
            counter++
        }
        target[key] = content
    }
}
