package com.qtekfun.ultimatevisitcards.ui.quickscan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.ui.capture.CameraCommands
import com.qtekfun.ultimatevisitcards.ui.capture.CameraPermission
import com.qtekfun.ultimatevisitcards.ui.capture.CameraPreview

/** Quick scan: continuous capture with a counter; the cards wait in «Pending review». */
@Composable
fun QuickScanScreen(onFinished: () -> Unit, viewModel: QuickScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler { viewModel.finish() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPermission(onClose = onFinished) {
            Content(state, viewModel)
        }
        if (state.finishing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.quick_finishing), color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Content(state: QuickScanUiState, viewModel: QuickScanViewModel) {
    val commands = remember { CameraCommands() }
    CameraPreview(
        commands = commands,
        flashOn = state.flashOn,
        analyzing = !state.finishing,
        onPhoto = viewModel::onPhoto,
        onAnalysis = { analysis ->
            viewModel.onSharpness(analysis.blurry)
            if (analysis.stable && state.autoCapture && state.canShoot) commands.takePicture()
        },
        onError = {},
        modifier = Modifier.fillMaxSize()
    )
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(state, viewModel)
        Box(Modifier.weight(1f).fillMaxWidth())
        BottomBar(state, viewModel, onShoot = commands::takePicture)
    }
}

@Composable
private fun TopBar(state: QuickScanUiState, viewModel: QuickScanViewModel) {
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::finish) { Text(stringResource(R.string.quick_finish), color = Color.White) }
            Box(Modifier.weight(1f))
            FilterChip(
                selected = state.flashOn,
                onClick = viewModel::toggleFlash,
                label = { Text(stringResource(R.string.capture_flash)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_flash), contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            val autoDescription = stringResource(R.string.capture_auto_description)
            FilterChip(
                selected = state.autoCapture,
                onClick = viewModel::toggleAutoCapture,
                label = { Text(stringResource(R.string.capture_auto)) },
                modifier = Modifier.semantics { contentDescription = autoDescription }
            )
        }
        EventChooser(state, viewModel)
        if (state.blurry) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    stringResource(R.string.capture_blurry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** The active event of the batch, with «New event…». */
@Composable
private fun EventChooser(state: QuickScanUiState, viewModel: QuickScanViewModel) {
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val none = stringResource(R.string.review_none)
    val name = state.events.firstOrNull { it.event.id == state.eventId }?.event?.name ?: none
    Box {
        FilterChip(
            selected = state.eventId != null,
            onClick = { open = true },
            label = { Text(stringResource(R.string.quick_event, name)) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(none) }, onClick = {
                viewModel.chooseEvent(null)
                open = false
            })
            state.events.forEach { summary ->
                DropdownMenuItem(text = { Text(summary.event.name) }, onClick = {
                    viewModel.chooseEvent(summary.event.id)
                    open = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.review_new_event)) }, onClick = {
                open = false
                creating = true
            })
        }
    }
    if (creating) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.review_new_event_title)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.review_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    viewModel.createEvent(text.trim())
                    creating = false
                }) { Text(stringResource(R.string.review_create)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.review_cancel)) } }
        )
    }
}

@Composable
private fun BottomBar(state: QuickScanUiState, viewModel: QuickScanViewModel, onShoot: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (state.awaitingBack) R.string.capture_hint_back else R.string.capture_hint_front),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.quick_counter, state.saved), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (state.failed > 0) Text(stringResource(R.string.quick_failed, state.failed), color = MaterialTheme.colorScheme.error)
                }
                Shutter(enabled = state.canShoot, onShoot = onShoot)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (state.awaitingBack) {
                        TextButton(onClick = viewModel::skipBack) { Text(stringResource(R.string.capture_skip_back), color = Color.White) }
                    } else {
                        Text(stringResource(R.string.quick_has_back), color = Color.White, modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = state.hasBack, onCheckedChange = { viewModel.toggleHasBack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onShoot: () -> Unit) {
    val description = stringResource(R.string.capture_shutter)
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onShoot)
            .semantics { contentDescription = description }
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.Gray)
    )
}
