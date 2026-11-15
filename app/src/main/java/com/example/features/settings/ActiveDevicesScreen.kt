package com.example.features.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.localization.DateTimeFormatterUtil
import com.example.core.localization.LocalAppStrings
import com.example.core.model.ActiveSessionDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveDevicesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onCurrentDeviceSignedOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    val language by viewModel.language.collectAsState()
    val state by viewModel.devicesState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var sessionToKick by remember { mutableStateOf<ActiveSessionDto?>(null) }
    var showKickAllOthersDialog by remember { mutableStateOf(false) }
    var showBindEmailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadActiveSessions(strings)
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeStatusMessage()
    }

    val currentDeviceSession = state.sessions.firstOrNull { it.isCurrent }
    val otherDeviceSessions = state.sessions.filterNot { it.isCurrent }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.securityDevicesTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("active_devices_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadActiveSessions(strings) },
                        enabled = !state.isLoading,
                        modifier = Modifier.testTag("active_devices_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.retry)
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
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Verified Security Email Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.emailVerified) Icons.Default.VerifiedUser else Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.securityEmailTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (state.emailVerified && !state.emailMasked.isNullOrBlank()) {
                                        state.emailMasked!!
                                    } else {
                                        strings.securityEmailNotVerified
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.emailVerified) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.resetBindEmailDialog()
                                showBindEmailDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_bind_email_dialog_button")
                        ) {
                            Text(strings.addOrChangeEmailButton)
                        }
                    }
                }

                // New Device Email OTP Switch Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.loginOtpToggleTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.loginOtpToggleSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = state.requireLoginOtp,
                            enabled = !state.isTogglingOtp && state.emailVerified,
                            onCheckedChange = { enabled ->
                                viewModel.toggleLoginOtp(enabled, strings)
                            },
                            modifier = Modifier.testTag("toggle_login_otp_switch")
                        )
                    }
                }

                if (state.isLoading && state.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Current Device Highlighted Card
                if (currentDeviceSession != null) {
                    val lastActiveEpoch = DateTimeFormatterUtil.parseUtcTimestampMillis(currentDeviceSession.lastSeenAt)
                    val formattedTime = DateTimeFormatterUtil.formatMessageTimestampLine(lastActiveEpoch, language, strings)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = currentDeviceSession.deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = strings.currentDeviceBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${strings.lastActivePrefix} $formattedTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Other Logged-in Devices Section
                if (otherDeviceSessions.isNotEmpty()) {
                    Button(
                        onClick = { showKickAllOthersDialog = true },
                        enabled = !state.isRevoking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("logout_all_other_devices_button")
                    ) {
                        Icon(Icons.Default.LockPerson, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.logoutAllOthersButton, fontWeight = FontWeight.SemiBold)
                    }

                    otherDeviceSessions.forEach { deviceSession ->
                        val lastActiveEpoch = DateTimeFormatterUtil.parseUtcTimestampMillis(deviceSession.lastSeenAt)
                        val formattedTime = DateTimeFormatterUtil.formatMessageTimestampLine(lastActiveEpoch, language, strings)
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("other_device_card_${deviceSession.sessionId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deviceSession.deviceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${strings.lastActivePrefix} $formattedTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { sessionToKick = deviceSession },
                                    enabled = !state.isRevoking,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("kick_device_button_${deviceSession.sessionId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.logoutDeviceButton)
                                }
                            }
                        }
                    }
                } else if (!state.isLoading) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.noOtherDevicesConnected,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    sessionToKick?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isRevoking) sessionToKick = null },
            title = { Text(strings.logoutDeviceConfirmTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.deviceName, fontWeight = FontWeight.Bold)
                    Text(strings.logoutDeviceConfirmBody, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.sessionId
                        sessionToKick = null
                        viewModel.revokeSession(id, strings, onCurrentDeviceSignedOut)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_kick_single_device_button")
                ) {
                    Text(strings.logoutDeviceButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToKick = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showKickAllOthersDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isRevoking) showKickAllOthersDialog = false },
            title = {
                Text(
                    text = strings.logoutAllOthersConfirmTitle,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(strings.logoutAllOthersConfirmBody, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKickAllOthersDialog = false
                        viewModel.revokeOtherSessions(strings)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_kick_all_other_devices_button")
                ) {
                    Text(strings.logoutAllOthersButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickAllOthersDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showBindEmailDialog) {
        val awaitingOtp = !state.bindEmailChallengeId.isNullOrBlank()
        AlertDialog(
            onDismissRequest = {
                if (!state.isBindingEmail) {
                    showBindEmailDialog = false
                    viewModel.resetBindEmailDialog()
                }
            },
            title = { Text(strings.addOrChangeEmailButton) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!awaitingOtp) {
                        OutlinedTextField(
                            value = state.bindEmailInput,
                            onValueChange = viewModel::updateBindEmailInput,
                            label = { Text(strings.emailLabel) },
                            placeholder = { Text(strings.emailHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bind_email_input")
                        )
                    } else {
                        Text(
                            text = "${strings.otpSentToPrefix} ${state.bindEmailMasked ?: state.bindEmailInput}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = state.bindEmailOtpInput,
                            onValueChange = viewModel::updateBindEmailOtpInput,
                            label = { Text(strings.otpCodeLabel) },
                            placeholder = { Text(strings.otpCodeHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bind_email_otp_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.requestBindEmailOtp(strings) },
                                enabled = state.bindEmailCooldownSeconds == 0 && !state.isBindingEmail
                            ) {
                                Text(
                                    if (state.bindEmailCooldownSeconds > 0) {
                                        "${strings.resendOtpCountdownPrefix} ${state.bindEmailCooldownSeconds}s"
                                    } else {
                                        strings.resendOtpButton
                                    }
                                )
                            }
                        }
                    }

                    if (!state.bindEmailDialogError.isNullOrBlank()) {
                        Text(
                            text = state.bindEmailDialogError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isBindingEmail,
                    onClick = {
                        if (!awaitingOtp) {
                            viewModel.requestBindEmailOtp(strings)
                        } else {
                            viewModel.verifyBindEmailOtp(strings) {
                                showBindEmailDialog = false
                                viewModel.loadActiveSessions(strings)
                            }
                        }
                    },
                    modifier = Modifier.testTag("bind_email_confirm_button")
                ) {
                    if (state.isBindingEmail) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (!awaitingOtp) strings.sendOtpButton else strings.verifyOtpButton)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isBindingEmail,
                    onClick = {
                        showBindEmailDialog = false
                        viewModel.resetBindEmailDialog()
                    }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
