package com.qorvhex.qobearvpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies real internet reachability strictly through the active VPN network over HTTPS
 * across multiple resilient endpoints (never falling back to an unbound direct socket).
 */
object OpenVpnExitProbe {
    private val ENDPOINTS = listOf(
        "https://ipinfo.io/json" to Pair("ip", "country"),
        "https://ifconfig.co/json" to Pair("ip", "country_iso"),
        "https://api.ipify.org?format=json" to Pair("ip", ""),
    )

    fun probe(context: Context, timeoutMs: Int): Pair<String, String?>? {
        val vpnNetwork = waitForVpnNetwork(context, 1_500L) ?: run {
            ConnectionLogStore.append("ExitProbe", "VPN network not yet registered in ConnectivityManager")
            return null
        }

        for ((rawUrl, fields) in ENDPOINTS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (vpnNetwork.openConnection(URL(rawUrl)) as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "QobearVPN/1.0")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    ConnectionLogStore.append("ExitProbe", "$rawUrl returned HTTP $code")
                    continue
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val ip = json.optString(fields.first).takeIf { it.isNotBlank() } ?: continue
                val cc = if (fields.second.isNotEmpty()) {
                    json.optString(fields.second).takeIf { it.length == 2 }
                } else {
                    null
                }
                return ip to cc
            } catch (e: Exception) {
                ConnectionLogStore.append("ExitProbe", "$rawUrl failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun waitForVpnNetwork(context: Context, maxWaitMs: Long): Network? {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            activeVpnNetwork(context)?.let { return it }
            try {
                Thread.sleep(120)
            } catch (_: InterruptedException) {
                return null
            }
        }
        return activeVpnNetwork(context)
    }

    @Suppress("DEPRECATION")
    private fun activeVpnNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }
}
