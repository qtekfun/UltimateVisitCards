package com.qtekfun.ultimatevisitcards.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.backup.BackupCounts
import com.qtekfun.ultimatevisitcards.data.backup.BackupManifest
import com.qtekfun.ultimatevisitcards.data.backup.BackupRestorer
import com.qtekfun.ultimatevisitcards.data.backup.InspectResult
import com.qtekfun.ultimatevisitcards.data.backup.RestoreMode
import com.qtekfun.ultimatevisitcards.data.backup.RestoreOutcome
import com.qtekfun.ultimatevisitcards.data.backup.RestoreReport
import com.qtekfun.ultimatevisitcards.settings.CardsView
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.ui.common.Documents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a backup cannot be restored, for the message shown. */
enum class RestoreProblem { NOT_A_BACKUP, CORRUPT, TOO_NEW, FAILED }

/** Restore, step by step (docs/05 §3): validate → (password) → summary and mode → confirm → restore → report. */
sealed interface RestoreStep {
    data object Idle : RestoreStep

    data object Checking : RestoreStep

    /** Encrypted: ask for the password; [wrong] after a wrong one (≠ damaged file). */
    data class Password(val wrong: Boolean) : RestoreStep

    /** «843 contacts · 376 companies…» and the choice of mode. */
    data class Summary(val manifest: BackupManifest, val counts: BackupCounts) : RestoreStep

    /** «Replace everything» asks once more: it deletes the current data. */
    data class ConfirmReplace(val counts: BackupCounts) : RestoreStep

    data class Running(val progress: Float) : RestoreStep

    data class Done(val report: RestoreReport) : RestoreStep

    data class Problem(val problem: RestoreProblem, val version: Int? = null) : RestoreStep
}

@HiltViewModel
class RestoreViewModel @Inject constructor(private val restorer: BackupRestorer, private val documents: Documents, private val preferences: UiPreferences) :
    ViewModel() {
    private val _step = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val step: StateFlow<RestoreStep> = _step.asStateFlow()

    private var uri: Uri? = null
    private var password: CharArray? = null

    fun open(document: Uri) {
        uri = document
        clearPassword()
        inspect()
    }

    fun submitPassword(value: CharArray) {
        clearPassword()
        password = value
        inspect()
    }

    private fun inspect() {
        val document = uri ?: return
        _step.value = RestoreStep.Checking
        viewModelScope.launch {
            _step.value = when (val result = restorer.inspect({ documents.openInput(document) }, password)) {
                is InspectResult.Ready -> RestoreStep.Summary(result.manifest, result.counts)
                InspectResult.NeedsPassword -> RestoreStep.Password(wrong = false)
                InspectResult.WrongPassword -> RestoreStep.Password(wrong = true)
                is InspectResult.TooNew -> RestoreStep.Problem(RestoreProblem.TOO_NEW, result.version)
                InspectResult.NotABackup -> RestoreStep.Problem(RestoreProblem.NOT_A_BACKUP)
                is InspectResult.Corrupt -> RestoreStep.Problem(RestoreProblem.CORRUPT)
            }
        }
    }

    fun choose(mode: RestoreMode) {
        val summary = _step.value as? RestoreStep.Summary ?: return
        if (mode == RestoreMode.REPLACE) _step.value = RestoreStep.ConfirmReplace(summary.counts) else restore(RestoreMode.MERGE)
    }

    fun confirmReplace() = restore(RestoreMode.REPLACE)

    private fun restore(mode: RestoreMode) {
        val document = uri ?: return
        _step.value = RestoreStep.Running(0f)
        viewModelScope.launch {
            val outcome = restorer.restore({ documents.openInput(document) }, password, mode) { p -> _step.value = RestoreStep.Running(p) }
            _step.value = when (outcome) {
                is RestoreOutcome.Done -> {
                    // The view preference travels with the backup (settings.json).
                    outcome.report.settings?.cardsView?.let { name -> CardsView.entries.firstOrNull { it.name == name } }?.let(preferences::setCardsView)
                    RestoreStep.Done(outcome.report)
                }
                is RestoreOutcome.Rejected -> RestoreStep.Problem(RestoreProblem.CORRUPT)
                is RestoreOutcome.Failed -> RestoreStep.Problem(RestoreProblem.FAILED)
            }
            clearPassword()
        }
    }

    fun close() {
        clearPassword()
        uri = null
        _step.value = RestoreStep.Idle
    }

    private fun clearPassword() {
        password?.fill(' ')
        password = null
    }

    override fun onCleared() = clearPassword()
}
