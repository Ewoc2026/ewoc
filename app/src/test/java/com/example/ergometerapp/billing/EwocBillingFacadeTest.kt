package com.example.ergometerapp.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EwocBillingFacadeTest {
    @Test
    fun defaultBillingStateIsEntitledForFreeRelease() {
        assertTrue(EwocBillingUiState().isEntitled)
    }

    @Test
    fun selectPreferredOfferPrefersTrialWhenAvailable() {
        val selected = selectPreferredOffer(
            listOf(
                EwocBillingOfferCandidate(
                    offerToken = "no_trial",
                    displayPrice = "EUR 9.99",
                    hasFreeTrial = false,
                ),
                EwocBillingOfferCandidate(
                    offerToken = "trial",
                    displayPrice = "EUR 9.99",
                    hasFreeTrial = true,
                ),
            ),
        )

        assertEquals("trial", selected?.offerToken)
    }

    @Test
    fun selectPreferredOfferReturnsNullWhenNoOffersExist() {
        assertNull(selectPreferredOffer(emptyList()))
    }

    @Test
    fun resolveEntitlementSnapshotAlwaysKeepsFreeReleaseEntitled() {
        val entitlement = resolveEntitlementSnapshot(
            listOf(
                EwocPurchaseSnapshot(
                    productIds = listOf(EWOC_PRO_PRODUCT_ID),
                    purchaseState = EwocPurchaseState.PURCHASED,
                    isSuspended = false,
                ),
            ),
        )

        assertTrue(entitlement.isEntitled)
        assertTrue(!entitlement.hasPendingPurchase)
        assertTrue(!entitlement.hasSuspendedPurchase)
    }

    @Test
    fun resolveEntitlementSnapshotIgnoresPendingPurchasesForFreeRelease() {
        val entitlement = resolveEntitlementSnapshot(
            listOf(
                EwocPurchaseSnapshot(
                    productIds = listOf(EWOC_PRO_PRODUCT_ID),
                    purchaseState = EwocPurchaseState.PENDING,
                    isSuspended = false,
                ),
            ),
        )

        assertTrue(entitlement.isEntitled)
        assertTrue(!entitlement.hasPendingPurchase)
    }
}
