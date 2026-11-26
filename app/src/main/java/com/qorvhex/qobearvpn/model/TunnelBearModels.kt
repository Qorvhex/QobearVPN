package com.qorvhex.qobearvpn.model

enum class AuthState { UNKNOWN, ACCEPTED, FAILED }

data class Account(
    val id: String,
    val username: String,
    val auth: AuthState = AuthState.UNKNOWN,
    val lastConnected: Long? = null,
    val lastError: String? = null,
    val credentialRef: String = id,
)

data class Remote(val host: String, val port: Int, val protocol: String)

data class ProbeResult(
    val millis: Long?,
    val checkedAt: Long,
    val method: String,
    val error: String? = null,
    val healthy: List<String> = emptyList(),
) {
    val isReachable: Boolean get() = millis != null && error == null
}

data class Profile(
    val id: String,
    val name: String,
    val config: String,
    val remotes: List<Remote>,
    val authenticatedControl: Boolean = false,
    val favorite: Boolean = false,
    val isCustom: Boolean = false,
    val probe: ProbeResult? = null,
) {
    val ownCredentials: Pair<String, String>? get() = inlineCredentials(config)

    val selfContained: Boolean
        get() = ownCredentials != null ||
            config.lineSequence().none { it.trim().lowercase().startsWith("auth-user-pass") }

    companion object {
        fun inlineCredentials(config: String): Pair<String, String>? {
            val lines = config.lineSequence().map { it.trim() }.toList()
            val start = lines.indexOf("<auth-user-pass>")
            if (start < 0) return null
            val end = lines.indexOf("</auth-user-pass>")
            if (end < start) return null
            val body = lines.subList(start + 1, end).filter { it.isNotEmpty() }
            return body.getOrNull(0)?.let { it to body.getOrElse(1) { "" } }
        }
    }
}

enum class ConnectionPhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    ERROR;

    val isActive: Boolean get() = this != DISCONNECTED && this != ERROR
}

data class TrafficSample(val rxPerSec: Long, val txPerSec: Long)

data class OpenVpnConnection(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val profileId: String? = null,
    val accountId: String? = null,
    val bypassRegion: BypassRegion = BypassRegion.IRAN,
    val connectedAt: Long? = null,
    val received: Long = 0,
    val sent: Long = 0,
    val rxSpeedBps: Long = 0,
    val txSpeedBps: Long = 0,
    val speedHistory: List<TrafficSample> = emptyList(),
    val error: String? = null,
    val detail: String? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
) {
    val active: Boolean get() = phase.isActive
}

object ServerPolicy {
    private const val PROBE_FRESH_MS = 10 * 60 * 1000L

    fun fastest(profiles: List<Profile>, now: Long, allowStale: Boolean = false): Profile? {
        val fresh = profiles.filter { p ->
            val probe = p.probe ?: return@filter false
            probe.isReachable && (allowStale || (now - probe.checkedAt in 0..PROBE_FRESH_MS))
        }
        return fresh.minByOrNull { it.probe!!.millis!! }
    }
}
