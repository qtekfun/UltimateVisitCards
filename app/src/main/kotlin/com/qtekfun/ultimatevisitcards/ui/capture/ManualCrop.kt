package com.qtekfun.ultimatevisitcards.ui.capture

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.NormPoint
import kotlin.math.roundToInt

/**
 * Manual crop over the original photo: four draggable corners (48 dp touch targets). «Use whole photo» is
 * the button alternative to dragging.
 */
@Composable
fun ManualCrop(original: Bitmap, initial: CardQuad, onApply: (CardQuad) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var corners by remember(initial) { mutableStateOf(initial.points) }
    val image = remember(original) { original.asImageBitmap() }
    val lineColor = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        Text(
            text = stringResource(R.string.crop_title),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            // The displayed image rectangle (ContentScale.Fit) in pixels.
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val scale = minOf(boxW / original.width, boxH / original.height)
            val shownW = original.width * scale
            val shownH = original.height * scale
            val left = (boxW - shownW) / 2
            val top = (boxH - shownH) / 2
            fun toScreen(p: NormPoint) = Offset(left + p.x * shownW, top + p.y * shownH)

            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    corners.forEachIndexed { i, p -> toScreen(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                    close()
                }
                drawPath(path, lineColor, style = Stroke(3.dp.toPx()))
            }
            val handle = 48.dp
            val handlePx = with(density) { handle.toPx() }
            corners.forEachIndexed { index, point ->
                val description = stringResource(R.string.crop_corner, index + 1)
                val center = toScreen(point)
                Box(
                    Modifier
                        .offset { IntOffset((center.x - handlePx / 2).roundToInt(), (center.y - handlePx / 2).roundToInt()) }
                        .size(handle)
                        .semantics { contentDescription = description }
                        .pointerInput(index, shownW, shownH) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                corners = corners.toMutableList().also {
                                    val p = it[index]
                                    it[index] = NormPoint(
                                        (p.x + drag.x / shownW).coerceIn(0f, 1f),
                                        (p.y + drag.y / shownH).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(lineColor))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { corners = WHOLE_PHOTO.points }) { Text(stringResource(R.string.crop_whole_photo)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.crop_cancel)) }
            Button(onClick = { onApply(CardQuad.of(corners)) }) { Text(stringResource(R.string.crop_apply)) }
        }
    }
}

private val WHOLE_PHOTO = CardQuad(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f))
