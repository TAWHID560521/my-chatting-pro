package com.example.features.auth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.config.AppConfig
import com.example.core.localization.LocalAppStrings
import com.example.ui.components.CountrySelectorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRecoveryScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val recentCountries by viewModel.recentCountryIsos.collectAsState()
    var showCountrySheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.resetRecoveryFlow()
        viewModel.loadRecoveryInfo()
    }

    val handleBack: () -> Unit = {
        viewModel.clearError()
        if (state.recoveryStep == 1) {
            viewModel.resetRecoveryFlow()
        } else {
            onBack()
        }
    }

    BackHandler(enabled = state.recoveryStep == 1, onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.resetPinTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = handleBack, modifier = Modifier.testTag("recovery_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state.recoveryStep) {
                    0 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = strings.resetPinTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedCard(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCountrySheet = true }
                                        .testTag("recovery_country_selector")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = state.selectedCountry.flagEmoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = state.selectedCountry.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = state.selectedCountry.callingCode,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = strings.selectCountryTitle)
                                    }
                                }

                                OutlinedTextField(
                                    value = state.phoneInput,
                                    onValueChange = viewModel::updatePhone,
                                    label = { Text(strings.phoneNumberLabel) },
                                    placeholder = { Text(strings.phoneNumberHint) },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    prefix = { Text("${state.selectedCountry.callingCode} ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recovery_phone_input")
                                )

                                OutlinedTextField(
                                    value = state.emailInput,
                                    onValueChange = viewModel::updateEmail,
                                    label = { Text(strings.emailLabel) },
                                    placeholder = { Text(strings.emailHint) },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recovery_email_input")
                                )

                                if (state.errorMessage != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.errorMessage!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.requestRecoveryOtp(strings) },
                                    enabled = !state.isLoading,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("recovery_send_otp_button")
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(strings.sendOtpButton, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(strings.otpSentToPrefix, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = state.recoveryMaskedEmail ?: state.emailInput.trim(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        PinDotProgressRow(enteredCount = state.recoveryOtpCode.length, totalDigits = 6)

                        OutlinedTextField(
                            value = state.recoveryOtpCode,
                            onValueChange = viewModel::updateRecoveryOtp,
                            label = { Text(strings.otpCodeLabel) },
                            placeholder = { Text(strings.otpCodeHint) },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_otp_input")
                        )

                        OutlinedTextField(
                            value = state.recoveryNewPin,
                            onValueChange = viewModel::updateRecoveryNewPin,
                            label = { Text(strings.resetPinNewPinLabel) },
                            placeholder = { Text(strings.pinHint) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_new_pin_input")
                        )

                        OutlinedTextField(
                            value = state.recoveryConfirmPin,
                            onValueChange = viewModel::updateRecoveryConfirmPin,
                            label = { Text(strings.resetPinConfirmLabel) },
                            placeholder = { Text(strings.pinHint) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_confirm_pin_input")
                        )

                        if (state.errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.requestRecoveryOtp(strings) },
                                enabled = state.otpCooldownSeconds == 0 && !state.isLoading,
                                modifier = Modifier.testTag("recovery_resend_otp_button")
                            ) {
                                Text(
                                    if (state.otpCooldownSeconds > 0) {
                                        "${strings.resendOtpCountdownPrefix} ${state.otpCooldownSeconds}s"
                                    } else {
                                        strings.resendOtpButton
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.resetPinWithRecoveryOtp(strings) },
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("recovery_reset_pin_submit_button")
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(strings.verifyOtpButton, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    else -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = state.recoverySuccessMessage ?: strings.resetPinSuccessMsg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Button(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("recovery_done_login_button")
                                ) {
                                    Text(strings.loginButton, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Secondary support fallback if email was never linked on an older account
                val supportEmail = state.recoveryInfo?.supportEmail ?: AppConfig.FEEDBACK_EMAIL
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$supportEmail?subject=My%20Chatting%20Pro%20Account%20Recovery")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("recovery_contact_support_button")
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${strings.feedbackEmailLabel}: $supportEmail")
                }
            }
        }
    }

    if (showCountrySheet) {
        CountrySelectorSheet(
            selectedCountry = state.selectedCountry,
            recentCountryIsos = recentCountries,
            onSelect = {
                viewModel.selectCountry(it)
                showCountrySheet = false
            },
            onDismiss = { showCountrySheet = false }
        )
    }
}
