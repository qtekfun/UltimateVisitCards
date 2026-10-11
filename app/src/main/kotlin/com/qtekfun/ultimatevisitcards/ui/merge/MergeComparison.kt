package com.qtekfun.ultimatevisitcards.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource

/**
 * Field-by-field comparison (docs/06 §4): conflicting fields offer both values (existing / new); the rest
 * shows the value that will be kept. Notes are joined, never chosen.
 */
@Composable
fun MergeComparison(plan: MergePlan, existingLabel: String, incomingLabel: String, onChoose: (MergeField, MergeSource) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val conflicts = plan.conflicts
        if (conflicts.isNotEmpty()) Text(stringResource(R.string.merge_choose), style = MaterialTheme.typography.titleSmall)
        conflicts.forEach { field ->
            Text(stringResource(field.label()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            listOf(MergeSource.EXISTING to existingLabel, MergeSource.INCOMING to incomingLabel).forEach { (source, label) ->
                val value = if (source == MergeSource.EXISTING) plan.existing.value(field) else plan.incoming.value(field)
                val selected = plan.choice[field] == source
                Row(
                    Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = {
                        onChoose(field, source)
                    }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(value.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val kept = MergeField.entries.filter { it !in conflicts }.mapNotNull { field -> plan.chosen(field)?.let { field to it } }
        if (kept.isNotEmpty()) {
            Text(stringResource(R.string.merge_kept), style = MaterialTheme.typography.titleSmall)
            kept.forEach { (field, value) -> Text("${stringResource(field.label())}: $value", style = MaterialTheme.typography.bodyMedium) }
        }
        if (plan.existing.notes != null || plan.incoming.notes != null) {
            Text(stringResource(R.string.merge_notes_joined), style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun MergeField.label(): Int = when (this) {
    MergeField.FIRST_NAME -> R.string.field_first_name
    MergeField.LAST_NAME -> R.string.field_last_name
    MergeField.JOB_TITLE -> R.string.field_job_title
    MergeField.COMPANY -> R.string.field_company
    MergeField.COUNTRY -> R.string.field_country
    MergeField.PHONE -> R.string.field_phone
    MergeField.MOBILE -> R.string.field_mobile
    MergeField.EMAIL -> R.string.field_email
    MergeField.WEBSITE -> R.string.field_website
    MergeField.ADDRESS -> R.string.field_address
}
