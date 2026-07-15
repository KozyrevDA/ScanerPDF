package ru.aiscanner.docs.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.usecase.DeleteDocumentUseCase
import ru.aiscanner.docs.domain.usecase.GetDocumentsUseCase
import ru.aiscanner.docs.domain.usecase.RenameDocumentUseCase
import ru.aiscanner.docs.fakes.FakeDocumentRepository
import ru.aiscanner.docs.presentation.home.HomeUiEffect
import ru.aiscanner.docs.presentation.home.HomeViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeDocumentRepository()
    private val analyticsEvents = mutableListOf<AnalyticsEvent>()
    private val analytics = Analytics { event -> analyticsEvents.add(event) }

    private fun createViewModel() = HomeViewModel(
        getDocuments = GetDocumentsUseCase(repo),
        renameDocument = RenameDocumentUseCase(repo),
        deleteDocument = DeleteDocumentUseCase(repo),
        analytics = analytics,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state exposes documents from repository`() = runTest(dispatcher) {
        repo.createDocument("Первый")
        repo.createDocument("Второй")
        val viewModel = createViewModel()

        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.documents.size)
        assertEquals(false, state.isLoading)
        collectJob.cancel()
    }

    @Test
    fun `scan click emits camera effect and logs analytics`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onScanClick()
        dispatcher.scheduler.advanceUntilIdle()

        val effect = viewModel.effects
        val collected = mutableListOf<HomeUiEffect>()
        val job = launch { effect.collect { collected.add(it); cancel() } }
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.first() is HomeUiEffect.OpenCamera)
        assertTrue(analyticsEvents.contains(AnalyticsEvent.SCAN_STARTED))
    }

    @Test
    fun `delete removes document from state`() = runTest(dispatcher) {
        val doc = repo.createDocument("Удаляемый")
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDelete(doc.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.documents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `rename updates document name`() = runTest(dispatcher) {
        val doc = repo.createDocument("Старое имя")
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRename(doc.id, "Новое имя")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Новое имя", viewModel.state.value.documents.first().name)
        collectJob.cancel()
    }
}
