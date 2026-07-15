package ru.aiscanner.docs.data.ai

import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

/** Бросается, когда backend-прокси не сконфигурирован (AI_BASE_URL пуст). */
class AiNotConfiguredException : Exception()

/**
 * Реализация для сборок без настроенного backend-прокси: AI-функции
 * отключены с понятным сообщением. Фиктивные даты, суммы и реквизиты
 * пользователю не показываются.
 */
class DisabledAiDocumentService : AiDocumentService {
    override suspend fun summarize(text: String, language: String): AiSummary =
        throw AiNotConfiguredException()

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData =
        throw AiNotConfiguredException()

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis =
        throw AiNotConfiguredException()
}
