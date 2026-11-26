package com.qorvhex.qobearvpn.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qorvhex.qobearvpn.core.ConfigArchiveExtractor
import com.qorvhex.qobearvpn.core.OpenVpnLatency
import com.qorvhex.qobearvpn.core.ProfileImporter
import com.qorvhex.qobearvpn.core.ProfileRuntime
import com.qorvhex.qobearvpn.data.AppSettings
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.data.LogEntry
import com.qorvhex.qobearvpn.data.QobearCatalog
import com.qorvhex.qobearvpn.data.QobearRepository
import com.qorvhex.qobearvpn.data.SettingsStore
import com.qorvhex.qobearvpn.data.SplitTunnelConfig
import com.qorvhex.qobearvpn.data.SplitTunnelStore
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.AppThemeMode
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.ConnectionPhase
import com.qorvhex.qobearvpn.model.DnsPreset
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.ServerPolicy
import com.qorvhex.qobearvpn.model.SplitTunnelMode
import com.qorvhex.qobearvpn.service.OpenVpnRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val iconBitmap: ImageBitmap? = null,
)

class QobearViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = QobearRepository.get(app)
    private val settingsStore = SettingsStore.get(app)
    private val splitStore = SplitTunnelStore.get(app)

    val catalog: StateFlow<QobearCatalog> = repo.data
    val settings: StateFlow<AppSettings> = settingsStore.settings
    val splitConfig: StateFlow<SplitTunnelConfig> = splitStore.config
    val connection: StateFlow<OpenVpnConnection> = OpenVpnRuntime.connection
    val logs: StateFlow<List<LogEntry>> = ConnectionLogStore.logs

    private val _probingIds = MutableStateFlow<Set<String>>(emptySet())
    val probingIds: StateFlow<Set<String>> = _probingIds.asStateFlow()

    private val _probeTotal = MutableStateFlow(0)
    val probeTotal: StateFlow<Int> = _probeTotal.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    // Survives Activity recreation during VPN/Notification system permission dialogs
    var pendingConnectProfileId: String? = null
    var pendingConnectAccountId: String? = null

    private var batchProbeJob: Job? = null
    private var autoPickJob: Job? = null
    private var lastConnectedProbeKey: String? = null

    init {
        viewModelScope.launch {
            connection.collect { conn ->
                if (conn.phase == ConnectionPhase.CONNECTED && conn.profileId != null && conn.connectedAt != null) {
                    val key = "${conn.profileId}:${conn.connectedAt}"
                    if (lastConnectedProbeKey != key) {
                        lastConnectedProbeKey = key
                        catalog.value.profiles.firstOrNull { it.id == conn.profileId }?.let { profile ->
                            pingSingle(profile)
                        }
                    }
                }
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(msg: String) {
        _bannerMessage.value = msg
    }

    fun resetStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.resetCorruptStorage()
                _bannerMessage.value = null
            } catch (e: Exception) {
                ConnectionLogStore.append("Vault", "Reset failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun completeOnboarding(
        username: String,
        password: String,
        region: BypassRegion,
        dpiBypassEnabled: Boolean,
        language: AppLanguage,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (username.isNotBlank() && password.isNotEmpty()) {
                    repo.saveAccount(null, username, password)
                }
                settingsStore.update {
                    it.copy(
                        onboardingCompleted = true,
                        bypassRegion = region,
                        dpiBypassEnabled = dpiBypassEnabled,
                        language = language,
                    )
                }
            } catch (e: Exception) {
                ConnectionLogStore.append("Onboarding", "Save failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun saveAccount(id: String?, username: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val acc = repo.saveAccount(id, username, password)
                repo.selectAccount(acc.id)
                val err = OpenVpnRuntime.connection.value.error
                if (err == "AUTH_FAILED" || err == "ACCOUNT_REQUIRED") {
                    OpenVpnRuntime.clearError()
                }
            } catch (e: Exception) {
                ConnectionLogStore.append("Account", "Save failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun selectAccount(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.selectAccount(id)
            } catch (e: Exception) {
                ConnectionLogStore.append("Account", "Select failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun removeAccount(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = OpenVpnRuntime.connection.value
                if (conn.active && conn.accountId == id) {
                    OpenVpnRuntime.disconnect(getApplication())
                } else if (!conn.active && conn.accountId == id && conn.error != null) {
                    OpenVpnRuntime.clearError()
                }
                repo.removeAccount(id)
            } catch (e: Exception) {
                ConnectionLogStore.append("Account", "Remove failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun setBypassRegion(region: BypassRegion) {
        settingsStore.update { it.copy(bypassRegion = region) }
    }

    fun setLanguage(language: AppLanguage) {
        settingsStore.update { it.copy(language = language) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsStore.update { it.copy(themeMode = mode) }
    }

    fun setDns(preset: DnsPreset, customDns: String = settings.value.customDns) {
        settingsStore.update { it.copy(dnsPreset = preset, customDns = customDns) }
    }

    fun setLanBypass(enabled: Boolean) {
        settingsStore.update { it.copy(lanBypass = enabled) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        settingsStore.update { it.copy(autoReconnect = enabled) }
    }

    fun setDpiBypassEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(dpiBypassEnabled = enabled) }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.selectProfile(profileId)
            } catch (e: Exception) {
                ConnectionLogStore.append("Profiles", "Select failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun toggleFavorite(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.updateProfile(profileId) { it.copy(favorite = !it.favorite) }
            } catch (e: Exception) {
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun removeCustomProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = OpenVpnRuntime.connection.value
                if (conn.active && conn.profileId == profileId) {
                    OpenVpnRuntime.disconnect(getApplication())
                } else if (!conn.active && conn.profileId == profileId && conn.error != null) {
                    OpenVpnRuntime.clearError()
                }
                OpenVpnRuntime.clearTransientProfileCredentials(profileId)
                repo.removeCustomProfile(profileId)
            } catch (e: Exception) {
                ConnectionLogStore.append("Profiles", "Remove custom profile failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
            }
        }
    }

    fun saveCustomProfileCredentials(
        profileId: String,
        username: String,
        password: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = try {
                repo.saveProfileCredentials(profileId, username, password)
                true
            } catch (e: Exception) {
                ConnectionLogStore.append("Vault", "Save custom creds failed: ${e.message}", isError = true)
                _bannerMessage.value = "SAVE_FAILED"
                false
            }
            if (saved) {
                withContext(Dispatchers.Main) {
                    onSaved()
                }
            }
        }
    }

    fun pingSingle(profile: Profile) {
        if (profile.id in _probingIds.value) return
        val currentSettings = settings.value
        val region = currentSettings.bypassRegion
        val dpi = currentSettings.dpiBypassEnabled
        _probingIds.update { it + profile.id }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = OpenVpnLatency.measure(getApplication(), profile, region, dpi)
                runCatching { repo.updateProfile(profile.id) { it.copy(probe = res) } }
            } finally {
                _probingIds.update { it - profile.id }
            }
        }
    }

    fun pingAll() {
        if (batchProbeJob?.isActive == true || autoPickJob?.isActive == true) {
            cancelProbes()
            return
        }
        val profiles = catalog.value.profiles
        if (profiles.isEmpty()) return
        val currentSettings = settings.value
        val region = currentSettings.bypassRegion
        val dpi = currentSettings.dpiBypassEnabled
        val ownIds = profiles.map { it.id }.toSet()
        _probingIds.value = ownIds
        _probeTotal.value = profiles.size

        batchProbeJob = viewModelScope.launch {
            val semaphore = Semaphore(8)
            try {
                coroutineScope {
                    profiles.map { profile ->
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                val res = OpenVpnLatency.measure(getApplication(), profile, region, dpi)
                                repo.updateProfileProbeInMemory(profile.id, res)
                                _probingIds.update { it - profile.id }
                            }
                        }
                    }.joinAll()
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _bannerMessage.value = e.message ?: "PROBE_FAILED"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { repo.persistCatalog() }.onFailure {
                        ConnectionLogStore.append("Probe", "Failed saving batch probes: ${it.message}", isError = true)
                    }
                }
                _probingIds.update { it - ownIds }
                if (_probingIds.value.isEmpty()) _probeTotal.value = 0
            }
        }
    }

    fun cancelProbes() {
        batchProbeJob?.cancel()
        autoPickJob?.cancel()
        _probingIds.value = emptySet()
        _probeTotal.value = 0
    }

    fun connectProfile(profile: Profile) {
        cancelProbes()
        val data = catalog.value
        val liveProfile = data.profiles.firstOrNull { it.id == profile.id } ?: profile
        val accountId = OpenVpnRuntime.resolveAccountId(liveProfile, data.activeAccountId) ?: run {
            _bannerMessage.value = if (ProfileRuntime.isTunnelBear(liveProfile)) "ACCOUNT_REQUIRED" else "CUSTOM_AUTH_FAILED"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.selectProfile(liveProfile.id) }.onFailure {
                ConnectionLogStore.append("Profiles", "Select before connect failed: ${it.message}", isError = true)
            }
        }
        if (!OpenVpnRuntime.connect(getApplication(), liveProfile.id, accountId)) {
            OpenVpnRuntime.clearTransientProfileCredentials(liveProfile.id)
            _bannerMessage.value = "CONNECTION_FAILED"
        }
    }

    fun connectAutoBest(
        onNeedVpnPermission: (Profile, String) -> Unit,
        onNeedCustomAuth: (Profile) -> Unit = {},
        onNeedTunnelBearAccount: () -> Unit = {},
    ) {
        if (autoPickJob?.isActive == true) {
            cancelProbes()
            return
        }
        val data = catalog.value
        val candidatePool = if (data.activeAccountId != null) {
            data.profiles
        } else {
            data.profiles.filter { !ProfileRuntime.isTunnelBear(it) }
        }
        if (candidatePool.isEmpty()) {
            _bannerMessage.value = if (data.profiles.isEmpty()) "NO_PROFILES" else "ACCOUNT_REQUIRED"
            if (data.profiles.isNotEmpty()) onNeedTunnelBearAccount()
            return
        }
        val now = System.currentTimeMillis()
        val freshBest = ServerPolicy.fastest(candidatePool, now, allowStale = false)
        if (freshBest != null) {
            dispatchResolvedProfile(
                freshBest,
                data.activeAccountId,
                onNeedVpnPermission,
                onNeedCustomAuth,
                onNeedTunnelBearAccount,
            )
            return
        }

        batchProbeJob?.cancel()
        val priorityCandidates = (candidatePool.filter { it.favorite } + candidatePool)
            .distinctBy { it.id }
            .take(16)
        val currentSettings = settings.value
        val region = currentSettings.bypassRegion
        val dpi = currentSettings.dpiBypassEnabled
        val ownIds = priorityCandidates.map { it.id }.toSet()
        _probingIds.value = ownIds
        _probeTotal.value = priorityCandidates.size

        autoPickJob = viewModelScope.launch {
            val semaphore = Semaphore(8)
            try {
                coroutineScope {
                    priorityCandidates.map { profile ->
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                val res = OpenVpnLatency.measure(getApplication(), profile, region, dpi)
                                repo.updateProfileProbeInMemory(profile.id, res)
                                _probingIds.update { it - profile.id }
                            }
                        }
                    }.joinAll()
                }
                val liveProfiles = catalog.value.profiles
                val probePool = if (catalog.value.activeAccountId != null) {
                    liveProfiles
                } else {
                    liveProfiles.filter { !ProfileRuntime.isTunnelBear(it) }
                }
                val chosen = ServerPolicy.fastest(probePool, System.currentTimeMillis(), allowStale = true)
                    ?: probePool.firstOrNull { it.id == data.selectedProfileId && it.probe?.error == "CONTROL_AUTH" }
                    ?: probePool.firstOrNull { it.favorite && it.probe?.error == "CONTROL_AUTH" }
                    ?: probePool.firstOrNull { it.probe?.error == "CONTROL_AUTH" }
                if (chosen != null) {
                    dispatchResolvedProfile(
                        chosen,
                        catalog.value.activeAccountId,
                        onNeedVpnPermission,
                        onNeedCustomAuth,
                        onNeedTunnelBearAccount,
                    )
                } else {
                    _bannerMessage.value = "AUTO_PROBE_FAILED"
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _bannerMessage.value = e.message ?: "AUTO_PROBE_FAILED"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { repo.persistCatalog() }.onFailure {
                        ConnectionLogStore.append("Probe", "Failed saving auto-pick probes: ${it.message}", isError = true)
                    }
                }
                _probingIds.update { it - ownIds }
                if (_probingIds.value.isEmpty()) _probeTotal.value = 0
            }
        }
    }

    private fun dispatchResolvedProfile(
        profile: Profile,
        activeAccountId: String?,
        onNeedVpnPermission: (Profile, String) -> Unit,
        onNeedCustomAuth: (Profile) -> Unit,
        onNeedTunnelBearAccount: () -> Unit,
    ) {
        selectProfile(profile.id)
        val resolvedAccount = OpenVpnRuntime.resolveAccountId(profile, activeAccountId)
        when {
            resolvedAccount != null -> onNeedVpnPermission(profile, resolvedAccount)
            !ProfileRuntime.isTunnelBear(profile) -> onNeedCustomAuth(profile)
            else -> {
                _bannerMessage.value = "ACCOUNT_REQUIRED"
                onNeedTunnelBearAccount()
            }
        }
    }

    fun importOvpnUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                require(uris.size <= ConfigArchiveExtractor.MAX_SELECTED_ITEMS)
                val resolver = getApplication<Application>().contentResolver
                val rawInputs = ArrayList<Pair<String, ByteArray>>(uris.size)
                var totalRawBytes = 0
                for ((idx, uri) in uris.withIndex()) {
                    val rawName = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "profile_${idx + 1}.ovpn"

                    val bytes = resolver.openInputStream(uri)?.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            totalRawBytes += n
                            require(totalRawBytes <= ConfigArchiveExtractor.MAX_TOTAL_BYTES) { "Import size limit exceeded" }
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    } ?: error("Unreadable file")

                    rawInputs += rawName to bytes
                }
                val files = ConfigArchiveExtractor.collectFromInputs(rawInputs)
                val summary = repo.importFiles(files)
                _bannerMessage.value = when {
                    summary.errors.isNotEmpty() -> "IMPORT_ERROR:${summary.errors.first()}"
                    summary.imported > 0 -> "IMPORTED:${summary.imported}"
                    summary.duplicates > 0 -> "IMPORT_DUPLICATE:${summary.duplicates}"
                    else -> "IMPORT_ERROR:No valid .ovpn profile found"
                }
            } catch (e: Exception) {
                _bannerMessage.value = "IMPORT_ERROR:${e.message ?: "Invalid file"}"
            }
        }
    }

    fun applySplitTunnelConfig(mode: SplitTunnelMode, packages: Set<String>) {
        splitStore.save(mode, packages)
        val conn = connection.value
        val activeProfile = conn.profileId
        val activeAccount = conn.accountId
        if (conn.active && activeProfile != null && activeAccount != null) {
            val app = getApplication<Application>()
            // Directly issuing connect replaces the active session cleanly without wiping unsaved transient creds.
            val started = OpenVpnRuntime.connect(app, activeProfile, activeAccount)
            _bannerMessage.value = if (started) "SPLIT_SAVED_RECONNECTED" else "SAVE_FAILED"
        } else {
            _bannerMessage.value = "SPLIT_SAVED"
        }
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _appsLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            loadInstalledAppsSync()
        }
    }

    private fun loadInstalledAppsSync() {
        _appsLoading.value = true
        try {
            val app = getApplication<Application>()
            val pm = app.packageManager
            val selected = splitStore.config.value.packages
            val pkgs = runCatching { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) }
                .onFailure { ConnectionLogStore.append("Apps", "Package query failed: ${it.message}", isError = true) }
                .getOrDefault(emptyList())

            val initial = pkgs.filter { pkg ->
                pkg.packageName != app.packageName &&
                    (pkg.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true ||
                        pkg.packageName in selected)
            }.map { pkg ->
                val ai = pkg.applicationInfo
                val isSys = ((ai?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    pm.getLaunchIntentForPackage(pkg.packageName) == null
                val label = ai?.let { info -> runCatching { pm.getApplicationLabel(info).toString() }.getOrNull() }
                    ?: pkg.packageName
                InstalledAppItem(
                    label = label,
                    packageName = pkg.packageName,
                    isSystem = isSys,
                    iconBitmap = null,
                )
            }.sortedWith(compareBy<InstalledAppItem> { it.isSystem }.thenBy { it.label.lowercase() })

            _installedApps.value = initial
            _appsLoading.value = false

            val px = (36 * app.resources.displayMetrics.density).toInt().coerceAtLeast(48)
            _installedApps.value = initial.map { item ->
                val bmp = runCatching { pm.getApplicationIcon(item.packageName).toBitmap(px, px).asImageBitmap() }.getOrNull()
                item.copy(iconBitmap = bmp)
            }
        } finally {
            _appsLoading.value = false
        }
    }
}
