package com.qtekfun.ultimatevisitcards.ocr.processing

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.roundToInt

/** Conversions between Android images and the pure-Kotlin images of this package. */
object Bitmaps {
    fun toArgb(bitmap: Bitmap): ArgbImage {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ArgbImage(bitmap.width, bitmap.height, pixels)
    }

    fun toBitmap(image: ArgbImage): Bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)

    /** Grey image as an opaque ARGB_8888 bitmap (the format Tesseract expects). */
    fun toBitmap(image: LumaImage): Bitmap = Bitmap.createBitmap(
        IntArray(image.data.size) { i -> image.data[i].let { (0xFF shl 24) or (it shl 16) or (it shl 8) or it } },
        image.width,
        image.height,
        Bitmap.Config.ARGB_8888
    )

    /** The bitmap scaled down (never up) so its long side is at most [maxSide]. */
    fun downscale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val factor = max(bitmap.width, bitmap.height).toDouble() / maxSide
        if (factor <= 1.0) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width / factor).roundToInt()),
            max(1, (bitmap.height / factor).roundToInt()),
            true
        )
    }

    /** Rotates by the camera's [degrees] (0, 90, 180, 270) so the bitmap is upright. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * The Y plane of a camera analysis frame as an upright [LumaImage], downsampled by [step] (1 = full size)
     * to keep live analysis cheap.
     */
    fun lumaOf(frame: ImageProxy, step: Int): LumaImage {
        val plane = frame.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val crop = frame.cropRect
        val w = crop.width() / step
        val h = crop.height() / step
        val data = IntArray(w * h)
        for (y in 0 until h) {
            val row = (crop.top + y * step) * rowStride
            for (x in 0 until w) {
                data[y * w + x] = buffer.get(row + (crop.left + x * step) * pixelStride).toInt() and 0xFF
            }
        }
        return rotate(LumaImage(w, h, data), frame.imageInfo.rotationDegrees)
    }

    internal fun rotate(image: LumaImage, degrees: Int): LumaImage {
        val w = image.width
        val h = image.height
        return when ((degrees % 360 + 360) % 360) {
            90 -> LumaImage(h, w, IntArray(w * h) { i -> image[i / h, h - 1 - i % h] })
            180 -> LumaImage(w, h, IntArray(w * h) { i -> image.data[w * h - 1 - i] })
            270 -> LumaImage(h, w, IntArray(w * h) { i -> image[w - 1 - i / h, i % h] })
            else -> image
        }
    }
}
