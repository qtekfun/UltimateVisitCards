package com.qtekfun.ultimatevisitcards

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

    @Test
    fun startsOnCards() {
        compose.onNodeWithText(string(R.string.nav_cards)).assertIsSelected()
        compose.onNodeWithText(string(R.string.cards_empty)).assertExists()
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
            compose.onNodeWithText(string(content)).assertExists()
        }
    }
}
