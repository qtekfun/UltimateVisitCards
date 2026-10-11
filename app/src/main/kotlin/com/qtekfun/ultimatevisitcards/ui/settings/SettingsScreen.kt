package com.qtekfun.ultimatevisitcards.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    EmptyState(message = R.string.settings_empty, modifier = modifier)
}
