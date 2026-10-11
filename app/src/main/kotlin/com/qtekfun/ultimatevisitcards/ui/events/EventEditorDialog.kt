package com.qtekfun.ultimatevisitcards.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.ui.common.DateField

/** Create or edit an event: name (required), place, start and end dates, notes. */
@Composable
fun EventEditorDialog(title: String, initial: Event?, error: EditError?, onSave: (EventDraft) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var start by remember { mutableStateOf(initial?.startDate) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.review_name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(stringResource(error.message())) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.event_location)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(stringResource(R.string.event_start), start, { start = it }, Modifier.fillMaxWidth())
                DateField(stringResource(R.string.event_end), end, { end = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.field_notes)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(EventDraft(name = name.trim(), location = location, startDate = start, endDate = end, notes = notes))
            }) { Text(stringResource(R.string.catalog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } },
    )
}

/** The text shown for an edit error. */
fun EditError.message(): Int = when (this) {
    EditError.NAME_REQUIRED -> R.string.catalog_name_required
    EditError.NAME_TAKEN -> R.string.company_name_conflict
    EditError.SAVE_FAILED -> R.string.catalog_save_failed
}

/** «12 Sep 2026 – 15 Sep 2026», a single date, or null without dates. */
internal fun eventDates(event: Event, format: (Long) -> String): Pair<String, String?>? {
    val start = event.startDate
    val end = event.endDate
    return when {
        start != null && end != null && end != start -> format(start) to format(end)
        start != null -> format(start) to null
        end != null -> format(end) to null
        else -> null
    }
}
