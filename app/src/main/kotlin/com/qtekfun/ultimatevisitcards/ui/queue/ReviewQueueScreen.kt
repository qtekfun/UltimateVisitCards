package com.qtekfun.ultimatevisitcards.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.formatDateTime

@Composable
fun ReviewQueueScreen(onReview: (cardId: String) -> Unit, onBack: () -> Unit, viewModel: ReviewQueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReviewQueueContent(state, onReview, onBack, onRetry = viewModel::retry, onDiscard = viewModel::discard)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueContent(state: QueueUiState, onReview: (cardId: String) -> Unit, onBack: () -> Unit, onRetry: (String) -> Unit, onDiscard: (String) -> Unit) {
    var discarding by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back)) }
                },
                actions = {
                    (state as? QueueUiState.Content)?.items?.firstOrNull()?.let { first ->
                        TextButton(onClick = { onReview(first.card.id) }) { Text(stringResource(R.string.queue_review_all)) }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                QueueUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                QueueUiState.Empty -> EmptyState(R.string.queue_empty)
                QueueUiState.Error -> EmptyState(R.string.queue_error)
                is QueueUiState.Content -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.card.id }) { item ->
                        QueueRow(item, onReview, onRetry, onDiscard = { discarding = it })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    discarding?.let { id ->
        AlertDialog(
            onDismissRequest = { discarding = null },
            text = { Text(stringResource(R.string.queue_discard_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDiscard(id)
                    discarding = null
                }) { Text(stringResource(R.string.queue_discard)) }
            },
            dismissButton = { TextButton(onClick = { discarding = null }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
}

@Composable
private fun QueueRow(item: QueueItem, onReview: (String) -> Unit, onRetry: (String) -> Unit, onDiscard: (String) -> Unit) {
    val card = item.card
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.queue_review)) { onReview(card.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = stringResource(R.string.queue_thumbnail),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 96.dp, height = 60.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(card.capturedAt), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when (card.ocrStatus) {
                        OcrStatus.PENDING, OcrStatus.PROCESSING -> R.string.queue_status_reading
                        OcrStatus.OCR_ERROR -> R.string.queue_status_error
                        else -> R.string.queue_status_ready
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (card.ocrStatus == OcrStatus.OCR_ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (card.ocrStatus == OcrStatus.OCR_ERROR) TextButton(onClick = { onRetry(card.id) }) { Text(stringResource(R.string.queue_retry)) }
        TextButton(onClick = { onDiscard(card.id) }) { Text(stringResource(R.string.queue_discard)) }
    }
}
