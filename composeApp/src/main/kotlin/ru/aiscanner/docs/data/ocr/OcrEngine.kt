package ru.aiscanner.docs.data.ocr

/**
 * Интерфейс офлайн OCR-движка (п. 3 ТЗ). Позволяет подключить PaddleOCR
 * или Tesseract без переделки остального приложения.
 */
interface OcrEngine {
    val isAvailable: Boolean
    val supportedLanguages: Set<String>

    /** Распознаёт текст изображения. Бросает исключение при ошибке движка. */
    suspend fun recognize(imagePath: String, language: String): String
}

/** Временная заглушка до интеграции PaddleOCR (допущено ТЗ, п. 3). */
class StubOcrEngine : OcrEngine {
    override val isAvailable: Boolean = false
    override val supportedLanguages: Set<String> = emptySet()

    override suspend fun recognize(imagePath: String, language: String): String {
        throw UnsupportedOperationException("OCR-движок не подключён")
    }
}
