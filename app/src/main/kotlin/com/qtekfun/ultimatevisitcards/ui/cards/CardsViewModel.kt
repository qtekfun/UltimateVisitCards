package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface CardsUiState {
    data object Loading : CardsUiState

    data class Content(val pendingReview: Int) : CardsUiState

    data object Error : CardsUiState
}

/** Home screen. Phase 2: the pending-review count; the card list arrives in phase 6. */
@HiltViewModel
class CardsViewModel @Inject constructor(cards: BusinessCardRepository) : ViewModel() {
    val state: StateFlow<CardsUiState> = cards.observeAwaitingReviewCount()
        .map<Int, CardsUiState> { CardsUiState.Content(it) }
        .catch { emit(CardsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), CardsUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
