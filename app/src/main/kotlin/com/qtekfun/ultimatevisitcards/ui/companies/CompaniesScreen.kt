package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState

@Composable
fun CompaniesScreen(modifier: Modifier = Modifier) {
    EmptyState(message = R.string.companies_empty, modifier = modifier)
}
