package com.qorvhex.qobearvpn.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.qorvhex.qobearvpn.MainActivity
import com.qorvhex.qobearvpn.core.ProfileRuntime
import com.qorvhex.qobearvpn.data.QobearRepository
import com.qorvhex.qobearvpn.data.SettingsStore
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.ConnectionPhase
import com.qorvhex.qobearvpn.model.CountryGeoMapper
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.ServerPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class QobearTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        collectorJob?.cancel()
        collectorJob = scope.launch {
            OpenVpnRuntime.connection.collect { conn ->
                refreshTile(conn)
            }
        }
    }

    override fun onStopListening() {
        collectorJob?.cancel()
        collectorJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val state = OpenVpnRuntime.connection.value
        if (state.active) {
            if (!OpenVpnRuntime.disconnect(this)) openApp()
            return
        }

        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }

        val repo = QobearRepository.get(this).data.value
        val selected = repo.profiles.firstOrNull { it.id == repo.selectedProfileId }
            ?: ServerPolicy.fastest(repo.profiles, System.currentTimeMillis(), allowStale = true)
            ?: repo.profiles.firstOrNull()

        if (selected == null) {
            openApp()
            return
        }

        val accountId = if (selected.selfContained && !ProfileRuntime.isTunnelBear(selected)) {
            OpenVpnRuntime.SELF_CREDENTIAL
        } else {
            repo.activeAccountId
        }

        if (accountId == null) {
            openApp()
            return
        }

        if (!OpenVpnRuntime.connect(this, selected.id, accountId)) {
            openApp()
        }
    }

    private fun refreshTile(conn: OpenVpnConnection = OpenVpnRuntime.connection.value) {
        val tile = qsTile ?: return
        val fa = SettingsStore.get(this).settings.value.language == AppLanguage.FA
        val catalog = QobearRepository.get(this).data.value
        val activeProfile = (conn.profileId ?: catalog.selectedProfileId)?.let { id ->
            catalog.profiles.firstOrNull { it.id == id }
        }
        val place = activeProfile?.let { CountryGeoMapper.resolve(it) }

        tile.state = when (conn.phase) {
            ConnectionPhase.CONNECTED, ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> Tile.STATE_ACTIVE
            ConnectionPhase.DISCONNECTING -> Tile.STATE_UNAVAILABLE
            ConnectionPhase.DISCONNECTED, ConnectionPhase.ERROR -> Tile.STATE_INACTIVE
        }
        tile.label = "QobearVPN"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (conn.phase) {
                ConnectionPhase.CONNECTED -> place?.let { "${it.flag} ${it.localizedName(fa)}" }
                    ?: if (fa) "متصل" else "Connected"
                ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING ->
                    if (fa) "در حال اتصال..." else "Connecting..."
                ConnectionPhase.DISCONNECTING ->
                    if (fa) "در حال قطع..." else "Disconnecting..."
                ConnectionPhase.ERROR ->
                    if (fa) "خطای اتصال" else "Error"
                ConnectionPhase.DISCONNECTED -> place?.let { "${it.flag} ${it.localizedName(fa)}" }
                    ?: if (fa) "خاموش" else "Off"
            }
        }
        runCatching { tile.updateTile() }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
