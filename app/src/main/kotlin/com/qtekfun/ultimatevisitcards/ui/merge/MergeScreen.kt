package com.qtekfun.ultimatevisitcards.ui.merge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: MergeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.plan != null) viewModel.back() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val plan = state.plan
            val keep = state.keep
            val other = state.other
            if (plan == null || keep == null || other == null) {
                Picker(state, viewModel)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.merge_into, name(other), name(keep)), style = MaterialTheme.typography.bodyLarge)
                    MergeComparison(plan, existingLabel = name(keep), incomingLabel = name(other), onChoose = viewModel::choose)
                    Text(stringResource(R.string.merge_cards_events), style = MaterialTheme.typography.bodySmall)
                    if (state.failed) Text(stringResource(R.string.merge_failed), color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (state.merging) {
                            CircularProgressIndicator()
                        } else {
                            Button(onClick = viewModel::confirm) {
                                Text(stringResource(R.string.merge_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Picker(state: MergeUiState, viewModel: MergeViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.merge_pick, state.keep?.let { name(it) }.orEmpty()), modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.cards_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.candidates, key = { it.contact.id }) { item ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { viewModel.pick(item.contact) }.padding(vertical = 12.dp)) {
                    Text(name(item.contact), style = MaterialTheme.typography.bodyLarge)
                    listOfNotNull(item.contact.jobTitle, item.companyName, item.contact.email).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun name(contact: Contact) = "${contact.firstName} ${contact.lastName}".trim()
