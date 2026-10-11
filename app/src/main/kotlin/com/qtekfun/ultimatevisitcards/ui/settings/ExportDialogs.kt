package com.qtekfun.ultimatevisitcards.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R

/** Backup options: originals and optional encryption (with the «lose it and it is gone» warning). */
@Composable
fun ExportOptionsDialog(onConfirm: (ExportChoice) -> Unit, onDismiss: () -> Unit) {
    var originals by remember { mutableStateOf(true) }
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val passwordOk = !encrypt || (password.length >= MIN_PASSWORD && password == repeat)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(stringResource(R.string.backup_include_originals), originals) { originals = it }
                Text(stringResource(R.string.backup_include_originals_hint), style = MaterialTheme.typography.bodySmall)
                SwitchRow(stringResource(R.string.backup_encrypt), encrypt) { encrypt = it }
                if (encrypt) {
                    PasswordField(stringResource(R.string.backup_password), password) { password = it }
                    PasswordField(stringResource(R.string.backup_password_repeat), repeat) { repeat = it }
                    if (password.isNotEmpty() && password.length < MIN_PASSWORD) {
                        Text(stringResource(R.string.backup_password_short, MIN_PASSWORD), color = MaterialTheme.colorScheme.error)
                    } else if (repeat.isNotEmpty() && repeat != password) {
                        Text(stringResource(R.string.backup_password_mismatch), color = MaterialTheme.colorScheme.error)
                    }
                    Text(stringResource(R.string.backup_password_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = passwordOk, onClick = { onConfirm(ExportChoice(originals, if (encrypt) password.toCharArray() else null)) }) {
                Text(stringResource(R.string.backup_choose_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } },
    )
}

@Composable
fun ProgressDialog(title: String, progress: Float?, onCancel: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.review_cancel)) } } },
    )
}

@Composable
fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_ok)) } },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MIN_PASSWORD = 8
