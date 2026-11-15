package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppStrings
import com.example.core.localization.LocalAppStrings
import com.example.core.repository.AppError

fun appErrorToLocalizedMessage(error: Throwable, strings: AppStrings): String {
    return when (error) {
        is AppError.WrongPin -> strings.errorWrongPin
        is AppError.AccountNotFound -> strings.errorAccountNotFound
        is AppError.DuplicateAccount -> strings.errorDuplicateAccount
        is AppError.DuplicateEmail -> strings.errorDuplicateEmail
        is AppError.OtpInvalid -> strings.errorInvalidOtp
        is AppError.EmailOtpNotConfigured -> strings.errorEmailOtpNotConfigured
        is AppError.RateLimited -> {
            if (error.retryAfterSeconds != null && error.retryAfterSeconds > 0) {
                "${strings.errorRateLimited} (${error.retryAfterSeconds}s)"
            } else {
                strings.errorRateLimited
            }
        }
        is AppError.NetworkFailure -> strings.errorNetwork
        is AppError.BackendUnavailable -> strings.errorBackendUnavailable
        is AppError.StorageTransferFailure -> strings.errorStorageTransfer
        is AppError.ServerMessage -> error.serverMessage
        else -> strings.errorGeneric
    }
}

@Composable
fun PermissionRationaleDialog(
    reasonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.permissionRationaleTitle) },
        text = { Text(reasonText) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("permission_continue_button")
            ) {
                Text(strings.grantPermission)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ClearCacheDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.menuClearCache) },
        text = {
            Column {
                Text(strings.clearCacheConfirmMessage)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.clearCacheSafetyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_clear_cache_button")
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ReportDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text(strings.reportReasonHint) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_reason_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (reason.trim().isNotEmpty()) onSubmit(reason.trim()) },
                enabled = reason.trim().isNotEmpty(),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun DangerousFileDownloadDialog(
    fileName: String,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.dangerousFileWarningTitle) },
        text = {
            Column {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.dangerousFileWarningBody)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDownload,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_dangerous_download_button")
            ) {
                Text(strings.downloadAndOpen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
