package com.qtekfun.ultimatevisitcards.ui.capture

import android.graphics.Bitmap
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qtekfun.ultimatevisitcards.ocr.processing.Bitmaps
import com.qtekfun.ultimatevisitcards.ocr.processing.Quad
import java.util.concurrent.Executors

/** Commands from the screen to the camera. */
class CameraCommands {
    internal var takePicture: (() -> Unit)? = null

    fun takePicture() {
        takePicture?.invoke()
    }
}

/**
 * Camera preview with the capture guide and the detected card drawn on top. Photos are delivered upright
 * through [onPhoto] (on a background thread); live analysis results through [onAnalysis].
 */
@OptIn(TransformExperimental::class)
@Composable
fun CameraPreview(
    commands: CameraCommands,
    flashOn: Boolean,
    analyzing: Boolean,
    onPhoto: (Bitmap) -> Unit,
    onAnalysis: (FrameAnalysis) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val currentOnAnalysis by rememberUpdatedState(onAnalysis)
    val currentOnError by rememberUpdatedState(onError)
    var analysis by remember { mutableStateOf<FrameAnalysis?>(null) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            previewResolutionSelector = selector(null)
            // Enough for the ≤ 2400 px original without decoding a 12+ MP image.
            imageCaptureResolutionSelector = selector(Size(2400, 1800))
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageAnalysisResolutionSelector = selector(Size(640, 480))
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            isTapToFocusEnabled = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        previewView.controller = controller
        controller.bindToLifecycle(lifecycleOwner)
        commands.takePicture = {
            controller.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val photo = image.use { Bitmaps.rotate(it.toBitmap(), it.imageInfo.rotationDegrees) }
                        currentOnPhoto(photo)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute { currentOnError() }
                    }
                }
            )
        }
        onDispose {
            commands.takePicture = null
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(analyzing) {
        if (analyzing) {
            controller.setImageAnalysisAnalyzer(
                analysisExecutor,
                FrameAnalyzer { result ->
                    mainExecutor.execute {
                        analysis = result
                        currentOnAnalysis(result)
                    }
                }
            )
        } else {
            controller.clearImageAnalysisAnalyzer()
            analysis = null
        }
    }

    LaunchedEffect(flashOn) {
        controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    val guideColor = Color.White
    val cardColor = MaterialTheme.colorScheme.primary
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val frame = analysis ?: return@Canvas
            val target = previewView.outputTransform ?: return@Canvas
            val transform = CoordinateTransform(frame.transform, target)
            drawPath(frame.guide.toPath(transform), guideColor.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
            frame.card?.let { drawPath(it.toPath(transform), cardColor, style = Stroke(4.dp.toPx())) }
        }
    }
}

private fun selector(size: Size?): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .apply {
        if (size != null) setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    }
    .build()

@OptIn(TransformExperimental::class)
private fun Quad.toPath(transform: CoordinateTransform): Path {
    val coords = points.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
    transform.mapPoints(coords)
    return Path().apply {
        moveTo(coords[0], coords[1])
        for (i in 1 until 4) lineTo(coords[2 * i], coords[2 * i + 1])
        close()
    }
}
