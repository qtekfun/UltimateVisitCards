package com.qtekfun.ultimatevisitcards.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import kotlin.math.roundToInt

/** Lock and «hide in recent apps». */
@Composable
internal fun SecuritySection(viewModel: PreferencesViewModel) {
    val lock by viewModel.lockEnabled.collectAsStateWithLifecycle()
    val secure by viewModel.secureScreen.collectAsStateWithLifecycle()
    val available = viewModel.lockAvailable
    Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
    SwitchRow(
        title = stringResource(R.string.settings_lock),
        hint = stringResource(if (available) R.string.settings_lock_hint else R.string.settings_lock_unavailable),
        checked = lock && available,
        enabled = available,
        onChange = viewModel::setLock
    )
    SwitchRow(
        title = stringResource(R.string.settings_secure_screen),
        hint = stringResource(R.string.settings_secure_screen_hint),
        checked = secure,
        onChange = viewModel::setSecureScreen
    )
}

/** Keep originals (asks before deleting them) and the confidence threshold of the review. */
@Composable
internal fun CaptureSection(viewModel: PreferencesViewModel) {
    val keep by viewModel.keepOriginals.collectAsStateWithLifecycle()
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()
    var confirmOff by remember { mutableStateOf(false) }
    Text(stringResource(R.string.settings_capture), style = MaterialTheme.typography.titleMedium)
    SwitchRow(
        title = stringResource(R.string.settings_keep_originals),
        hint = stringResource(R.string.settings_keep_originals_hint),
        checked = keep,
        onChange = { on -> if (on) viewModel.keepOriginalsOn() else confirmOff = true }
    )
    // The slider moves freely and saves on release, so dragging does not write the preference on every step.
    var draft by remember(threshold) { mutableFloatStateOf(threshold) }
    Text(stringResource(R.string.settings_threshold, (draft * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.settings_threshold_hint), style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { viewModel.setThreshold(draft) },
        valueRange = UiPreferences.MIN_THRESHOLD..UiPreferences.MAX_THRESHOLD,
        steps = THRESHOLD_STEPS
    )
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text(stringResource(R.string.settings_keep_originals_off_title)) },
            text = { Text(stringResource(R.string.settings_keep_originals_off_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    viewModel.keepOriginalsOff()
                }) { Text(stringResource(R.string.settings_keep_originals_off_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
}

/** Rebuild the search index, possible duplicates and «About». */
@Composable
internal fun MaintenanceSection(viewModel: PreferencesViewModel, onDuplicates: () -> Unit, onAbout: () -> Unit) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    Text(stringResource(R.string.settings_maintenance), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::reindex, enabled = !busy) { Text(stringResource(R.string.settings_reindex)) }
        OutlinedButton(onClick = onDuplicates) { Text(stringResource(R.string.settings_duplicates)) }
    }
    OutlinedButton(onClick = onAbout) { Text(stringResource(R.string.settings_about)) }
    result?.let {
        AlertDialog(
            onDismissRequest = viewModel::resultShown,
            text = {
                Text(
                    stringResource(
                        when (it) {
                            MaintenanceResult.REINDEXED -> R.string.settings_reindex_done
                            MaintenanceResult.ORIGINALS_DELETED -> R.string.settings_originals_deleted
                            MaintenanceResult.FAILED -> R.string.settings_maintenance_failed
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = viewModel::resultShown) { Text(stringResource(R.string.review_ok)) } }
        )
    }
}

/** A title, a hint and a switch; the whole row toggles and reads as one switch for TalkBack. */
@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** 0.30 to 0.90 in steps of 0.05. */
private const val THRESHOLD_STEPS = 11
