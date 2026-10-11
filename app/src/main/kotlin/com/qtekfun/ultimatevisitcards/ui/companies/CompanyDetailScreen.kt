package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.ui.common.CatalogContactRow
import com.qtekfun.ultimatevisitcards.ui.common.ConfirmDialog
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.OptionField
import com.qtekfun.ultimatevisitcards.ui.events.EditError
import com.qtekfun.ultimatevisitcards.ui.events.message

@Composable
fun CompanyDetailScreen(onBack: () -> Unit, onOpenContact: (String) -> Unit, viewModel: CompanyDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    CompanyDetailContent(state, onBack, onOpenContact, onEdit = viewModel::startEdit, onDelete = viewModel::delete)
    val editor = state.editor
    val company = state.company
    if (editor != null && company != null) {
        CompanyEditorDialog(company, state.sectors, editor.error, onSave = viewModel::save, onDismiss = viewModel::cancelEdit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDetailContent(state: CompanyDetailUiState, onBack: () -> Unit, onOpenContact: (String) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.company?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back)) }
                },
                actions = {
                    if (state.company != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.catalog_edit)) }
                        IconButton(onClick = {
                            confirmDelete = true
                        }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.catalog_delete)) }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val company = state.company
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                state.error -> EmptyState(R.string.companies_error)
                state.missing || company == null -> EmptyState(R.string.company_missing)
                else -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOfNotNull(state.sectorName, company.country, company.website).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        company.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(stringResource(R.string.catalog_contact_count, state.contacts.size), style = MaterialTheme.typography.titleSmall)
                    }
                    if (state.contacts.isEmpty()) {
                        EmptyState(R.string.company_no_contacts, Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(state.contacts, key = { it.id }) { contact ->
                                CatalogContactRow(contact, companyName = null, onOpen = onOpenContact)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            message = stringResource(R.string.company_delete_confirm),
            confirmLabel = stringResource(R.string.catalog_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Edit name, sector, country, website and notes; a name already used by another company is reported. */
@Composable
fun CompanyEditorDialog(company: Company, sectors: List<Sector>, error: EditError?, onSave: (CompanyEdit) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(company.name) }
    var sectorId by remember { mutableStateOf(company.sectorId) }
    var country by remember { mutableStateOf(company.country.orEmpty()) }
    var website by remember { mutableStateOf(company.website.orEmpty()) }
    var notes by remember { mutableStateOf(company.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.company_edit_title)) },
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
                    modifier = Modifier.fillMaxWidth()
                )
                OptionField(
                    label = stringResource(R.string.company_sector),
                    options = sectors.map { it.id to it.name },
                    selected = sectorId,
                    onSelect = { sectorId = it },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text(stringResource(R.string.field_country)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text(stringResource(R.string.field_website)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.field_notes)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(CompanyEdit(name.trim(), sectorId, country, website, notes)) }) { Text(stringResource(R.string.catalog_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } }
    )
}
