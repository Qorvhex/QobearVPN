package com.qorvhex.qobearvpn.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qorvhex.qobearvpn.data.AppSettings
import com.qorvhex.qobearvpn.data.QobearCatalog
import com.qorvhex.qobearvpn.model.Account
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.AppThemeMode
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.DnsPreset
import com.qorvhex.qobearvpn.ui.components.BypassRegionSheet
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily
import com.qorvhex.qobearvpn.ui.theme.VazirmatnFamily

@Composable
fun SettingsScreen(
    catalog: QobearCatalog,
    settings: AppSettings,
    vpnActive: Boolean,
    strings: QobearStrings,
    onSaveAccount: (id: String?, user: String, pass: String) -> Unit,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSelectBypassRegion: (BypassRegion) -> Unit,
    onSetDns: (DnsPreset, String) -> Unit,
    onSetLanBypass: (Boolean) -> Unit,
    onSetAutoReconnect: (Boolean) -> Unit,
    onSetDpiBypass: (Boolean) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onOpenLogs: () -> Unit,
    onReportBanner: (String) -> Unit,
) {
    val palette = LocalNothingPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showBypassSheet by remember { mutableStateOf(false) }
    var showCustomDnsDialog by remember { mutableStateOf(false) }

    if (showBypassSheet && !vpnActive) {
        BypassRegionSheet(
            current = settings.bypassRegion,
            strings = strings,
            onSelect = onSelectBypassRegion,
            onDismiss = { showBypassSheet = false },
        )
    }

    if (showAccountDialog) {
        AccountEditorDialog(
            existing = editingAccount,
            strings = strings,
            onDismiss = { showAccountDialog = false },
            onConfirm = { u, p ->
                onSaveAccount(editingAccount?.id, u, p)
                showAccountDialog = false
            },
        )
    }

    if (showCustomDnsDialog && !vpnActive) {
        CustomDnsDialog(
            initial = settings.customDns,
            strings = strings,
            onDismiss = { showCustomDnsDialog = false },
            onSave = { raw ->
                onSetDns(DnsPreset.CUSTOM, raw)
                showCustomDnsDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = strings.settingsTitle,
            style = TextStyle(
                fontFamily = if (strings.fa) VazirmatnFamily else DotMatrixFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = palette.textPrimary,
            ),
        )
        Spacer(Modifier.height(14.dp))

        // Accounts section
        SectionHeader(strings.sectionAccount)
        Surface(
            color = palette.surfaceCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, palette.borderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                catalog.accounts.forEach { acc ->
                    val active = acc.id == catalog.activeAccountId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectAccount(acc.id) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (active) palette.nothingRed else palette.borderHairline,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = acc.username,
                                style = TextStyle(
                                    fontFamily = SpaceMonoFamily,
                                    fontSize = 13.sp,
                                    color = palette.textPrimary,
                                ),
                            )
                            if (!acc.lastError.isNullOrBlank()) {
                                Text(
                                    text = strings.errorMessage(acc.lastError),
                                    color = palette.nothingRed,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                editingAccount = acc
                                showAccountDialog = true
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(17.dp))
                        }
                        IconButton(
                            onClick = { onRemoveAccount(acc.id) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            editingAccount = null
                            showAccountDialog = true
                        },
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.nothingRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = strings.addAccount, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(strings.sectionNetwork)
        Surface(
            color = palette.surfaceCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, palette.borderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vpnActive) {
                    Surface(
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.vpnConnectedLockedHint,
                                color = palette.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !vpnActive) { showBypassSheet = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = settings.bypassRegion.flag,
                        fontSize = 22.sp,
                        modifier = Modifier.alpha(if (vpnActive) 0.45f else 1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.regionTitle(settings.bypassRegion),
                            color = if (vpnActive) palette.disabledText else palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = strings.regionDesc(settings.bypassRegion),
                            color = if (vpnActive) palette.disabledText else palette.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = if (vpnActive) palette.disabledText else palette.textSecondary,
                    )
                }

                // DNS Selector Capsules
                Text(
                    text = strings.dnsLabel,
                    color = if (vpnActive) palette.disabledText else palette.textSecondary,
                    fontSize = 12.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DnsPreset.entries.forEach { preset ->
                        val active = settings.dnsPreset == preset
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(enabled = !vpnActive) {
                                    if (preset == DnsPreset.CUSTOM) showCustomDnsDialog = true
                                    else onSetDns(preset, settings.customDns)
                                },
                            color = when {
                                vpnActive -> palette.disabledSurface
                                active -> palette.nothingRed
                                else -> palette.surfaceElevated
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = if (vpnActive) BorderStroke(1.dp, palette.borderHairline) else null,
                        ) {
                            Text(
                                text = preset.id.uppercase(),
                                style = TextStyle(
                                    fontFamily = SpaceMonoFamily,
                                    fontSize = 11.sp,
                                    color = when {
                                        vpnActive -> palette.disabledText
                                        active -> palette.onAccent
                                        else -> palette.textPrimary
                                    },
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                ToggleRow(strings.lanBypassLabel, settings.lanBypass, onSetLanBypass, enabled = !vpnActive)
                ToggleRow(strings.autoReconnectLabel, settings.autoReconnect, onSetAutoReconnect, enabled = true)
                ToggleRow(
                    label = strings.dpiBypassLabel,
                    checked = settings.dpiBypassEnabled,
                    onCheckedChange = onSetDpiBypass,
                    subtitle = strings.dpiBypassSub,
                    enabled = !vpnActive,
                )

                // Always-on VPN & Kill Switch system settings
                ActionRow(
                    icon = Icons.Rounded.Security,
                    title = strings.alwaysOnSystem,
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                            .onFailure { onReportBanner("NO_INTENT_APP") }
                    },
                )

                // Battery optimization exemption (in-app system dialog first, settings fallback on custom OEM ROMs)
                ActionRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = if (batteryExempt) strings.batteryExemptActive else strings.batteryOpt,
                    accent = if (batteryExempt) palette.statusConnected else palette.nothingRed,
                    onClick = {
                        if (batteryExempt) {
                            onReportBanner("BATTERY_ALREADY_EXEMPT")
                        } else {
                            val popupIntent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(popupIntent) }.onFailure {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure { onReportBanner("NO_INTENT_APP") }
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(strings.sectionAppearance)
        Surface(
            color = palette.surfaceCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, palette.borderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.themeLabel, color = palette.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppThemeMode.DARK to if (strings.fa) "دارک Nothing" else "Nothing Dark",
                        AppThemeMode.LIGHT to if (strings.fa) "لایت کاغذی" else "Paper Light",
                        AppThemeMode.SYSTEM to if (strings.fa) "سیستم" else "System",
                    ).forEach { (mode, label) ->
                        val active = settings.themeMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSetTheme(mode) },
                            color = if (active) palette.nothingRed else palette.surfaceElevated,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (active) palette.onAccent else palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                            )
                        }
                    }
                }

                Text(strings.languageLabel, color = palette.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppLanguage.FA to "فارسی", AppLanguage.EN to "English").forEach { (lang, label) ->
                        val active = settings.language == lang
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSetLanguage(lang) },
                            color = if (active) palette.nothingRed else palette.surfaceElevated,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (active) palette.onAccent else palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            )
                        }
                    }
                }

                ActionRow(
                    icon = Icons.Rounded.Terminal,
                    title = strings.viewLogs,
                    onClick = onOpenLogs,
                )
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalNothingPalette.current
    Text(
        text = title,
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val palette = LocalNothingPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) palette.textPrimary else palette.disabledText,
                fontSize = 13.sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (enabled) palette.textSecondary else palette.disabledText,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccent,
                checkedTrackColor = palette.nothingRed,
                disabledCheckedThumbColor = palette.disabledText,
                disabledCheckedTrackColor = palette.disabledSurface,
                disabledUncheckedThumbColor = palette.disabledText,
                disabledUncheckedTrackColor = palette.disabledSurface,
                disabledCheckedBorderColor = palette.borderHairline,
                disabledUncheckedBorderColor = palette.borderHairline,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    accent: androidx.compose.ui.graphics.Color = LocalNothingPalette.current.nothingRed,
    onClick: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = title, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
    }
}

@Composable
private fun AccountEditorDialog(
    existing: Account?,
    strings: QobearStrings,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val palette = LocalNothingPalette.current
    val context = LocalContext.current
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        title = { Text(strings.enterCredentialsTitle, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(strings.usernameLabel) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(strings.passwordLabel) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                )
                TextButton(
                    onClick = {
                        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.tunnelbear.android"))
                        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.tunnelbear.android"))
                        if (runCatching { context.startActivity(market); true }.getOrDefault(false).not()) {
                            runCatching { context.startActivity(web) }
                        }
                    },
                ) {
                    Text(
                        text = strings.openTunnelBearPlay,
                        color = palette.nothingRed,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (user.isNotBlank() && pass.isNotEmpty()) onConfirm(user.trim(), pass) },
                enabled = user.isNotBlank() && pass.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.nothingRed,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.disabledSurface,
                    disabledContentColor = palette.disabledText,
                ),
            ) {
                Text(if (strings.fa) "ذخیره" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (strings.fa) "انصراف" else "Cancel", color = palette.textSecondary)
            }
        },
    )
}

@Composable
private fun CustomDnsDialog(
    initial: String,
    strings: QobearStrings,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val palette = LocalNothingPalette.current
    var dnsText by remember { mutableStateOf(initial.ifBlank { "1.1.1.1, 1.0.0.1" }) }
    val valid = AppSettings.parseValidIpv4List(dnsText) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        title = { Text(strings.dnsLabel, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = dnsText,
                    onValueChange = { dnsText = it },
                    label = { Text("IPv4 (e.g. 1.1.1.1, 8.8.8.8)") },
                    isError = !valid,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(dnsText) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.nothingRed,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.disabledSurface,
                    disabledContentColor = palette.disabledText,
                ),
            ) {
                Text(if (strings.fa) "تایید" else "Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (strings.fa) "انصراف" else "Cancel", color = palette.textSecondary)
            }
        },
    )
}
