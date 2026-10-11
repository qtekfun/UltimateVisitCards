package com.qtekfun.ultimatevisitcards.ui.capture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.di.ApplicationScope
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CaptureStep {
    /** Framing the front. */
    FRONT,

    /** Front taken: «Add back» / «Continue without back». */
    FRONT_TAKEN,

    /** Framing the back. */
    BACK,

    /** Back taken: save it or retake. */
    BACK_TAKEN,

    /** The card is stored as pending review. */
    SAVED
}

enum class CaptureError { SAVE_FAILED, CAMERA_FAILED }

/** Manual cropping of one side over its original photo. */
data class CropState(val side: CardSide, val original: Bitmap, val quad: CardQuad)

data class CaptureUiState(
    val step: CaptureStep = CaptureStep.FRONT,
    /** A photo is being processed or saved. */
    val busy: Boolean = false,
    val front: CapturedSide? = null,
    val back: CapturedSide? = null,
    val crop: CropState? = null,
    val flashOn: Boolean = false,
    val autoCapture: Boolean = false,
    val blurry: Boolean = false,
    val error: CaptureError? = null
) {
    val framing: Boolean get() = (step == CaptureStep.FRONT || step == CaptureStep.BACK) && crop == null
    val currentSide: CardSide get() = if (step == CaptureStep.BACK || step == CaptureStep.BACK_TAKEN) CardSide.BACK else CardSide.FRONT
}

/** Normal capture mode (docs/03-pantallas-navegacion.md): front → optional back → card saved as pending. */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val saver: CardCaptureSaver,
    private val ids: IdGenerator,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var cardId = ids.newId()
    private var capturedAt: Long? = null

    /** An upright photo from the camera for the side being framed. */
    fun onPhoto(photo: Bitmap) {
        val current = _state.value
        if (!current.framing || current.busy) return
        val side = current.currentSide
        if (capturedAt == null) capturedAt = clock.nowMillis()
        launchBusy {
            val captured = saver.saveSide(cardId, side, photo)
            _state.update {
                if (side == CardSide.FRONT) {
                    it.copy(front = captured, step = CaptureStep.FRONT_TAKEN)
                } else {
                    it.copy(back = captured, step = CaptureStep.BACK_TAKEN)
                }
            }
        }
    }

    fun addBack() = _state.update { if (it.step == CaptureStep.FRONT_TAKEN) it.copy(step = CaptureStep.BACK) else it }

    fun skipBack() {
        if (_state.value.step == CaptureStep.FRONT_TAKEN) save()
    }

    fun saveWithBack() {
        if (_state.value.step == CaptureStep.BACK_TAKEN) save()
    }

    /** Throws away the side just taken and frames it again. */
    fun retake() {
        val current = _state.value
        val side = when (current.step) {
            CaptureStep.FRONT_TAKEN -> CardSide.FRONT
            CaptureStep.BACK_TAKEN -> CardSide.BACK
            else -> return
        }
        launchBusy {
            saver.discardSide(cardId, side)
            _state.update {
                if (side == CardSide.FRONT) it.copy(front = null, step = CaptureStep.FRONT) else it.copy(back = null, step = CaptureStep.BACK)
            }
        }
    }

    fun openCrop(side: CardSide) {
        val captured = (if (side == CardSide.FRONT) _state.value.front else _state.value.back) ?: return
        launchBusy {
            val original = saver.original(captured) ?: throw IOException("Original missing")
            _state.update { it.copy(crop = CropState(side, original, captured.quad)) }
        }
    }

    fun applyCrop(quad: CardQuad) {
        val crop = _state.value.crop ?: return
        val captured = (if (crop.side == CardSide.FRONT) _state.value.front else _state.value.back) ?: return
        launchBusy {
            val recropped = saver.recrop(cardId, captured, quad)
            _state.update {
                val updated = it.copy(crop = null)
                if (crop.side == CardSide.FRONT) updated.copy(front = recropped) else updated.copy(back = recropped)
            }
        }
    }

    fun cancelCrop() = _state.update { it.copy(crop = null) }

    fun toggleFlash() = _state.update { it.copy(flashOn = !it.flashOn) }

    fun toggleAutoCapture() = _state.update { it.copy(autoCapture = !it.autoCapture) }

    fun onSharpness(blurry: Boolean) {
        if (_state.value.blurry != blurry) _state.update { it.copy(blurry = blurry) }
    }

    fun onCameraError() = _state.update { it.copy(error = CaptureError.CAMERA_FAILED) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Starts a new card after one was saved («Scan another»). */
    fun scanAnother() {
        if (_state.value.step != CaptureStep.SAVED) return
        cardId = ids.newId()
        capturedAt = null
        _state.update { CaptureUiState(flashOn = it.flashOn, autoCapture = it.autoCapture) }
    }

    /** Leaving the screen: a card that was not saved leaves no files behind. */
    override fun onCleared() {
        if (_state.value.step != CaptureStep.SAVED && (_state.value.front != null || _state.value.back != null)) {
            val abandoned = cardId
            // The view model scope is already cancelled: the clean-up runs in the application scope.
            appScope.launch {
                try {
                    saver.discard(abandoned)
                } catch (e: IOException) {
                    // Best effort: leftovers are removed by ImageStore.deleteOrphans.
                }
            }
        }
    }

    private fun save() {
        val front = _state.value.front ?: return
        val back = _state.value.back
        launchBusy {
            when (saver.commit(cardId, front, back, eventId = null, capturedAt = capturedAt ?: clock.nowMillis())) {
                is DomainResult.Success -> _state.update { it.copy(step = CaptureStep.SAVED) }
                is DomainResult.Failure -> _state.update { it.copy(error = CaptureError.SAVE_FAILED) }
            }
        }
    }

    /** Runs [block] marked busy; storage failures become [CaptureError.SAVE_FAILED], never a crash. */
    private fun launchBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                _state.update { it.copy(error = CaptureError.SAVE_FAILED) }
            } finally {
                withContext(NonCancellable) { _state.update { it.copy(busy = false) } }
            }
        }
    }
}
