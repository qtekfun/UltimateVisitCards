package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.formatDate
import com.qtekfun.ultimatevisitcards.ui.review.CardImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(onBack: () -> Unit, onOpenCompany: (String) -> Unit, viewModel: ContactDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ContactDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ContactDetailUiState.Missing -> EmptyState(R.string.contact_missing)
                ContactDetailUiState.Error -> EmptyState(R.string.contact_error)
                is ContactDetailUiState.Content -> Details(s, onOpenCompany)
            }
        }
    }
}

@Composable
private fun Details(state: ContactDetailUiState.Content, onOpenCompany: (String) -> Unit) {
    val contact = state.contact
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${contact.firstName} ${contact.lastName}".trim(), style = MaterialTheme.typography.headlineSmall)
        contact.jobTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        state.company?.let { company ->
            TextButton(onClick = { onOpenCompany(company.id) }) { Text(company.name) }
        }
        Section(R.string.contact_section_company, listOfNotNull(state.sectorName, state.country))
        Section(R.string.contact_section_contact, listOfNotNull(contact.phone, contact.mobile, contact.email, contact.website, contact.address))
        Section(
            R.string.contact_section_origin,
            state.events.map { it.name } + stringResource(R.string.contact_added_on, formatDate(contact.createdAt))
        )
        contact.notes?.let { Section(R.string.field_notes, listOf(it)) }
        state.cards.firstOrNull()?.let { CardSection(it) }
        if (state.cards.size > 1) {
            Text(stringResource(R.string.contact_cards_count, state.cards.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: Int, lines: List<String>) {
    if (lines.isEmpty()) return
    HorizontalDivider()
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun CardSection(card: CardImages) {
    var back by remember { mutableStateOf(false) }
    HorizontalDivider()
    Text(stringResource(R.string.contact_section_card), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    val front = stringResource(R.string.review_front)
    val backLabel = stringResource(R.string.review_back_side)
    CardImage(
        if (back) card.back else card.front,
        stringResource(R.string.review_photo, if (back) backLabel else front),
        Modifier.fillMaxWidth().height(220.dp)
    )
    if (card.back != null) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = !back, onClick = { back = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(front) }
            SegmentedButton(selected = back, onClick = { back = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(backLabel) }
        }
    }
}
