package com.qtekfun.ultimatevisitcards.data.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val files by lazy { folder.newFolder("files") }
    private val cache by lazy { folder.newFolder("cache") }
    private val store by lazy { ImageStore(files, cache, dispatcher) }
    private val id = "0b7e7c0e-1d2a-4c55-9a0e-3a3f1f6c9b10"

    private fun photo(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 180, 40)) }

    private fun size(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun `originals and straightened images are stored at their maximum size under relative paths`() = runTest(dispatcher) {
        val original = store.saveOriginal(id, CardSide.FRONT, photo(4000, 3000))
        val processed = store.saveProcessed(id, CardSide.FRONT, photo(1700, 1100))
        assertEquals("cards/$id/front_original.jpg", original)
        assertEquals("cards/$id/front.jpg", processed)
        assertEquals(2400 to 1800, size(store.file(original)))
        assertEquals(1600 to 1035, size(store.file(processed)))
        // Small images are not enlarged.
        assertEquals(800 to 500, size(store.file(store.saveProcessed(id, CardSide.BACK, photo(800, 500)))))
    }

    @Test
    fun `stored JPEGs carry no EXIF metadata`() = runTest(dispatcher) {
        val bytes = store.file(store.saveOriginal(id, CardSide.FRONT, photo(640, 480))).readBytes()
        assertEquals(0xFF.toByte() to 0xD8.toByte(), bytes[0] to bytes[1])
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Exif"))
    }

    @Test
    fun `writes are atomic and leave no temporary files`() = runTest(dispatcher) {
        val path = store.saveProcessed(id, CardSide.FRONT, photo(300, 200))
        store.saveProcessed(id, CardSide.FRONT, photo(600, 400))
        assertEquals(600 to 400, size(store.file(path)))
        assertEquals(listOf("front.jpg"), store.file(path).parentFile!!.list()!!.toList())
    }

    @Test
    fun `a failed write keeps the previous image`() = runTest(dispatcher) {
        val path = store.saveProcessed(id, CardSide.FRONT, photo(300, 200))
        val recycled = photo(10, 10).also { it.recycle() }
        assertFailsWith<RuntimeException> { store.saveProcessed(id, CardSide.FRONT, recycled) }
        assertEquals(300 to 200, size(store.file(path)))
        assertEquals(listOf("front.jpg"), store.file(path).parentFile!!.list()!!.toList())
    }

    @Test
    fun `loading subsamples large images and returns null for missing ones`() = runTest(dispatcher) {
        val path = store.saveOriginal(id, CardSide.FRONT, photo(2400, 1600))
        val loaded = assertNotNull(store.load(path, maxSide = 600))
        assertEquals(600 to 400, loaded.width to loaded.height)
        assertNull(store.load("cards/$id/back.jpg"))
    }

    @Test
    fun `thumbnails are generated when missing and refreshed when the image changes`() = runTest(dispatcher) {
        assertNull(store.thumbnail(id, CardSide.FRONT))
        store.saveProcessed(id, CardSide.FRONT, photo(1600, 1000))
        val thumb = assertNotNull(store.thumbnail(id, CardSide.FRONT))
        assertEquals(400 to 250, size(thumb))
        store.saveProcessed(id, CardSide.FRONT, photo(1000, 1600))
        assertEquals(250 to 400, size(assertNotNull(store.thumbnail(id, CardSide.FRONT))))
    }

    @Test
    fun `paths cannot escape the store`() = runTest(dispatcher) {
        assertFailsWith<IOException> { store.file("../outside.jpg") }
        assertFailsWith<IOException> { store.file("cards/../../outside.jpg") }
        assertFailsWith<IOException> { store.saveOriginal("../x", CardSide.FRONT, photo(10, 10)) }
    }

    @Test
    fun `deleting a card removes its folder and thumbnails`() = runTest(dispatcher) {
        store.saveOriginal(id, CardSide.FRONT, photo(100, 60))
        store.saveProcessed(id, CardSide.FRONT, photo(100, 60))
        assertNotNull(store.thumbnail(id, CardSide.FRONT))
        store.deleteCard(id)
        assertFalse(File(files, "cards/$id").exists())
        assertNull(store.thumbnail(id, CardSide.FRONT))
    }

    @Test
    fun `orphans are deleted and missing files reported`() = runTest(dispatcher) {
        val keep = id
        val orphan = "5f0c2a8e-7c1b-4f4e-8a55-2a1b3c4d5e6f"
        store.saveProcessed(keep, CardSide.FRONT, photo(100, 60))
        store.saveProcessed(orphan, CardSide.FRONT, photo(100, 60))
        store.thumbnail(orphan, CardSide.FRONT)
        File(files, "cards/$keep/.write-123.tmp").writeText("interrupted")

        assertEquals(setOf(orphan), store.deleteOrphans(setOf(keep)))
        assertTrue(store.file("cards/$keep/front.jpg").isFile)
        assertFalse(File(files, "cards/$keep/.write-123.tmp").exists())
        assertEquals(emptyList(), cache.resolve("thumbs").list()!!.toList())
        assertEquals(listOf("cards/$keep/back.jpg"), store.missing(listOf("cards/$keep/front.jpg", "cards/$keep/back.jpg")))
    }
}
