package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CardsView { GRID, LIST }

/**
 * App preferences that survive restarts. Plain SharedPreferences: no extra dependency for a few values.
 * Only [cardsView] goes into the backup (settings.json); security choices stay on the device.
 */
@Singleton
class UiPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _cardsView = MutableStateFlow(CardsView.entries.firstOrNull { it.name == prefs.getString(KEY_CARDS_VIEW, null) } ?: CardsView.GRID)
    val cardsView: StateFlow<CardsView> = _cardsView.asStateFlow()

    /** Optional biometric/PIN lock (docs/03 «Seguridad»), off by default. */
    private val _lock = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    val lock: StateFlow<Boolean> = _lock.asStateFlow()

    /** Hide the content in the recent apps view (FLAG_SECURE), off by default. */
    private val _secureScreen = MutableStateFlow(prefs.getBoolean(KEY_SECURE_SCREEN, false))
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()

    /** Keep the uncropped originals (docs/05 §1), on by default; off means no re-cropping later. */
    private val _keepOriginals = MutableStateFlow(prefs.getBoolean(KEY_KEEP_ORIGINALS, true))
    val keepOriginals: StateFlow<Boolean> = _keepOriginals.asStateFlow()

    /** Below this OCR confidence a field is highlighted in the review (docs/04 §3.6). */
    private val _confidenceThreshold = MutableStateFlow(prefs.getFloat(KEY_THRESHOLD, ExtractedFields.DEFAULT_THRESHOLD))
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold.asStateFlow()

    fun setCardsView(view: CardsView) {
        prefs.edit().putString(KEY_CARDS_VIEW, view.name).apply()
        _cardsView.value = view
    }

    fun setLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK, enabled).apply()
        _lock.value = enabled
    }

    fun setSecureScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECURE_SCREEN, enabled).apply()
        _secureScreen.value = enabled
    }

    fun setKeepOriginals(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ORIGINALS, enabled).apply()
        _keepOriginals.value = enabled
    }

    fun setConfidenceThreshold(value: Float) {
        val clamped = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        prefs.edit().putFloat(KEY_THRESHOLD, clamped).apply()
        _confidenceThreshold.value = clamped
    }

    companion object {
        const val MIN_THRESHOLD = 0.3f
        const val MAX_THRESHOLD = 0.9f
        private const val NAME = "ui"
        private const val KEY_CARDS_VIEW = "cards_view"
        private const val KEY_LOCK = "lock"
        private const val KEY_SECURE_SCREEN = "secure_screen"
        private const val KEY_KEEP_ORIGINALS = "keep_originals"
        private const val KEY_THRESHOLD = "confidence_threshold"
    }
}
