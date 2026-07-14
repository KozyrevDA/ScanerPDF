package ru.aiscanner.docs.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

/**
 * Клиент backend-прокси. POST-запросы не ретраятся автоматически
 * (не идемпотентны, п. 9 ТЗ); таймауты настроены в HttpClient;
 * отмена — стандартная отмена корутины.
 */
class KtorAiDocumentService(
    private val client: HttpClient,
    private val baseUrl: String,
) : AiDocumentService {

    override suspend fun summarize(text: String, language: String): AiSummary =
        post("v1/summarize", text, language).let { AiResponseParser.json.decodeFromString<AiSummaryDto>(it).toDomain() }

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData =
        post("v1/extract", text, language).let { AiResponseParser.json.decodeFromString<ExtractedDataDto>(it).toDomain() }

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis =
        post("v1/contract", text, language).let { AiResponseParser.json.decodeFromString<ContractAnalysisDto>(it).toDomain() }

    private suspend fun post(path: String, text: String, language: String): String {
        if (text.length > AI_MAX_TEXT_CHARS) throw DocumentTooLargeException()
        return client.post("$baseUrl/$path") {
            contentType(ContentType.Application.Json)
            setBody(AiTextRequestDto(text = text, language = language))
        }.body()
    }
}
