package com.qorvhex.qobearvpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.data.SplitTunnelConfig
import com.qorvhex.qobearvpn.model.SplitTunnelMode
import com.qorvhex.qobearvpn.ui.InstalledAppItem
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily

@Composable
fun SplitTunnelScreen(
    config: SplitTunnelConfig,
    apps: List<InstalledAppItem>,
    loading: Boolean,
    strings: QobearStrings,
    onLoadApps: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onTogglePackage: (String) -> Unit,
    onApplyIranPreset: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadApps()
    }

    val visibleApps = remember(apps, query, showSystem, config.packages) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            val visibleByType = showSystem || !app.isSystem || app.packageName in config.packages
            val matchesQuery = q.isEmpty() ||
                app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            visibleByType && matchesQuery
        }.sortedWith(
            compareByDescending<InstalledAppItem> { it.packageName in config.packages }
                .thenBy { it.label.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = strings.splitTitle,
            style = TextStyle(
                fontFamily = DotMatrixFamily,
                fontSize = 20.sp,
                color = palette.textPrimary,
            ),
        )

        Spacer(Modifier.height(10.dp))

        // Segmented Mode Capsule
        Surface(
            color = palette.surfaceCard,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, palette.borderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                listOf(
                    SplitTunnelMode.ALL to strings.splitAll,
                    SplitTunnelMode.INCLUDE to strings.splitInclude,
                    SplitTunnelMode.EXCLUDE to strings.splitExclude,
                ).forEach { (mode, label) ->
                    val active = config.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) palette.nothingRed else palette.surfaceCard)
                            .clickable { onModeChange(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (active) palette.onAccent else palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = when (config.mode) {
                SplitTunnelMode.ALL -> strings.splitAllSub
                SplitTunnelMode.INCLUDE -> strings.splitIncludeSub
                SplitTunnelMode.EXCLUDE -> strings.splitExcludeSub
            },
            color = palette.textSecondary,
            fontSize = 12.sp,
        )

        if (config.mode != SplitTunnelMode.ALL) {
            Spacer(Modifier.height(10.dp))

            // Quick Domestic Banking & Local Apps Preset
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onApplyIranPreset),
                color = palette.surfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = palette.nothingRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.iranBankPreset,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.searchAppsHint, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = palette.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.nothingRed,
                    unfocusedBorderColor = palette.borderHairline,
                    focusedContainerColor = palette.surfaceCard,
                    unfocusedContainerColor = palette.surfaceCard,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = strings.showSystemApps, color = palette.textSecondary, fontSize = 12.sp)
                Switch(
                    checked = showSystem,
                    onCheckedChange = { showSystem = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onAccent,
                        checkedTrackColor = palette.nothingRed,
                    ),
                )
            }

            Spacer(Modifier.height(6.dp))

            if (loading && apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.nothingRed)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in config.packages
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTogglePackage(app.packageName) },
                            color = palette.surfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (checked) palette.nothingRed else palette.borderHairline,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(palette.surfaceElevated),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Android,
                                            contentDescription = null,
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = palette.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = TextStyle(
                                            fontFamily = SpaceMonoFamily,
                                            fontSize = 10.sp,
                                            color = palette.textSecondary,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onTogglePackage(app.packageName) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = palette.nothingRed,
                                        checkmarkColor = palette.onAccent,
                                        uncheckedColor = palette.textSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
