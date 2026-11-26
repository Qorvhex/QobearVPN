package com.qorvhex.qobearvpn.core

import android.content.Context
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.BypassRegion
import java.net.Inet4Address
import java.net.InetAddress

data class CidrPrefix(val networkLong: Long, val prefixLen: Int) {
    init {
        require(prefixLen in 0..32)
    }

    fun contains(ipLong: Long): Boolean {
        if (prefixLen == 0) return true
        val mask = (0xFFFF_FFFFL shl (32 - prefixLen)) and 0xFFFF_FFFFL
        return (ipLong and mask) == (networkLong and mask)
    }

    fun overlaps(other: CidrPrefix): Boolean {
        val minPrefix = minOf(prefixLen, other.prefixLen)
        if (minPrefix == 0) return true
        val mask = (0xFFFF_FFFFL shl (32 - minPrefix)) and 0xFFFF_FFFFL
        return (networkLong and mask) == (other.networkLong and mask)
    }

    fun toInetAddress(): InetAddress {
        val bytes = byteArrayOf(
            ((networkLong ushr 24) and 0xFF).toByte(),
            ((networkLong ushr 16) and 0xFF).toByte(),
            ((networkLong ushr 8) and 0xFF).toByte(),
            (networkLong and 0xFF).toByte(),
        )
        return InetAddress.getByAddress(bytes)
    }

    fun format(): String = "${toInetAddress().hostAddress}/$prefixLen"

    companion object {
        fun ipv4ToLong(ip: Inet4Address): Long {
            val b = ip.address
            return ((b[0].toLong() and 0xFF) shl 24) or
                ((b[1].toLong() and 0xFF) shl 16) or
                ((b[2].toLong() and 0xFF) shl 8) or
                (b[3].toLong() and 0xFF)
        }

        fun parse(cidr: String): CidrPrefix? {
            val trimmed = cidr.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            val slash = trimmed.indexOf('/')
            if (slash <= 0) return null
            val ipPart = trimmed.substring(0, slash).trim()
            val prefixPart = trimmed.substring(slash + 1).trim().toIntOrNull() ?: return null
            if (prefixPart !in 0..32) return null
            val octets = ipPart.split('.')
            if (octets.size != 4) return null
            var addr = 0L
            for (o in octets) {
                val v = o.toIntOrNull() ?: return null
                if (v !in 0..255) return null
                addr = (addr shl 8) or v.toLong()
            }
            val mask = if (prefixPart == 0) 0L else (0xFFFF_FFFFL shl (32 - prefixPart)) and 0xFFFF_FFFFL
            return CidrPrefix(addr and mask, prefixLen = prefixPart)
        }
    }
}

object GeoBypassRouter {
    const val MAX_API33_EXCLUDES = 1280
    const val MAX_LEGACY_OUTPUT_ROUTES = 768

    val LAN_IPV4_CIDRS: List<CidrPrefix> = listOf(
        "10.0.0.0/8",
        "100.64.0.0/10",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
    ).mapNotNull { CidrPrefix.parse(it) }

    val LAN_IPV6_CIDRS: List<Pair<String, Int>> = listOf(
        "fc00::" to 7,
        "fe80::" to 10,
    )

    fun loadCountryCidrs(context: Context, region: BypassRegion): List<CidrPrefix> {
        val asset = region.cidrAsset ?: return emptyList()
        return runCatching {
            context.assets.open(asset).bufferedReader().useLines { lines ->
                lines.mapNotNull { CidrPrefix.parse(it) }.toList()
            }
        }.onFailure { err ->
            ConnectionLogStore.append("GeoBypass", "Failed loading $asset: ${err.message}", isError = true)
        }.getOrDefault(emptyList())
    }

    /**
     * Builds the bounded list of IPv4 CIDR exclusions for Android 13+ (`VpnService.Builder.excludeRoute`).
     * VPN sockets are already protected via `VpnService.protect()`, so domestic/LAN blocks stay intact
     * even if an endpoint resides in a nearby range.
     */
    fun buildApi33Exclusions(
        countryCidrs: List<CidrPrefix>,
        lanBypass: Boolean,
        cap: Int = MAX_API33_EXCLUDES,
    ): List<CidrPrefix> {
        val combined = buildList {
            if (lanBypass) addAll(LAN_IPV4_CIDRS)
            addAll(countryCidrs)
        }
        return combined.distinct().take(cap)
    }

    /**
     * Exact binary-trie complement of `(countryCidrs + LAN)` subtracted from `0.0.0.0/0`
     * for Android 7–12 (`API 24..32`), where `excludeRoute` does not exist.
     */
    fun buildLegacyComplementRoutes(
        countryCidrs: List<CidrPrefix>,
        lanBypass: Boolean,
        maxOutputRoutes: Int = MAX_LEGACY_OUTPUT_ROUTES,
    ): List<CidrPrefix> {
        val exclusions = buildApi33Exclusions(
            countryCidrs = countryCidrs,
            lanBypass = lanBypass,
            cap = 512,
        )
        if (exclusions.isEmpty()) return listOf(CidrPrefix(0L, 0))

        val trie = TrieNode(State.INCLUDED)
        var lastSafeRoutes = listOf(CidrPrefix(0L, 0))

        for (cidr in exclusions) {
            trie.exclude(cidr.networkLong, cidr.prefixLen, 0)
            val candidate = mutableListOf<CidrPrefix>()
            trie.collectIncluded(0L, 0, candidate)
            if (candidate.size <= maxOutputRoutes) {
                lastSafeRoutes = candidate
            } else {
                return lastSafeRoutes.ifEmpty { listOf(CidrPrefix(0L, 0)) }
            }
        }
        return lastSafeRoutes
    }

    private enum class State { INCLUDED, EXCLUDED, MIXED }

    private class TrieNode(
        var state: State,
        var left: TrieNode? = null,
        var right: TrieNode? = null,
    ) {
        fun exclude(network: Long, prefixLen: Int, depth: Int) {
            if (state == State.EXCLUDED) return
            if (depth == prefixLen) {
                state = State.EXCLUDED
                left = null
                right = null
                return
            }
            if (state == State.INCLUDED) {
                state = State.MIXED
                left = TrieNode(State.INCLUDED)
                right = TrieNode(State.INCLUDED)
            }
            val bit = (network ushr (31 - depth)) and 1L
            if (bit == 0L) {
                left!!.exclude(network, prefixLen, depth + 1)
            } else {
                right!!.exclude(network, prefixLen, depth + 1)
            }
            if (left?.state == State.EXCLUDED && right?.state == State.EXCLUDED) {
                state = State.EXCLUDED
                left = null
                right = null
            }
        }

        fun collectIncluded(addr: Long, depth: Int, out: MutableList<CidrPrefix>) {
            when (state) {
                State.INCLUDED -> out += CidrPrefix(addr, depth)
                State.EXCLUDED -> Unit
                State.MIXED -> {
                    val nextDepth = depth + 1
                    left?.collectIncluded(addr, nextDepth, out)
                    right?.collectIncluded(addr or (1L shl (31 - depth)), nextDepth, out)
                }
            }
        }
    }
}
