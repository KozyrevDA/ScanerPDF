package ru.aiscanner.docs.presentation.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ImageProcessingRepository
import ru.aiscanner.docs.domain.usecase.AddPageToDocumentUseCase
import ru.aiscanner.docs.domain.usecase.CreateDocumentUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CameraUiState(
    val documentId: String? = null,
    val pageCount: Int = 0,
    val torchEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)

sealed interface CameraUiEffect {
    data class OpenCrop(val pageId: String) : CameraUiEffect
    data class OpenDocument(val documentId: String) : CameraUiEffect
}

class CameraViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val createDocument: CreateDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
    private val imageProcessing: ImageProcessingRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CameraUiState(documentId = savedStateHandle.get<String>("documentId")),
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _effects = Channel<CameraUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        _state.value.documentId?.let { id ->
            viewModelScope.launch {
                val doc = documents.getDocument(id)
                _state.update { it.copy(pageCount = doc?.pages?.size ?: 0) }
            }
        }
    }

    /** Файл для следующего снимка; документ создаётся при первом кадре. */
    suspend fun prepareCaptureFilePath(): String {
        val documentId = ensureDocument()
        return documents.newOriginalFilePath(documentId)
    }

    fun toggleTorch() = _state.update { it.copy(torchEnabled = !it.torchEnabled) }

    fun onCaptureError() = _state.update { it.copy(error = AppError.CaptureFailed, isSaving = false) }

    fun onCameraError() = _state.update { it.copy(error = AppError.CameraUnavailable) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun onPhotoSaved(originalPath: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            analytics.logEvent(AnalyticsEvent.PHOTO_CAPTURED)
            try {
                val documentId = ensureDocument()
                val detection = imageProcessing.detectCorners(originalPath)
                val corners = (detection as? AppResult.Success)?.value?.corners
                val page = addPage(documentId, originalPath, corners)
                _state.update { it.copy(isSaving = false, pageCount = it.pageCount + 1) }
                _effects.send(CameraUiEffect.OpenCrop(page.id))
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = AppError.CaptureFailed) }
            }
        }
    }

    fun onOpenDocument() {
        val id = _state.value.documentId ?: return
        viewModelScope.launch { _effects.send(CameraUiEffect.OpenDocument(id)) }
    }

    private suspend fun ensureDocument(): String {
        _state.value.documentId?.let { return it }
        val name = "Скан " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val document = createDocument(name)
        analytics.logEvent(AnalyticsEvent.DOCUMENT_CREATED)
        _state.update { it.copy(documentId = document.id) }
        return document.id
    }
}
