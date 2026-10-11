package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import com.qtekfun.ultimatevisitcards.domain.model.CompanySummary
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.LoadState

@Composable
fun CompaniesScreen(onOpenCompany: (String) -> Unit, modifier: Modifier = Modifier, viewModel: CompaniesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    CompaniesContent(state, filter, viewModel::onFilter, onOpenCompany, modifier)
}

@Composable
fun CompaniesContent(
    state: LoadState<CompanySummary>,
    filter: String,
    onFilter: (String) -> Unit,
    onOpenCompany: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilter,
            label = { Text(stringResource(R.string.companies_filter)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LoadState.Error -> EmptyState(R.string.companies_error)
                is LoadState.Data -> when {
                    state.items.isEmpty() && filter.isBlank() -> EmptyState(R.string.companies_empty)
                    state.items.isEmpty() -> EmptyState(R.string.catalog_no_results)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.items, key = { it.company.id }) { summary ->
                            CompanyRow(summary, onOpenCompany)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyRow(summary: CompanySummary, onOpenCompany: (String) -> Unit) {
    val details = listOfNotNull(summary.sectorName, summary.company.country).joinToString(" · ")
    ListItem(
        headlineContent = { Text(summary.company.name) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details) }
        } else {
            null
        },
        trailingContent = { Text(stringResource(R.string.catalog_contact_count, summary.contactCount)) },
        modifier = Modifier.clickable(role = Role.Button) { onOpenCompany(summary.company.id) },
    )
}
