package com.qtekfun.ultimatevisitcards.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.ui.common.CatalogContactRow
import com.qtekfun.ultimatevisitcards.ui.common.ConfirmDialog
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.formatDate

/**
 * An event and its contacts, with a search limited to it. Removing the «Only in this event» chip goes to the
 * global search with the same text ([onGlobalSearch]).
 */
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onOpenContact: (String) -> Unit,
    onGlobalSearch: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    EventDetailContent(
        state = state,
        onBack = onBack,
        onOpenContact = onOpenContact,
        onGlobalSearch = { onGlobalSearch(state.query) },
        onQuery = viewModel::onQuery,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::delete,
    )
    val editor = state.editor
    val event = state.event
    if (editor != null && event != null) {
        EventEditorDialog(stringResource(R.string.event_edit_title), event, editor.error, onSave = viewModel::save, onDismiss = viewModel::cancelEdit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailContent(
    state: EventDetailUiState,
    onBack: () -> Unit,
    onOpenContact: (String) -> Unit,
    onGlobalSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.event?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back)) }
                },
                actions = {
                    if (state.event != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.catalog_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.catalog_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val event = state.event
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                state.error -> EmptyState(R.string.events_error)
                state.missing || event == null -> EmptyState(R.string.event_missing)
                else -> Column(Modifier.fillMaxSize()) {
                    Header(event)
                    SearchBar(state.query, onQuery, onGlobalSearch)
                    if (state.contacts.isEmpty()) {
                        EmptyState(if (state.query.isBlank()) R.string.event_no_contacts else R.string.catalog_no_results, Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(state.contacts, key = { it.contact.id }) { item ->
                                CatalogContactRow(item.contact, item.companyName, onOpenContact)
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
            message = stringResource(R.string.event_delete_confirm),
            confirmLabel = stringResource(R.string.catalog_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Header(event: Event) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        eventDates(event, ::formatDate)?.let { (from, to) ->
            Text(if (to == null) from else stringResource(R.string.event_dates, from, to), style = MaterialTheme.typography.bodyLarge)
        }
        event.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        event.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onGlobalSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.event_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val everywhere = stringResource(R.string.event_search_everywhere)
        FilterChip(
            selected = true,
            onClick = onGlobalSearch,
            label = { Text(stringResource(R.string.event_only_this)) },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = everywhere) },
        )
    }
}
