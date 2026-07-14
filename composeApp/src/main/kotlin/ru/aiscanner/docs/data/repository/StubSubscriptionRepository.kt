package ru.aiscanner.docs.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionPeriod
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.repository.SubscriptionRepository

/**
 * Заглушка до подключения RuStore Billing (Этап 8 ТЗ).
 * Интерфейс совпадает с боевой реализацией — замена не потребует
 * изменений в остальном приложении.
 */
class StubSubscriptionRepository : SubscriptionRepository {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)

    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    override suspend fun loadProducts(): List<SubscriptionProduct> = listOf(
        SubscriptionProduct("premium_monthly", "Месячная подписка", "—", SubscriptionPeriod.MONTHLY),
        SubscriptionProduct("premium_yearly", "Годовая подписка", "—", SubscriptionPeriod.YEARLY),
    )

    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Error("Покупки будут доступны после публикации в RuStore")

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.Success(restored = false)

    override suspend fun refreshSubscriptionStatus() = Unit
}
