package ru.aiscanner.docs.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.core.DispatchersProvider
import ru.aiscanner.docs.data.ocr.OcrEngine
import ru.aiscanner.docs.domain.model.OcrProgress
import ru.aiscanner.docs.domain.model.RecognizedPage
import ru.aiscanner.docs.domain.model.RecognizedText
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.OcrRepository

/**
 * Постраничное распознавание с прогрессом и поддержкой отмены
 * (отмена — через отмену коллектора Flow, п. 5.6 ТЗ).
 */
class OcrRepositoryImpl(
    private val engine: OcrEngine,
    private val documents: DocumentRepository,
    private val dispatchers: DispatchersProvider,
) : OcrRepository {

    override val isEngineAvailable: Boolean get() = engine.isAvailable

    override fun recognizeDocument(documentId: String, language: String): Flow<AppResult<OcrProgress>> =
        flow {
            if (!engine.isAvailable) {
                emit(AppResult.Failure(AppError.OcrEngineUnavailable))
                return@flow
            }
            val doc = documents.getDocument(documentId)
            if (doc == null) {
                emit(AppResult.Failure(AppError.DocumentNotFound))
                return@flow
            }
            val pages = doc.pages.sortedBy { it.position }
            val recognized = mutableListOf<RecognizedPage>()
            pages.forEachIndexed { index, page ->
                emit(AppResult.Success(OcrProgress.PageInProgress(index + 1, pages.size)))
                val imagePath = page.processedPath ?: page.originalPath
                val text = try {
                    engine.recognize(imagePath, language)
                } catch (e: Exception) {
                    emit(AppResult.Failure(AppError.OcrEmpty))
                    return@flow
                }
                documents.updatePage(page.copy(recognizedText = text))
                recognized += RecognizedPage(page.id, index + 1, text)
            }
            if (recognized.all { it.text.isBlank() }) {
                emit(AppResult.Failure(AppError.OcrEmpty))
            } else {
                emit(AppResult.Success(OcrProgress.Completed(RecognizedText(documentId, recognized))))
            }
        }.flowOn(dispatchers.default)
}
