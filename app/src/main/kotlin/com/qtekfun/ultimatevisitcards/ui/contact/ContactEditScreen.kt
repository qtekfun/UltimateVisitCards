package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.ui.common.ContactFieldInput
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(onDone: () -> Unit, viewModel: ContactEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back)) }
                },
                actions = { TextButton(onClick = viewModel::save, enabled = !state.saving && !state.loading) { Text(stringResource(R.string.review_save)) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> EmptyState(R.string.contact_missing)
                else -> Column(
                    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EDIT_FIELDS.forEach { field ->
                        ContactFieldInput(field, state.value(field)) { viewModel.onChange(field, it) }
                        if (field == CardField.COMPANY) {
                            state.companySuggestions.forEach { company -> TextButton(onClick = { viewModel.chooseCompany(company) }) { Text(company.name) } }
                        }
                    }
                }
            }
        }
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.review_ok)) } },
            text = { Text(stringResource(if (error == EditError.EMPTY) R.string.review_error_empty else R.string.review_error_save)) }
        )
    }
}
