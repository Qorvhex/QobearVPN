package com.qorvhex.qobearvpn.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qorvhex.qobearvpn.core.ProfileRuntime
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

object OpenVpnRuntime {
    internal val mutable = MutableStateFlow(OpenVpnConnection())
    val connection: StateFlow<OpenVpnConnection> = mutable.asStateFlow()

    @Volatile
    internal var socketProtector: ((Socket) -> Boolean)? = null

    @Volatile
    internal var datagramProtector: ((DatagramSocket) -> Boolean)? = null

    const val SELF_CREDENTIAL = "@profile"
    const val CUSTOM_PROFILE_CREDENTIAL = "@custom"

    private val transientCredentials = ConcurrentHashMap<String, Pair<String, String>>()

    fun setTransientProfileCredentials(profileId: String, username: String, password: String) {
        transientCredentials[profileId] = username to password
    }

    fun transientProfileCredentials(profileId: String): Pair<String, String>? =
        transientCredentials[profileId]

    fun hasTransientProfileCredentials(profileId: String): Boolean =
        transientCredentials.containsKey(profileId)

    fun clearTransientProfileCredentials(profileId: String) {
        transientCredentials.remove(profileId)
    }

    fun clearAllTransientProfileCredentials() {
        transientCredentials.clear()
    }

    fun resolveAccountId(profile: Profile, activeAccountId: String?): String? {
        val isTb = ProfileRuntime.isTunnelBear(profile)
        return when {
            profile.selfContained && !isTb -> SELF_CREDENTIAL
            !isTb -> {
                if (profile.hasSavedCredentials || hasTransientProfileCredentials(profile.id)) {
                    CUSTOM_PROFILE_CREDENTIAL
                } else {
                    null
                }
            }
            else -> activeAccountId
        }
    }

    fun protect(socket: Socket): Boolean = socketProtector?.invoke(socket) ?: true

    fun protect(socket: DatagramSocket): Boolean = datagramProtector?.invoke(socket) ?: true

    fun connect(context: Context, profileId: String, accountId: String): Boolean {
        val intent = Intent(context, QobearVpnService::class.java)
            .setAction(QobearVpnService.ACTION_CONNECT)
            .putExtra(QobearVpnService.EXTRA_PROFILE, profileId)
            .putExtra(QobearVpnService.EXTRA_ACCOUNT, accountId)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            ConnectionLogStore.append("Runtime", "Failed starting VPN service: ${e.message}", isError = true)
            false
        }
    }

    fun disconnect(context: Context): Boolean {
        if (!connection.value.active) return true
        val intent = Intent(context, QobearVpnService::class.java)
            .setAction(QobearVpnService.ACTION_DISCONNECT)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            ConnectionLogStore.append("Runtime", "Failed stopping VPN service: ${e.message}", isError = true)
            false
        }
    }

    fun clearError() {
        mutable.update { current ->
            if (!current.active) OpenVpnConnection(bypassRegion = current.bypassRegion) else current
        }
    }

    fun switchToFastest(context: Context): Boolean {
        val intent = Intent(context, QobearVpnService::class.java)
            .setAction(QobearVpnService.ACTION_SWITCH_BEST)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            ConnectionLogStore.append("Runtime", "Failed switching VPN server: ${e.message}", isError = true)
            false
        }
    }
}
