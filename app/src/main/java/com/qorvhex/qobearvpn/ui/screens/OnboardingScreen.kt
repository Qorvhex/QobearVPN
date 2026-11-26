package com.qorvhex.qobearvpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qorvhex.qobearvpn.model.AppLanguage
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.ui.theme.DotMatrixFamily
import com.qorvhex.qobearvpn.ui.theme.LocalNothingPalette
import com.qorvhex.qobearvpn.ui.theme.QobearStrings
import com.qorvhex.qobearvpn.ui.theme.SpaceMonoFamily

@Composable
fun OnboardingScreen(
    initialRegion: BypassRegion,
    initialLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onComplete: (username: String, password: String, region: BypassRegion, language: AppLanguage) -> Unit,
) {
    val palette = LocalNothingPalette.current
    val context = LocalContext.current
    val strings = remember(initialLanguage) { QobearStrings.of(initialLanguage) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedRegion by rememberSaveable { mutableStateOf(initialRegion) }
    var username by rememberSaveable { mutableStateOf("") }
    // Keep password strictly in volatile Compose memory, never serialized into Activity Bundle
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Top header: QOBEAR dot-matrix mark + Language toggle pill + Step indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.nothingRed),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "QOBEAR VPN",
                    style = TextStyle(
                        fontFamily = DotMatrixFamily,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                        color = palette.textPrimary,
                    ),
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        val next = if (initialLanguage == AppLanguage.FA) AppLanguage.EN else AppLanguage.FA
                        onLanguageChange(next)
                    },
                color = palette.surfaceCard,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Text(
                    text = if (initialLanguage == AppLanguage.FA) "FA / EN" else "EN / FA",
                    style = TextStyle(
                        fontFamily = SpaceMonoFamily,
                        fontSize = 11.sp,
                        color = palette.textPrimary,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Step dot-matrix indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(0, 1).forEach { idx ->
                val active = step == idx
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (active) 28.dp else 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) palette.nothingRed else palette.borderHairline),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 2 else -w / 2 } + fadeIn(tween(260)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(180))
                    )
            },
            label = "onboardingStep",
        ) { currentStep ->
            if (currentStep == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = strings.onboardWelcomeTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.onboardWelcomeSub,
                        fontSize = 14.sp,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = strings.chooseBypassTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.chooseBypassSub,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))

                    BypassRegion.entries.forEach { region ->
                        val isSelected = selectedRegion == region
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedRegion = region },
                            color = if (isSelected) palette.surfaceElevated else palette.surfaceCard,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) palette.nothingRed else palette.borderHairline,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = region.flag, fontSize = 26.sp)
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.regionTitle(region),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.regionDesc(region),
                                        fontSize = 12.sp,
                                        color = palette.textSecondary,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.nothingRed,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = strings.enterCredentialsTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.enterCredentialsSub,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(22.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            validationError = null
                        },
                        label = { Text(strings.usernameLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.nothingRed,
                            unfocusedBorderColor = palette.borderHairline,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedLabelColor = palette.nothingRed,
                            unfocusedLabelColor = palette.textSecondary,
                            cursorColor = palette.nothingRed,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationError = null
                        },
                        label = { Text(strings.passwordLabel) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.nothingRed,
                            unfocusedBorderColor = palette.borderHairline,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedLabelColor = palette.nothingRed,
                            unfocusedLabelColor = palette.textSecondary,
                            cursorColor = palette.nothingRed,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!validationError.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = validationError!!,
                            color = palette.nothingRed,
                            fontSize = 13.sp,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = palette.surfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = palette.statusConnected,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${selectedRegion.flag} ${strings.regionTitle(selectedRegion)}",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tunnelbear.com/account/signup"))
                                )
                            }
                        },
                    ) {
                        Text(
                            text = strings.createAccountHint,
                            color = palette.nothingRed,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (step == 0) {
            Button(
                onClick = { step = 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.nothingRed,
                    contentColor = palette.onAccent,
                ),
            ) {
                Text(text = strings.next, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { step = 0 },
                    modifier = Modifier
                        .weight(0.36f)
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, palette.borderHairline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textPrimary),
                ) {
                    Text(text = strings.back, fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        if (username.isBlank() || password.isEmpty()) {
                            validationError = strings.credentialValidationError
                        } else {
                            onComplete(username.trim(), password, selectedRegion, initialLanguage)
                        }
                    },
                    modifier = Modifier
                        .weight(0.64f)
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.nothingRed,
                        contentColor = palette.onAccent,
                        disabledContainerColor = palette.disabledSurface,
                        disabledContentColor = palette.disabledText,
                    ),
                ) {
                    Text(text = strings.finishAndEnter, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
