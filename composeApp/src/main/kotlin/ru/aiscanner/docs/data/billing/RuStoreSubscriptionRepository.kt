package ru.aiscanner.docs.data.billing

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionPeriod
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.repository.SubscriptionRepository
import ru.rustore.sdk.billingclient.RuStoreBillingClient
import ru.rustore.sdk.billingclient.model.purchase.PaymentResult
import ru.rustore.sdk.billingclient.model.purchase.PurchaseState
import ru.rustore.sdk.core.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Пробрасывание deeplink-интентов оплаты в биллинг-клиент. */
interface BillingDeeplinkHandler {
    fun onNewIntent(intent: Intent)
}

/**
 * Реальная интеграция RuStore Billing (production DI).
 * Product ID приходят из конфигурации сборки, цены берутся из
 * `priceLabel` продуктов RuStore — плейсхолдеры пользователю не показываются.
 */
class RuStoreSubscriptionRepository(
    private val client: RuStoreBillingClient,
    private val monthlyProductId: String,
    private val yearlyProductId: String,
) : SubscriptionRepository, BillingDeeplinkHandler {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)

    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    private val productIds: List<String> get() = listOf(monthlyProductId, yearlyProductId)

    override fun onNewIntent(intent: Intent) {
        client.onNewIntent(intent)
    }

    override suspend fun loadProducts(): List<SubscriptionProduct> =
        client.products.getProducts(productIds).awaitResult().mapNotNull { product ->
            val price = product.priceLabel ?: return@mapNotNull null
            SubscriptionProduct(
                productId = product.productId,
                title = product.title ?: product.productId,
                price = price,
                period = if (product.productId == yearlyProductId) {
                    SubscriptionPeriod.YEARLY
                } else {
                    SubscriptionPeriod.MONTHLY
                },
            )
        }

    override suspend fun purchase(productId: String): PurchaseResult =
        try {
            when (val result = client.purchases.purchaseProduct(productId).awaitResult()) {
                is PaymentResult.Success -> {
                    runCatching {
                        client.purchases.confirmPurchase(result.purchaseId).awaitResult()
                    }
                    refreshSubscriptionStatus()
                    PurchaseResult.Success
                }
                is PaymentResult.Cancelled -> PurchaseResult.Cancelled
                is PaymentResult.Failure -> PurchaseResult.Error(null)
                else -> PurchaseResult.Error(null)
            }
        } catch (e: Exception) {
            PurchaseResult.Error(null)
        }

    override suspend fun restorePurchases(): RestoreResult =
        try {
            refreshSubscriptionStatus()
            RestoreResult.Success(restored = status.value is SubscriptionStatus.Premium)
        } catch (e: Exception) {
            RestoreResult.Error(null)
        }

    /** Проверка ранее купленной подписки (вызывается и при запуске приложения). */
    override suspend fun refreshSubscriptionStatus() {
        val purchases = try {
            client.purchases.getPurchases().awaitResult()
        } catch (e: Exception) {
            return
        }
        val hasActive = purchases.any { purchase ->
            purchase.productId in productIds &&
                purchase.purchaseState in setOf(PurchaseState.CONFIRMED, PurchaseState.PAID)
        }
        status.value = if (hasActive) SubscriptionStatus.Premium(null) else SubscriptionStatus.Free
    }
}

private suspend fun <T : Any> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
