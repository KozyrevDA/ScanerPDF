package ru.aiscanner.docs.domain.usecase

import kotlinx.coroutines.CancellationException
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.model.DocumentPage
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ImageImporter
import ru.aiscanner.docs.domain.repository.ImageProcessingRepository

/**
 * Импорт изображения из галереи: при необходимости создаёт документ,
 * копирует файл, запускает автодетект границ и добавляет страницу.
 */
class ImportImageToDocumentUseCase(
    private val documents: DocumentRepository,
    private val importer: ImageImporter,
    private val imageProcessing: ImageProcessingRepository,
) {
    suspend operator fun invoke(
        documentId: String?,
        uriString: String,
        newDocumentName: String,
    ): AppResult<DocumentPage> = try {
        val targetDocumentId = documentId ?: documents.createDocument(newDocumentName).id
        val path = importer.importImage(targetDocumentId, uriString)
        val corners = (imageProcessing.detectCorners(path) as? AppResult.Success)?.value?.corners
        AppResult.Success(documents.addPage(targetDocumentId, path, corners))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}
