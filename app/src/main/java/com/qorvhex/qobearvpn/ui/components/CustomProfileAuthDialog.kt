package com.qorvhex.qobearvpn.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.model.CountryGeoMapper
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings

@Composable
fun CustomProfileAuthDialog(
    profile: Profile,
    strings: QobearStrings,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String, savePassword: Boolean) -> Unit,
) {
    val palette = LocalNothingPalette.current
    val place = remember(profile.id, strings.fa) { CountryGeoMapper.resolve(profile) }
    var username by remember(profile.id) { mutableStateOf("") }
    var password by remember(profile.id) { mutableStateOf("") }
    var passwordVisible by remember(profile.id) { mutableStateOf(false) }
    var savePassword by remember(profile.id) { mutableStateOf(false) }
    var lastSubmitMs by remember(profile.id) { mutableStateOf(0L) }

    val trimmed = username.trim()
    val valid = trimmed.isNotBlank() &&
        trimmed.length <= 320 &&
        password.isNotEmpty() &&
        password.length <= 4096 &&
        '\u0000' !in trimmed &&
        '\n' !in trimmed &&
        '\r' !in trimmed &&
        '\u0000' !in password

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceCard,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.customAuthTitle,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${place.flag} ${place.localizedName(strings.fa)}",
                    color = palette.nothingRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.customAuthSub,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.customUsernameLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.nothingRed,
                        unfocusedBorderColor = palette.borderHairline,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.passwordLabel) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = palette.textSecondary,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.nothingRed,
                        unfocusedBorderColor = palette.borderHairline,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { savePassword = !savePassword }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = palette.nothingRed,
                            checkmarkColor = palette.onAccent,
                            uncheckedColor = palette.textSecondary,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = strings.savePasswordLabel,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (valid && now - lastSubmitMs >= 1_200L) {
                        lastSubmitMs = now
                        onSubmit(trimmed, password, savePassword)
                    }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.nothingRed,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.disabledSurface,
                    disabledContentColor = palette.disabledText,
                ),
            ) {
                Text(strings.connectAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction, color = palette.textSecondary)
            }
        },
    )
}
