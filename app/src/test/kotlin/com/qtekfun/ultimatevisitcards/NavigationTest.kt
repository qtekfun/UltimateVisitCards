package com.qtekfun.ultimatevisitcards

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** MainActivity starts on Cards and the bottom bar reaches every section (default locale: English). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    /** The home screen loads from the database off the main thread: wait for its content. */
    private fun waitForText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun startsOnCards() {
        compose.onNodeWithText(string(R.string.nav_cards)).assertIsSelected()
        waitForText(string(R.string.cards_empty))
    }

    @Test
    fun addCardOpensCaptureOverTheBottomBar() {
        compose.onNodeWithText(string(R.string.cards_add), useUnmergedTree = true).performClick()
        // Without the camera permission, capture asks for it first.
        compose.onNodeWithText(string(R.string.capture_permission_title)).assertExists()
        compose.onNodeWithText(string(R.string.nav_events)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.capture_close)).performClick()
        compose.onNodeWithText(string(R.string.nav_events)).assertExists()
    }

    @Test
    fun bottomBarReachesEverySection() {
        val sections = listOf(
            R.string.nav_events to R.string.events_empty,
            R.string.nav_companies to R.string.companies_empty,
            R.string.nav_sectors to R.string.sectors_empty,
            R.string.nav_settings to R.string.settings_empty,
            R.string.nav_cards to R.string.cards_empty
        )
        sections.forEach { (label, content) ->
            compose.onNodeWithText(string(label)).performClick()
            compose.onNodeWithText(string(label)).assertIsSelected()
            waitForText(string(content))
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
