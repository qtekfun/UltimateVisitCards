package com.qtekfun.ultimatevisitcards.ui.merge

import com.qtekfun.ultimatevisitcards.domain.usecase.MergeUndo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last merge, kept in memory so the contact sheet can offer «Undo» for a few seconds after the merge
 * screen closes (docs/06 §4). Never persisted: after a restart a merge is final.
 */
@Singleton
class MergeUndoHolder @Inject constructor() {
    private val _last = MutableStateFlow<MergeUndo?>(null)
    val last: StateFlow<MergeUndo?> = _last.asStateFlow()

    fun offer(undo: MergeUndo) {
        _last.value = undo
    }

    /** Takes the pending undo (if it is for [contactId]) so it is offered only once. */
    fun take(contactId: String): MergeUndo? = _last.value?.takeIf { it.keptBefore.id == contactId }?.also { _last.value = null }

    fun clear() {
        _last.value = null
    }
}
