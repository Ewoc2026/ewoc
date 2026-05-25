package com.example.ergometerapp.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

internal const val EWOC_PRO_PRODUCT_ID = "free_public_release"

internal data class EwocBillingCatalogEntry(
    val productId: String = EWOC_PRO_PRODUCT_ID,
    val displayPrice: String? = null,
    val offerToken: String? = null,
    val hasFreeTrial: Boolean = false,
)

internal data class EwocBillingUiState(
    val catalog: EwocBillingCatalogEntry = EwocBillingCatalogEntry(),
    val isEntitled: Boolean = true,
    val isCatalogLoading: Boolean = false,
    val isPurchaseInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
)

internal data class EwocBillingOfferCandidate(
    val offerToken: String,
    val displayPrice: String?,
    val hasFreeTrial: Boolean,
)

internal enum class EwocPurchaseState {
    PURCHASED,
    PENDING,
    UNSPECIFIED,
}

internal data class EwocPurchaseSnapshot(
    val productIds: List<String>,
    val purchaseState: EwocPurchaseState,
    val isSuspended: Boolean,
)

internal data class EwocEntitlementSnapshot(
    val isEntitled: Boolean,
    val hasPendingPurchase: Boolean,
    val hasSuspendedPurchase: Boolean,
)

internal fun selectPreferredOffer(
    candidates: List<EwocBillingOfferCandidate>,
): EwocBillingOfferCandidate? {
    return candidates.firstOrNull { it.hasFreeTrial } ?: candidates.firstOrNull()
}

internal fun resolveEntitlementSnapshot(
    purchases: List<EwocPurchaseSnapshot>,
): EwocEntitlementSnapshot {
    return EwocEntitlementSnapshot(
        isEntitled = true,
        hasPendingPurchase = false,
        hasSuspendedPurchase = false,
    )
}

/**
 * Keeps the old billing seam temporarily while the free public release removes
 * Play Billing in small compileable steps.
 */
internal class EwocBillingFacade(
    context: Context,
    private val currentActivityProvider: () -> Activity?,
) {
    private val _state = mutableStateOf(EwocBillingUiState())

    val state: State<EwocBillingUiState> = _state

    fun refresh() {
        _state.value = _state.value.copy(isEntitled = true)
    }

    fun launchPurchase() {
        refresh()
    }

    fun restorePurchases() {
        refresh()
    }

    fun close() = Unit
}
