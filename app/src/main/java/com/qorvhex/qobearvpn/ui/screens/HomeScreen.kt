package com.qorvhex.qobearvpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.data.AppSettings
import com.qorvhex.qobearvpn.data.QobearCatalog
import com.qorvhex.qobearvpn.model.ConnectionPhase
import com.qorvhex.qobearvpn.model.CountryGeoMapper
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.ui.components.DotMatrixConnectDial
import com.qorvhex.qobearvpn.ui.components.QorvhexTelegramBadge
import com.qorvhex.qobearvpn.ui.components.SpeedSparklineCard
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    catalog: QobearCatalog,
    settings: AppSettings,
    connection: OpenVpnConnection,
    probingIds: Set<String>,
    probeTotal: Int,
    strings: QobearStrings,
    onDismissError: () -> Unit,
    onDialClick: () -> Unit,
    onAutoBestClick: () -> Unit,
    onOpenServers: () -> Unit,
    onPingSelected: (Profile) -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenAccountSettings: () -> Unit,
) {
    val palette = LocalNothingPalette.current

    val selectedProfile: Profile? = remember(catalog.profiles, catalog.selectedProfileId, connection.profileId) {
        val activeId = connection.profileId ?: catalog.selectedProfileId
        catalog.profiles.firstOrNull { it.id == activeId } ?: catalog.profiles.firstOrNull()
    }
    val place = remember(selectedProfile) { selectedProfile?.let { CountryGeoMapper.resolve(it) } }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connection.phase, connection.connectedAt) {
        if (connection.phase == ConnectionPhase.CONNECTED && connection.connectedAt != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header: QOBEAR Logo on Start + @Qorvhex_Channel Telegram Badge on End Corner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (connection.phase == ConnectionPhase.CONNECTED) palette.statusConnected
                            else palette.nothingRed
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.appTitle,
                    style = TextStyle(
                        fontFamily = DotMatrixFamily,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp,
                        color = palette.textPrimary,
                    ),
                )
            }

            QorvhexTelegramBadge(
                handle = strings.telegramHandle,
                onClick = onOpenTelegram,
            )
        }

        Spacer(Modifier.height(10.dp))

        AnimatedVisibility(
            visible = connection.phase == ConnectionPhase.ERROR && !connection.error.isNullOrBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val errorText = strings.errorMessage(connection.error)
            if (errorText.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (connection.error == "AUTH_FAILED" || connection.error == "ACCOUNT_REQUIRED") {
                                onOpenAccountSettings()
                            } else {
                                onDismissError()
                            }
                        },
                    color = palette.nothingRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.nothingRed.copy(alpha = 0.65f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = palette.nothingRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = errorText,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Main Animated Dot-Matrix Connect Dial
        DotMatrixConnectDial(
            phase = connection.phase,
            detail = connection.detail,
            rxSpeedBps = connection.rxSpeedBps,
            txSpeedBps = connection.txSpeedBps,
            strings = strings,
            onClick = onDialClick,
        )

        Spacer(Modifier.height(12.dp))

        // Session Elapsed Stopwatch (tabular-nums)
        val elapsedSec = if (connection.phase == ConnectionPhase.CONNECTED && connection.connectedAt != null) {
            ((nowMs - connection.connectedAt) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        Text(
            text = formatDuration(elapsedSec),
            style = TextStyle(
                fontFamily = SpaceMonoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (connection.phase == ConnectionPhase.CONNECTED) palette.textPrimary else palette.textSecondary,
                fontFeatureSettings = "tnum",
                letterSpacing = 2.sp,
            ),
        )

        Spacer(Modifier.height(18.dp))

        // Live Speed & Sparkline Telemetry (visible while connected or connecting)
        AnimatedVisibility(
            visible = connection.phase == ConnectionPhase.CONNECTED || connection.received > 0L,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SpeedSparklineCard(
                rxSpeedBps = connection.rxSpeedBps,
                txSpeedBps = connection.txSpeedBps,
                totalRx = connection.received,
                totalTx = connection.sent,
                samples = connection.speedHistory,
                exitIp = connection.exitIp,
                exitCountry = connection.exitCountry,
                strings = strings,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }

        // Active Server Card + Auto-Best Quick Switcher
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenServers),
            color = palette.surfaceCard,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, palette.borderHairline),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = place?.flag ?: "🌐", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.activeServer,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = place?.localizedName(strings.fa) ?: (selectedProfile?.name ?: "QobearVPN"),
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val pingMs = selectedProfile?.probe?.millis
                val isSelectedProbing = selectedProfile != null && selectedProfile.id in probingIds
                if (selectedProfile != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !isSelectedProbing) {
                                onPingSelected(selectedProfile)
                            },
                        color = palette.surfaceElevated,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelectedProbing) palette.nothingRed else palette.borderHairline,
                        ),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelectedProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.nothingRed,
                                )
                            } else {
                                Text(
                                    text = if (pingMs != null) "${pingMs}ms" else strings.tapToPing,
                                    style = TextStyle(
                                        fontFamily = if (pingMs != null) SpaceMonoFamily else com.qorvhex.qobearvpn.ui.theme.VazirmatnFamily,
                                        fontSize = 12.sp,
                                        color = if (pingMs != null) {
                                            pingColor(pingMs, palette.statusConnected, palette.statusWarning, palette.nothingRed)
                                        } else {
                                            palette.textSecondary
                                        },
                                        fontFeatureSettings = "tnum",
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = strings.changeServer,
                    tint = palette.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quick Auto-Best Fastest Server Pill Button (with live sweep progress when active)
        val isAutoProbing = probingIds.isNotEmpty()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onAutoBestClick),
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (isAutoProbing) palette.nothingRed else palette.borderHairline),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = palette.nothingRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isAutoProbing) strings.probingNow else strings.autoFastest,
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (isAutoProbing && probeTotal > 0) {
                    val done = (probeTotal - probingIds.size).coerceIn(0, probeTotal)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / probeTotal },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = palette.nothingRed,
                        trackColor = palette.surfaceCard,
                    )
                }
            }
        }

        Spacer(Modifier.height(84.dp))
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

internal fun pingColor(
    ms: Long,
    green: androidx.compose.ui.graphics.Color,
    amber: androidx.compose.ui.graphics.Color,
    red: androidx.compose.ui.graphics.Color,
) = when {
    ms < 300L -> green
    ms < 600L -> amber
    else -> red
}
