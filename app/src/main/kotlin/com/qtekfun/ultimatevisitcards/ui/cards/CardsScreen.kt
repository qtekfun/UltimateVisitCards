package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun CardsScreen(
    onAddCard: () -> Unit,
    onQuickScan: () -> Unit,
    onPending: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CardsContent(state, onAddCard, onQuickScan, onPending, modifier)
}

@Composable
fun CardsContent(state: CardsUiState, onAddCard: () -> Unit, onQuickScan: () -> Unit, onPending: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            CardsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            CardsUiState.Error -> EmptyState(R.string.cards_error)
            is CardsUiState.Content -> Column(Modifier.fillMaxSize()) {
                if (state.pendingReview > 0) {
                    // «Pending review: N» → review queue (docs/03).
                    Surface(
                        onClick = onPending,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            stringResource(R.string.cards_pending, state.pendingReview),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                EmptyState(R.string.cards_empty, Modifier.weight(1f))
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(onClick = onQuickScan) {
                Text(stringResource(R.string.cards_quick_scan), modifier = Modifier.padding(horizontal = 12.dp))
            }
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cards_add)) }
            )
        }
    }
}
