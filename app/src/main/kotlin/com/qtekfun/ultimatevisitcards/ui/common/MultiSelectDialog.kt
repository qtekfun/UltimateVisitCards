package com.qtekfun.ultimatevisitcards.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R

/** One choice of a [MultiSelectDialog]. */
data class SelectOption(val id: String, val label: String, val detail: String? = null)

/**
 * A list of checkboxes with an optional filter field (choosing contacts for an event, or events for a contact).
 * [onConfirm] gets every checked id; [empty] is shown when there is nothing to choose.
 */
@Composable
fun MultiSelectDialog(
    title: String,
    options: List<SelectOption>,
    initial: Set<String>,
    confirmLabel: String,
    empty: String,
    searchable: Boolean,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var checked by remember { mutableStateOf(initial) }
    var filter by remember { mutableStateOf("") }
    val visible = if (filter.isBlank()) {
        options
    } else {
        options.filter { it.label.contains(filter.trim(), ignoreCase = true) || it.detail?.contains(filter.trim(), ignoreCase = true) == true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable && options.isNotEmpty()) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text(stringResource(R.string.select_filter)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (visible.isEmpty()) {
                    Text(if (options.isEmpty()) empty else stringResource(R.string.catalog_no_results), Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(visible, key = { it.id }) { option ->
                            val on = option.id in checked
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(on, role = Role.Checkbox) { checked = if (it) checked + option.id else checked - option.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = on, onCheckedChange = null)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                    option.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(checked) }, enabled = options.isNotEmpty()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) } }
    )
}
