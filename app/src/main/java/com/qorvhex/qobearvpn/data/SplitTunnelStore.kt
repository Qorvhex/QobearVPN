package com.qorvhex.qobearvpn.data

import android.content.Context
import com.qorvhex.qobearvpn.model.SplitTunnelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SplitTunnelConfig(
    val mode: SplitTunnelMode = SplitTunnelMode.ALL,
    val packages: Set<String> = emptySet(),
)

class SplitTunnelStore private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("qobear_split_tunnel", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<SplitTunnelConfig> = _config.asStateFlow()

    private fun read(): SplitTunnelConfig = SplitTunnelConfig(
        mode = SplitTunnelMode.fromId(prefs.getString(KEY_MODE, SplitTunnelMode.ALL.id)),
        packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
    )

    @Synchronized
    fun save(mode: SplitTunnelMode, packages: Set<String>) {
        val clean = packages.toSet()
        prefs.edit()
            .putString(KEY_MODE, mode.id)
            .putStringSet(KEY_PACKAGES, clean)
            .apply()
        _config.value = SplitTunnelConfig(mode, clean)
    }

    @Synchronized
    fun togglePackage(pkg: String) {
        val current = _config.value
        val next = if (pkg in current.packages) current.packages - pkg else current.packages + pkg
        save(current.mode, next)
    }

    @Synchronized
    fun setMode(mode: SplitTunnelMode) {
        save(mode, _config.value.packages)
    }

    @Synchronized
    fun addPackages(pkgs: Collection<String>) {
        val current = _config.value
        val nextMode = if (current.mode == SplitTunnelMode.ALL) SplitTunnelMode.EXCLUDE else current.mode
        save(nextMode, current.packages + pkgs)
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_PACKAGES = "packages"

        /** Common domestic Iranian packages for one-tap split-tunnel bypass convenience. */
        val IRAN_BANKING_PREFIXES = listOf(
            "ir.melli.", "com.bmi.", "ir.sep.", "com.tosan.", "ir.tejarat.", "com.fcp.",
            "ir.snapp.", "cab.snapp.", "ir.tapsi.", "com.digikala.", "ir.divar.",
            "ir.shaparak.", "com.sibche.", "ir.cafebazaar.", "com.aparat.", "ir.blubank.",
            "com.pmb.", "ir.bankmellat.", "ir.pasargad.", "ir.saman.", "ir.resalat.",
            "ir.ayandeh.", "ir.irancell.", "ir.mci.", "ir.rightel.", "ir.post."
        )

        @Volatile
        private var instance: SplitTunnelStore? = null

        fun get(context: Context): SplitTunnelStore =
            instance ?: synchronized(this) {
                instance ?: SplitTunnelStore(context.applicationContext).also { instance = it }
            }
    }
}
