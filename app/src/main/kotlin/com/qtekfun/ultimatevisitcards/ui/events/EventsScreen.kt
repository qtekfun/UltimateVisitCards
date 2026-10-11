package com.qtekfun.ultimatevisitcards.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun EventsScreen(modifier: Modifier = Modifier) {
    EmptyState(message = R.string.events_empty, modifier = modifier)
}
