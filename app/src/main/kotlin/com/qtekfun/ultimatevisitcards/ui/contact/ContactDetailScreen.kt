package com.qtekfun.ultimatevisitcards.ui.contact

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.common.EmptyState
import com.qtekfun.ultimatevisitcards.ui.common.MultiSelectDialog
import com.qtekfun.ultimatevisitcards.ui.common.SelectOption
import com.qtekfun.ultimatevisitcards.ui.common.formatDate
import com.qtekfun.ultimatevisitcards.ui.review.CardImage
import java.io.File

/** What the contact sheet can open. */
class ContactActions(
    val onBack: () -> Unit = {},
    val onOpenCompany: (String) -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onMerge: (String) -> Unit = {},
    /** «Add new card»: capture linked to this contact. */
    val onAddCard: (String) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(actions: ContactActions, viewModel: ContactDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askAgain by remember { mutableStateOf<Long?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.addToPhone() else viewModel.shareVCard()
    }
    LaunchedEffect(deleted) { if (deleted) actions.onBack() }

    val messages = Messages(
        added = stringResource(R.string.contact_added_to_phone),
        failed = stringResource(R.string.contact_action_failed),
        phoneFailed = stringResource(R.string.contact_phone_failed),
        cardDeleted = stringResource(R.string.contact_card_deleted),
        onlyCard = stringResource(R.string.contact_only_card),
        undone = stringResource(R.string.merge_undone),
        shareTitle = stringResource(R.string.contact_share)
    )
    LaunchedEffect(event) {
        when (val e = event) {
            null -> return@LaunchedEffect
            is ContactEvent.Share -> share(context, e.file, messages.shareTitle)
            ContactEvent.PhonePermissionMissing -> permission.launch(Manifest.permission.WRITE_CONTACTS)
            is ContactEvent.AlreadyAddedToPhone -> askAgain = e.at
            ContactEvent.AddedToPhone -> snackbar.showSnackbar(messages.added)
            ContactEvent.PhoneFailed -> snackbar.showSnackbar(messages.phoneFailed)
            ContactEvent.CardDeleted -> snackbar.showSnackbar(messages.cardDeleted)
            ContactEvent.OnlyCard -> snackbar.showSnackbar(messages.onlyCard)
            ContactEvent.MergeUndone -> snackbar.showSnackbar(messages.undone)
            ContactEvent.Failed -> snackbar.showSnackbar(messages.failed)
        }
        viewModel.consumed()
    }
    // A merge just made into this contact can be undone for a few seconds (docs/06 §4).
    val merged = stringResource(R.string.merge_done)
    val undoLabel = stringResource(R.string.merge_undo)
    LaunchedEffect(undo) {
        if (undo == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(merged, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoMerge() else viewModel.dismissUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                },
                actions = {
                    if (state is ContactDetailUiState.Content) {
                        IconButton(onClick = {
                            actions.onEdit(viewModel.contactId)
                        }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.contact_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.contact_merge_with)) }, onClick = {
                                    menu = false
                                    actions.onMerge(viewModel.contactId)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.contact_delete)) }, onClick = {
                                    menu = false
                                    confirmDelete = true
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ContactDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ContactDetailUiState.Missing -> EmptyState(R.string.contact_missing)
                ContactDetailUiState.Error -> EmptyState(R.string.contact_error)
                is ContactDetailUiState.Content -> Details(s, viewModel, actions, onAddToPhone = {
                    viewModel.addToPhone()
                }, context = context, snackbar = snackbar)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.contact_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteContact()
                }) { Text(stringResource(R.string.contact_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
    askAgain?.let { at ->
        AlertDialog(
            onDismissRequest = { askAgain = null },
            text = { Text(stringResource(R.string.contact_already_added, formatDate(at))) },
            confirmButton = {
                TextButton(onClick = {
                    askAgain = null
                    viewModel.addToPhone(again = true)
                }) { Text(stringResource(R.string.contact_add_again)) }
            },
            dismissButton = { TextButton(onClick = { askAgain = null }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
}

private class Messages(
    val added: String,
    val failed: String,
    val phoneFailed: String,
    val cardDeleted: String,
    val onlyCard: String,
    val undone: String,
    val shareTitle: String
)

/** Shares a file through the FileProvider, on explicit user action only (CLAUDE.md). */
private fun share(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, title))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    state: ContactDetailUiState.Content,
    viewModel: ContactDetailViewModel,
    actions: ContactActions,
    onAddToPhone: () -> Unit,
    context: Context,
    snackbar: SnackbarHostState
) {
    val contact = state.contact
    var copied by remember { mutableStateOf(false) }
    val copiedText = stringResource(R.string.contact_copied)
    LaunchedEffect(copied) {
        if (copied) {
            snackbar.showSnackbar(copiedText)
            copied = false
        }
    }
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(text, text))
        copied = true
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${contact.firstName} ${contact.lastName}".trim(), style = MaterialTheme.typography.headlineSmall)
        contact.jobTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        state.company?.let { company -> TextButton(onClick = { actions.onOpenCompany(company.id) }) { Text(company.name) } }
        // Quick actions, only when there is data for them (docs/03).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val number = contact.mobile ?: contact.phone
            number?.let { n ->
                AssistChip(onClick = {
                    open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
                }, label = { Text(stringResource(R.string.contact_call)) })
            }
            contact.email?.let { e ->
                AssistChip(onClick = {
                    open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + e)))
                }, label = { Text(stringResource(R.string.contact_write_email)) })
            }
            contact.website?.let { w ->
                AssistChip(onClick = {
                    open(Intent(Intent.ACTION_VIEW, Uri.parse(if (w.startsWith("http")) w else "https://$w")))
                }, label = { Text(stringResource(R.string.contact_open_web)) })
            }
            contact.email?.let { e -> AssistChip(onClick = { copy(e) }, label = { Text(stringResource(R.string.contact_copy_email)) }) }
            number?.let { n -> AssistChip(onClick = { copy(n) }, label = { Text(stringResource(R.string.contact_copy_phone)) }) }
            AssistChip(onClick = viewModel::shareVCard, label = { Text(stringResource(R.string.contact_share)) })
            AssistChip(onClick = onAddToPhone, label = { Text(stringResource(R.string.contact_add_to_phone)) })
        }
        Section(R.string.contact_section_company, listOfNotNull(state.sectorName, state.country))
        Section(R.string.contact_section_contact, listOfNotNull(contact.phone, contact.mobile, contact.email, contact.website, contact.address))
        Section(R.string.contact_section_origin, state.events.map { it.name } + stringResource(R.string.contact_added_on, formatDate(contact.createdAt)))
        EventsButton(state, viewModel::setEvents)
        contact.notes?.let { Section(R.string.field_notes, listOf(it)) }
        History(state, viewModel, onAddCard = { actions.onAddCard(contact.id) })
    }
}

/** «Events…»: choose which events the contact belongs to. */
@Composable
private fun EventsButton(state: ContactDetailUiState.Content, onSave: (Set<String>) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.contact_events_edit)) }
    if (picking) {
        MultiSelectDialog(
            title = stringResource(R.string.contact_events_title),
            options = state.allEvents.map { SelectOption(it.id, it.name, it.location) },
            initial = state.events.map { it.id }.toSet(),
            confirmLabel = stringResource(R.string.catalog_save),
            empty = stringResource(R.string.contact_events_none),
            searchable = state.allEvents.size > EVENT_FILTER_FROM,
            onConfirm = {
                picking = false
                onSave(it)
            },
            onDismiss = { picking = false }
        )
    }
}

/** With few events a filter field is just noise. */
private const val EVENT_FILTER_FROM = 8

@Composable
private fun Section(title: Int, lines: List<String>) {
    if (lines.isEmpty()) return
    HorizontalDivider()
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

/** Card history (docs/06 §5): every card with its date and event, the primary one marked. */
@Composable
private fun History(state: ContactDetailUiState.Content, viewModel: ContactDetailViewModel, onAddCard: () -> Unit) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var back by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val selected = state.cards.firstOrNull { it.card.id == selectedId } ?: state.cards.firstOrNull { it.card.isPrimary } ?: state.cards.firstOrNull()
    HorizontalDivider()
    Text(stringResource(R.string.contact_cards, state.cards.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    selected?.let { card ->
        val front = stringResource(R.string.review_front)
        val backLabel = stringResource(R.string.review_back_side)
        CardImage(
            if (back &&
                card.back != null
            ) {
                card.back
            } else {
                card.front
            },
            stringResource(R.string.review_photo, if (back) backLabel else front),
            Modifier.fillMaxWidth().height(220.dp)
        )
        if (card.back != null) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = !back, onClick = { back = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(front) }
                SegmentedButton(selected = back, onClick = { back = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(backLabel) }
            }
        }
        if (state.cards.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!card.card.isPrimary) {
                    OutlinedButton(onClick = {
                        viewModel.setPrimary(card.card.id)
                    }) { Text(stringResource(R.string.contact_make_primary)) }
                }
                OutlinedButton(onClick = { confirm = card.card.id }) { Text(stringResource(R.string.contact_delete_card)) }
            }
        }
    }
    if (state.cards.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.cards, key = { it.card.id }) { item ->
                val eventName = state.events.firstOrNull { it.id == item.card.eventId }?.name
                Card(
                    Modifier.width(150.dp).clickable(role = Role.Button) {
                        selectedId = item.card.id
                        back = false
                    },
                    border = if (item.card.id == selected?.card?.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDate(item.card.capturedAt), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (item.card.isPrimary) Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.contact_primary))
                        }
                        eventName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = onAddCard) { Text(stringResource(R.string.contact_add_card)) }
    confirm?.let { id ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(R.string.contact_delete_card_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    selectedId = null
                    viewModel.deleteCard(id)
                }) { Text(stringResource(R.string.contact_delete_card)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
}
