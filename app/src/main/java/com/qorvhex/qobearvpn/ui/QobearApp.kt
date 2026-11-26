package com.qorvhex.qobearvpn.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qorvhex.qobearvpn.core.ProfileRuntime
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.service.OpenVpnRuntime
import com.qorvhex.qobearvpn.ui.components.MainTab
import com.qorvhex.qobearvpn.ui.components.NothingCapsuleNavBar
import com.qorvhex.qobearvpn.ui.screens.HomeScreen
import com.qorvhex.qobearvpn.ui.screens.LogsScreen
import com.qorvhex.qobearvpn.ui.screens.OnboardingScreen
import com.qorvhex.qobearvpn.ui.screens.ServersScreen
import com.qorvhex.qobearvpn.ui.screens.SettingsScreen
import com.qorvhex.qobearvpn.ui.screens.SplitTunnelScreen
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.QobearTheme
import kotlinx.coroutines.delay

@Composable
fun QobearApp(vm: QobearViewModel = viewModel()) {
    val context = LocalContext.current
    val catalog by vm.catalog.collectAsState()
    val settings by vm.settings.collectAsState()
    val splitConfig by vm.splitConfig.collectAsState()
    val connection by vm.connection.collectAsState()
    val probingIds by vm.probingIds.collectAsState()
    val probeTotal by vm.probeTotal.collectAsState()
    val banner by vm.bannerMessage.collectAsState()
    val apps by vm.installedApps.collectAsState()
    val appsLoading by vm.appsLoading.collectAsState()
    val logs by vm.logs.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.CONNECT) }
    var showingLogs by rememberSaveable { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pendingId = vm.pendingConnectProfileId
        vm.pendingConnectProfileId = null
        vm.pendingConnectAccountId = null
        val pendingProfile = pendingId?.let { id -> catalog.profiles.firstOrNull { it.id == id } }
        if (result.resultCode == Activity.RESULT_OK && pendingProfile != null) {
            vm.connectProfile(pendingProfile)
        } else if (pendingId != null) {
            vm.showBanner("VPN_DENIED")
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val pendingId = vm.pendingConnectProfileId
        val pendingProfile = pendingId?.let { id -> catalog.profiles.firstOrNull { it.id == id } }
        if (pendingProfile != null) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                vm.pendingConnectProfileId = null
                vm.pendingConnectAccountId = null
                vm.connectProfile(pendingProfile)
            }
        }
    }

    val importOvpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importOvpnUris(uris)
    }

    fun requestConnect(profile: Profile, accountId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                vm.pendingConnectProfileId = profile.id
                vm.pendingConnectAccountId = accountId
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            vm.pendingConnectProfileId = profile.id
            vm.pendingConnectAccountId = accountId
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            vm.connectProfile(profile)
        }
    }

    LaunchedEffect(banner) {
        if (!banner.isNullOrBlank()) {
            delay(4_500)
            vm.clearBanner()
        }
    }

    QobearTheme(
        themeMode = settings.themeMode,
        language = settings.language,
    ) {
        val palette = LocalNothingPalette.current
        val strings = remember(settings.language) { QobearStrings.of(settings.language) }

        DisposableEffect(palette.isDark) {
            val activity = context as? ComponentActivity
            activity?.enableEdgeToEdge(
                statusBarStyle = if (palette.isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
                navigationBarStyle = if (palette.isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
            )
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bgCanvas),
        ) {
            when {
                catalog.storageError -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = palette.nothingRed,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = strings.storageErrorTitle,
                            color = palette.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings.storageErrorSub,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick = vm::resetStorage,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.nothingRed,
                                contentColor = palette.onAccent,
                            ),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(strings.resetVaultAction)
                        }
                    }
                }

                else -> {
                    AnimatedContent(
                        targetState = settings.onboardingCompleted,
                        transitionSpec = {
                            (fadeIn(tween(360)) + scaleIn(initialScale = 0.96f, animationSpec = tween(360)))
                                .togetherWith(fadeOut(tween(240)))
                        },
                        label = "rootOnboardingGate",
                    ) { onboarded ->
                        if (!onboarded) {
                            OnboardingScreen(
                                initialRegion = settings.bypassRegion,
                                initialLanguage = settings.language,
                                onLanguageChange = vm::setLanguage,
                                onComplete = { user, pass, region, lang ->
                                    vm.completeOnboarding(user, pass, region, lang)
                                },
                            )
                        } else if (showingLogs) {
                            BackHandler { showingLogs = false }
                            LogsScreen(
                                logs = logs,
                                strings = strings,
                                onClear = { ConnectionLogStore.clear() },
                                onBack = { showingLogs = false },
                            )
                        } else {
                            BackHandler(enabled = currentTab != MainTab.CONNECT) {
                                currentTab = MainTab.CONNECT
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                            ) {
                                AnimatedContent(
                                    targetState = currentTab,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        val forward = targetState.ordinal > initialState.ordinal
                                        (slideInHorizontally(tween(300)) { w -> if (forward) w / 4 else -w / 4 } +
                                            fadeIn(tween(260)))
                                            .togetherWith(
                                                slideOutHorizontally(tween(260)) { w -> if (forward) -w / 5 else w / 5 } +
                                                    fadeOut(tween(180))
                                            )
                                    },
                                    label = "mainTabTransition",
                                ) { tab ->
                                    when (tab) {
                                        MainTab.CONNECT -> HomeScreen(
                                            catalog = catalog,
                                            settings = settings,
                                            connection = connection,
                                            probingIds = probingIds,
                                            probeTotal = probeTotal,
                                            strings = strings,
                                            onDismissError = { OpenVpnRuntime.clearError() },
                                            onDialClick = {
                                                if (connection.active) {
                                                    OpenVpnRuntime.disconnect(context)
                                                } else {
                                                    val selected = catalog.profiles.firstOrNull { it.id == catalog.selectedProfileId }
                                                        ?: catalog.profiles.firstOrNull()
                                                    if (selected == null) {
                                                        vm.showBanner("NO_PROFILES")
                                                    } else {
                                                        val accountId = catalog.activeAccountId
                                                        val needsAccount = !selected.selfContained || ProfileRuntime.isTunnelBear(selected)
                                                        if (accountId == null && needsAccount) {
                                                            vm.showBanner("ACCOUNT_REQUIRED")
                                                            currentTab = MainTab.SETTINGS
                                                        } else {
                                                            requestConnect(
                                                                selected,
                                                                if (!needsAccount) OpenVpnRuntime.SELF_CREDENTIAL else accountId!!
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onAutoBestClick = {
                                                val accountId = catalog.activeAccountId
                                                if (accountId == null) {
                                                    vm.showBanner("ACCOUNT_REQUIRED")
                                                    currentTab = MainTab.SETTINGS
                                                } else {
                                                    vm.connectAutoBest { best, acc -> requestConnect(best, acc) }
                                                }
                                            },
                                            onOpenServers = { currentTab = MainTab.SERVERS },
                                            onSelectBypassRegion = vm::setBypassRegion,
                                            onOpenTelegram = {
                                                if (!openTelegramChannel(context)) {
                                                    vm.showBanner("NO_INTENT_APP")
                                                }
                                            },
                                            onOpenAccountSettings = { currentTab = MainTab.SETTINGS },
                                        )

                                        MainTab.SERVERS -> ServersScreen(
                                            catalog = catalog,
                                            connection = connection,
                                            probingIds = probingIds,
                                            probeTotal = probeTotal,
                                            strings = strings,
                                            onPingAll = vm::pingAll,
                                            onPingSingle = vm::pingSingle,
                                            onSelectAndConnect = { profile ->
                                                val accountId = catalog.activeAccountId
                                                val needsAccount = !profile.selfContained || ProfileRuntime.isTunnelBear(profile)
                                                if (accountId == null && needsAccount) {
                                                    vm.selectProfile(profile.id)
                                                    vm.showBanner("ACCOUNT_REQUIRED")
                                                    currentTab = MainTab.SETTINGS
                                                } else {
                                                    requestConnect(profile, if (!needsAccount) OpenVpnRuntime.SELF_CREDENTIAL else accountId!!)
                                                    currentTab = MainTab.CONNECT
                                                }
                                            },
                                            onAutoBest = {
                                                val accountId = catalog.activeAccountId
                                                if (accountId == null) {
                                                    vm.showBanner("ACCOUNT_REQUIRED")
                                                    currentTab = MainTab.SETTINGS
                                                } else {
                                                    vm.connectAutoBest { best, acc ->
                                                        requestConnect(best, acc)
                                                        currentTab = MainTab.CONNECT
                                                    }
                                                }
                                            },
                                            onToggleFavorite = { vm.toggleFavorite(it.id) },
                                            onImportOvpn = { importOvpnLauncher.launch(arrayOf("*/*")) },
                                        )

                                        MainTab.SPLIT -> SplitTunnelScreen(
                                            config = splitConfig,
                                            apps = apps,
                                            loading = appsLoading,
                                            strings = strings,
                                            onLoadApps = vm::loadInstalledAppsIfNeeded,
                                            onModeChange = vm::setSplitMode,
                                            onTogglePackage = vm::toggleSplitPackage,
                                            onApplyIranPreset = vm::applyIranBankPreset,
                                        )

                                        MainTab.SETTINGS -> SettingsScreen(
                                            catalog = catalog,
                                            settings = settings,
                                            strings = strings,
                                            onSaveAccount = vm::saveAccount,
                                            onSelectAccount = vm::selectAccount,
                                            onRemoveAccount = vm::removeAccount,
                                            onSelectBypassRegion = vm::setBypassRegion,
                                            onSetDns = vm::setDns,
                                            onSetLanBypass = vm::setLanBypass,
                                            onSetAutoReconnect = vm::setAutoReconnect,
                                            onSetGlobalFallback = vm::setGlobalAutoFallback,
                                            onSetAutoServer = vm::setAutoConnectBestServer,
                                            onSetTheme = vm::setThemeMode,
                                            onSetLanguage = vm::setLanguage,
                                            onOpenLogs = { showingLogs = true },
                                            onReportBanner = vm::showBanner,
                                        )
                                    }
                                }

                                NothingCapsuleNavBar(
                                    selected = currentTab,
                                    strings = strings,
                                    onSelect = { currentTab = it },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }
            }

            // Global Notification Banner across ALL screens (Onboarding, Home, Servers, Split, Settings)
            AnimatedVisibility(
                visible = !banner.isNullOrBlank(),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val isError = banner?.let {
                    it.contains("ERROR", true) || it.contains("FAILED", true) || it == "VPN_DENIED" || it == "ACCOUNT_REQUIRED" || it == "NO_INTENT_APP"
                } == true
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.clearBanner() },
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isError) palette.nothingRed else palette.statusConnected),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (isError) palette.nothingRed else palette.statusConnected,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings.bannerText(banner),
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun openTelegramChannel(context: Context): Boolean {
    val tgUri = Uri.parse("tg://resolve?domain=Qorvhex_Channel")
    val webUri = Uri.parse("https://t.me/Qorvhex_Channel")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, tgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
