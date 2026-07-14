package ru.aiscanner.docs.data.ai

import kotlinx.serialization.json.Json
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

/** Парсинг JSON-ответов backend-прокси. Выделен для unit-тестов (п. 17 ТЗ). */
object AiResponseParser {

    val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parseSummary(raw: String): Result<AiSummary> =
        runCatching { json.decodeFromString<AiSummaryDto>(raw).toDomain() }

    fun parseExtraction(raw: String): Result<ExtractedDocumentData> =
        runCatching { json.decodeFromString<ExtractedDataDto>(raw).toDomain() }

    fun parseContract(raw: String): Result<ContractAnalysis> =
        runCatching { json.decodeFromString<ContractAnalysisDto>(raw).toDomain() }
}
