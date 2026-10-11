package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.ui.common.OptionSelector
import com.qtekfun.ultimatevisitcards.ui.common.formatDate

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** All combinable filters of docs/02 (the free text stays in the search box). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(initial: ContactFilter, options: FilterOptions, onApply: (ContactFilter) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<DateEnd?>(null) }
    val none = stringResource(R.string.filter_any)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge)
            TextFilter(R.string.field_first_name, filter.firstName) { filter = filter.copy(firstName = it) }
            TextFilter(R.string.field_last_name, filter.lastName) { filter = filter.copy(lastName = it) }
            TextFilter(R.string.field_job_title, filter.jobTitle) { filter = filter.copy(jobTitle = it) }
            OptionSelector(stringResource(R.string.field_company), options.companies.map { it.id to it.name }, filter.companyId, none, {
                filter =
                    filter.copy(companyId = it)
            })
            OptionSelector(stringResource(R.string.field_country), options.countries.map { it to it }, filter.country, none, {
                filter =
                    filter.copy(country = it)
            })
            OptionSelector(stringResource(R.string.review_sector), options.sectors.map { it.id to it.name }, filter.sectorId, none, {
                filter =
                    filter.copy(sectorId = it)
            })
            OptionSelector(stringResource(R.string.review_event), options.events.map { it.event.id to it.event.name }, filter.eventId, none, {
                filter =
                    filter.copy(eventId = it)
            })
            Text(stringResource(R.string.filter_added_between), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = DateEnd.FROM }, modifier = Modifier.weight(1f)) {
                    Text(filter.from?.let { formatDate(it) } ?: stringResource(R.string.filter_from))
                }
                OutlinedButton(onClick = { picking = DateEnd.TO }, modifier = Modifier.weight(1f)) {
                    Text(filter.to?.let { formatDate(it) } ?: stringResource(R.string.filter_to))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { filter = ContactFilter(text = filter.text) }) { Text(stringResource(R.string.filters_clear)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onApply(filter) }) { Text(stringResource(R.string.filters_apply)) }
                }
            }
        }
    }
    picking?.let { end ->
        val state = rememberDatePickerState(initialSelectedDateMillis = if (end == DateEnd.FROM) filter.from else filter.to)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val day = state.selectedDateMillis
                    // The picker gives UTC midnight; «to» covers that whole day.
                    filter = if (end == DateEnd.FROM) filter.copy(from = day) else filter.copy(to = day?.plus(DAY_MILLIS - 1))
                    picking = null
                }) { Text(stringResource(R.string.review_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    filter = if (end == DateEnd.FROM) filter.copy(from = null) else filter.copy(to = null)
                    picking = null
                }) { Text(stringResource(R.string.filter_no_date)) }
            }
        ) { DatePicker(state) }
    }
}

private enum class DateEnd { FROM, TO }

@Composable
private fun TextFilter(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}
