package ru.aiscanner.docs.presentation.ocr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.model.OcrProgress
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ExportRepository
import ru.aiscanner.docs.domain.repository.SettingsRepository
import ru.aiscanner.docs.domain.usecase.RecognizeDocumentTextUseCase

data class OcrPageText(val pageId: String, val pageNumber: Int, val text: String)

data class OcrUiState(
    val documentId: String = "",
    val pages: List<OcrPageText> = emptyList(),
    val isRecognizing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val error: AppError? = null,
) {
    val fullText: String get() = pages.joinToString("\n\n") { it.text }
}

class OcrViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val recognizeText: RecognizeDocumentTextUseCase,
    private val settings: SettingsRepository,
    private val export: ExportRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val _state = MutableStateFlow(OcrUiState(documentId = documentId))
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    private var recognitionJob: Job? = null

    init {
        loadSavedText()
    }

    private fun loadSavedText() {
        viewModelScope.launch {
            val doc = documents.getDocument(documentId) ?: return@launch
            _state.update { current ->
                current.copy(
                    pages = doc.pages.sortedBy { it.position }.mapIndexed { index, page ->
                        OcrPageText(page.id, index + 1, page.recognizedText.orEmpty())
                    },
                )
            }
        }
    }

    fun onStartRecognition() {
        if (_state.value.isRecognizing) return
        recognitionJob = viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.OCR_STARTED)
            _state.update { it.copy(isRecognizing = true, error = null) }
            val language = settings.settings.first().ocrLanguage
            recognizeText(documentId, language).collect { result ->
                when (result) {
                    is AppResult.Success -> when (val progress = result.value) {
                        is OcrProgress.PageInProgress -> _state.update {
                            it.copy(progressCurrent = progress.current, progressTotal = progress.total)
                        }
                        is OcrProgress.Completed -> {
                            analytics.logEvent(AnalyticsEvent.OCR_COMPLETED)
                            _state.update { current ->
                                current.copy(
                                    isRecognizing = false,
                                    pages = progress.result.pages.map {
                                        OcrPageText(it.pageId, it.pageNumber, it.text)
                                    },
                                )
                            }
                        }
                    }
                    is AppResult.Failure -> {
                        analytics.logEvent(AnalyticsEvent.OCR_FAILED)
                        _state.update { it.copy(isRecognizing = false, error = result.error) }
                    }
                }
            }
            _state.update { it.copy(isRecognizing = false) }
        }
    }

    /** Отмена распознавания пользователем (п. 5.6 ТЗ). */
    fun onCancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.update { it.copy(isRecognizing = false) }
    }

    fun onTextEdited(pageId: String, newText: String) {
        _state.update { current ->
            current.copy(
                pages = current.pages.map { if (it.pageId == pageId) it.copy(text = newText) else it },
            )
        }
        viewModelScope.launch {
            val page = documents.getPage(pageId) ?: return@launch
            documents.updatePage(page.copy(recognizedText = newText))
        }
    }

    fun onExportTxt() {
        viewModelScope.launch {
            when (val result = export.exportTextToTxt(documentId, _state.value.fullText)) {
                is AppResult.Success -> export.shareFile(result.value)
                is AppResult.Failure -> _state.update { it.copy(error = result.error) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
