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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
fun LoginScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onNavigateToCreateAccount: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    onLoginSuccess: () -> Unit
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
                title = { Text(strings.loginButton, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = handleStepBack, modifier = Modifier.testTag("login_back_button")) {
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
                    totalSteps = 2,
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
                    label = "login_step_transition"
                ) { step ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (step) {
                            0 -> {
                                Text(
                                    text = strings.loginStepPhoneTitle,
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
                                        .testTag("login_country_selector")
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
                                        .testTag("login_phone_input")
                                )

                                if (state.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
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
                                        .testTag("login_next_button")
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
                                            onNavigateToCreateAccount()
                                        },
                                        modifier = Modifier.testTag("go_to_create_account_button")
                                    ) {
                                        Text(strings.createAccountPrompt)
                                    }
                                }
                            }

                            else -> {
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
                                    text = strings.loginStepPinTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                PinDotProgressRow(enteredCount = state.pinInput.length)

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
                                        .testTag("login_pin_input")
                                )

                                if (state.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
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

                                Spacer(modifier = Modifier.height(26.dp))

                                Button(
                                    onClick = { viewModel.login(strings, onLoginSuccess) },
                                    enabled = !state.isLoading,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("login_submit_button")
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        Text(
                                            text = strings.loginButton,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    TextButton(
                                        onClick = onNavigateToRecovery,
                                        modifier = Modifier.testTag("go_to_recovery_info_button")
                                    ) {
                                        Text(
                                            text = strings.accountRecoveryTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
