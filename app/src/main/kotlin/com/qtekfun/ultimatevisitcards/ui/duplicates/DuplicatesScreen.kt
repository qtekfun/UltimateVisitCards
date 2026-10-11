package com.qtekfun.ultimatevisitcards.ui.duplicates

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.dedupe.MatchReason
import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

/** «Posibles duplicados»: tapping a pair opens the merge of [DuplicateRow.other] into [DuplicateRow.keep]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, onMerge: (keepId: String, otherId: String) -> Unit, viewModel: DuplicatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a merge: the list must not offer the merged contact again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dedupe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DuplicatesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                DuplicatesUiState.Empty -> EmptyState(R.string.dedupe_empty)
                DuplicatesUiState.Error -> EmptyState(R.string.dedupe_error)
                is DuplicatesUiState.Content -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            stringResource(R.string.dedupe_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(s.rows, key = { "${it.keep.contact.id}|${it.other.contact.id}" }) { row ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dedupe_merge)) {
                                    onMerge(row.keep.contact.id, row.other.contact.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(summary(row.keep), style = MaterialTheme.typography.bodyLarge)
                            Text(summary(row.other), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(row.reason.label()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun summary(c: ContactWithCompany): String = listOf(
    "${c.contact.firstName} ${c.contact.lastName}".trim(),
    c.companyName,
    c.contact.email
).filter { !it.isNullOrBlank() }.joinToString(" · ")

private fun MatchReason.label(): Int = when (this) {
    MatchReason.EMAIL -> R.string.duplicate_reason_email
    MatchReason.PHONE -> R.string.duplicate_reason_phone
    MatchReason.NAME_AND_COMPANY -> R.string.duplicate_reason_name_company
    MatchReason.FIRST_NAME_AND_COMPANY -> R.string.duplicate_reason_first_name_company
    MatchReason.NAME -> R.string.duplicate_reason_name
    MatchReason.INITIAL_AND_COMPANY -> R.string.duplicate_reason_initial_company
}
