package com.qtekfun.ultimatevisitcards.ui.sectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.model.SectorSummary
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a sector name was refused. */
enum class SectorError { NAME_REQUIRED, NAME_TAKEN, SAVE_FAILED }

/** The open dialog: create (no [sector]) or rename [sector]. */
data class SectorEditor(val sector: Sector? = null, val error: SectorError? = null)

/**
 * Sectors (docs/03): the editable list seeded on first launch, with «N companies». Create, rename and delete;
 * deleting leaves its companies without a sector.
 */
@HiltViewModel
class SectorsViewModel @Inject constructor(private val sectors: SectorRepository) : ViewModel() {
    val state: StateFlow<LoadState<SectorSummary>> = sectors.observeSummaries()
        .map<List<SectorSummary>, LoadState<SectorSummary>> { LoadState.Data(it) }
        .catch { emit(LoadState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LoadState.Loading)

    private val _editor = MutableStateFlow<SectorEditor?>(null)
    val editor: StateFlow<SectorEditor?> = _editor.asStateFlow()

    fun startCreate() {
        _editor.value = SectorEditor()
    }

    fun startRename(sector: Sector) {
        _editor.value = SectorEditor(sector)
    }

    fun cancel() {
        _editor.value = null
    }

    /** Creates or renames, depending on the open dialog. */
    fun save(name: String) {
        val editing = _editor.value ?: return
        viewModelScope.launch {
            val target = editing.sector
            val result = if (target == null) sectors.create(name) else sectors.rename(target.id, name)
            _editor.value = when (result) {
                is DomainResult.Success -> null
                is DomainResult.Failure -> editing.copy(error = result.error.toSectorError())
            }
        }
    }

    fun delete(sectorId: String) {
        viewModelScope.launch { sectors.delete(sectorId) }
    }

    private fun DomainError.toSectorError() = when (this) {
        is DomainError.Invalid -> SectorError.NAME_REQUIRED
        is DomainError.Conflict -> SectorError.NAME_TAKEN
        else -> SectorError.SAVE_FAILED
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
