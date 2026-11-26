package com.qorvhex.qobearvpn.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qorvhex.qobearvpn.MainActivity
import com.qorvhex.qobearvpn.R
import com.qorvhex.qobearvpn.core.CidrPrefix
import com.qorvhex.qobearvpn.core.GeoBypassRouter
import com.qorvhex.qobearvpn.core.OpenVpnExitProbe
import com.qorvhex.qobearvpn.core.OpenVpnLatency
import com.qorvhex.qobearvpn.core.OpenVpnNative
import com.qorvhex.qobearvpn.core.ProfileRuntime
import com.qorvhex.qobearvpn.core.SplitBypassRelay
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.data.QobearRepository
import com.qorvhex.qobearvpn.data.SettingsStore
import com.qorvhex.qobearvpn.data.SplitTunnelStore
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.AuthState
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.ConnectionPhase
import com.qorvhex.qobearvpn.model.CountryGeoMapper
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.RelayProfile
import com.qorvhex.qobearvpn.model.ServerPolicy
import com.qorvhex.qobearvpn.model.SplitTunnelMode
import com.qorvhex.qobearvpn.model.TrafficSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class QobearVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var runningJob: Job? = null

    @Volatile
    private var activeSession: Session? = null

    @Volatile
    private var shuttingDown = false

    @Volatile
    private var latestStartId = 0

    @Volatile
    private var pendingStarts = 0

    private val repo by lazy { QobearRepository.get(this) }
    private val settingsStore by lazy { SettingsStore.get(this) }
    private val splitStore by lazy { SplitTunnelStore.get(this) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastPhysicalNetworkId: Long? = null

    private data class Command(
        val profileId: String? = null,
        val accountId: String? = null,
        val forceFallbackRelay: Boolean = false,
        val startId: Int = 0,
        val preserveError: Boolean = false,
        val finished: Session? = null,
        val deadlineExpired: Session? = null,
    )

    override fun onCreate() {
        super.onCreate()
        OpenVpnRuntime.socketProtector = { protect(it) }
        OpenVpnRuntime.datagramProtector = { protect(it) }
        ensureNotificationChannel()
        registerPhysicalNetworkWatcher()

        scope.launch {
            for (cmd in commands) {
                val expired = cmd.deadlineExpired
                if (expired != null) {
                    if (activeSession !== expired) continue
                    expired.timeOut()
                    val stoppedCleanly = withTimeoutOrNull(STOP_GRACE_MS) {
                        runningJob?.join()
                        true
                    } == true
                    if (!stoppedCleanly) expired.abandon()
                    runningJob = null
                    activeSession = null
                    handleSessionFinished(expired, forceErrorIfNoFallback = "CONNECT_DEADLINE")
                    continue
                }

                val finished = cmd.finished
                if (finished != null) {
                    if (activeSession !== finished) continue
                    runningJob?.join()
                    runningJob = null
                    activeSession = null
                    handleSessionFinished(finished, forceErrorIfNoFallback = null)
                    continue
                }

                if (cmd.startId > 0) pendingStarts = (pendingStarts - 1).coerceAtLeast(0)

                val prevProfileId = activeSession?.profileId
                activeSession?.userStop()
                val stoppedCleanly = withTimeoutOrNull(STOP_GRACE_MS) {
                    runningJob?.join()
                    true
                } == true

                if (!stoppedCleanly) {
                    activeSession?.abandon()
                    OpenVpnRuntime.mutable.update {
                        OpenVpnConnection(bypassRegion = settingsStore.settings.value.bypassRegion)
                    }
                }
                runningJob = null
                activeSession = null
                if (prevProfileId != null && prevProfileId != cmd.profileId) {
                    OpenVpnRuntime.clearTransientProfileCredentials(prevProfileId)
                }

                if (cmd.profileId == null || cmd.accountId == null) {
                    OpenVpnRuntime.clearAllTransientProfileCredentials()
                    if (!cmd.preserveError) {
                        OpenVpnRuntime.mutable.update {
                            OpenVpnConnection(bypassRegion = settingsStore.settings.value.bypassRegion)
                        }
                    }
                    refreshQuickTile()
                    if (pendingStarts == 0 && latestStartId == cmd.startId) {
                        stopForegroundCompat()
                        stopSelfResult(cmd.startId)
                    }
                    continue
                }

                startSession(cmd)
            }
        }
    }

    private fun handleSessionFinished(finished: Session, forceErrorIfNoFallback: String?) {
        if (finished.accountId == OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL) {
            OpenVpnRuntime.clearTransientProfileCredentials(finished.profileId)
        }
        val settings = settingsStore.settings.value
        val terminal = finished.terminalError ?: forceErrorIfNoFallback

        if (forceErrorIfNoFallback != null || terminal != null) {
            val shouldReportError = !finished.userStopped || finished.timedOut
            OpenVpnRuntime.mutable.update { current ->
                OpenVpnConnection(
                    phase = if (shouldReportError && terminal != null) ConnectionPhase.ERROR else ConnectionPhase.DISCONNECTED,
                    profileId = current.profileId,
                    accountId = current.accountId,
                    bypassRegion = settings.bypassRegion,
                    error = if (shouldReportError) terminal else null,
                )
            }
        }
        refreshQuickTile()

        if (pendingStarts == 0 && latestStartId == finished.requestId) {
            stopForegroundCompat()
            stopSelfResult(finished.requestId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action

        when {
            action == ACTION_DISCONNECT -> {
                startForegroundCompat(buildTransitionNotification(isSwitching = false, isDisconnecting = true))
                activeSession?.userStop()
                pendingStarts++
                commands.trySend(Command(startId = startId))
            }
            action == ACTION_SWITCH_BEST -> {
                startForegroundCompat(buildTransitionNotification(isSwitching = true, isDisconnecting = false))
                val catalog = repo.data.value
                val candidatePool = if (catalog.activeAccountId != null) {
                    catalog.profiles
                } else {
                    catalog.profiles.filter { !ProfileRuntime.isTunnelBear(it) }
                }
                val best = ServerPolicy.fastest(candidatePool, System.currentTimeMillis(), allowStale = true)
                    ?: candidatePool.firstOrNull { it.id == catalog.selectedProfileId }
                    ?: candidatePool.firstOrNull()
                if (best != null) {
                    val accountId = resolveSafeAccountId(best, catalog.activeAccountId)
                    if (accountId != null) {
                        runCatching { repo.selectProfile(best.id) }.onFailure {
                            ConnectionLogStore.append(TAG, "selectProfile failed during switch: ${it.message}", isError = true)
                        }
                        pendingStarts++
                        commands.trySend(Command(best.id, accountId, startId = startId))
                    } else {
                        val isTb = ProfileRuntime.isTunnelBear(best)
                        OpenVpnRuntime.mutable.update {
                            OpenVpnConnection(
                                phase = ConnectionPhase.ERROR,
                                profileId = best.id,
                                bypassRegion = settingsStore.settings.value.bypassRegion,
                                error = if (isTb) "ACCOUNT_REQUIRED" else "CUSTOM_AUTH_FAILED",
                            )
                        }
                        pendingStarts++
                        commands.trySend(Command(startId = startId, preserveError = true))
                    }
                } else {
                    OpenVpnRuntime.mutable.update {
                        OpenVpnConnection(
                            phase = ConnectionPhase.ERROR,
                            bypassRegion = settingsStore.settings.value.bypassRegion,
                            error = if (catalog.profiles.isEmpty()) "NO_PROFILES" else "ACCOUNT_REQUIRED",
                        )
                    }
                    pendingStarts++
                    commands.trySend(Command(startId = startId, preserveError = true))
                }
            }
            action == ACTION_CONNECT -> {
                startForegroundCompat(buildTransitionNotification(isSwitching = false, isDisconnecting = false))
                val p = intent.getStringExtra(EXTRA_PROFILE)
                val a = intent.getStringExtra(EXTRA_ACCOUNT)
                if (p != null && a != null) {
                    if (!OpenVpnRuntime.connection.value.active) {
                        OpenVpnRuntime.mutable.update {
                            OpenVpnConnection(
                                phase = ConnectionPhase.CONNECTING,
                                profileId = p,
                                accountId = a,
                                bypassRegion = settingsStore.settings.value.bypassRegion,
                            )
                        }
                    }
                    pendingStarts++
                    commands.trySend(Command(p, a, startId = startId))
                } else {
                    pendingStarts++
                    commands.trySend(Command(startId = startId))
                }
            }
            intent == null || action == SERVICE_INTERFACE -> {
                startForegroundCompat(buildTransitionNotification(isSwitching = false, isDisconnecting = false))
                val catalog = repo.data.value
                val profile = catalog.profiles.firstOrNull { it.id == catalog.selectedProfileId }
                    ?: catalog.profiles.firstOrNull()
                val accountId = profile?.let { resolveSafeAccountId(it, catalog.activeAccountId) }
                if (profile != null && accountId != null) {
                    pendingStarts++
                    commands.trySend(Command(profile.id, accountId, startId = startId))
                } else {
                    OpenVpnRuntime.mutable.update {
                        OpenVpnConnection(
                            phase = ConnectionPhase.ERROR,
                            profileId = profile?.id,
                            bypassRegion = settingsStore.settings.value.bypassRegion,
                            error = when {
                                profile == null -> "NO_PROFILES"
                                ProfileRuntime.isTunnelBear(profile) -> "ACCOUNT_REQUIRED"
                                else -> "CUSTOM_AUTH_FAILED"
                            },
                        )
                    }
                    pendingStarts++
                    commands.trySend(Command(startId = startId, preserveError = true))
                }
            }
            else -> {
                startForegroundCompat(buildTransitionNotification(isSwitching = false, isDisconnecting = true))
                activeSession?.userStop()
                pendingStarts++
                commands.trySend(Command(startId = startId))
            }
        }
        return START_NOT_STICKY
    }

    private fun resolveSafeAccountId(profile: Profile, activeAccountId: String?): String? =
        OpenVpnRuntime.resolveAccountId(profile, activeAccountId)

    override fun onRevoke() {
        activeSession?.userStop()
        commands.trySend(Command(startId = latestStartId))
        super.onRevoke()
    }

    override fun onDestroy() {
        shuttingDown = true
        OpenVpnRuntime.socketProtector = null
        OpenVpnRuntime.datagramProtector = null
        OpenVpnRuntime.clearAllTransientProfileCredentials()
        unregisterPhysicalNetworkWatcher()
        activeSession?.userStop()
        commands.close()
        scope.cancel()
        OpenVpnRuntime.mutable.update {
            OpenVpnConnection(bypassRegion = settingsStore.settings.value.bypassRegion)
        }
        refreshQuickTile()
        super.onDestroy()
    }

    private fun startSession(cmd: Command) {
        val profileId = cmd.profileId ?: return
        val accountId = cmd.accountId ?: return
        val profile = repo.data.value.profiles.firstOrNull { it.id == profileId }
        val region = settingsStore.settings.value.bypassRegion
        val dpiEnabled = settingsStore.settings.value.dpiBypassEnabled
        val allowRelay = profile?.let { ProfileRuntime.supportsTcpRelay(it) } ?: false
        val relayProfile = if (allowRelay) region.effectiveDpiRelay(dpiEnabled || cmd.forceFallbackRelay) else null

        val newSession = Session(
            profileId = profileId,
            accountId = accountId,
            bypassRegion = region,
            relayProfile = relayProfile,
            supportsTcpRelay = allowRelay,
            requestId = cmd.startId,
        )
        activeSession = newSession
        OpenVpnRuntime.mutable.update {
            OpenVpnConnection(
                phase = ConnectionPhase.CONNECTING,
                profileId = profileId,
                accountId = accountId,
                bypassRegion = region,
                detail = if (relayProfile != null) "DPI_RELAY" else "DIRECT",
            )
        }
        refreshQuickTile()
        updateNotification(OpenVpnRuntime.connection.value)

        runningJob = scope.launch(Dispatchers.IO) { newSession.run() }

        scope.launch {
            delay(CONNECT_DEADLINE_MS)
            if (activeSession === newSession &&
                OpenVpnRuntime.connection.value.phase != ConnectionPhase.CONNECTED &&
                OpenVpnRuntime.connection.value.connectedAt == null
            ) {
                commands.trySend(Command(deadlineExpired = newSession))
            }
        }
    }

    private fun registerPhysicalNetworkWatcher() {
        val cm = connectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val id = network.networkHandle
                val prev = lastPhysicalNetworkId
                lastPhysicalNetworkId = id
                if (prev != null &&
                    prev != id &&
                    !shuttingDown &&
                    activeSession?.userStopped != true &&
                    settingsStore.settings.value.autoReconnect
                ) {
                    val conn = OpenVpnRuntime.connection.value
                    if (conn.phase == ConnectionPhase.CONNECTED && conn.profileId != null && conn.accountId != null) {
                        ConnectionLogStore.append(TAG, "Default network changed; reconnecting tunnel")
                        commands.trySend(Command(conn.profileId, conn.accountId, startId = latestStartId))
                    }
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    private fun unregisterPhysicalNetworkWatcher() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
    }

    @Keep
    inner class Session(
        val profileId: String,
        val accountId: String,
        val bypassRegion: BypassRegion,
        private val relayProfile: RelayProfile?,
        val supportsTcpRelay: Boolean,
        val requestId: Int,
    ) {
        private val stopped = AtomicBoolean(false)
        private val userStoppedFlag = AtomicBoolean(false)
        val userStopped: Boolean get() = userStoppedFlag.get()
        val usedRelay: Boolean get() = relayProfile != null

        @Volatile
        var terminalError: String? = null
            private set

        @Volatile
        var timedOut = false
            private set

        private val nativeHandle = AtomicLong(0L)
        private var builder: Builder? = null
        private var retainedTun: ParcelFileDescriptor? = null
        private var dnsAddedCount = 0
        private var activeExclusions: List<CidrPrefix> = emptyList()

        @Volatile
        private var relay: SplitBypassRelay? = null

        private var lastTrafficTickMs = 0L
        private var lastRxBytes = 0L
        private var lastTxBytes = 0L
        private var lastNotifUpdateMs = 0L

        private val verifyGeneration = AtomicInteger(0)
        @Volatile
        private var verifyJob: Job? = null

        fun timeOut() {
            timedOut = true
            if (terminalError == null) terminalError = "CONNECT_DEADLINE"
            stopNative()
        }

        fun userStop() {
            userStoppedFlag.set(true)
            stopNative()
        }

        private fun stopNative() {
            stopped.set(true)
            verifyJob?.cancel()
            if (activeSession === this && OpenVpnRuntime.connection.value.active) {
                OpenVpnRuntime.mutable.update {
                    it.copy(phase = ConnectionPhase.DISCONNECTING)
                }
            }
            nativeHandle.get().takeIf { it != 0L }?.let { runCatching { OpenVpnNative.stop(it) } }
        }

        fun abandon() {
            stopped.set(true)
            verifyJob?.cancel()
            nativeHandle.get().takeIf { it != 0L }?.let { runCatching { OpenVpnNative.stop(it) } }
            relay?.let { r -> relay = null; runCatching { r.close() } }
            synchronized(this) {
                runCatching { retainedTun?.close() }
                retainedTun = null
                builder = null
            }
        }

        suspend fun run() {
            try {
                val catalog = repo.data.value
                val profile = catalog.profiles.firstOrNull { it.id == profileId }
                    ?: run { terminalError = "PROFILE_NOT_FOUND"; return }

                // Enforce strict credential isolation regardless of caller intent extras
                val isTb = ProfileRuntime.isTunnelBear(profile)
                val useSelf = profile.selfContained && !isTb
                val useCustom = !isTb && !useSelf && accountId == OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL
                if (!isTb && !useSelf && !useCustom) {
                    terminalError = "ACCOUNT_REQUIRED"
                    return
                }
                val customCreds = if (useCustom) {
                    OpenVpnRuntime.transientProfileCredentials(profileId) ?: repo.profileCredentials(profileId)
                } else {
                    null
                }
                if (useCustom && customCreds == null) {
                    runCatching { repo.clearProfileCredentials(profileId) }.onFailure {
                        ConnectionLogStore.append(TAG, "Failed clearing missing custom creds: ${it.message}", isError = true)
                    }
                    terminalError = "CUSTOM_AUTH_FAILED"
                    return
                }
                val account = if (useSelf || useCustom) null else catalog.accounts.firstOrNull { it.id == accountId }
                if (!useSelf && !useCustom && account == null) {
                    terminalError = "ACCOUNT_REQUIRED"
                    return
                }
                if (!OpenVpnNative.available()) {
                    terminalError = "CORE_UNAVAILABLE"
                    return
                }

                val own = profile.ownCredentials
                val (username, password) = when {
                    useSelf -> own?.first.orEmpty() to own?.second.orEmpty()
                    useCustom -> customCreds!!
                    else -> account!!.username to repo.password(accountId)
                }

                val dnsServers = settingsStore.settings.value.effectiveDnsServers()
                val validation = OpenVpnNative.evaluate(
                    ProfileRuntime.effective(profile, forceTcp = ProfileRuntime.isTunnelBear(profile), customDns = dnsServers)
                )
                if (validation.isNotEmpty()) {
                    terminalError = validation
                    return
                }
                if (stopped.get()) return

                val settings = settingsStore.settings.value
                val countryCidrs = GeoBypassRouter.loadCountryCidrs(this@QobearVpnService, bypassRegion)
                activeExclusions = GeoBypassRouter.buildApi33Exclusions(
                    countryCidrs = countryCidrs,
                    lanBypass = settings.lanBypass,
                )

                // Pre-resolve candidate IPs strictly BEFORE TUN establishes so reconnects never query ISP DNS through a stalled tunnel.
                val rawHosts = (profile.probe?.healthy.orEmpty() + OpenVpnLatency.candidates(profile))
                    .distinct()
                    .ifEmpty { listOf(profile.remotes.first().host) }
                val resolvedIps = rawHosts.flatMap { h ->
                    runCatching {
                        InetAddress.getAllByName(h).filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
                    }.getOrDefault(emptyList())
                }.distinct().ifEmpty { rawHosts }

                if (stopped.get()) return

                val effectiveConfig = if (relayProfile != null && supportsTcpRelay) {
                    val splitRelay = SplitBypassRelay(
                        service = this@QobearVpnService,
                        hosts = resolvedIps,
                        port = if (ProfileRuntime.isTunnelBear(profile)) ProfileRuntime.TUNNELBEAR_TCP_PORT
                        else profile.remotes.first().port,
                        profile = relayProfile,
                    )
                    relay = splitRelay
                    splitRelay.start()
                    ProfileRuntime.effective(
                        profile = profile,
                        forceTcp = true,
                        relayPort = splitRelay.localPort,
                        customDns = dnsServers,
                    )
                } else {
                    ProfileRuntime.effective(
                        profile = profile,
                        forceTcp = ProfileRuntime.isTunnelBear(profile),
                        relayPort = null,
                        customDns = dnsServers,
                    )
                }

                if (stopped.get()) return

                val handle = OpenVpnNative.create()
                check(handle != 0L)
                nativeHandle.set(handle)
                if (stopped.get()) OpenVpnNative.stop(handle)

                ConnectionLogStore.append(TAG, "Starting OpenVPN 3 session (${bypassRegion.id})")
                val err = OpenVpnNative.run(handle, effectiveConfig, username, password, this)
                if (err == "CREDENTIALS_REJECTED" && accountId == OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL) {
                    terminalError = "CUSTOM_AUTH_FAILED"
                    OpenVpnRuntime.clearTransientProfileCredentials(profileId)
                    runCatching { repo.clearProfileCredentials(profileId) }.onFailure {
                        ConnectionLogStore.append(TAG, "Failed clearing rejected custom creds: ${it.message}", isError = true)
                    }
                }
                if (terminalError == null && err.isNotEmpty()) terminalError = err
            } catch (_: CancellationException) {
                stopNative()
            } catch (e: Exception) {
                ConnectionLogStore.append(TAG, "Session failure: ${e.message ?: "unknown"}", isError = true)
                if (terminalError == null) terminalError = "CONNECTION_FAILED"
            } catch (_: LinkageError) {
                terminalError = "CORE_UNAVAILABLE"
            } finally {
                verifyJob?.cancel()
                relay?.let { r -> relay = null; runCatching { r.close() } }
                synchronized(this) {
                    runCatching { retainedTun?.close() }
                    retainedTun = null
                    builder = null
                }
                nativeHandle.getAndSet(0L).takeIf { it != 0L }?.let { OpenVpnNative.release(it) }

                if (activeSession === this) {
                    if (accountId == OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL) {
                        OpenVpnRuntime.clearTransientProfileCredentials(profileId)
                    }
                    val nextPhase = if (terminalError != null && (!userStoppedFlag.get() || timedOut)) {
                        ConnectionPhase.ERROR
                    } else {
                        ConnectionPhase.DISCONNECTED
                    }
                    OpenVpnRuntime.mutable.update { current ->
                        current.copy(
                            phase = nextPhase,
                            rxSpeedBps = 0L,
                            txSpeedBps = 0L,
                            error = if (nextPhase == ConnectionPhase.ERROR) terminalError else null,
                        )
                    }
                    refreshQuickTile()
                }
                commands.trySend(Command(finished = this))
            }
        }

        @Keep
        fun configureTun(operation: String, value: Int): Boolean = synchronized(this) {
            if (stopped.get() || activeSession !== this) return@synchronized false
            return@synchronized runCatching {
                when (operation) {
                    "new" -> {
                        dnsAddedCount = 0
                        builder = Builder()
                            .setSession("QobearVPN")
                            .setBlocking(false)
                            .apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
                            }
                        true
                    }
                    "mtu" -> {
                        builder?.setMtu(value.coerceIn(576, 9000)) != null
                    }
                    "protect" -> protect(value)
                    else -> false
                }
            }.getOrDefault(false)
        }

        @Keep
        fun addAddress(address: String, prefix: Int): Boolean = synchronized(this) {
            if (stopped.get()) return@synchronized false
            runCatching { builder!!.addAddress(address, prefix); true }.getOrDefault(false)
        }

        @Keep
        fun addRoute(address: String, prefix: Int): Boolean = synchronized(this) {
            if (stopped.get()) return@synchronized false
            runCatching {
                // Defer all default / def1 redirect-gateway routes so GeoBypassRouter remains authoritative.
                val isV4Default = (address == "0.0.0.0" && prefix in listOf(0, 1)) ||
                    (address == "128.0.0.0" && prefix == 1)
                val isV6Default = (address == "::" && prefix in listOf(0, 1)) ||
                    (address == "8000::" && prefix == 1) ||
                    (address == "2000::" && prefix == 3)
                if (isV4Default || isV6Default) return@runCatching true

                val pushed = CidrPrefix.parse("$address/$prefix")
                if (pushed != null && activeExclusions.any { excl -> excl.overlaps(pushed) }) {
                    ConnectionLogStore.append(TAG, "Skipped pushed route overlapping bypass: $address/$prefix")
                    return@runCatching true
                }
                builder!!.addRoute(address, prefix)
                true
            }.getOrDefault(false)
        }

        @Keep
        fun addDns(address: String, ignored: Int): Boolean = synchronized(this) {
            if (stopped.get()) return@synchronized false
            runCatching {
                builder!!.addDnsServer(address)
                dnsAddedCount++
                true
            }.getOrDefault(false)
        }

        @Keep
        fun addSearchDomain(domain: String, ignored: Int): Boolean = synchronized(this) {
            if (stopped.get()) return@synchronized false
            runCatching { builder!!.addSearchDomain(domain); true }.getOrDefault(false)
        }

        @Keep
        fun establishTun(): Int = synchronized(this) {
            if (stopped.get() || activeSession !== this) return@synchronized -1
            val b = builder ?: return@synchronized -1

            // Prevent Android falling back to local/ISP DNS when server pushes no DNS
            if (dnsAddedCount == 0) {
                runCatching { b.addDnsServer("1.1.1.1"); b.addDnsServer("1.0.0.1") }
            }

            val settings = settingsStore.settings.value
            val countryCidrs = GeoBypassRouter.loadCountryCidrs(this@QobearVpnService, bypassRegion)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                b.addRoute("0.0.0.0", 0)
                b.addRoute("::", 0)
                val exclusions = GeoBypassRouter.buildApi33Exclusions(
                    countryCidrs = countryCidrs,
                    lanBypass = settings.lanBypass,
                )
                exclusions.forEach { cidr ->
                    runCatching {
                        b.excludeRoute(IpPrefix(cidr.toInetAddress(), cidr.prefixLen))
                    }.onFailure { err ->
                        ConnectionLogStore.append(TAG, "excludeRoute ${cidr.format()} failed: ${err.message}", isError = true)
                    }
                }
                if (settings.lanBypass) {
                    GeoBypassRouter.LAN_IPV6_CIDRS.forEach { (addr, prefix) ->
                        runCatching {
                            b.excludeRoute(IpPrefix(InetAddress.getByName(addr), prefix))
                        }.onFailure { err ->
                            ConnectionLogStore.append(TAG, "excludeRoute6 $addr/$prefix failed: ${err.message}", isError = true)
                        }
                    }
                }
                ConnectionLogStore.append(TAG, "GeoBypass(${bypassRegion.id}): excluded ${exclusions.size} routes (API 33+)")
            } else {
                val routes = GeoBypassRouter.buildLegacyComplementRoutes(
                    countryCidrs = countryCidrs,
                    lanBypass = settings.lanBypass,
                )
                routes.forEach { r ->
                    runCatching { b.addRoute(r.toInetAddress(), r.prefixLen) }
                        .onFailure { err -> ConnectionLogStore.append(TAG, "addRoute ${r.format()} failed: ${err.message}", isError = true) }
                }
                b.addRoute("::", 0)
                ConnectionLogStore.append(TAG, "GeoBypass(${bypassRegion.id}): installed ${routes.size} complement routes")
            }

            applySplitTunneling(b)

            if (stopped.get() || activeSession !== this) return@synchronized -1
            val tun = b.establish() ?: return@synchronized -1
            if (stopped.get() || activeSession !== this) {
                runCatching { tun.close() }
                return@synchronized -1
            }
            retainedTun?.close()
            retainedTun = tun
            return@synchronized ParcelFileDescriptor.dup(tun.fileDescriptor).detachFd()
        }

        private fun applySplitTunneling(b: Builder) {
            val split = splitStore.config.value
            when (split.mode) {
                SplitTunnelMode.ALL -> Unit
                SplitTunnelMode.EXCLUDE -> {
                    split.packages.forEach { pkg ->
                        if (pkg != packageName) {
                            runCatching { b.addDisallowedApplication(pkg) }
                        }
                    }
                    ConnectionLogStore.append(TAG, "SplitTunnel(exclude): ${split.packages.size} app(s)")
                }
                SplitTunnelMode.INCLUDE -> {
                    var added = 0
                    split.packages.forEach { pkg ->
                        try {
                            b.addAllowedApplication(pkg)
                            added++
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                    if (added == 0) {
                        ConnectionLogStore.append(TAG, "SplitTunnel(include) list empty; routing all apps", isError = true)
                    } else {
                        ConnectionLogStore.append(TAG, "SplitTunnel(include): $added app(s)")
                    }
                }
            }
        }

        @Keep
        fun onCoreEvent(name: String, error: Boolean) {
            if (activeSession !== this || stopped.get()) return
            ConnectionLogStore.append("OpenVPN3", "Event: $name", isError = error)

            val mappedPhase = when (name) {
                "CONNECTED" -> null
                "RECONNECTING", "PAUSE", "RESOLVE", "WAIT", "CONNECTING" ->
                    if (OpenVpnRuntime.connection.value.connectedAt != null) {
                        ConnectionPhase.RECONNECTING
                    } else {
                        ConnectionPhase.CONNECTING
                    }
                "DISCONNECTED" -> ConnectionPhase.DISCONNECTING
                else -> null
            }

            if (name == "AUTH_FAILED") {
                when (accountId) {
                    OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL -> {
                        terminalError = "CUSTOM_AUTH_FAILED"
                        OpenVpnRuntime.clearTransientProfileCredentials(profileId)
                        runCatching { repo.clearProfileCredentials(profileId) }.onFailure {
                            ConnectionLogStore.append(TAG, "Failed clearing bad custom creds: ${it.message}", isError = true)
                        }
                    }
                    OpenVpnRuntime.SELF_CREDENTIAL -> {
                        terminalError = "CUSTOM_AUTH_FAILED"
                    }
                    else -> {
                        terminalError = "AUTH_FAILED"
                        runCatching {
                            repo.updateAccount(accountId) {
                                it.copy(auth = AuthState.FAILED, lastError = "AUTH_FAILED")
                            }
                        }.onFailure {
                            ConnectionLogStore.append(TAG, "Failed marking account auth failure: ${it.message}", isError = true)
                        }
                    }
                }
            } else if (error && terminalError == null) {
                terminalError = when (name) {
                    "CERT_VERIFY_FAIL", "TLS_VERSION_MIN", "CONNECTION_TIMEOUT", "NETWORK_UNREACHABLE" -> name
                    else -> "CONNECTION_FAILED"
                }
            }

            if (mappedPhase != null) {
                OpenVpnRuntime.mutable.update { old ->
                    old.copy(phase = mappedPhase, detail = name)
                }
            }

            if (name == "CONNECTED") {
                terminalError = null
                if (accountId != OpenVpnRuntime.SELF_CREDENTIAL &&
                    accountId != OpenVpnRuntime.CUSTOM_PROFILE_CREDENTIAL
                ) {
                    runCatching {
                        repo.updateAccount(accountId) {
                            it.copy(
                                auth = AuthState.ACCEPTED,
                                lastConnected = System.currentTimeMillis(),
                                lastError = null,
                            )
                        }
                    }
                }
                OpenVpnRuntime.mutable.update { old ->
                    old.copy(
                        phase = if (old.phase == ConnectionPhase.RECONNECTING) ConnectionPhase.CONNECTING else old.phase,
                        detail = "VERIFYING",
                    )
                }
                val gen = verifyGeneration.incrementAndGet()
                verifyJob?.cancel()
                verifyJob = scope.launch(Dispatchers.IO) { verifyThroughTunnel(gen) }
            }

            refreshQuickTile()
            updateNotification(OpenVpnRuntime.connection.value)
        }

        private suspend fun verifyThroughTunnel(generation: Int) {
            val split = splitStore.config.value
            if (split.mode == SplitTunnelMode.INCLUDE && packageName !in split.packages) {
                if (stopped.get() || activeSession !== this || verifyGeneration.get() != generation) return
                OpenVpnRuntime.mutable.update { old ->
                    old.copy(
                        phase = ConnectionPhase.CONNECTED,
                        detail = null,
                        connectedAt = old.connectedAt ?: System.currentTimeMillis(),
                    )
                }
                ConnectionLogStore.append(TAG, "Tunnel connected (whitelist split mode)")
                refreshQuickTile()
                updateNotification(OpenVpnRuntime.connection.value)
                return
            }

            repeat(3) { attempt ->
                if (stopped.get() || activeSession !== this || verifyGeneration.get() != generation) return
                val exit = runCatching { OpenVpnExitProbe.probe(this@QobearVpnService, 6_000) }.getOrNull()
                if (exit != null) {
                    if (stopped.get() || activeSession !== this || verifyGeneration.get() != generation) return
                    OpenVpnRuntime.mutable.update { old ->
                        old.copy(
                            phase = ConnectionPhase.CONNECTED,
                            detail = null,
                            connectedAt = old.connectedAt ?: System.currentTimeMillis(),
                            exitIp = exit.first,
                            exitCountry = exit.second,
                        )
                    }
                    ConnectionLogStore.append(TAG, "Tunnel verified: exit ${exit.first} (${exit.second ?: "--"})")
                    refreshQuickTile()
                    updateNotification(OpenVpnRuntime.connection.value)
                    return
                }
                if (attempt < 2) delay(1_800)
            }

            if (stopped.get() || activeSession !== this || verifyGeneration.get() != generation) return
            // Check whether TUN actually received data (e.g., all external geo endpoints blocked/rate-limited)
            val rxNow = OpenVpnRuntime.connection.value.received
            if (rxNow > 2_048L) {
                ConnectionLogStore.append(TAG, "Exit IP lookup unreachable, but tunnel carries data ($rxNow B RX)")
                OpenVpnRuntime.mutable.update { old ->
                    old.copy(
                        phase = ConnectionPhase.CONNECTED,
                        detail = null,
                        connectedAt = old.connectedAt ?: System.currentTimeMillis(),
                    )
                }
                refreshQuickTile()
                updateNotification(OpenVpnRuntime.connection.value)
            } else {
                ConnectionLogStore.append(TAG, "Tunnel verification timed out (NO_DATA)", isError = true)
                terminalError = "NO_DATA"
                nativeHandle.get().takeIf { it != 0L }?.let { OpenVpnNative.stop(it) }
            }
        }

        @Keep
        fun onCoreTraffic(bytesOut: Long, bytesIn: Long) {
            if (activeSession !== this || stopped.get()) return
            val now = System.currentTimeMillis()
            val rx = bytesIn.coerceAtLeast(0L)
            val tx = bytesOut.coerceAtLeast(0L)

            val prevMs = lastTrafficTickMs
            val dtMs = (now - prevMs).coerceAtLeast(1L)
            val rxSpeed = if (prevMs == 0L) 0L else ((rx - lastRxBytes).coerceAtLeast(0L) * 1000L) / dtMs
            val txSpeed = if (prevMs == 0L) 0L else ((tx - lastTxBytes).coerceAtLeast(0L) * 1000L) / dtMs
            lastTrafficTickMs = now
            lastRxBytes = rx
            lastTxBytes = tx

            OpenVpnRuntime.mutable.update { old ->
                old.copy(
                    received = rx,
                    sent = tx,
                    rxSpeedBps = rxSpeed,
                    txSpeedBps = txSpeed,
                    speedHistory = (old.speedHistory + TrafficSample(rxSpeed, txSpeed)).takeLast(30),
                )
            }

            if (now - lastNotifUpdateMs >= 1_500L && OpenVpnRuntime.connection.value.phase == ConnectionPhase.CONNECTED) {
                lastNotifUpdateMs = now
                updateNotification(OpenVpnRuntime.connection.value)
            }
        }

        @Keep
        fun onCoreLog(line: String) {
            if (activeSession === this && !stopped.get()) {
                ConnectionLogStore.append("OpenVPN3", line)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "QobearVPN Connection", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(conn: OpenVpnConnection) {
        if (shuttingDown) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(conn),
        )
    }

    private fun buildTransitionNotification(isSwitching: Boolean, isDisconnecting: Boolean): Notification {
        val fa = settingsStore.settings.value.language == AppLanguage.FA
        val text = when {
            isDisconnecting -> if (fa) "در حال قطع اتصال..." else "Disconnecting..."
            isSwitching -> if (fa) "در حال انتخاب سریع‌ترین سرور..." else "Switching to fastest server..."
            else -> if (fa) "در حال آماده‌سازی تانل..." else "Preparing tunnel..."
        }
        return buildRawNotification("QobearVPN", text, ongoing = !isDisconnecting)
    }

    private fun buildNotification(conn: OpenVpnConnection): Notification {
        val fa = settingsStore.settings.value.language == AppLanguage.FA
        val profile = conn.profileId?.let { id -> repo.data.value.profiles.firstOrNull { it.id == id } }
        val place = profile?.let { CountryGeoMapper.resolve(it) }

        val title = if (place != null) "${place.flag} ${place.localizedName(fa)}" else "QobearVPN"
        val text = when (conn.phase) {
            ConnectionPhase.CONNECTED -> {
                val down = formatRate(conn.rxSpeedBps)
                val up = formatRate(conn.txSpeedBps)
                val ip = conn.exitIp?.let { " · $it" }.orEmpty()
                if (fa) "متصل · ↓ $down  ↑ $up$ip" else "Connected · ↓ $down  ↑ $up$ip"
            }
            ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING ->
                if (fa) "در حال اتصال (${conn.bypassRegion.flag})..." else "Connecting (${conn.bypassRegion.id})..."
            ConnectionPhase.DISCONNECTING -> if (fa) "در حال قطع اتصال..." else "Disconnecting..."
            ConnectionPhase.ERROR -> if (fa) "خطای اتصال" else "Connection error"
            ConnectionPhase.DISCONNECTED -> if (fa) "قطع شده" else "Disconnected"
        }
        return buildRawNotification(title, text, ongoing = conn.active)
    }

    private fun buildRawNotification(title: String, text: String, ongoing: Boolean): Notification {
        val fa = settingsStore.settings.value.language == AppLanguage.FA
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectPending = servicePendingIntent(101, ACTION_DISCONNECT)
        val switchBestPending = servicePendingIntent(102, ACTION_SWITCH_BEST)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qobear_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .addAction(0, if (fa) "قطع اتصال" else "Disconnect", disconnectPending)
            .addAction(0, if (fa) "سریع‌ترین" else "Switch Best", switchBestPending)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, QobearVpnService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun refreshQuickTile() {
        runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, QobearTileService::class.java)
            )
        }
    }

    private fun formatRate(bps: Long): String = when {
        bps >= 1_048_576L -> String.format(Locale.US, "%.1f MB/s", bps / 1_048_576.0)
        bps >= 1024L -> String.format(Locale.US, "%d KB/s", bps / 1024L)
        else -> "$bps B/s"
    }

    companion object {
        private const val TAG = "QobearVpnService"
        const val ACTION_CONNECT = "com.qorvhex.qobearvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.qorvhex.qobearvpn.DISCONNECT"
        const val ACTION_SWITCH_BEST = "com.qorvhex.qobearvpn.SWITCH_BEST"
        const val EXTRA_PROFILE = "profile_id"
        const val EXTRA_ACCOUNT = "account_id"

        private const val CHANNEL_ID = "qobear_vpn_channel"
        private const val NOTIFICATION_ID = 7011
        private const val STOP_GRACE_MS = 5_000L
        private const val CONNECT_DEADLINE_MS = 55_000L
    }
}
