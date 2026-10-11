package com.qtekfun.ultimatevisitcards.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = UiPreferences(context)

    @Test
    fun `off by default - coming back never locks`() {
        val lock = AppLock(context, preferences)
        assertFalse(lock.locked.value)
        lock.onBackground(0)
        lock.onForeground(10 * AppLock.GRACE_MILLIS)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `a short trip away does not lock, a long one does`() {
        preferences.setLock(true)
        val lock = AppLock(context, preferences)
        // Enabled at start-up: a cold start is locked.
        assertTrue(lock.locked.value)
        lock.setEnabled(false)
        lock.setEnabled(true)
        assertFalse(lock.locked.value, "turning it on does not lock the current session")

        lock.onBackground(1_000)
        lock.onForeground(1_000 + AppLock.GRACE_MILLIS - 1)
        assertFalse(lock.locked.value)

        lock.onBackground(50_000)
        lock.onForeground(50_000 + AppLock.GRACE_MILLIS)
        assertTrue(lock.locked.value)
    }

    @Test
    fun `coming to the foreground without having left does not lock`() {
        preferences.setLock(true)
        val lock = AppLock(context, preferences)
        lock.setEnabled(false)
        lock.setEnabled(true)
        lock.onForeground(Long.MAX_VALUE)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `turning it off unlocks at once and is remembered`() {
        preferences.setLock(true)
        val lock = AppLock(context, preferences)
        lock.setEnabled(false)
        assertFalse(lock.locked.value)
        assertEquals(false, UiPreferences(context).lock.value)
    }
}
