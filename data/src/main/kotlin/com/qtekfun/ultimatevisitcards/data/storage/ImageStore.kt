package com.qtekfun.ultimatevisitcards.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The only class that touches the file system for card images (docs/05-almacenamiento-backup-export.md):
 *
 * ```
 * filesDir/cards/<cardId>/front_original.jpg   original, ≤ 2400 px, JPEG q88
 * filesDir/cards/<cardId>/front.jpg            straightened, ≤ 1600 px, JPEG q90
 * filesDir/cards/<cardId>/back_original.jpg, back.jpg (optional)
 * cacheDir/thumbs/<cardId>_front.jpg           ≈ 400 px, regenerated when missing
 * ```
 *
 * Paths stored in the database are relative to `filesDir`. Every write is atomic (temporary file in the
 * same folder, fsync, rename) and the JPEGs are encoded from pixels, so they carry no EXIF (no location).
 * Everything is private to the app; nothing goes to MediaStore or public folders. Failures throw
 * [IOException]; callers turn them into UI errors.
 */
@Singleton
class ImageStore(private val filesRoot: File, private val cacheRoot: File, private val io: CoroutineDispatcher) {
    @Inject
    constructor(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) : this(context.filesDir, context.cacheDir, io)

    fun originalPath(cardId: String, side: CardSide): String = "$CARDS/${checkId(cardId)}/${side.fileStem}_original.jpg"

    fun processedPath(cardId: String, side: CardSide): String = "$CARDS/${checkId(cardId)}/${side.fileStem}.jpg"

    /** Saves the photo as taken (upright), reduced to ≤ 2400 px. Returns its relative path. */
    suspend fun saveOriginal(cardId: String, side: CardSide, photo: Bitmap): String = withContext(io) {
        originalPath(cardId, side).also { writeJpeg(file(it), photo, ORIGINAL_MAX_SIDE, ORIGINAL_QUALITY) }
    }

    /** Saves the straightened card, ≤ 1600 px, and drops its outdated thumbnail. Returns its relative path. */
    suspend fun saveProcessed(cardId: String, side: CardSide, card: Bitmap): String = withContext(io) {
        processedPath(cardId, side).also {
            writeJpeg(file(it), card, PROCESSED_MAX_SIDE, PROCESSED_QUALITY)
            thumbnailFile(cardId, side).delete()
        }
    }

    /** Decodes a stored image, subsampled so it is not much larger than [maxSide]; null if it is missing. */
    suspend fun load(relativePath: String, maxSide: Int = Int.MAX_VALUE): Bitmap? = withContext(io) {
        val file = file(relativePath)
        if (!file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The ≈400 px thumbnail of a side, generated from the straightened image when missing. */
    suspend fun thumbnail(cardId: String, side: CardSide): File? = withContext(io) {
        val thumb = thumbnailFile(cardId, side)
        if (thumb.isFile) return@withContext thumb
        val card = load(processedPath(cardId, side), THUMBNAIL_MAX_SIDE) ?: return@withContext null
        writeJpeg(thumb, card, THUMBNAIL_MAX_SIDE, THUMBNAIL_QUALITY)
        thumb
    }

    /**
     * Best existing image to show small, without generating anything (safe for long lists): the thumbnail
     * if it exists, otherwise the straightened image (the image loader downsamples it).
     */
    suspend fun preview(cardId: String, side: CardSide): File? = withContext(io) {
        thumbnailFile(cardId, side).takeIf { it.isFile } ?: file(processedPath(cardId, side)).takeIf { it.isFile }
    }

    /**
     * Cache folder for files the user explicitly shares (vCards, exports), exposed only through the
     * FileProvider (`res/xml/file_paths.xml`). Regenerable: the system may clear it.
     */
    fun sharedDir(): File = File(cacheRoot, SHARED)

    /** Resolves a relative path inside the app's private storage; rejects anything that would escape it. */
    fun file(relativePath: String): File {
        val root = filesRoot.canonicalFile
        val file = File(root, relativePath).canonicalFile
        if (!file.path.startsWith(root.path + File.separator)) throw IOException("Path outside the image store: $relativePath")
        return file
    }

    /** Deletes one side's images (e.g. the back is removed) and its thumbnail. */
    suspend fun deleteSide(cardId: String, side: CardSide) = withContext(io) {
        file(originalPath(cardId, side)).delete()
        file(processedPath(cardId, side)).delete()
        thumbnailFile(cardId, side).delete()
    }

    /** Deletes every image of a card (when the card or its contact is deleted). */
    suspend fun deleteCard(cardId: String) = withContext(io) {
        File(filesRoot, "$CARDS/${checkId(cardId)}").deleteRecursively()
        CardSide.entries.forEach { thumbnailFile(cardId, it).delete() }
    }

    /**
     * Maintenance: deletes the folders and thumbnails of cards that are no longer in the database, plus any
     * temporary file left by an interrupted write. Returns the ids whose folders were deleted.
     */
    suspend fun deleteOrphans(existingCardIds: Set<String>): Set<String> = withContext(io) {
        val deleted = mutableSetOf<String>()
        File(filesRoot, CARDS).listFiles().orEmpty().forEach { dir ->
            if (dir.name !in existingCardIds) {
                if (dir.deleteRecursively()) deleted += dir.name
            } else {
                dir.listFiles { f -> f.name.startsWith(TEMP_PREFIX) }.orEmpty().forEach { it.delete() }
            }
        }
        File(cacheRoot, THUMBS).listFiles().orEmpty()
            .filter { it.name.substringBeforeLast('_') !in existingCardIds }
            .forEach { it.delete() }
        deleted
    }

    /** The relative paths from [paths] whose file is missing (rows without file). */
    suspend fun missing(paths: Collection<String>): List<String> = withContext(io) {
        paths.filter { !file(it).isFile }
    }

    private fun thumbnailFile(cardId: String, side: CardSide) = File(cacheRoot, "$THUMBS/${checkId(cardId)}_${side.fileStem}.jpg")

    private fun writeJpeg(target: File, bitmap: Bitmap, maxSide: Int, quality: Int) {
        val dir = target.parentFile ?: throw IOException("No folder for $target")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val scaled = downscale(bitmap, maxSide)
        val temp = File.createTempFile(TEMP_PREFIX, ".tmp", dir)
        try {
            FileOutputStream(temp).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encoding failed")
                out.flush()
                out.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Cannot move $temp to $target")
        } finally {
            temp.delete()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val factor = max(bitmap.width, bitmap.height).toDouble() / maxSide
        if (factor <= 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width / factor).roundToInt()), max(1, (bitmap.height / factor).roundToInt()), true)
    }

    /** Card ids are UUIDs; anything else could build a path outside the store. */
    private fun checkId(cardId: String): String {
        if (!ID.matches(cardId)) throw IOException("Invalid card id: $cardId")
        return cardId
    }

    companion object {
        const val ORIGINAL_MAX_SIDE = 2400
        const val PROCESSED_MAX_SIDE = 1600
        const val THUMBNAIL_MAX_SIDE = 400
        private const val ORIGINAL_QUALITY = 88
        private const val PROCESSED_QUALITY = 90
        private const val THUMBNAIL_QUALITY = 85
        private const val CARDS = "cards"
        private const val THUMBS = "thumbs"
        private const val SHARED = "shared"
        private const val TEMP_PREFIX = ".write-"
        private val ID = Regex("[A-Za-z0-9-]{1,64}")
    }
}
