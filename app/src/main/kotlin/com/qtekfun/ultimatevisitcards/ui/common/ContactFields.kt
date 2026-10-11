package com.qtekfun.ultimatevisitcards.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.extract.CardField

/** One contact field with the right keyboard; [doubtful] highlights it (low OCR confidence). */
@Composable
fun ContactFieldInput(field: CardField, value: String, doubtful: Boolean = false, onChange: (String) -> Unit) {
    val warning = MaterialTheme.colorScheme.tertiary
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(field.label())) },
        singleLine = field != CardField.ADDRESS && field != CardField.NOTES,
        keyboardOptions = field.keyboard(),
        supportingText = if (doubtful) {
            { Text(stringResource(R.string.review_check_field)) }
        } else {
            null
        },
        trailingIcon = if (doubtful) {
            { Icon(Icons.Filled.Warning, contentDescription = null, tint = warning) }
        } else {
            null
        },
        colors = if (doubtful) {
            OutlinedTextFieldDefaults.colors(unfocusedBorderColor = warning, unfocusedLabelColor = warning, unfocusedSupportingTextColor = warning)
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = Modifier.fillMaxWidth()
    )
}

fun CardField.label(): Int = when (this) {
    CardField.FIRST_NAME -> R.string.field_first_name
    CardField.LAST_NAME -> R.string.field_last_name
    CardField.JOB_TITLE -> R.string.field_job_title
    CardField.COMPANY -> R.string.field_company
    CardField.COUNTRY -> R.string.field_country
    CardField.PHONE -> R.string.field_phone
    CardField.MOBILE -> R.string.field_mobile
    CardField.EMAIL -> R.string.field_email
    CardField.WEBSITE -> R.string.field_website
    CardField.ADDRESS -> R.string.field_address
    CardField.NOTES -> R.string.field_notes
}

/** Keyboards that fit each field (docs/03): email, phone and URL keyboards. */
private fun CardField.keyboard(): KeyboardOptions = when (this) {
    CardField.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
    CardField.PHONE, CardField.MOBILE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
    CardField.WEBSITE -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
    CardField.ADDRESS, CardField.NOTES -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    else -> KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
}
