package ru.aiscanner.docs.domain.usecase

import kotlinx.coroutines.flow.first
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.logic.FreePlanLimiter
import ru.aiscanner.docs.domain.model.ExportedFile
import ru.aiscanner.docs.domain.model.PdfExportOptions
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ExportRepository
import ru.aiscanner.docs.domain.repository.SubscriptionRepository

class ExportDocumentToPdfUseCase(
    private val documents: DocumentRepository,
    private val export: ExportRepository,
    private val subscriptions: SubscriptionRepository,
    private val limiter: FreePlanLimiter,
) {
    suspend operator fun invoke(documentId: String, options: PdfExportOptions): AppResult<ExportedFile> {
        val doc = documents.getDocument(documentId)
            ?: return AppResult.Failure(AppError.DocumentNotFound)
        val status = subscriptions.subscriptionStatus.first()
        if (!limiter.canExportPdf(status, doc.pages.size)) {
            return AppResult.Failure(AppError.FreeLimitReached(limiter.maxPagesPerPdf()))
        }
        return export.exportToPdf(documentId, options)
    }
}

class ExportPageToJpgUseCase(private val export: ExportRepository) {
    suspend operator fun invoke(documentId: String): AppResult<List<ExportedFile>> =
        export.exportPagesToJpg(documentId)
}

class ShareDocumentUseCase(private val export: ExportRepository) {
    suspend operator fun invoke(file: ExportedFile): AppResult<Unit> = export.shareFile(file)
}
