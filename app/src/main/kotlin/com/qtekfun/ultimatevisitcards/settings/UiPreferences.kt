package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CardsView { GRID, LIST }

/**
 * Small UI preferences that survive restarts (docs/03: «Se recuerda la elección»). Plain SharedPreferences:
 * no extra dependency for a couple of values; they go into the backup's settings.json in phase 9.
 */
@Singleton
class UiPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val _cardsView = MutableStateFlow(CardsView.entries.firstOrNull { it.name == prefs.getString(KEY_CARDS_VIEW, null) } ?: CardsView.GRID)
    val cardsView: StateFlow<CardsView> = _cardsView.asStateFlow()

    fun setCardsView(view: CardsView) {
        prefs.edit().putString(KEY_CARDS_VIEW, view.name).apply()
        _cardsView.value = view
    }

    private companion object {
        const val NAME = "ui"
        const val KEY_CARDS_VIEW = "cards_view"
    }
}
