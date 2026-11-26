package com.qorvhex.qobearvpn.core

import androidx.annotation.Keep

@Keep
object OpenVpnNative {
    @Volatile
    private var loaded: Boolean = false

    @Synchronized
    fun available(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("qobearopenvpn")
            val ok = apiVersion() == 1
            loaded = ok
            ok
        } catch (_: LinkageError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    external fun apiVersion(): Int
    external fun evaluate(config: String): String
    external fun create(): Long
    external fun run(handle: Long, config: String, username: String, password: String, callback: Any): String
    external fun stop(handle: Long)
    external fun release(handle: Long)
}
