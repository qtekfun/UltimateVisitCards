package com.qtekfun.ultimatevisitcards.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.qtekfun.ultimatevisitcards.domain.model.Contact

/** A contact in the lists of an event or a company: name, job title and company. */
@Composable
fun CatalogContactRow(contact: Contact, companyName: String?, onOpen: (String) -> Unit) {
    val details = listOfNotNull(contact.jobTitle, companyName).joinToString(" · ")
    ListItem(
        supportingContent = if (details.isNotEmpty()) {
            { Text(details) }
        } else {
            null
        },
        modifier = Modifier.clickable(role = Role.Button) { onOpen(contact.id) }
    ) { Text("${contact.firstName} ${contact.lastName}".trim()) }
}
