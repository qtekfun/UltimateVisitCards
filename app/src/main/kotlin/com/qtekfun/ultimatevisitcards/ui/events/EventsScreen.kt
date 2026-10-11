package com.qtekfun.ultimatevisitcards.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import com.qtekfun.ultimatevisitcards.ui.common.formatDate

@Composable
fun EventsScreen(onOpenEvent: (String) -> Unit, modifier: Modifier = Modifier, viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    EventsContent(state, onOpenEvent, onNew = viewModel::startCreate, modifier)
    editor?.let { open ->
        EventEditorDialog(
            title = stringResource(R.string.review_new_event_title),
            initial = null,
            error = open.error,
            onSave = viewModel::create,
            onDismiss = viewModel::cancelCreate,
        )
    }
}

@Composable
fun EventsContent(state: LoadState<EventSummary>, onOpenEvent: (String) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LoadState.Error -> EmptyState(R.string.events_error)
            is LoadState.Data -> if (state.items.isEmpty()) {
                EmptyState(R.string.events_empty)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.event.id }) { summary ->
                        EventRow(summary, onOpenEvent)
                        HorizontalDivider()
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.events_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun EventRow(summary: EventSummary, onOpenEvent: (String) -> Unit) {
    val event = summary.event
    val dates = eventDates(event, ::formatDate)?.let { (from, to) ->
        if (to == null) from else stringResource(R.string.event_dates, from, to)
    }
    val details = listOfNotNull(dates, event.location).joinToString(" · ")
    ListItem(
        headlineContent = { Text(event.name) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details) }
        } else {
            null
        },
        trailingContent = { Text(stringResource(R.string.catalog_contact_count, summary.contactCount)) },
        modifier = Modifier.clickable(role = Role.Button) { onOpenEvent(event.id) },
    )
}
