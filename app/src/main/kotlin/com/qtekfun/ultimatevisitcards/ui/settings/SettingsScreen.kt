package com.qtekfun.ultimatevisitcards.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.data.backup.BackupCounts
import com.qtekfun.ultimatevisitcards.data.backup.RestoreMode
import java.time.LocalDate

/** Settings (docs/03): security, capture and review, backup and restore, maintenance and «About». */
@Composable
fun SettingsScreen(
    onDuplicates: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: PreferencesViewModel = hiltViewModel(),
    backup: BackupViewModel = hiltViewModel(),
    restore: RestoreViewModel = hiltViewModel()
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).then(modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecuritySection(preferences)
        HorizontalDivider()
        CaptureSection(preferences)
        HorizontalDivider()
        BackupSection(backup)
        HorizontalDivider()
        RestoreSection(restore)
        HorizontalDivider()
        MaintenanceSection(preferences, onDuplicates, onAbout)
    }
}

@Composable
private fun BackupSection(viewModel: BackupViewModel) {
    val step by viewModel.export.collectAsStateWithLifecycle()
    val file = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri) else viewModel.closeExport()
    }
    Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = viewModel::startExport) { Text(stringResource(R.string.settings_backup_export)) }
    when (val s = step) {
        ExportStep.Idle -> Unit
        ExportStep.Options -> ExportOptionsDialog(
            onConfirm = { choice ->
                viewModel.chooseOptions(choice)
                file.launch("ultimatevisitcards_backup_${LocalDate.now()}.zip")
            },
            onDismiss = viewModel::closeExport
        )
        is ExportStep.Running -> ProgressDialog(stringResource(R.string.backup_running), s.progress, onCancel = viewModel::cancelExport)
        is ExportStep.Done -> MessageDialog(stringResource(R.string.backup_done, counts(s.manifest.counts)), onDismiss = viewModel::closeExport)
        is ExportStep.Failed -> MessageDialog(
            stringResource(
                when (s.reason) {
                    ExportFailure.WRITE -> R.string.backup_failed
                    ExportFailure.VERIFICATION -> R.string.backup_verification_failed
                    ExportFailure.CANCELLED -> R.string.backup_cancelled
                }
            ),
            onDismiss = viewModel::closeExport
        )
    }
}

@Composable
private fun RestoreSection(viewModel: RestoreViewModel) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }
    Text(stringResource(R.string.settings_restore), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_restore_hint), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text(stringResource(R.string.settings_restore_open)) }
    when (val s = step) {
        RestoreStep.Idle -> Unit
        RestoreStep.Checking -> ProgressDialog(stringResource(R.string.restore_checking), null, onCancel = null)
        is RestoreStep.Password -> PasswordDialog(s.wrong, onSubmit = viewModel::submitPassword, onDismiss = viewModel::close)
        is RestoreStep.Summary -> ModeDialog(s.counts, onChoose = viewModel::choose, onDismiss = viewModel::close)
        is RestoreStep.ConfirmReplace -> AlertDialog(
            onDismissRequest = viewModel::close,
            title = { Text(stringResource(R.string.restore_replace_title)) },
            text = { Text(stringResource(R.string.restore_replace_warning)) },
            confirmButton = { TextButton(onClick = viewModel::confirmReplace) { Text(stringResource(R.string.restore_replace_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::close) { Text(stringResource(R.string.review_cancel)) } }
        )
        is RestoreStep.Running -> ProgressDialog(stringResource(R.string.restore_running), s.progress, onCancel = null)
        is RestoreStep.Done -> MessageDialog(
            stringResource(
                R.string.restore_done,
                s.report.added.contacts,
                s.report.updated.contacts,
                s.report.skipped.contacts,
                s.report.images
            ),
            onDismiss = viewModel::close
        )
        is RestoreStep.Problem -> MessageDialog(
            when (s.problem) {
                RestoreProblem.NOT_A_BACKUP -> stringResource(R.string.restore_not_a_backup)
                RestoreProblem.CORRUPT -> stringResource(R.string.restore_corrupt)
                RestoreProblem.TOO_NEW -> stringResource(R.string.restore_too_new)
                RestoreProblem.FAILED -> stringResource(R.string.restore_failed)
            },
            onDismiss = viewModel::close
        )
    }
}

@Composable
private fun counts(c: BackupCounts) = stringResource(R.string.backup_counts, c.contacts, c.companies, c.events, c.sectors, c.images)

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrong) Text(stringResource(R.string.restore_wrong_password), color = MaterialTheme.colorScheme.error)
                PasswordField(stringResource(R.string.backup_password), value) { value = it }
            }
        },
        confirmButton = { TextButton(enabled = value.isNotEmpty(), onClick = { onSubmit(value.toCharArray()) }) { Text(stringResource(R.string.review_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } }
    )
}

/** What the backup holds, and «Combine» (default) or «Replace everything». */
@Composable
private fun ModeDialog(counts: BackupCounts, onChoose: (RestoreMode) -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_summary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(counts(counts))
                listOf(RestoreMode.MERGE to R.string.restore_mode_merge, RestoreMode.REPLACE to R.string.restore_mode_replace).forEach { (m, label) ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.selectable(selected = mode == m, role = Role.RadioButton, onClick = { mode = m }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == m, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(mode) }) { Text(stringResource(R.string.restore_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } }
    )
}
