package ru.aiscanner.docs.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.withContext
import ru.aiscanner.docs.core.DispatchersProvider
import ru.aiscanner.docs.domain.model.CornerDetectionResult
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.DocumentFilter
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Реализация на Android graphics. Perspective transform выполняется через
 * Matrix.setPolyToPoly (проективное преобразование по 4 точкам).
 * OpenCV-детектор подключается через [DocumentCornerDetector].
 */
class AndroidDocumentImageProcessor(
    private val cornerDetector: DocumentCornerDetector,
    private val dispatchers: DispatchersProvider,
) : DocumentImageProcessor {

    override suspend fun detectDocumentCorners(image: SourceImage): CornerDetectionResult =
        cornerDetector.detect(image)

    override suspend fun cropAndCorrectPerspective(
        image: SourceImage,
        corners: CropCorners,
    ): Bitmap = withContext(dispatchers.default) {
        val source = BitmapLoader.decodeSampled(image.path, MAX_PROCESSING_DIMENSION)
        try {
            val w = source.width.toFloat()
            val h = source.height.toFloat()
            val tl = corners.topLeft
            val tr = corners.topRight
            val br = corners.bottomRight
            val bl = corners.bottomLeft

            val topWidth = hypot((tr.x - tl.x) * w, (tr.y - tl.y) * h)
            val bottomWidth = hypot((br.x - bl.x) * w, (br.y - bl.y) * h)
            val leftHeight = hypot((bl.x - tl.x) * w, (bl.y - tl.y) * h)
            val rightHeight = hypot((br.x - tr.x) * w, (br.y - tr.y) * h)

            val dstWidth = ((topWidth + bottomWidth) / 2f).roundToInt().coerceAtLeast(32)
            val dstHeight = ((leftHeight + rightHeight) / 2f).roundToInt().coerceAtLeast(32)

            val src = floatArrayOf(
                tl.x * w, tl.y * h,
                tr.x * w, tr.y * h,
                br.x * w, br.y * h,
                bl.x * w, bl.y * h,
            )
            val dst = floatArrayOf(
                0f, 0f,
                dstWidth.toFloat(), 0f,
                dstWidth.toFloat(), dstHeight.toFloat(),
                0f, dstHeight.toFloat(),
            )
            val matrix = Matrix()
            check(matrix.setPolyToPoly(src, 0, dst, 0, 4)) { "Некорректное преобразование" }

            val result = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            result
        } finally {
            source.recycle()
        }
    }

    override suspend fun applyFilter(
        bitmap: Bitmap,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
    ): Bitmap = withContext(dispatchers.default) {
        val matrix = buildColorMatrix(filter, brightness, contrast)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        result
    }

    override suspend fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        withContext(dispatchers.default) {
            val normalized = ((degrees % 360) + 360) % 360
            if (normalized == 0) return@withContext bitmap
            val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    override suspend fun createPreview(bitmap: Bitmap, maxSize: Int): Bitmap =
        withContext(dispatchers.default) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale >= 1f) return@withContext bitmap
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }

    companion object {
        const val MAX_PROCESSING_DIMENSION = 3200

        /** Матрица цвета для фильтра + яркости (-1..1) + контраста (0.25..3). */
        fun buildColorMatrix(filter: DocumentFilter, brightness: Float, contrast: Float): ColorMatrix {
            val cm = ColorMatrix()
            when (filter) {
                DocumentFilter.ORIGINAL -> Unit
                DocumentFilter.COLOR -> {
                    cm.setSaturation(1.15f)
                    cm.postConcat(contrastMatrix(1.1f))
                }
                DocumentFilter.ENHANCE -> {
                    cm.postConcat(contrastMatrix(1.3f))
                    cm.postConcat(brightnessMatrix(0.06f))
                }
                DocumentFilter.GRAYSCALE -> cm.setSaturation(0f)
                DocumentFilter.BLACK_WHITE -> {
                    cm.setSaturation(0f)
                    cm.postConcat(contrastMatrix(2.4f))
                }
            }
            if (contrast != 1f) cm.postConcat(contrastMatrix(contrast))
            if (brightness != 0f) cm.postConcat(brightnessMatrix(brightness))
            return cm
        }

        private fun contrastMatrix(scale: Float): ColorMatrix {
            val translate = (-0.5f * scale + 0.5f) * 255f
            return ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }

        private fun brightnessMatrix(brightness: Float): ColorMatrix {
            val offset = brightness * 255f
            return ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, offset,
                    0f, 1f, 0f, 0f, offset,
                    0f, 0f, 1f, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
    }
}
