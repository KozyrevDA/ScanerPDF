package ru.aiscanner.docs.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

/**
 * Клиент backend-прокси (п. 9 ТЗ). API-ключ AI-провайдера в APK не хранится —
 * авторизация выполняется на стороне прокси. POST-запросы не ретраятся
 * автоматически (не идемпотентны); таймауты настроены в HttpClient;
 * отмена — стандартная отмена корутины Ktor.
 */
class KtorAiDocumentService(
    private val client: HttpClient,
    private val baseUrl: String,
) : AiDocumentService {

    override suspend fun summarize(text: String, language: String): AiSummary =
        AiResponseParser.parseSummary(post("v1/summarize", text, language)).getOrThrow()

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData =
        AiResponseParser.parseExtraction(post("v1/extract", text, language)).getOrThrow()

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis =
        AiResponseParser.parseContract(post("v1/contract", text, language)).getOrThrow()

    private suspend fun post(path: String, text: String, language: String): String {
        if (text.length > AI_MAX_TEXT_CHARS) throw DocumentTooLargeException()
        return client.post("$baseUrl/$path") {
            contentType(ContentType.Application.Json)
            setBody(AiTextRequestDto(text = text, language = language))
        }.bodyAsText()
    }
}
