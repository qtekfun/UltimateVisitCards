package com.qtekfun.ultimatevisitcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R

/** Shown instead of the app while it is locked; asks for the biometric/PIN at once and on demand. */
@Composable
fun LockScreen(onUnlock: (title: String, subtitle: String) -> Unit) {
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    LaunchedEffect(Unit) { onUnlock(title, subtitle) }
    // Opaque and clickable: nothing underneath can be seen or touched.
    Surface(onClick = {}, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Button(onClick = { onUnlock(title, subtitle) }) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
