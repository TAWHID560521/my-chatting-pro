package com.example.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.localization.LocalAppStrings
import com.example.ui.components.CountrySelectorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onAccountCreated: () -> Unit
) {
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsState()
    val recentCountries by viewModel.recentCountryIsos.collectAsState()

    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var showCountrySheet by remember { mutableStateOf(false) }
    var pinVisible by remember { mutableStateOf(false) }

    val handleStepBack: () -> Unit = {
        viewModel.clearError()
        if (currentStep > 0) {
            currentStep -= 1
        } else {
            onBack()
        }
    }

    BackHandler(enabled = currentStep > 0, onBack = handleStepBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.createAccountButton, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = handleStepBack, modifier = Modifier.testTag("register_back_button")) {
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
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                AuthStepProgressHeader(
                    currentStep = currentStep,
                    totalSteps = 4,
                    strings = strings
                )

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "register_step_transition"
                ) { step ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (step) {
                            0 -> {
                                Text(
                                    text = strings.registerStepPhoneTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = strings.countryLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedCard(
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCountrySheet = true }
                                        .testTag("register_country_selector")
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
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = strings.selectCountryTitle)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

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
                                        .testTag("register_phone_input")
                                )

                                StepErrorBanner(state.errorMessage)

                                Spacer(modifier = Modifier.height(26.dp))

                                Button(
                                    onClick = {
                                        if (viewModel.validatePhoneStep(strings)) {
                                            currentStep = 1
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("register_next_step_1_button")
                                ) {
                                    Text(
                                        text = strings.continueButton,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.clearError()
                                            onNavigateToLogin()
                                        },
                                        modifier = Modifier.testTag("go_to_login_button")
                                    ) {
                                        Text(strings.alreadyHaveAccountPrompt)
                                    }
                                }
                            }

                            1 -> {
                                SelectedPhonePill(
                                    flagEmoji = state.selectedCountry.flagEmoji,
                                    phoneE164 = viewModel.formattedPhonePreview(),
                                    changeLabel = strings.changeLabel,
                                    onEdit = {
                                        viewModel.clearError()
                                        currentStep = 0
                                    }
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = strings.registerStepNameTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedTextField(
                                    value = state.displayNameInput,
                                    onValueChange = viewModel::updateDisplayName,
                                    label = { Text(strings.displayNameLabel) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_name_input")
                                )

                                Spacer(modifier = Modifier.height(14.dp))

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
                                        .testTag("register_email_input")
                                )

                                StepErrorBanner(state.errorMessage)

                                Spacer(modifier = Modifier.height(26.dp))

                                Button(
                                    onClick = {
                                        if (viewModel.validateNameAndEmailStep(strings)) {
                                            currentStep = 2
                                        }
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("register_next_step_2_button")
                                ) {
                                    Text(
                                        text = strings.continueButton,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }

                            2 -> {
                                SelectedPhonePill(
                                    flagEmoji = state.selectedCountry.flagEmoji,
                                    phoneE164 = viewModel.formattedPhonePreview(),
                                    changeLabel = strings.changeLabel,
                                    onEdit = {
                                        viewModel.clearError()
                                        currentStep = 0
                                    }
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = strings.registerStepPinTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                PinDotProgressRow(enteredCount = state.pinInput.length, totalDigits = 5)

                                Spacer(modifier = Modifier.height(18.dp))

                                OutlinedTextField(
                                    value = state.pinInput,
                                    onValueChange = viewModel::updatePin,
                                    label = { Text(strings.pinLabel) },
                                    placeholder = { Text(strings.pinHint) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { pinVisible = !pinVisible }) {
                                            Icon(
                                                imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = strings.pinLabel
                                            )
                                        }
                                    },
                                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_pin_input")
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = state.confirmPinInput,
                                    onValueChange = viewModel::updateConfirmPin,
                                    label = { Text(strings.confirmPinLabel) },
                                    placeholder = { Text(strings.pinHint) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_confirm_pin_input")
                                )

                                StepErrorBanner(state.errorMessage)

                                Spacer(modifier = Modifier.height(26.dp))

                                Button(
                                    onClick = {
                                        viewModel.sendRegisterOtp(strings) {
                                            currentStep = 3
                                        }
                                    },
                                    enabled = !state.isLoading,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("register_send_otp_button")
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Text(
                                            text = strings.sendOtpButton,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                    }
                                }
                            }

                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MarkEmailRead,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = strings.otpSentToPrefix,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = state.maskedEmail ?: state.emailInput.trim(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Text(
                                    text = strings.registerStepEmailOtpTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                PinDotProgressRow(enteredCount = state.otpInput.length, totalDigits = 6)

                                Spacer(modifier = Modifier.height(18.dp))

                                OutlinedTextField(
                                    value = state.otpInput,
                                    onValueChange = viewModel::updateOtp,
                                    label = { Text(strings.otpCodeLabel) },
                                    placeholder = { Text(strings.otpCodeHint) },
                                    leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("register_otp_input")
                                )

                                StepErrorBanner(state.errorMessage)

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { viewModel.resendRegisterOtp(strings) },
                                        enabled = state.otpCooldownSeconds == 0 && !state.isLoading,
                                        modifier = Modifier.testTag("register_resend_otp_button")
                                    ) {
                                        Text(
                                            text = if (state.otpCooldownSeconds > 0) {
                                                "${strings.resendOtpCountdownPrefix} ${state.otpCooldownSeconds}s"
                                            } else {
                                                strings.resendOtpButton
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.verifyRegisterOtpAndCreate(strings, onAccountCreated) },
                                    enabled = !state.isLoading && state.otpInput.length == 6,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("register_verify_submit_button")
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Text(
                                            text = strings.verifyOtpButton,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
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

@Composable
private fun StepErrorBanner(errorMessage: String?) {
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
