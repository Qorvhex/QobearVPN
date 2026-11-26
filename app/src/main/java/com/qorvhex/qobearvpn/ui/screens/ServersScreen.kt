package com.qorvhex.qobearvpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.qorvhex.qobearvpn.data.QobearCatalog
import com.qorvhex.qobearvpn.model.CountryGeoMapper
import com.qorvhex.qobearvpn.model.OpenVpnConnection
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily
import com.qorvhex.qobearvpn.ui.theme.VazirmatnFamily

private enum class ServerFilter { ALL, FAVORITES, REACHABLE }

@Immutable
private data class ServerRowUi(
    val profile: Profile,
    val flag: String,
    val displayName: String,
    val searchKey: String,
)

@Composable
fun ServersScreen(
    catalog: QobearCatalog,
    connection: OpenVpnConnection,
    probingIds: Set<String>,
    probeTotal: Int,
    strings: QobearStrings,
    onPingAll: () -> Unit,
    onPingSingle: (Profile) -> Unit,
    onSelectAndConnect: (Profile) -> Unit,
    onAutoBest: () -> Unit,
    onToggleFavorite: (Profile) -> Unit,
    onImportOvpn: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ServerFilter.ALL) }
    var sortByPing by rememberSaveable { mutableStateOf(true) }

    val isBatchProbing = probingIds.size > 1 && probeTotal > 0

    val rows = remember(catalog.profiles, strings.fa) {
        catalog.profiles.map { profile ->
            val place = CountryGeoMapper.resolve(profile)
            val display = place.localizedName(strings.fa)
            val search = buildString {
                append(place.nameFa.lowercase())
                append('|')
                append(place.nameEn.lowercase())
                append('|')
                append(profile.name.lowercase())
                if (!place.isoCode.isNullOrEmpty()) {
                    append('|')
                    append(place.isoCode.lowercase())
                }
            }
            ServerRowUi(
                profile = profile,
                flag = place.flag,
                displayName = display,
                searchKey = search,
            )
        }
    }

    val filtered = remember(rows, query, filter, sortByPing) {
        val q = query.trim().lowercase()
        val list = rows.filter { row ->
            val matchesQuery = q.isEmpty() || row.searchKey.contains(q)
            val matchesFilter = when (filter) {
                ServerFilter.ALL -> true
                ServerFilter.FAVORITES -> row.profile.favorite
                ServerFilter.REACHABLE -> row.profile.probe?.isReachable == true
            }
            matchesQuery && matchesFilter
        }
        if (sortByPing) {
            list.sortedWith(
                compareByDescending<ServerRowUi> { it.profile.favorite }
                    .thenBy { it.profile.probe?.millis ?: Long.MAX_VALUE }
                    .thenBy { it.displayName }
            )
        } else {
            list.sortedWith(
                compareByDescending<ServerRowUi> { it.profile.favorite }
                    .thenBy { it.displayName }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        // Top Bar: Title + Ping All Capsule + Import .ovpn
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = strings.serversTitle,
                style = TextStyle(
                    fontFamily = if (strings.fa) VazirmatnFamily else DotMatrixFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = palette.textPrimary,
                ),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onPingAll),
                    color = if (isBatchProbing) palette.nothingRed else palette.surfaceCard,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (isBatchProbing) palette.nothingRed else palette.borderHairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isBatchProbing) Icons.Rounded.Close else Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = if (isBatchProbing) palette.onAccent else palette.nothingRed,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isBatchProbing) strings.stopPing else strings.pingAll,
                            color = if (isBatchProbing) palette.onAccent else palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onImportOvpn),
                    color = palette.surfaceCard,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, palette.borderHairline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = strings.importOvpn,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Batch Ping Progress Indicator
        AnimatedVisibility(visible = isBatchProbing) {
            val done = (probeTotal - probingIds.size).coerceIn(0, probeTotal)
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = strings.probingNow,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = "$done / $probeTotal",
                        style = TextStyle(
                            fontFamily = SpaceMonoFamily,
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { if (probeTotal > 0) done.toFloat() / probeTotal else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = palette.nothingRed,
                    trackColor = palette.surfaceElevated,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Search Box
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(strings.searchServersHint, fontSize = 13.sp) },
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

        Spacer(Modifier.height(10.dp))

        // Filter & Sort Capsules
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipCapsule(
                label = strings.filterAll,
                selected = filter == ServerFilter.ALL,
                onClick = { filter = ServerFilter.ALL },
            )
            FilterChipCapsule(
                label = strings.filterFav,
                selected = filter == ServerFilter.FAVORITES,
                onClick = { filter = ServerFilter.FAVORITES },
            )
            FilterChipCapsule(
                label = strings.filterReachable,
                selected = filter == ServerFilter.REACHABLE,
                onClick = { filter = ServerFilter.REACHABLE },
            )
            FilterChipCapsule(
                label = strings.sortPing,
                selected = sortByPing,
                onClick = { sortByPing = !sortByPing },
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pinned Auto-Fastest Card
            item(key = "__auto_fastest__") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onAutoBest),
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, palette.nothingRed.copy(alpha = 0.7f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(palette.nothingRed.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = palette.nothingRed,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.autoConnectCardTitle,
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.autoConnectCardSub,
                                color = palette.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            items(filtered, key = { it.profile.id }) { row ->
                val profile = row.profile
                val isSelected = profile.id == catalog.selectedProfileId
                val isConnectedHere = connection.active && connection.profileId == profile.id
                val isProbing = profile.id in probingIds

                val borderColor = when {
                    isConnectedHere -> palette.statusConnected
                    isSelected -> palette.nothingRed
                    else -> palette.borderHairline
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectAndConnect(profile) },
                    color = palette.surfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(if (isSelected || isConnectedHere) 1.5.dp else 1.dp, borderColor),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = row.flag, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.displayName,
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            val probe = profile.probe
                            val statusText = when {
                                isProbing -> strings.probingNow
                                probe == null -> strings.notTested
                                probe.millis != null -> "${probe.millis} ms · ${probe.healthy.size.coerceAtLeast(1)} IP"
                                probe.error == "CONTROL_AUTH" -> strings.controlAuthUnprobeable
                                else -> strings.blockedOrTimeout
                            }
                            val statusColor = when {
                                isProbing -> palette.statusWarning
                                probe?.millis != null -> pingColor(
                                    probe.millis,
                                    palette.statusConnected,
                                    palette.statusWarning,
                                    palette.nothingRed,
                                )
                                probe?.error == "CONTROL_AUTH" -> palette.textSecondary
                                probe != null -> palette.nothingRed
                                else -> palette.textSecondary
                            }
                            Text(
                                text = statusText,
                                style = TextStyle(
                                    fontFamily = if (probe?.millis != null) SpaceMonoFamily else VazirmatnFamily,
                                    fontSize = 11.sp,
                                    color = statusColor,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }

                        // Individual Single-Server Ping Button
                        IconButton(
                            onClick = { onPingSingle(profile) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.nothingRed,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = strings.pingSingleHint,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }

                        // Favorite Toggle
                        IconButton(
                            onClick = { onToggleFavorite(profile) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (profile.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = null,
                                tint = if (profile.favorite) palette.statusWarning else palette.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = if (selected) palette.nothingRed else palette.surfaceCard,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) palette.nothingRed else palette.borderHairline),
    ) {
        Text(
            text = label,
            color = if (selected) palette.onAccent else palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
