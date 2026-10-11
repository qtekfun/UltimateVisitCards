package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.R
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardsContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun emptyWithoutPendingCards() {
        compose.setContent { CardsContent(CardsUiState.Content(pendingReview = 0), onAddCard = {}) }
        compose.onNodeWithText(string(R.string.cards_empty)).assertExists()
        compose.onNodeWithText(string(R.string.cards_pending, 0)).assertDoesNotExist()
    }

    @Test
    fun showsThePendingReviewCount() {
        compose.setContent { CardsContent(CardsUiState.Content(pendingReview = 3), onAddCard = {}) }
        compose.onNodeWithText(string(R.string.cards_pending, 3)).assertExists()
    }

    @Test
    fun errorState() {
        compose.setContent { CardsContent(CardsUiState.Error, onAddCard = {}) }
        compose.onNodeWithText(string(R.string.cards_error)).assertExists()
    }

    @Test
    fun theAddButtonOpensCapture() {
        var opened = false
        compose.setContent { CardsContent(CardsUiState.Loading, onAddCard = { opened = true }) }
        // The FAB merges its label into its own node; the text is in the unmerged tree.
        compose.onNodeWithText(string(R.string.cards_add), useUnmergedTree = true).performClick()
        assertTrue(opened)
    }
}
