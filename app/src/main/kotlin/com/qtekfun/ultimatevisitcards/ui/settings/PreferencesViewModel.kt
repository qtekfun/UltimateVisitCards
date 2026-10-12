package com.qtekfun.ultimatevisitcards.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.settings.AppLock
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A one-off result shown after a maintenance action. */
enum class MaintenanceResult { REINDEXED, ORIGINALS_DELETED, FAILED }

/**
 * Security, capture, review and maintenance settings (docs/07 fase 10): lock, hide in recents, keep
 * originals, confidence threshold, rebuild the search index.
 */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferences: UiPreferences,
    private val lock: AppLock,
    private val contacts: ContactRepository,
    private val store: ImageStore
) : ViewModel() {
    val lockEnabled: StateFlow<Boolean> = preferences.lock
    val secureScreen: StateFlow<Boolean> = preferences.secureScreen
    val keepOriginals: StateFlow<Boolean> = preferences.keepOriginals
    val threshold: StateFlow<Float> = preferences.confidenceThreshold

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _result = MutableStateFlow<MaintenanceResult?>(null)
    val result: StateFlow<MaintenanceResult?> = _result.asStateFlow()

    /** The lock needs a screen lock or biometrics on the device. */
    val lockAvailable: Boolean get() = lock.available()

    fun setLock(enabled: Boolean) = lock.setEnabled(enabled && lockAvailable)

    fun setSecureScreen(enabled: Boolean) = preferences.setSecureScreen(enabled)

    fun setThreshold(value: Float) = preferences.setConfidenceThreshold(value)

    fun keepOriginalsOn() = preferences.setKeepOriginals(true)

    /** Switching originals off deletes the ones already stored (asked first by the screen): no re-cropping after. */
    fun keepOriginalsOff() {
        preferences.setKeepOriginals(false)
        run(MaintenanceResult.ORIGINALS_DELETED) { store.deleteAllOriginals() }
    }

    fun reindex() = run(MaintenanceResult.REINDEXED) {
        if (contacts.rebuildSearchIndex() is DomainResult.Failure) throw IOException("Search index not rebuilt")
    }

    fun resultShown() {
        _result.value = null
    }

    private fun run(success: MaintenanceResult, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _result.value = try {
                block()
                success
            } catch (e: IOException) {
                MaintenanceResult.FAILED
            } finally {
                _busy.value = false
            }
        }
    }
}
