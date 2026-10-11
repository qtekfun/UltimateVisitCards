package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.settings.CardsView
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.formatDate

/** What the home screen can open. */
class CardsActions(val onAddCard: () -> Unit = {}, val onQuickScan: () -> Unit = {}, val onPending: () -> Unit = {}, val onOpenContact: (String) -> Unit = {})

@Composable
fun CardsScreen(actions: CardsActions, modifier: Modifier = Modifier, viewModel: CardsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exported by viewModel.exported.collectAsStateWithLifecycle()
    // The picker needs its MIME type up front: one launcher per format.
    val header = EXPORT_COLUMNS.map { stringResource(it) }
    val baseName = stringResource(R.string.cards_export_file_name)
    val launchers = ExportFormat.entries.associateWith { format ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
            uri?.let { viewModel.export(format, it, header, ::formatDate) }
        }
    }
    exported?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::exportShown,
            confirmButton = { TextButton(onClick = viewModel::exportShown) { Text(stringResource(R.string.review_ok)) } },
            text = {
                Text(
                    when (result) {
                        is ExportResult.Done -> pluralStringResource(R.plurals.cards_exported, result.count, result.count)
                        ExportResult.Failed -> stringResource(R.string.cards_export_failed)
                    }
                )
            }
        )
    }
    CardsContent(
        state = state,
        actions = actions,
        onSearch = viewModel::search,
        onApplyFilter = viewModel::applyFilter,
        onClearFilters = viewModel::clearFilters,
        onView = viewModel::setView,
        modifier = modifier
    )
}

@Composable
fun CardsContent(
    state: CardsUiState,
    actions: CardsActions,
    onSearch: (String) -> Unit = {},
    onApplyFilter: (ContactFilter) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onView: (CardsView) -> Unit = {},
    onExport: (ExportFormat) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var filtersOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        when (state) {
            CardsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            CardsUiState.Error -> EmptyState(R.string.cards_error)
            is CardsUiState.Content -> {
                val columns = if (state.view == CardsView.GRID) GridCells.Adaptive(160.dp) else GridCells.Fixed(1)
                LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (state.view == CardsView.GRID) 12.dp else 0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Header(state, onSearch, onOpenFilters = { filtersOpen = true }, onApplyFilter, onClearFilters, onView, actions, onExport)
                    }
                    if (state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(if (state.noContacts) R.string.cards_empty else R.string.cards_no_results, Modifier.padding(top = 32.dp))
                        }
                    }
                    items(state.items, key = { it.item.contact.id }) { card ->
                        if (state.view == CardsView.GRID) GridCard(card, actions.onOpenContact) else ListRow(card, actions.onOpenContact)
                    }
                }
                if (filtersOpen) {
                    FilterSheet(state.filter, state.options, onApply = {
                        onApplyFilter(it)
                        filtersOpen = false
                    }, onDismiss = { filtersOpen = false })
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(onClick = actions.onQuickScan) {
                Text(stringResource(R.string.cards_quick_scan), modifier = Modifier.padding(horizontal = 12.dp))
            }
            ExtendedFloatingActionButton(
                onClick = actions.onAddCard,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cards_add)) }
            )
        }
    }
}

@Composable
private fun Header(
    state: CardsUiState.Content,
    onSearch: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onApplyFilter: (ContactFilter) -> Unit,
    onClearFilters: () -> Unit,
    onView: (CardsView) -> Unit,
    actions: CardsActions,
    onExport: (ExportFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.filter.text,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.cards_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (state.filter.text.isNotEmpty()) {
                { IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cards_clear_search)) } }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActiveFilters(state, onOpenFilters, onApplyFilter, onClearFilters)
        if (state.pendingReview > 0) {
            // «Pending review: N» → review queue (docs/03).
            Surface(
                onClick = actions.onPending,
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.cards_pending, state.pendingReview),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        Text(
            stringResource(R.string.cards_stats, state.stats.contacts, state.stats.events, state.stats.companies),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Quick access to the most recent events.
        if (state.options.events.isNotEmpty() && state.filter.eventId == null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.options.events.take(RECENT_EVENTS), key = { it.event.id }) { summary ->
                    AssistChip(onClick = { onApplyFilter(state.filter.copy(eventId = summary.event.id)) }, label = { Text(summary.event.name) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (state.filter.isEmpty) R.string.cards_latest else R.string.cards_results, state.items.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            // Exports exactly what is listed: everything, a search, an event, a company, a sector…
            if (state.items.isNotEmpty()) ExportMenu(onExport)
            SingleChoiceSegmentedButtonRow {
                listOf(CardsView.GRID to R.string.cards_view_grid, CardsView.LIST to R.string.cards_view_list).forEachIndexed { i, (view, label) ->
                    SegmentedButton(selected = state.view == view, onClick = { onView(view) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(stringResource(label))
                    }
                }
            }
        }
    }
}

/** One removable chip per active filter, plus the «Filters» button. */
@Composable
private fun ActiveFilters(state: CardsUiState.Content, onOpenFilters: () -> Unit, onApplyFilter: (ContactFilter) -> Unit, onClearFilters: () -> Unit) {
    val f = state.filter
    val o = state.options
    val chips = buildList {
        if (f.eventId !=
            null
        ) {
            add(
                stringResource(
                    R.string.filter_event_value,
                    o.events.firstOrNull {
                        it.event.id == f.eventId
                    }?.event?.name.orEmpty()
                ) to f.copy(eventId = null)
            )
        }
        if (f.sectorId !=
            null
        ) {
            add(stringResource(R.string.filter_sector_value, o.sectors.firstOrNull { it.id == f.sectorId }?.name.orEmpty()) to f.copy(sectorId = null))
        }
        if (f.companyId !=
            null
        ) {
            add(stringResource(R.string.filter_company_value, o.companies.firstOrNull { it.id == f.companyId }?.name.orEmpty()) to f.copy(companyId = null))
        }
        f.country?.let { country -> add(stringResource(R.string.filter_country_value, country) to f.copy(country = null)) }
        if (f.firstName.isNotBlank()) add(stringResource(R.string.filter_first_name_value, f.firstName) to f.copy(firstName = ""))
        if (f.lastName.isNotBlank()) add(stringResource(R.string.filter_last_name_value, f.lastName) to f.copy(lastName = ""))
        if (f.jobTitle.isNotBlank()) add(stringResource(R.string.filter_job_title_value, f.jobTitle) to f.copy(jobTitle = ""))
        if (f.from != null || f.to != null) {
            add(
                stringResource(R.string.filter_dates_value, f.from?.let { formatDate(it) }.orEmpty(), f.to?.let { formatDate(it) }.orEmpty()) to
                    f.copy(from = null, to = null)
            )
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            FilterChip(
                selected = f.activeFilters > 0,
                onClick = onOpenFilters,
                label = { Text(if (f.activeFilters > 0) stringResource(R.string.filters_count, f.activeFilters) else stringResource(R.string.filters)) }
            )
        }
        items(chips) { (label, without) ->
            val remove = stringResource(R.string.filter_remove, label)
            InputChip(
                selected = true,
                onClick = { onApplyFilter(without) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = remove, modifier = Modifier.size(18.dp)) }
            )
        }
        if (chips.size > 1) item { TextButton(onClick = onClearFilters) { Text(stringResource(R.string.filters_clear)) } }
    }
}

@Composable
private fun GridCard(card: CardItem, onOpen: (String) -> Unit) {
    val item = card.item
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item.contact.id) }) {
        Column {
            AsyncImage(
                model = card.image,
                contentDescription = stringResource(R.string.cards_card_image, displayName(item)),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(85f / 55f)
            )
            Column(Modifier.padding(8.dp)) {
                Text(displayName(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOfNotNull(item.companyName, item.country).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ListRow(card: CardItem, onOpen: (String) -> Unit) {
    val item = card.item
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item.contact.id) }) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(displayName(item), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOfNotNull(item.contact.jobTitle, item.companyName, item.country).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun displayName(item: com.qtekfun.ultimatevisitcards.domain.model.ContactListItem): String =
    "${item.contact.firstName} ${item.contact.lastName}".trim().ifEmpty { item.companyName ?: item.contact.email ?: stringResource(R.string.cards_unnamed) }

private const val RECENT_EVENTS = 5

/** The table columns of docs/05 §6, in order. */
private val EXPORT_COLUMNS = listOf(
    R.string.field_first_name,
    R.string.field_last_name,
    R.string.field_job_title,
    R.string.field_company,
    R.string.export_col_sector,
    R.string.field_country,
    R.string.field_phone,
    R.string.field_mobile,
    R.string.field_email,
    R.string.field_website,
    R.string.field_address,
    R.string.export_col_event,
    R.string.export_col_date,
    R.string.field_notes
)

@Composable
private fun ExportMenu(onExport: (ExportFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.cards_export)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                ExportFormat.VCF to R.string.export_vcf,
                ExportFormat.CSV to R.string.export_csv,
                ExportFormat.XLSX to R.string.export_xlsx
            ).forEach { (format, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    open = false
                    onExport(format)
                })
            }
        }
    }
}
