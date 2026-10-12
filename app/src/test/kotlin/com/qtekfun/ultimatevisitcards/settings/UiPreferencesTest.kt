package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `defaults - no lock, visible in recents, originals kept, default threshold`() {
        val preferences = UiPreferences(context)
        assertFalse(preferences.lock.value)
        assertFalse(preferences.secureScreen.value)
        assertTrue(preferences.keepOriginals.value)
        assertEquals(ExtractedFields.DEFAULT_THRESHOLD, preferences.confidenceThreshold.value)
    }

    @Test
    fun `the values survive a restart`() {
        UiPreferences(context).apply {
            setLock(true)
            setSecureScreen(true)
            setKeepOriginals(false)
            setConfidenceThreshold(0.75f)
        }
        val reopened = UiPreferences(context)
        assertTrue(reopened.lock.value)
        assertTrue(reopened.secureScreen.value)
        assertFalse(reopened.keepOriginals.value)
        assertEquals(0.75f, reopened.confidenceThreshold.value)
    }

    @Test
    fun `the threshold stays within its range`() {
        val preferences = UiPreferences(context)
        preferences.setConfidenceThreshold(0.05f)
        assertEquals(UiPreferences.MIN_THRESHOLD, preferences.confidenceThreshold.value)
        preferences.setConfidenceThreshold(2f)
        assertEquals(UiPreferences.MAX_THRESHOLD, preferences.confidenceThreshold.value)
    }
}
