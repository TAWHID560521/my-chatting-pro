package com.example.features.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.config.AppConfig
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.ui.components.AvatarView
import com.example.ui.theme.ObsidianHeroBg
import com.example.ui.theme.ThemePreference
import com.example.ui.theme.VipGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenProfile: () -> Unit,
    onAccountSignedOutOrDeleted: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    val session by viewModel.session.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    val currentTheme by viewModel.themePreference.collectAsState()
    val isDeleting by viewModel.isDeletingAccount.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePinInput by remember { mutableStateOf("") }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeStatusMessage()
    }

    fun openExternalUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsTitle,
                        fontWeight = FontWeight.Bold
                    )
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // User Profile Summary Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProfile)
                        .testTag("settings_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            displayName = session.displayName.ifBlank { "User" },
                            avatarUrl = session.avatarUrl,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.displayName.ifBlank { strings.profileTitle },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = session.phoneE164,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (session.about.isNotBlank()) {
                                Text(
                                    text = session.about,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(Icons.Default.Person, contentDescription = strings.profileTitle)
                    }
                }

                // Non-editable VIP Owner Section: TAWHID SHAKH
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, VipGold.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ObsidianHeroBg, Color(0xFF123E44))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = VipGold,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = strings.ownerSectionTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VipGold
                                )
                                Text(
                                    text = AppConfig.DEVELOPER_OWNER_NAME,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Language Selector Card (Bangla, English, Arabic RTL)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(strings.languageTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = currentLanguage == lang,
                                    onClick = { viewModel.setLanguage(lang) },
                                    label = { Text(lang.nativeLabel) },
                                    modifier = Modifier.testTag("settings_lang_${lang.code}")
                                )
                            }
                        }
                    }
                }

                // Light / Dark Mode Appearance Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Brightness6, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(strings.appearanceTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ThemePreference.SYSTEM to strings.themeSystem,
                                ThemePreference.LIGHT to strings.themeLight,
                                ThemePreference.DARK to strings.themeDark
                            ).forEach { (pref, label) ->
                                FilterChip(
                                    selected = currentTheme == pref,
                                    onClick = { viewModel.setTheme(pref) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("settings_theme_${pref.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Links & App Info Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Default.PlayCircle,
                            title = strings.youtubeSubscribeLabel,
                            subtitle = "YouTube",
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            testTag = "settings_youtube_row",
                            onClick = { openExternalUrl(AppConfig.YOUTUBE_URL) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Public,
                            title = strings.facebookProfileLabel,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            testTag = "settings_facebook_row",
                            onClick = { openExternalUrl(AppConfig.FACEBOOK_URL) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Email,
                            title = strings.feedbackEmailLabel,
                            subtitle = AppConfig.FEEDBACK_EMAIL,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            testTag = "settings_feedback_row",
                            onClick = {
                                val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${AppConfig.FEEDBACK_EMAIL}?subject=My%20Chatting%20Pro%20Feedback")
                                }
                                try {
                                    context.startActivity(mailIntent)
                                } catch (_: Exception) {
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Policy,
                            title = strings.privacyPolicyLabel,
                            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            testTag = "settings_privacy_row",
                            onClick = { openExternalUrl(AppConfig.PRIVACY_POLICY_URL) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.Info,
                            title = strings.versionMenuLabel,
                            testTag = "settings_version_row",
                            onClick = { showVersionDialog = true }
                        )
                    }
                }

                // Account Controls Card (Logout & Permanent Deletion)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = strings.logoutLabel,
                            testTag = "settings_logout_row",
                            onClick = { showLogoutDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Default.DeleteForever,
                            title = strings.deleteAccountLabel,
                            tint = MaterialTheme.colorScheme.error,
                            testTag = "settings_delete_account_row",
                            onClick = {
                                deletePinInput = ""
                                showDeleteDialog = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showVersionDialog) {
        val versionInfo = remember(context) { AppConfig.readPackageVersionInfo(context) }
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text(strings.versionDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${strings.versionNameLabel}: ${versionInfo.versionName}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${strings.versionCodeLabel}: ${versionInfo.versionCode}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showVersionDialog = false },
                    modifier = Modifier.testTag("close_version_dialog_button")
                ) {
                    Text(strings.ok)
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(strings.logoutLabel) },
            text = { Text(strings.logoutConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onAccountSignedOutOrDeleted)
                    },
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text(
                    text = strings.deleteAccountConfirmTitle,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = strings.deleteAccountConfirmBody,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = deletePinInput,
                        onValueChange = { deletePinInput = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(strings.pinLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_pin_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting && deletePinInput.length == 5,
                    onClick = {
                        viewModel.deleteAccount(deletePinInput, strings) {
                            showDeleteDialog = false
                            onAccountSignedOutOrDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(strings.delete)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingIcon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
