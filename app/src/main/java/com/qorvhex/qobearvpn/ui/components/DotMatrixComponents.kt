package com.qorvhex.qobearvpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.ConnectionPhase
import com.qorvhex.qobearvpn.model.TrafficSample
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hero Nothing OS dot-matrix dial with radial Glyph rings, radar sweep during handshake,
 * live throughput pulse when connected, and tactile spring press physics.
 */
@Composable
fun DotMatrixConnectDial(
    phase: ConnectionPhase,
    detail: String?,
    rxSpeedBps: Long,
    txSpeedBps: Long,
    strings: QobearStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNothingPalette.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "dialScale",
    )

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == ConnectionPhase.ERROR) {
            repeat(4) { i ->
                shakeOffset.animateTo(if (i % 2 == 0) 10f else -10f, tween(45))
            }
            shakeOffset.animateTo(0f, spring())
        }
    }

    val infinite = rememberInfiniteTransition(label = "dialPulse")
    val radarAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "radar",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val ringAccent by animateColorAsState(
        targetValue = when (phase) {
            ConnectionPhase.CONNECTED -> palette.statusConnected
            ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> palette.nothingRed
            ConnectionPhase.DISCONNECTING -> palette.statusWarning
            ConnectionPhase.ERROR -> palette.nothingRed
            ConnectionPhase.DISCONNECTED -> palette.borderHairline
        },
        animationSpec = tween(350),
        label = "ringAccent",
    )

    val statusLabel = when (phase) {
        ConnectionPhase.CONNECTED -> strings.tapToDisconnect
        ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING ->
            if (detail == "VERIFYING") strings.verifyingTunnel else strings.tapToCancel
        ConnectionPhase.DISCONNECTING -> strings.disconnecting
        ConnectionPhase.ERROR -> if (strings.fa) "خطای اتصال" else "ERROR"
        ConnectionPhase.DISCONNECTED -> strings.tapToConnect
    }

    Column(
        modifier = modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(236.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val activityBoost = ((rxSpeedBps + txSpeedBps).coerceAtMost(2_000_000L) / 2_000_000f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.minDimension / 2f - 8.dp.toPx()

                // Concentric dot-matrix Glyph rings
                val rings = 5
                for (ring in 1..rings) {
                    val fraction = ring.toFloat() / rings
                    val r = maxR * (0.44f + 0.56f * fraction)
                    val dotCount = 16 + ring * 8
                    for (i in 0 until dotCount) {
                        val angleDeg = (360f / dotCount) * i - 90f
                        val rad = angleDeg * (PI.toFloat() / 180f)
                        val x = cx + cos(rad) * r
                        val y = cy + sin(rad) * r

                        val (dotColor, dotRadius) = when (phase) {
                            ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> {
                                val delta = ((angleDeg - radarAngle + 360f) % 360f)
                                val hit = (1f - (delta / 85f)).coerceIn(0f, 1f)
                                val c = if (hit > 0.05f) {
                                    palette.nothingRed.copy(alpha = 0.25f + 0.75f * hit)
                                } else {
                                    palette.dotInactive.copy(alpha = 0.45f)
                                }
                                c to (1.8.dp.toPx() + hit * 1.6.dp.toPx())
                            }
                            ConnectionPhase.CONNECTED -> {
                                val wave = ((sin((angleDeg * 3f + radarAngle) * PI / 180f) + 1.0) / 2.0).toFloat()
                                val intensity = 0.35f + 0.45f * wave + 0.20f * activityBoost
                                val c = if (ring == rings) {
                                    palette.statusConnected.copy(alpha = intensity.coerceIn(0.25f, 1f))
                                } else {
                                    palette.dotActive.copy(alpha = (0.12f + 0.28f * wave))
                                }
                                c to (1.8.dp.toPx() + (if (ring == rings) 1.0.dp.toPx() * wave else 0f))
                            }
                            ConnectionPhase.ERROR -> {
                                val c = if (ring == rings) palette.nothingRed.copy(alpha = 0.85f)
                                else palette.dotInactive.copy(alpha = 0.35f)
                                c to 2.dp.toPx()
                            }
                            ConnectionPhase.DISCONNECTING -> {
                                palette.statusWarning.copy(alpha = 0.3f + 0.4f * breathe) to 2.dp.toPx()
                            }
                            ConnectionPhase.DISCONNECTED -> {
                                val isTopMarker = ring == rings && i == 0
                                if (isTopMarker) {
                                    palette.nothingRed.copy(alpha = 0.45f + 0.55f * breathe) to 3.4.dp.toPx()
                                } else {
                                    palette.dotInactive.copy(alpha = 0.55f) to 1.8.dp.toPx()
                                }
                            }
                        }

                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x, y),
                        )
                    }
                }

                // Inner tactile core circle
                val coreRadius = maxR * 0.40f
                drawCircle(
                    color = palette.surfaceCard,
                    radius = coreRadius,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = ringAccent,
                    radius = coreRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.6.dp.toPx()),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = statusLabel,
                    tint = when (phase) {
                        ConnectionPhase.CONNECTED -> palette.statusConnected
                        ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING, ConnectionPhase.ERROR -> palette.nothingRed
                        ConnectionPhase.DISCONNECTING -> palette.statusWarning
                        ConnectionPhase.DISCONNECTED -> palette.textPrimary
                    },
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (phase) {
                        ConnectionPhase.CONNECTED -> "ON"
                        ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> "..."
                        ConnectionPhase.DISCONNECTING -> "OFF"
                        ConnectionPhase.ERROR -> "!"
                        ConnectionPhase.DISCONNECTED -> "GO"
                    },
                    style = TextStyle(
                        fontFamily = DotMatrixFamily,
                        fontSize = 14.sp,
                        color = palette.textSecondary,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = palette.surfaceCard,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, ringAccent.copy(alpha = if (phase == ConnectionPhase.DISCONNECTED) 1f else 0.75f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (phase) {
                                ConnectionPhase.CONNECTED -> palette.statusConnected
                                ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> palette.nothingRed
                                ConnectionPhase.DISCONNECTING -> palette.statusWarning
                                ConnectionPhase.ERROR -> palette.nothingRed
                                ConnectionPhase.DISCONNECTED -> palette.textSecondary
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Corner Telegram channel capsule (`@Qorvhex_Channel`) styled cleanly in Nothing OS hardware minimalism.
 */
@Composable
fun QorvhexTelegramBadge(
    handle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNothingPalette.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = palette.surfaceCard,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, palette.borderHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelegramPaperPlaneIcon(
                tint = palette.nothingRed,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = handle,
                style = TextStyle(
                    fontFamily = SpaceMonoFamily,
                    fontSize = 11.sp,
                    color = palette.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TelegramPaperPlaneIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.94f, h * 0.14f)
            lineTo(w * 0.06f, h * 0.48f)
            lineTo(w * 0.38f, h * 0.60f)
            lineTo(w * 0.78f, h * 0.28f)
            lineTo(w * 0.47f, h * 0.64f)
            lineTo(w * 0.77f, h * 0.87f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun SpeedSparklineCard(
    rxSpeedBps: Long,
    txSpeedBps: Long,
    totalRx: Long,
    totalTx: Long,
    samples: List<TrafficSample>,
    exitIp: String?,
    exitCountry: String?,
    strings: QobearStrings,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNothingPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.surfaceCard,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, palette.borderHairline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricCol(
                    label = "↓ ${strings.downloadLabel}",
                    value = formatRate(rxSpeedBps),
                    accent = palette.statusConnected,
                )
                MetricCol(
                    label = "↑ ${strings.uploadLabel}",
                    value = formatRate(txSpeedBps),
                    accent = palette.textPrimary,
                )
                MetricCol(
                    label = strings.totalTraffic,
                    value = formatBytes(totalRx + totalTx),
                    accent = palette.textSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Smooth Canvas Sparkline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
            ) {
                val pts = if (samples.size >= 2) samples else listOf(TrafficSample(0, 0), TrafficSample(0, 0))
                val maxVal = pts.maxOf { maxOf(it.rxPerSec, it.txPerSec) }.coerceAtLeast(1024L).toFloat()
                val stepX = size.width / (pts.size - 1).coerceAtLeast(1)

                val rxPath = Path()
                val fillPath = Path()
                pts.forEachIndexed { idx, s ->
                    val x = idx * stepX
                    val y = size.height - (s.rxPerSec / maxVal) * (size.height - 6.dp.toPx()) - 3.dp.toPx()
                    if (idx == 0) {
                        rxPath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        val prevX = (idx - 1) * stepX
                        val prevY = size.height - (pts[idx - 1].rxPerSec / maxVal) * (size.height - 6.dp.toPx()) - 3.dp.toPx()
                        val midX = (prevX + x) / 2f
                        rxPath.cubicTo(midX, prevY, midX, y, x, y)
                        fillPath.cubicTo(midX, prevY, midX, y, x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(palette.statusConnected.copy(alpha = 0.22f), Color.Transparent)
                    ),
                )
                drawPath(
                    path = rxPath,
                    color = palette.statusConnected,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            if (!exitIp.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.exitIpLabel,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = listOfNotNull(exitCountry, exitIp).joinToString(" · "),
                        style = TextStyle(
                            fontFamily = SpaceMonoFamily,
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, accent: Color) {
    val palette = LocalNothingPalette.current
    Column {
        Text(text = label, color = palette.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = SpaceMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accent,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

enum class MainTab { CONNECT, SERVERS, SPLIT, SETTINGS }

@Composable
fun NothingCapsuleNavBar(
    selected: MainTab,
    strings: QobearStrings,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNothingPalette.current
    val haptic = LocalHapticFeedback.current
    val items = listOf(
        Triple(MainTab.CONNECT, Icons.Rounded.Bolt, strings.navConnect),
        Triple(MainTab.SERVERS, Icons.Rounded.Public, strings.navServers),
        Triple(MainTab.SPLIT, Icons.Rounded.Tune, strings.navSplit),
        Triple(MainTab.SETTINGS, Icons.Rounded.Settings, strings.navSettings),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = palette.surfaceCard,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, palette.borderHairline),
    ) {
        BoxWithConstraints(modifier = Modifier.padding(6.dp)) {
            val itemWidth = maxWidth / items.size
            val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "navPill",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceElevated)
                    .border(1.dp, palette.borderHairline, RoundedCornerShape(999.dp)),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                items.forEach { (tab, icon, label) ->
                    val active = tab == selected
                    val tint by animateColorAsState(
                        targetValue = if (active) palette.nothingRed else palette.textSecondary,
                        animationSpec = tween(200),
                        label = "tabTint",
                    )
                    NavTabCell(
                        icon = icon,
                        label = label,
                        tint = tint,
                        active = active,
                        onClick = {
                            if (!active) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabCell(
    icon: ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNothingPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Text(
            text = label,
            color = if (active) palette.textPrimary else palette.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BypassRegionSheet(
    current: BypassRegion,
    strings: QobearStrings,
    onSelect: (BypassRegion) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        contentColor = palette.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.chooseBypassTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.chooseBypassSub,
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(14.dp))

            BypassRegion.entries.forEach { region ->
                val selected = region == current
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onSelect(region)
                            onDismiss()
                        },
                    color = if (selected) palette.surfaceElevated else palette.bgCanvas,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) palette.nothingRed else palette.borderHairline,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = region.flag, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.regionTitle(region),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.regionDesc(region),
                                fontSize = 11.sp,
                                color = palette.textSecondary,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = palette.nothingRed,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatRate(bps: Long): String = when {
    bps >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB/s", bps / 1_048_576.0)
    bps >= 1024L -> String.format(java.util.Locale.US, "%d KB/s", bps / 1024L)
    else -> "$bps B/s"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
