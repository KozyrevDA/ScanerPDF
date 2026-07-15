package ru.aiscanner.docs.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.Document
import ru.aiscanner.docs.domain.model.DocumentPage
import ru.aiscanner.docs.domain.model.DocumentWithPages
import ru.aiscanner.docs.domain.model.ExtractedDocumentData
import ru.aiscanner.docs.domain.model.AppSettings
import ru.aiscanner.docs.domain.model.ExportedFile
import ru.aiscanner.docs.domain.model.OcrProgress
import ru.aiscanner.docs.domain.model.PdfExportOptions
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.model.ThemeMode

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(documentId: String): Flow<DocumentWithPages?>
    suspend fun getDocument(documentId: String): DocumentWithPages?
    suspend fun createDocument(name: String): Document
    suspend fun renameDocument(documentId: String, newName: String)
    suspend fun deleteDocument(documentId: String)
    suspend fun deleteAllDocuments()

    suspend fun addPage(documentId: String, originalPath: String, initialCrop: CropCorners?): DocumentPage
    suspend fun getPage(pageId: String): DocumentPage?
    suspend fun updatePage(page: DocumentPage)
    suspend fun deletePage(pageId: String)
    suspend fun reorderPages(documentId: String, orderedPageIds: List<String>)

    /** Директория для сохранения нового оригинала страницы. */
    suspend fun newOriginalFilePath(documentId: String): String
}

interface ImageProcessingRepository {
    suspend fun detectCorners(imagePath: String): AppResult<ru.aiscanner.docs.domain.model.CornerDetectionResult>

    /** Рендер страницы по её неразрушающим параметрам в processed/preview файлы. */
    suspend fun renderPage(page: DocumentPage): AppResult<DocumentPage>
}

interface OcrRepository {
    val isEngineAvailable: Boolean
    fun recognizeDocument(documentId: String, language: String): Flow<AppResult<OcrProgress>>
}

interface AiRepository {
    suspend fun summarize(documentId: String, text: String, language: String): AppResult<AiSummary>
    suspend fun extractData(documentId: String, text: String, language: String): AppResult<ExtractedDocumentData>
    suspend fun analyzeContract(documentId: String, text: String, language: String): AppResult<ContractAnalysis>
}

interface ExportRepository {
    suspend fun exportToPdf(documentId: String, options: PdfExportOptions): AppResult<ExportedFile>
    suspend fun exportPagesToJpg(documentId: String): AppResult<List<ExportedFile>>
    suspend fun exportTextToTxt(documentId: String, text: String): AppResult<ExportedFile>
    suspend fun shareFile(file: ExportedFile): AppResult<Unit>
}

interface SubscriptionRepository {
    val subscriptionStatus: Flow<SubscriptionStatus>
    suspend fun loadProducts(): List<SubscriptionProduct>
    suspend fun purchase(productId: String): PurchaseResult
    suspend fun restorePurchases(): RestoreResult
    suspend fun refreshSubscriptionStatus()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoDetectCorners(enabled: Boolean)
    suspend fun setAiConsentGiven(given: Boolean)
    suspend fun incrementOcrOperations()
    suspend fun incrementAiOperations()
}

/** Импорт изображения из галереи (SAF/Photo Picker) во внутреннее хранилище. */
interface ImageImporter {
    /** Копирует изображение по URI в директорию документа, возвращает путь к файлу. */
    suspend fun importImage(documentId: String, uriString: String): String
}
