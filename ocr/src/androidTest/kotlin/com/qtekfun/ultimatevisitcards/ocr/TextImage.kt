package com.qtekfun.ultimatevisitcards.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File

/** A generated card image with known text, for the engines' smoke tests (no real cards in the repo). */
object TextImage {
    val LINES = listOf("ULTIMATE MARINE S.L.", "Ana Ruiz", "+34 600 123 456")

    fun write(dir: File): File {
        val bitmap = Bitmap.createBitmap(1200, 776, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
            textSize = 64f
        }
        LINES.forEachIndexed { i, line -> canvas.drawText(line, 80f, 200f + i * 160f, paint) }
        return File(dir, "smoke.jpg").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
    }
}
