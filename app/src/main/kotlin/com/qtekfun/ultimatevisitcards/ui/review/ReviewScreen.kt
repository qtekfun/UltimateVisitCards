package com.qtekfun.ultimatevisitcards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateCandidate
import com.qtekfun.ultimatevisitcards.domain.dedupe.MatchReason
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun ReviewScreen(onExit: (ReviewExit) -> Unit, onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    ReviewContent(state, viewModel, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewContent(state: ReviewUiState, viewModel: ReviewViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> EmptyState(R.string.review_missing)
                else -> Form(state, viewModel)
            }
        }
    }
    if (state.duplicates.isNotEmpty() && state.preview == null) DuplicatesDialog(state.duplicates, viewModel)
    state.preview?.let { ExistingContactDialog(it, onClose = viewModel::closePreview) }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.review_ok)) } },
            text = { Text(stringResource(if (error == ReviewError.EMPTY) R.string.review_error_empty else R.string.review_error_save)) }
        )
    }
}

@Composable
private fun Form(state: ReviewUiState, viewModel: ReviewViewModel) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Photo(state, viewModel)
            if (state.reading) Notice(stringResource(R.string.review_reading), progress = true)
            if (state.ocrFailed) Notice(stringResource(R.string.review_ocr_failed), progress = false)
            REVIEW_FIELDS.forEach { field ->
                FieldInput(field, state, viewModel)
                if (field == CardField.COMPANY) {
                    state.companySuggestions.forEach { company ->
                        TextButton(onClick = { viewModel.chooseCompany(company) }) { Text(company.name) }
                    }
                    Selector(
                        label = stringResource(R.string.review_sector),
                        options = state.sectors.map { it.id to it.name },
                        selected = state.sectorId,
                        newLabel = stringResource(R.string.review_new_sector),
                        newTitle = stringResource(R.string.review_new_sector_title),
                        onSelect = viewModel::chooseSector,
                        onCreate = viewModel::createSector
                    )
                }
                if (field == CardField.COUNTRY) {
                    state.countrySuggestions.forEach { country -> TextButton(onClick = { viewModel.chooseCountry(country) }) { Text(country) } }
                }
            }
            Selector(
                label = stringResource(R.string.review_event),
                options = state.events.map { it.event.id to it.event.name },
                selected = state.eventId,
                newLabel = stringResource(R.string.review_new_event),
                newTitle = stringResource(R.string.review_new_event_title),
                onSelect = viewModel::chooseEvent,
                onCreate = viewModel::createEvent
            )
            Box(Modifier.height(8.dp))
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = viewModel::saveAndNext, enabled = !state.saving) {
                    Text(stringResource(if (viewModel.next == SaveAndNext.REVIEW) R.string.review_save_review_next else R.string.review_save_scan_next))
                }
                Button(onClick = viewModel::save, enabled = !state.saving) { Text(stringResource(R.string.review_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Photo(state: ReviewUiState, viewModel: ReviewViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val front = stringResource(R.string.review_front)
        val back = stringResource(R.string.review_back_side)
        val file = if (state.side == CardSide.BACK) state.backImage else state.frontImage
        CardImage(file, stringResource(R.string.review_photo, if (state.side == CardSide.BACK) back else front), Modifier.fillMaxWidth().height(200.dp))
        if (state.backImage != null) {
            SingleChoiceSegmentedButtonRow {
                listOf(CardSide.FRONT to front, CardSide.BACK to back).forEachIndexed { i, (side, label) ->
                    SegmentedButton(
                        selected = state.side == side,
                        onClick = { viewModel.showSide(side) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, progress: Boolean) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (progress) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FieldInput(field: CardField, state: ReviewUiState, viewModel: ReviewViewModel) {
    val doubtful = field in state.doubtful
    val warning = MaterialTheme.colorScheme.tertiary
    OutlinedTextField(
        value = state.value(field),
        onValueChange = { viewModel.onChange(field, it) },
        label = { Text(stringResource(field.label())) },
        singleLine = field != CardField.ADDRESS && field != CardField.NOTES,
        keyboardOptions = field.keyboard(),
        supportingText = if (doubtful) {
            { Text(stringResource(R.string.review_check_field)) }
        } else {
            null
        },
        trailingIcon = if (doubtful) {
            { Icon(Icons.Filled.Warning, contentDescription = null, tint = warning) }
        } else {
            null
        },
        colors = if (doubtful) {
            OutlinedTextFieldDefaults.colors(unfocusedBorderColor = warning, unfocusedLabelColor = warning, unfocusedSupportingTextColor = warning)
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun CardField.label(): Int = when (this) {
    CardField.FIRST_NAME -> R.string.field_first_name
    CardField.LAST_NAME -> R.string.field_last_name
    CardField.JOB_TITLE -> R.string.field_job_title
    CardField.COMPANY -> R.string.field_company
    CardField.COUNTRY -> R.string.field_country
    CardField.PHONE -> R.string.field_phone
    CardField.MOBILE -> R.string.field_mobile
    CardField.EMAIL -> R.string.field_email
    CardField.WEBSITE -> R.string.field_website
    CardField.ADDRESS -> R.string.field_address
    CardField.NOTES -> R.string.field_notes
}

/** Keyboards that fit each field (docs/03): email, phone and URL keyboards. */
private fun CardField.keyboard(): KeyboardOptions = when (this) {
    CardField.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
    CardField.PHONE, CardField.MOBILE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
    CardField.WEBSITE -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
    CardField.ADDRESS, CardField.NOTES -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    else -> KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
}

/** Drop-down with «None», the options and «Create new…» (a small dialog, without leaving the review). */
@Composable
private fun Selector(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    newLabel: String,
    newTitle: String,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val none = stringResource(R.string.review_none)
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: none,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // The whole field opens the menu (a read-only text field does not react to taps by itself).
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.DropdownList, onClickLabel = label) { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(none) }, onClick = {
                onSelect(null)
                expanded = false
            })
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
            DropdownMenuItem(text = { Text(newLabel) }, onClick = {
                expanded = false
                creating = true
            })
        }
    }
    if (creating) {
        NameDialog(newTitle, onDismiss = { creating = false }, onConfirm = { name ->
            creating = false
            onCreate(name)
        })
    }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.review_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.review_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } }
    )
}

@Composable
private fun DuplicatesDialog(candidates: List<DuplicateCandidate>, viewModel: ReviewViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDuplicates,
        title = { Text(stringResource(R.string.duplicates_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.summary(), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(candidate.reason.label()), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.viewExisting(candidate) }) { Text(stringResource(R.string.duplicates_view)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveAsNew) { Text(stringResource(R.string.duplicates_save_new)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissDuplicates) { Text(stringResource(R.string.review_cancel)) } }
    )
}

/** «View existing contact» without losing the review: its data, read only. */
@Composable
private fun ExistingContactDialog(candidate: DuplicateCandidate, onClose: () -> Unit) {
    val contact = candidate.contact
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${contact.firstName} ${contact.lastName}".trim(), style = MaterialTheme.typography.titleMedium)
                listOfNotNull(
                    contact.jobTitle,
                    candidate.companyName,
                    contact.country,
                    contact.email,
                    contact.phone,
                    contact.mobile,
                    contact.website,
                    contact.address
                )
                    .forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.preview_close)) } }
    )
}

private fun DuplicateCandidate.summary(): String = listOf(
    "${contact.firstName} ${contact.lastName}".trim(),
    companyName,
    contact.email
).filter { !it.isNullOrBlank() }.joinToString(" · ")

private fun MatchReason.label(): Int = when (this) {
    MatchReason.EMAIL -> R.string.duplicate_reason_email
    MatchReason.PHONE -> R.string.duplicate_reason_phone
    MatchReason.NAME_AND_COMPANY -> R.string.duplicate_reason_name_company
    MatchReason.FIRST_NAME_AND_COMPANY -> R.string.duplicate_reason_first_name_company
    MatchReason.NAME -> R.string.duplicate_reason_name
    MatchReason.INITIAL_AND_COMPANY -> R.string.duplicate_reason_initial_company
}
