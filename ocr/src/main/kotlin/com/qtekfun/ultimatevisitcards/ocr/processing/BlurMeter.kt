package com.qtekfun.ultimatevisitcards.ocr.processing

/**
 * Sharpness as the variance of the Laplacian: low values mean a blurry frame («Move the phone, the image is
 * blurry»). Measured on a downscaled image so the value does not depend on the camera resolution.
 */
object BlurMeter {
    const val WORK_SIZE = 640

    /** Below this the frame is reported as blurry. Conservative on purpose: a false alarm is worse than none. */
    const val BLURRY_BELOW = 40.0

    fun sharpness(image: LumaImage): Double {
        val small = image.downscale(WORK_SIZE)
        val w = small.width
        val h = small.height
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = (small.data[i - 1] + small.data[i + 1] + small.data[i - w] + small.data[i + w] - 4 * small.data[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    fun isBlurry(image: LumaImage): Boolean = sharpness(image) < BLURRY_BELOW
}
