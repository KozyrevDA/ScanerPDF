package ru.aiscanner.docs.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Загрузка больших изображений с учётом памяти (п. 6, 16 ТЗ):
 * поэтапный inSampleSize и повторные попытки при OutOfMemoryError.
 */
object BitmapLoader {

    fun decodeSampled(path: String, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать изображение" }

        var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        var attempts = 0
        while (true) {
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                return BitmapFactory.decodeFile(path, options)
                    ?: throw IllegalStateException("Не удалось декодировать изображение")
            } catch (e: OutOfMemoryError) {
                attempts++
                sampleSize *= 2
                if (attempts > 4) throw e
            }
        }
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (maxOf(w, h) > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize
    }
}
