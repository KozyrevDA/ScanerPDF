package ru.aiscanner.docs.presentation.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.repository.SubscriptionRepository

data class PremiumUiState(
    val isLoading: Boolean = true,
    val products: List<SubscriptionProduct> = emptyList(),
    val isPremium: Boolean = false,
    val isPurchasing: Boolean = false,
    val message: PremiumMessage? = null,
)

enum class PremiumMessage { PURCHASE_SUCCESS, PURCHASE_ERROR, RESTORED, NOT_RESTORED, PRODUCTS_UNAVAILABLE }

class PremiumViewModel(
    private val subscriptions: SubscriptionRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(PremiumUiState())
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    init {
        analytics.logEvent(AnalyticsEvent.PAYWALL_SHOWN)
        viewModelScope.launch {
            subscriptions.subscriptionStatus.collect { status ->
                _state.update { it.copy(isPremium = status is SubscriptionStatus.Premium) }
            }
        }
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            val products = runCatching { subscriptions.loadProducts() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    isLoading = false,
                    products = products,
                    message = if (products.isEmpty()) PremiumMessage.PRODUCTS_UNAVAILABLE else it.message,
                )
            }
        }
    }

    fun onPurchase(productId: String) {
        if (_state.value.isPurchasing) return
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.PURCHASE_STARTED)
            _state.update { it.copy(isPurchasing = true) }
            val message = when (subscriptions.purchase(productId)) {
                is PurchaseResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.PURCHASE_COMPLETED)
                    PremiumMessage.PURCHASE_SUCCESS
                }
                is PurchaseResult.Cancelled -> null
                is PurchaseResult.Error -> {
                    analytics.logEvent(AnalyticsEvent.PURCHASE_FAILED)
                    PremiumMessage.PURCHASE_ERROR
                }
            }
            _state.update { it.copy(isPurchasing = false, message = message) }
        }
    }

    fun onRestore() {
        viewModelScope.launch {
            val message = when (val result = subscriptions.restorePurchases()) {
                is RestoreResult.Success ->
                    if (result.restored) PremiumMessage.RESTORED else PremiumMessage.NOT_RESTORED
                is RestoreResult.Error -> PremiumMessage.PURCHASE_ERROR
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
