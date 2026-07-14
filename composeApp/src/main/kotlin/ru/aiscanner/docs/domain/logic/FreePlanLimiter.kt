package ru.aiscanner.docs.domain.logic

import ru.aiscanner.docs.domain.model.FreePlanLimits
import ru.aiscanner.docs.domain.model.SubscriptionStatus

/**
 * Применение ограничений бесплатной версии (п. 11 ТЗ).
 * Обычное сканирование, коррекция границ и экспорт JPG — бесплатны всегда.
 */
class FreePlanLimiter(private val limits: FreePlanLimits) {

    fun canExportPdf(status: SubscriptionStatus, pageCount: Int): Boolean =
        status is SubscriptionStatus.Premium || pageCount <= limits.maxPagesPerPdf

    fun canRunOcr(status: SubscriptionStatus, usedOcrOperations: Int): Boolean =
        status is SubscriptionStatus.Premium || usedOcrOperations < limits.freeOcrOperations

    fun canRunAi(status: SubscriptionStatus, usedAiOperations: Int): Boolean =
        status is SubscriptionStatus.Premium || usedAiOperations < limits.freeAiOperations

    fun maxPagesPerPdf(): Int = limits.maxPagesPerPdf
}
