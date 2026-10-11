package com.qtekfun.ultimatevisitcards.ocr.foss

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import com.googlecode.tesseract.android.TessBaseAPI.PageSegMode
import com.qtekfun.ultimatevisitcards.domain.ocr.NormRect
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrBlock
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrException
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrLine
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import com.qtekfun.ultimatevisitcards.ocr.processing.Bitmaps
import com.qtekfun.ultimatevisitcards.ocr.processing.ImagePreprocessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * `foss` flavour: Tesseract4Android with the *fast* models of eng, spa, fra, deu and ita bundled in the APK
 * (docs/04 §2). The models are copied to private storage on first use. One native instance is reused for a
 * whole queue run and freed by [release]. Cards are not pages of running text, so recognition starts in
 * `SPARSE_TEXT` mode and retries in `AUTO` when the result is poor.
 */
@Singleton
class TesseractOcrService @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) :
    OcrService {
    override val engineId: String = OcrService.TESSERACT

    private val mutex = Mutex()
    private var api: TessBaseAPI? = null
    private var languages: String? = null

    override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult = mutex.withLock {
        withContext(io) {
            val source = BitmapFactory.decodeFile(image.file.path) ?: throw OcrException("Cannot read ${image.file}")
            val prepared = Bitmaps.toBitmap(ImagePreprocessor.forTesseract(Bitmaps.toArgb(source)))
            source.recycle()
            try {
                val tess = engine(hints)
                tess.setImage(prepared)
                var result = read(tess, PageSegMode.PSM_SPARSE_TEXT, prepared.width, prepared.height)
                if (isPoor(result)) {
                    val auto = read(tess, PageSegMode.PSM_AUTO, prepared.width, prepared.height)
                    if (auto.lines.size > result.lines.size) result = auto
                }
                tess.clear()
                result
            } finally {
                prepared.recycle()
            }
        }
    }

    override suspend fun release() = mutex.withLock {
        api?.recycle()
        api = null
        languages = null
    }

    private fun engine(hints: OcrHints): TessBaseAPI {
        val wanted = hints.languages.filter { it in BUNDLED }.ifEmpty { BUNDLED }.joinToString("+")
        api?.let { if (languages == wanted) return it else it.recycle() }
        val dataPath = installModels()
        val tess = TessBaseAPI()
        if (!tess.init(dataPath.path, wanted)) {
            tess.recycle()
            throw OcrException("Tesseract could not load $wanted")
        }
        api = tess
        languages = wanted
        return tess
    }

    /** Copies the bundled models to `filesDir/tesseract/tessdata` (once per app version). */
    private fun installModels(): File {
        val root = File(context.filesDir, "tesseract")
        val dir = File(root, "tessdata")
        val marker = File(root, ".installed-$MODELS_VERSION")
        if (marker.isFile && BUNDLED.all { File(dir, "$it.traineddata").isFile }) return root
        if (!dir.isDirectory && !dir.mkdirs()) throw OcrException("Cannot create $dir")
        try {
            for (language in BUNDLED) {
                val target = File(dir, "$language.traineddata")
                val temp = File(dir, "$language.traineddata.tmp")
                context.assets.open("tessdata/$language.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
                if (!temp.renameTo(target)) throw IOException("Cannot move $temp")
            }
            root.listFiles { f -> f.name.startsWith(".installed-") }?.forEach { it.delete() }
            marker.createNewFile()
        } catch (e: IOException) {
            throw OcrException("Cannot install Tesseract models", e)
        }
        return root
    }

    private fun read(tess: TessBaseAPI, mode: Int, width: Int, height: Int): OcrResult {
        tess.pageSegMode = mode
        val text = tess.utF8Text ?: ""
        val iterator = tess.resultIterator ?: return OcrResult(text, emptyList())
        val blocks = mutableListOf<OcrBlock>()
        var current = mutableListOf<OcrLine>()
        fun flush() {
            if (current.isNotEmpty()) {
                val box = NormRect(current.minOf { it.box.left }, current.minOf { it.box.top }, current.maxOf { it.box.right }, current.maxOf { it.box.bottom })
                blocks += OcrBlock(current.joinToString("\n") { it.text }, current, box)
                current = mutableListOf()
            }
        }
        try {
            iterator.begin()
            do {
                if (iterator.isAtBeginningOf(PageIteratorLevel.RIL_BLOCK)) flush()
                val line = iterator.getUTF8Text(PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                if (line.isNotEmpty()) {
                    val rect: Rect = iterator.getBoundingRect(PageIteratorLevel.RIL_TEXTLINE)
                    current += OcrLine(
                        text = line,
                        box = NormRect(rect.left / width.toFloat(), rect.top / height.toFloat(), rect.right / width.toFloat(), rect.bottom / height.toFloat()),
                        confidence = (iterator.confidence(PageIteratorLevel.RIL_TEXTLINE) / 100f).coerceIn(0f, 1f),
                        heightPx = rect.height().toFloat()
                    )
                }
            } while (iterator.next(PageIteratorLevel.RIL_TEXTLINE))
            flush()
        } finally {
            iterator.delete()
        }
        return OcrResult(text, blocks)
    }

    /** Fewer than three lines or a low mean confidence: worth a second pass with automatic layout. */
    private fun isPoor(result: OcrResult): Boolean {
        val lines = result.lines
        if (lines.size < 3) return true
        val confidences = lines.mapNotNull { it.confidence }
        return confidences.isNotEmpty() && confidences.average() < 0.5
    }

    private companion object {
        val BUNDLED = listOf("eng", "spa", "fra", "deu", "ita")

        /** Bump when the bundled models change, so they are copied again. */
        const val MODELS_VERSION = 1
    }
}
