package com.qtekfun.ultimatevisitcards.ui.quickscan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.di.ApplicationScope
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.ui.capture.CapturedSide
import com.qtekfun.ultimatevisitcards.ui.capture.CardCaptureSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickScanUiState(
    val events: List<EventSummary> = emptyList(),
    /** Active event for the whole batch (docs/03, «Escaneo rápido»). */
    val eventId: String? = null,
    /** «Has a back?»: when on, the shot after a front is that card's back. */
    val hasBack: Boolean = false,
    /** The front of the current card is taken; the next shot is its back. */
    val awaitingBack: Boolean = false,
    /** Cards saved in this batch (the counter). */
    val saved: Int = 0,
    /** Photos taken but not yet saved to disk. */
    val processing: Int = 0,
    val flashOn: Boolean = false,
    val autoCapture: Boolean = false,
    val blurry: Boolean = false,
    /** A card of the batch could not be saved; scanning goes on. */
    val failed: Int = 0,
    /** «Finish» pressed: the last photos are being saved. */
    val finishing: Boolean = false,
    /** Everything is saved: leave the screen. */
    val finished: Boolean = false
) {
    /** At most [MAX_PENDING] photos wait in memory; the shutter pauses meanwhile. */
    val canShoot: Boolean get() = processing < MAX_PENDING && !finishing

    companion object {
        const val MAX_PENDING = 3
    }
}

/**
 * Quick scan (docs/03): shot → saved (original + straightened) → next, without dialogs and without
 * foreground OCR; each card is queued for OCR and appears in «Pending review». Photos are processed in
 * order on a background coroutine, so the camera is ready for the next card at once.
 */
@HiltViewModel
class QuickScanViewModel @Inject constructor(
    private val saver: CardCaptureSaver,
    private val events: EventRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {
    private val _state = MutableStateFlow(QuickScanUiState())
    val state: StateFlow<QuickScanUiState> = _state.asStateFlow()

    private sealed interface Job {
        val cardId: String

        data class Photo(override val cardId: String, val side: CardSide, val photo: Bitmap, val commit: Boolean) : Job

        /** «No back» after a front, or finishing with the back still missing. */
        data class CommitFront(override val cardId: String) : Job
    }

    private class PendingCard(val eventId: String?, val capturedAt: Long) {
        var front: CapturedSide? = null
        var back: CapturedSide? = null
    }

    private val jobs = Channel<Job>(Channel.UNLIMITED)

    // Written on the camera thread (onPhoto), read by the processing coroutine.
    private val cards = ConcurrentHashMap<String, PendingCard>()
    private val lock = Any()
    private var currentCardId: String? = null

    init {
        viewModelScope.launch {
            events.observeAll().catch { emit(emptyList()) }.collect { list -> _state.update { it.copy(events = list) } }
        }
        viewModelScope.launch { for (job in jobs) process(job) }
    }

    /** A photo from the camera (any thread). Ignored while the buffer is full or finishing. */
    fun onPhoto(photo: Bitmap) {
        val job = synchronized(lock) {
            val current = _state.value
            if (!current.canShoot) return
            val awaitingBack = current.awaitingBack
            val job = if (awaitingBack) {
                Job.Photo(checkNotNull(currentCardId), CardSide.BACK, photo, commit = true)
            } else {
                val id = ids.newId()
                currentCardId = id
                cards[id] = PendingCard(current.eventId, clock.nowMillis())
                Job.Photo(id, CardSide.FRONT, photo, commit = !current.hasBack)
            }
            _state.update { it.copy(processing = it.processing + 1, awaitingBack = !awaitingBack && it.hasBack) }
            job
        }
        jobs.trySend(job)
    }

    /** The current card has no back after all. */
    fun skipBack() {
        val id = synchronized(lock) {
            if (!_state.value.awaitingBack) return
            _state.update { it.copy(awaitingBack = false, processing = it.processing + 1) }
            checkNotNull(currentCardId)
        }
        jobs.trySend(Job.CommitFront(id))
    }

    fun toggleHasBack() = _state.update { if (it.awaitingBack) it else it.copy(hasBack = !it.hasBack) }

    fun chooseEvent(eventId: String?) = _state.update { it.copy(eventId = eventId) }

    fun createEvent(name: String) {
        viewModelScope.launch {
            (events.create(EventDraft(name)) as? DomainResult.Success)?.let { created -> _state.update { it.copy(eventId = created.value.id) } }
        }
    }

    fun toggleFlash() = _state.update { it.copy(flashOn = !it.flashOn) }

    fun toggleAutoCapture() = _state.update { it.copy(autoCapture = !it.autoCapture) }

    fun onSharpness(blurry: Boolean) {
        if (_state.value.blurry != blurry) _state.update { it.copy(blurry = blurry) }
    }

    /** «Finish»: a front waiting for its back is saved alone, then the screen closes once all is on disk. */
    fun finish() {
        skipBack()
        _state.update { it.copy(finishing = true) }
        checkFinished()
    }

    private suspend fun process(job: Job) {
        try {
            val card = cards.getValue(job.cardId)
            when (job) {
                is Job.Photo -> {
                    val side = saver.saveSide(job.cardId, job.side, job.photo)
                    if (job.side == CardSide.FRONT) card.front = side else card.back = side
                    if (job.commit) commit(job.cardId, card)
                }
                is Job.CommitFront -> commit(job.cardId, card)
            }
        } catch (e: IOException) {
            fail(job.cardId)
        } finally {
            _state.update { it.copy(processing = it.processing - 1) }
            checkFinished()
        }
    }

    private suspend fun commit(cardId: String, card: PendingCard) {
        val front = card.front ?: return fail(cardId)
        when (saver.commit(cardId, front, card.back, card.eventId, card.capturedAt)) {
            is DomainResult.Success -> _state.update { it.copy(saved = it.saved + 1) }
            is DomainResult.Failure -> _state.update { it.copy(failed = it.failed + 1) }
        }
        cards.remove(cardId)
    }

    private suspend fun fail(cardId: String) {
        cards.remove(cardId)
        _state.update { it.copy(failed = it.failed + 1) }
        try {
            saver.discard(cardId)
        } catch (e: IOException) {
            // Leftovers are removed by ImageStore.deleteOrphans.
        }
    }

    private fun checkFinished() {
        _state.update { if (it.finishing && it.processing == 0) it.copy(finished = true) else it }
    }

    /** Leaving without «Finish» (back gesture handled by the screen calls finish; this covers the rest). */
    override fun onCleared() {
        jobs.close()
        val orphanFronts = cards.filterValues { it.front != null }.toList()
        if (orphanFronts.isNotEmpty()) {
            appScope.launch {
                for ((id, card) in orphanFronts) {
                    try {
                        saver.commit(id, checkNotNull(card.front), card.back, card.eventId, card.capturedAt)
                    } catch (e: IOException) {
                        // Leftovers are removed by ImageStore.deleteOrphans.
                    }
                }
            }
        }
    }
}
