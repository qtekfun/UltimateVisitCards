package com.qtekfun.ultimatevisitcards.ui.capture

import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.qtekfun.ultimatevisitcards.ocr.processing.Bitmaps
import com.qtekfun.ultimatevisitcards.ocr.processing.BlurMeter
import com.qtekfun.ultimatevisitcards.ocr.processing.CaptureGuide
import com.qtekfun.ultimatevisitcards.ocr.processing.CardDetector
import com.qtekfun.ultimatevisitcards.ocr.processing.Point
import com.qtekfun.ultimatevisitcards.ocr.processing.Quad
import com.qtekfun.ultimatevisitcards.ocr.processing.StabilityTracker
import kotlin.math.hypot
import kotlin.math.max

/**
 * What one preview frame shows, in the frame's upright crop coordinates (map them to the screen with
 * [transform]). Uses CameraX's experimental transform API, the documented way to map analysis coordinates
 * onto the preview (see docs/decisions.md).
 */
@OptIn(TransformExperimental::class)
class FrameAnalysis(
    val transform: OutputTransform,
    /** The capture guide (always drawn). */
    val guide: Quad,
    /** The detected card, if any. */
    val card: Quad?,
    val blurry: Boolean,
    /** The card has stayed still and sharp long enough: shoot if auto capture is on. */
    val stable: Boolean
)

/**
 * Live analysis of preview frames on the analysis executor: card outline, blur warning and stability for
 * automatic capture. Frames are downsampled to ~[WORK_SIZE] px before any processing.
 */
@OptIn(TransformExperimental::class)
class FrameAnalyzer(private val onResult: (FrameAnalysis) -> Unit) : ImageAnalysis.Analyzer {
    private val detector = CardDetector(workSize = WORK_SIZE)
    private val tracker = StabilityTracker()
    private val transforms = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }
    private val recentSharpness = ArrayDeque<Double>()

    override fun analyze(frame: ImageProxy) {
        frame.use {
            val step = max(1, max(it.cropRect.width(), it.cropRect.height()) / WORK_SIZE)
            val luma = Bitmaps.lumaOf(it, step)
            val scale = step.toDouble()
            val card = detector.detect(luma)?.quad?.scaled(scale, scale)
            recentSharpness.addLast(BlurMeter.sharpness(luma))
            if (recentSharpness.size > SHARPNESS_WINDOW) recentSharpness.removeFirst()
            // Averaged over a few frames so the warning does not flicker.
            val blurry = recentSharpness.average() < BlurMeter.BLURRY_BELOW
            val width = luma.width * scale
            val height = luma.height * scale
            val stable = tracker.onFrame(card, !blurry, hypot(width, height), it.imageInfo.timestamp / 1_000_000)
            val g = CaptureGuide.rectFor(width.toInt(), height.toInt())
            val guide = Quad(
                Point(g.left.toDouble(), g.top.toDouble()),
                Point(g.right.toDouble(), g.top.toDouble()),
                Point(g.right.toDouble(), g.bottom.toDouble()),
                Point(g.left.toDouble(), g.bottom.toDouble())
            )
            onResult(FrameAnalysis(transforms.getOutputTransform(it), guide, card, blurry, stable))
        }
    }

    companion object {
        const val WORK_SIZE = 640
        private const val SHARPNESS_WINDOW = 5
    }
}
