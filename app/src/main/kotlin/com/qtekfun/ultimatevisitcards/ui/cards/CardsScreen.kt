package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun CardsScreen(modifier: Modifier = Modifier) {
    EmptyState(message = R.string.cards_empty, modifier = modifier)
}
