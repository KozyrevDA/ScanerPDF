package ru.aiscanner.docs.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.aiscanner.docs.BuildConfig
import ru.aiscanner.docs.core.DefaultDispatchersProvider
import ru.aiscanner.docs.core.DispatchersProvider
import ru.aiscanner.docs.data.ai.AiDocumentService
import ru.aiscanner.docs.data.ai.AiResponseParser
import ru.aiscanner.docs.data.ai.MockAiDocumentService
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.DebugAnalytics
import ru.aiscanner.docs.data.db.AppDatabase
import ru.aiscanner.docs.data.export.PdfExporter
import ru.aiscanner.docs.data.files.DocumentFileStore
import ru.aiscanner.docs.data.imageprocessing.AndroidDocumentImageProcessor
import ru.aiscanner.docs.data.imageprocessing.DocumentCornerDetector
import ru.aiscanner.docs.data.imageprocessing.DocumentImageProcessor
import ru.aiscanner.docs.data.imageprocessing.FallbackCornerDetector
import ru.aiscanner.docs.data.ocr.OcrEngine
import ru.aiscanner.docs.data.ocr.StubOcrEngine
import ru.aiscanner.docs.data.repository.AiRepositoryImpl
import ru.aiscanner.docs.data.repository.DocumentRepositoryImpl
import ru.aiscanner.docs.data.repository.ExportRepositoryImpl
import ru.aiscanner.docs.data.repository.ImageProcessingRepositoryImpl
import ru.aiscanner.docs.data.repository.OcrRepositoryImpl
import ru.aiscanner.docs.data.repository.SettingsRepositoryImpl
import ru.aiscanner.docs.data.repository.StubSubscriptionRepository
import ru.aiscanner.docs.domain.logic.FreePlanLimiter
import ru.aiscanner.docs.domain.model.FreePlanLimits
import ru.aiscanner.docs.domain.repository.AiRepository
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ExportRepository
import ru.aiscanner.docs.domain.repository.ImageProcessingRepository
import ru.aiscanner.docs.domain.repository.OcrRepository
import ru.aiscanner.docs.domain.repository.SettingsRepository
import ru.aiscanner.docs.domain.repository.SubscriptionRepository
import ru.aiscanner.docs.domain.usecase.AddPageToDocumentUseCase
import ru.aiscanner.docs.domain.usecase.AiGate
import ru.aiscanner.docs.domain.usecase.AnalyzeContractUseCase
import ru.aiscanner.docs.domain.usecase.ApplyPageFilterUseCase
import ru.aiscanner.docs.domain.usecase.CreateDocumentUseCase
import ru.aiscanner.docs.domain.usecase.DeleteDocumentUseCase
import ru.aiscanner.docs.domain.usecase.DeletePageUseCase
import ru.aiscanner.docs.domain.usecase.ExportDocumentToPdfUseCase
import ru.aiscanner.docs.domain.usecase.ExportPageToJpgUseCase
import ru.aiscanner.docs.domain.usecase.ExtractDocumentDataUseCase
import ru.aiscanner.docs.domain.usecase.GetDocumentsUseCase
import ru.aiscanner.docs.domain.usecase.ObserveDocumentUseCase
import ru.aiscanner.docs.domain.usecase.RecognizeDocumentTextUseCase
import ru.aiscanner.docs.domain.usecase.RenameDocumentUseCase
import ru.aiscanner.docs.domain.usecase.ReorderPagesUseCase
import ru.aiscanner.docs.domain.usecase.ShareDocumentUseCase
import ru.aiscanner.docs.domain.usecase.SummarizeDocumentUseCase
import ru.aiscanner.docs.domain.usecase.UpdatePageCropUseCase
import ru.aiscanner.docs.presentation.ai.AiViewModel
import ru.aiscanner.docs.presentation.camera.CameraViewModel
import ru.aiscanner.docs.presentation.crop.CropViewModel
import ru.aiscanner.docs.presentation.document.DocumentViewModel
import ru.aiscanner.docs.presentation.editor.PageEditorViewModel
import ru.aiscanner.docs.presentation.home.HomeViewModel
import ru.aiscanner.docs.presentation.ocr.OcrViewModel
import ru.aiscanner.docs.presentation.settings.SettingsViewModel

val coreModule = module {
    single<DispatchersProvider> { DefaultDispatchersProvider() }
    single<Analytics> { DebugAnalytics() }
    single { FreePlanLimits.DEFAULT }
    single { FreePlanLimiter(get()) }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(AiResponseParser.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}

val dataModule = module {
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().documentDao() }
    single { DocumentFileStore(androidContext()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single<SubscriptionRepository> { StubSubscriptionRepository() }

    single<DocumentCornerDetector> { FallbackCornerDetector() }
    single<DocumentImageProcessor> { AndroidDocumentImageProcessor(get(), get()) }
    single<ImageProcessingRepository> { ImageProcessingRepositoryImpl(get(), get(), get(), get()) }

    single<OcrEngine> { StubOcrEngine() }
    single<OcrRepository> { OcrRepositoryImpl(get(), get(), get()) }

    // Для разработки используется mock (п. 9 ТЗ). Боевой клиент:
    // KtorAiDocumentService(get(), BuildConfig.AI_BASE_URL)
    single<AiDocumentService> { MockAiDocumentService() }
    single<AiRepository> { AiRepositoryImpl(get()) }

    single { PdfExporter() }
    single<ExportRepository> { ExportRepositoryImpl(androidContext(), get(), get(), get(), get()) }
}

val domainModule = module {
    factory { GetDocumentsUseCase(get()) }
    factory { ObserveDocumentUseCase(get()) }
    factory { CreateDocumentUseCase(get()) }
    factory { RenameDocumentUseCase(get()) }
    factory { DeleteDocumentUseCase(get()) }
    factory { AddPageToDocumentUseCase(get()) }
    factory { DeletePageUseCase(get()) }
    factory { ReorderPagesUseCase(get()) }
    factory { UpdatePageCropUseCase(get(), get()) }
    factory { ApplyPageFilterUseCase(get(), get()) }
    factory { ExportDocumentToPdfUseCase(get(), get(), get(), get()) }
    factory { ExportPageToJpgUseCase(get()) }
    factory { ShareDocumentUseCase(get()) }
    factory { RecognizeDocumentTextUseCase(get(), get(), get(), get()) }
    factory { AiGate(get(), get(), get()) }
    factory { SummarizeDocumentUseCase(get(), get()) }
    factory { ExtractDocumentDataUseCase(get(), get()) }
    factory { AnalyzeContractUseCase(get(), get()) }
}

val viewModelModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { CameraViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { CropViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PageEditorViewModel(get(), get(), get(), get()) }
    viewModel { DocumentViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { OcrViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { AiViewModel(get(), get(), get(), get(), get(), get(), get()) }
}

val appModules = listOf(coreModule, dataModule, domainModule, viewModelModule)
