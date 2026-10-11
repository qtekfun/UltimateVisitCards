package com.qtekfun.ultimatevisitcards.ui.common

/** State of a screen that shows a list loaded from the database (docs/03: loading, empty, error, content). */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data object Error : LoadState<Nothing>

    /** [items] may be empty: the screen shows its empty state. */
    data class Data<T>(val items: List<T>) : LoadState<T>
}
