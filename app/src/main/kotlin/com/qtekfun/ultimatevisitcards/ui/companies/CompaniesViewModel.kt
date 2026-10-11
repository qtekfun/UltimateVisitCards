package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.model.CompanySummary
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Companies list (docs/03): name, sector, country and «N contacts», with a quick filter by name. */
@HiltViewModel
class CompaniesViewModel @Inject constructor(companies: CompanyRepository) : ViewModel() {
    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    /** [LoadState.Data] holds the companies matching the filter (all of them when it is blank). */
    val state: StateFlow<LoadState<CompanySummary>> = combine(companies.observeSummaries(), _filter) { list, text ->
        val key = TextNormalizer.fold(text)
        val shown: LoadState<CompanySummary> = LoadState.Data(if (key.isEmpty()) list else list.filter { matches(it, key) })
        shown
    }.catch { emit(LoadState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LoadState.Loading)

    fun onFilter(text: String) {
        _filter.value = text
    }

    /** Name, sector or country contain the folded text («astill» finds the shipyards). */
    private fun matches(summary: CompanySummary, key: String): Boolean =
        listOfNotNull(summary.company.name, summary.sectorName, summary.company.country).any { TextNormalizer.fold(it).contains(key) }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
