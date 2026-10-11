package com.qtekfun.ultimatevisitcards.ui.sectors

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.model.SectorSummary
import com.qtekfun.ultimatevisitcards.ui.common.ConfirmDialog
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import com.qtekfun.ultimatevisitcards.ui.common.TextInputDialog

@Composable
fun SectorsScreen(modifier: Modifier = Modifier, viewModel: SectorsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    SectorsContent(state, onNew = viewModel::startCreate, onRename = viewModel::startRename, onDelete = viewModel::delete, modifier = modifier)
    editor?.let { open ->
        TextInputDialog(
            title = stringResource(if (open.sector == null) R.string.review_new_sector_title else R.string.sectors_rename_title),
            initial = open.sector?.name.orEmpty(),
            confirmLabel = stringResource(if (open.sector == null) R.string.review_create else R.string.catalog_save),
            error = open.error?.let { stringResource(it.message()) },
            onConfirm = viewModel::save,
            onDismiss = viewModel::cancel,
        )
    }
}

@Composable
fun SectorsContent(
    state: LoadState<SectorSummary>,
    onNew: () -> Unit,
    onRename: (Sector) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        when (state) {
            LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            LoadState.Error -> EmptyState(R.string.sectors_error)
            is LoadState.Data -> if (state.items.isEmpty()) {
                EmptyState(R.string.sectors_empty)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.sector.id }) { summary ->
                        ListItem(
                            headlineContent = { Text(summary.sector.name) },
                            supportingContent = { Text(stringResource(R.string.sectors_company_count, summary.companyCount)) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onRename(summary.sector) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.sectors_rename))
                                    }
                                    IconButton(onClick = { deleting = summary.sector.id }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.catalog_delete))
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.sectors_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            message = stringResource(R.string.sectors_delete_confirm),
            confirmLabel = stringResource(R.string.catalog_delete),
            onConfirm = {
                deleting = null
                onDelete(id)
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun SectorError.message(): Int = when (this) {
    SectorError.NAME_REQUIRED -> R.string.catalog_name_required
    SectorError.NAME_TAKEN -> R.string.sectors_conflict
    SectorError.SAVE_FAILED -> R.string.catalog_save_failed
}
