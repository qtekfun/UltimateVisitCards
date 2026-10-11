package com.qtekfun.ultimatevisitcards.ocr.play

import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.qtekfun.ultimatevisitcards.domain.ocr.NormRect
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrBlock
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrException
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrLine
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `play` flavour: ML Kit text recognition with the Latin model **bundled** in the APK
 * (`com.google.mlkit:text-recognition`), so it works offline from the first run, without Play Services
 * downloading anything.
 */
@Singleton
class MlKitOcrService @Inject constructor() : OcrService {
    override val engineId: String = OcrService.MLKIT

    private val mutex = Mutex()
    private var recognizer: TextRecognizer? = null

    override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult = mutex.withLock {
        val bitmap = BitmapFactory.decodeFile(image.file.path) ?: throw OcrException("Cannot read ${image.file}")
        val client = recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
        val text = try {
            suspendCancellableCoroutine { cont ->
                client.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(OcrException("ML Kit failed", it)) }
            }
        } finally {
            bitmap.recycle()
        }
        toResult(text, bitmap.width, bitmap.height)
    }

    override suspend fun release() = mutex.withLock {
        recognizer?.close()
        recognizer = null
    }

    private fun toResult(text: Text, width: Int, height: Int): OcrResult {
        fun norm(rect: Rect?) = rect?.let {
            NormRect(it.left / width.toFloat(), it.top / height.toFloat(), it.right / width.toFloat(), it.bottom / height.toFloat())
        } ?: NormRect(0f, 0f, 1f, 1f)
        val blocks = text.textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                lines = block.lines.map { line ->
                    OcrLine(line.text, norm(line.boundingBox), line.confidence.takeIf { it in 0f..1f }, (line.boundingBox?.height() ?: 0).toFloat())
                },
                box = norm(block.boundingBox)
            )
        }
        return OcrResult(text.text, blocks)
    }
}
