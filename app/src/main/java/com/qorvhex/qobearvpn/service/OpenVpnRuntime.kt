package com.qorvhex.qobearvpn.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OpenVpnRuntime {
    internal val mutable = MutableStateFlow(OpenVpnConnection())
    val connection: StateFlow<OpenVpnConnection> = mutable.asStateFlow()

    const val SELF_CREDENTIAL = "@profile"

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
