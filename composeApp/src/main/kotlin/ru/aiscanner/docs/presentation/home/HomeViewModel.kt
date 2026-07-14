package ru.aiscanner.docs.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.model.Document
import ru.aiscanner.docs.domain.usecase.DeleteDocumentUseCase
import ru.aiscanner.docs.domain.usecase.GetDocumentsUseCase
import ru.aiscanner.docs.domain.usecase.RenameDocumentUseCase

data class HomeUiState(
    val isLoading: Boolean = true,
    val documents: List<Document> = emptyList(),
)

sealed interface HomeUiEffect {
    data class OpenDocument(val documentId: String) : HomeUiEffect
    data object OpenCamera : HomeUiEffect
}

class HomeViewModel(
    getDocuments: GetDocumentsUseCase,
    private val renameDocument: RenameDocumentUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = getDocuments()
        .map { HomeUiState(isLoading = false, documents = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _effects = Channel<HomeUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onScanClick() {
        analytics.logEvent(AnalyticsEvent.SCAN_STARTED)
        viewModelScope.launch { _effects.send(HomeUiEffect.OpenCamera) }
    }

    fun onDocumentClick(documentId: String) {
        viewModelScope.launch { _effects.send(HomeUiEffect.OpenDocument(documentId)) }
    }

    fun onRename(documentId: String, newName: String) {
        viewModelScope.launch { renameDocument(documentId, newName) }
    }

    fun onDelete(documentId: String) {
        viewModelScope.launch { deleteDocument(documentId) }
    }
}
