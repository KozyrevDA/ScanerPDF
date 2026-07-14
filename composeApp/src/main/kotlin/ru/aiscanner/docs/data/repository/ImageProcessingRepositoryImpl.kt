package ru.aiscanner.docs.data.repository

import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.core.DispatchersProvider
import ru.aiscanner.docs.data.files.DocumentFileStore
import ru.aiscanner.docs.data.imageprocessing.DocumentImageProcessor
import ru.aiscanner.docs.data.imageprocessing.SourceImage
import ru.aiscanner.docs.domain.model.CornerDetectionResult
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.DocumentPage
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ImageProcessingRepository
import java.io.FileOutputStream

/**
 * Рендер страницы по неразрушающим параметрам: оригинал → перспектива →
 * поворот → фильтр → processed.jpg + preview.webp. Новый JPEG создаётся
 * только при сохранении, не при движении ползунков (п. 5.4 ТЗ).
 */
class ImageProcessingRepositoryImpl(
    private val processor: DocumentImageProcessor,
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
    private val dispatchers: DispatchersProvider,
) : ImageProcessingRepository {

    override suspend fun detectCorners(imagePath: String): AppResult<CornerDetectionResult> =
        try {
            AppResult.Success(processor.detectDocumentCorners(SourceImage(imagePath)))
        } catch (e: OutOfMemoryError) {
            AppResult.Failure(AppError.OutOfMemory)
        } catch (e: Exception) {
            AppResult.Failure(AppError.CornersNotFound)
        }

    override suspend fun renderPage(page: DocumentPage): AppResult<DocumentPage> =
        withContext(dispatchers.default) {
            var cropped: Bitmap? = null
            var rotated: Bitmap? = null
            var filtered: Bitmap? = null
            var preview: Bitmap? = null
            try {
                val corners = page.crop ?: CropCorners.withInset(0f)
                cropped = processor.cropAndCorrectPerspective(SourceImage(page.originalPath), corners)
                rotated = processor.rotate(cropped, page.rotationDegrees)
                filtered = processor.applyFilter(rotated, page.filter, page.brightness, page.contrast)
                preview = processor.createPreview(filtered, PREVIEW_MAX_SIZE)

                val processedFile = files.processedFileFor(page.documentId, page.id)
                files.writeAtomically(processedFile) { tmp ->
                    FileOutputStream(tmp).use { filtered.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }
                val previewFile = files.previewFileFor(page.documentId, page.id)
                files.writeAtomically(previewFile) { tmp ->
                    FileOutputStream(tmp).use { preview.compress(Bitmap.CompressFormat.WEBP, 80, it) }
                }

                val updated = page.copy(
                    processedPath = processedFile.absolutePath,
                    previewPath = previewFile.absolutePath,
                )
                documents.updatePage(updated)
                AppResult.Success(updated)
            } catch (e: OutOfMemoryError) {
                AppResult.Failure(AppError.OutOfMemory)
            } catch (e: Exception) {
                AppResult.Failure(AppError.Unknown)
            } finally {
                // Освобождаем промежуточные Bitmap (могут совпадать при no-op шагах)
                setOf(cropped, rotated, filtered, preview).filterNotNull().forEach {
                    runCatching { it.recycle() }
                }
            }
        }

    private companion object {
        const val PREVIEW_MAX_SIZE = 480
    }
}
