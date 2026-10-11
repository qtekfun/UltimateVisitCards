package com.qtekfun.ultimatevisitcards.ui.sectors

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun SectorsScreen(modifier: Modifier = Modifier) {
    EmptyState(message = R.string.sectors_empty, modifier = modifier)
}
