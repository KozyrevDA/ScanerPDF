package ru.aiscanner.docs.data.imageprocessing

import ru.aiscanner.docs.domain.model.CornerDetectionResult
import ru.aiscanner.docs.domain.model.CropCorners

/**
 * Fallback-детектор: если надёжный контур не найден (или OpenCV ещё
 * не подключён), возвращаем углы с отступом от краёв и detected=false —
 * пользователь корректирует вручную (п. 6 ТЗ).
 */
class FallbackCornerDetector : DocumentCornerDetector {
    override suspend fun detect(image: SourceImage): CornerDetectionResult =
        CornerDetectionResult(
            corners = CropCorners.withInset(),
            detected = false,
            confidence = 0f,
        )
}
