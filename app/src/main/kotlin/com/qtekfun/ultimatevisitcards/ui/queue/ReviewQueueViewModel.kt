package com.qtekfun.ultimatevisitcards.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.work.OcrRecovery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueItem(val card: BusinessCard, val thumbnail: File?)

sealed interface QueueUiState {
    data object Loading : QueueUiState

    data object Empty : QueueUiState

    data class Content(val items: List<QueueItem>) : QueueUiState

    data object Error : QueueUiState
}

/** «Pending review: N» (docs/03): cards waiting for OCR or for the user, oldest first. */
@HiltViewModel
class ReviewQueueViewModel @Inject constructor(private val cards: BusinessCardRepository, private val store: ImageStore, private val recovery: OcrRecovery) :
    ViewModel() {
    val state: StateFlow<QueueUiState> = cards.observeAwaitingReview()
        .map { list ->
            if (list.isEmpty()) QueueUiState.Empty else QueueUiState.Content(list.map { QueueItem(it, thumbnail(it.id)) })
        }
        .catch { emit(QueueUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), QueueUiState.Loading)

    private suspend fun thumbnail(cardId: String): File? = try {
        store.thumbnail(cardId, CardSide.FRONT)
    } catch (e: IOException) {
        null
    }

    /** Puts a card whose OCR failed back through the queue. */
    fun retry(cardId: String) {
        viewModelScope.launch { recovery.retry(cardId) }
    }

    /** Throws away a card that is not worth keeping (a bad shot in quick scan), with its photos. */
    fun discard(cardId: String) {
        viewModelScope.launch {
            cards.delete(cardId)
            try {
                store.deleteCard(cardId)
            } catch (e: IOException) {
                // Leftovers are removed by ImageStore.deleteOrphans.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
