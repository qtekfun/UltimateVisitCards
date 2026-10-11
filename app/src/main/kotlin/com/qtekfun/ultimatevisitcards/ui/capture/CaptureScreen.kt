package com.qtekfun.ultimatevisitcards.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.qtekfun.ultimatevisitcards.domain.model.CardSide

/** Normal capture: front, optional back, card saved as pending review. */
@Composable
fun CaptureScreen(onClose: () -> Unit, onSaved: (cardId: String) -> Unit, viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedCardId) { state.savedCardId?.let(onSaved) }
    BackHandler(enabled = state.crop != null) { viewModel.cancelCrop() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPermission(onClose = onClose) {
            val crop = state.crop
            if (crop != null) {
                ManualCrop(crop.original, crop.quad, onApply = viewModel::applyCrop, onCancel = viewModel::cancelCrop)
            } else {
                CaptureContent(state, viewModel, onClose)
            }
        }
        if (state.busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.capture_processing), color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.capture_dismiss)) } },
                text = {
                    Text(
                        stringResource(
                            when (error) {
                                CaptureError.SAVE_FAILED -> R.string.capture_error_save
                                CaptureError.CAMERA_FAILED -> R.string.capture_error_camera
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun CaptureContent(state: CaptureUiState, viewModel: CaptureViewModel, onClose: () -> Unit) {
    val commands = remember { CameraCommands() }
    // The camera stays bound while taking both sides; it is only released when the card is saved.
    if (state.step != CaptureStep.SAVED) {
        CameraPreview(
            commands = commands,
            flashOn = state.flashOn,
            analyzing = state.framing,
            onPhoto = viewModel::onPhoto,
            onAnalysis = { analysis ->
                viewModel.onSharpness(analysis.blurry)
                if (analysis.stable && state.autoCapture && state.framing && !state.busy) commands.takePicture()
            },
            onError = viewModel::onCameraError,
            modifier = Modifier.fillMaxSize()
        )
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(state, viewModel, onClose)
        Box(Modifier.weight(1f).fillMaxWidth())
        when (state.step) {
            CaptureStep.FRONT, CaptureStep.BACK -> Shutter(state, onShoot = commands::takePicture)
            CaptureStep.FRONT_TAKEN -> state.front?.let { front ->
                TakenPanel(front, R.string.capture_front_preview) {
                    OutlinedButton(onClick = viewModel::retake) { Text(stringResource(R.string.capture_retake)) }
                    OutlinedButton(onClick = { viewModel.openCrop(CardSide.FRONT) }) { Text(stringResource(R.string.capture_adjust_crop)) }
                    OutlinedButton(onClick = viewModel::skipBack) { Text(stringResource(R.string.capture_skip_back)) }
                    Button(onClick = viewModel::addBack) { Text(stringResource(R.string.capture_add_back)) }
                }
            }

            CaptureStep.BACK_TAKEN -> state.back?.let { back ->
                TakenPanel(back, R.string.capture_back_preview) {
                    OutlinedButton(onClick = viewModel::retake) { Text(stringResource(R.string.capture_retake)) }
                    OutlinedButton(onClick = { viewModel.openCrop(CardSide.BACK) }) { Text(stringResource(R.string.capture_adjust_crop)) }
                    Button(onClick = viewModel::saveWithBack) { Text(stringResource(R.string.capture_save)) }
                }
            }

            CaptureStep.SAVED -> Unit
        }
    }
}

@Composable
private fun TopBar(state: CaptureUiState, viewModel: CaptureViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.capture_close), tint = Color.White)
            }
            Box(Modifier.weight(1f))
            if (state.framing) {
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
        }
        if (state.framing) {
            Text(
                stringResource(if (state.currentSide == CardSide.FRONT) R.string.capture_hint_front else R.string.capture_hint_back),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (state.blurry) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
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
}

@Composable
private fun Shutter(state: CaptureUiState, onShoot: () -> Unit) {
    val description = stringResource(R.string.capture_shutter)
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = !state.busy, role = Role.Button, onClick = onShoot)
                .semantics { contentDescription = description }
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (state.busy) Color.Gray else Color.White)
        )
    }
}

@Composable
private fun TakenPanel(side: CapturedSide, preview: Int, actions: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                bitmap = remember(side.preview) { side.preview.asImageBitmap() },
                contentDescription = stringResource(preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
            )
            if (!side.detected) {
                Text(stringResource(R.string.capture_not_detected), style = MaterialTheme.typography.bodyMedium)
            }
            FlowButtons(actions)
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun FlowButtons(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}
