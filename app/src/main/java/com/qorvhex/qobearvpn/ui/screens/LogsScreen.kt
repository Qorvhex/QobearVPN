package com.qorvhex.qobearvpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.data.LogEntry
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily

@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    strings: QobearStrings,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalNothingPalette.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = palette.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = strings.viewLogs,
                    style = TextStyle(fontFamily = DotMatrixFamily, fontSize = 18.sp, color = palette.textPrimary),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            val text = logs.joinToString("\n") { "[${it.timestamp}] ${it.tag}: ${it.message}" }
                            clipboard.setText(AnnotatedString(text))
                        },
                    color = palette.surfaceCard,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, palette.borderHairline),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        tint = palette.textPrimary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onClear),
                    color = palette.surfaceCard,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, palette.borderHairline),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = strings.clearLogs,
                        tint = palette.nothingRed,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(logs) { entry ->
                Surface(
                    color = palette.surfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (entry.isError) palette.nothingRed.copy(alpha = 0.6f) else palette.borderHairline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${entry.timestamp} · ${entry.tag}",
                            style = TextStyle(
                                fontFamily = SpaceMonoFamily,
                                fontSize = 10.sp,
                                color = if (entry.isError) palette.nothingRed else palette.textSecondary,
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.message,
                            style = TextStyle(
                                fontFamily = SpaceMonoFamily,
                                fontSize = 11.sp,
                                color = palette.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
