package com.oloomyar.app.billing

import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.entity.PurchaseState

internal object BazaarPurchasePolicy {
    // The receipt verifier checks RSA before delivering these records.
    // An authentic record must still be an active purchase for this app and SKU.
    fun isEligible(purchase: PurchaseInfo, packageName: String): Boolean =
        purchase.purchaseState == PurchaseState.PURCHASED &&
            purchase.packageName == packageName &&
            purchase.productId in BazaarProducts.allSkus &&
            purchase.purchaseToken.isNotBlank() && purchase.purchaseTime > 0

    fun restoredSkus(purchases: List<PurchaseInfo>, packageName: String): Set<String> =
        restoredPurchases(purchases, packageName).mapTo(mutableSetOf()) { it.productId }

    fun restoredPurchases(purchases: List<PurchaseInfo>, packageName: String): List<PurchaseInfo> =
        purchases.filter { it.packageName == packageName && it.productId in BazaarProducts.allSkus }
            .groupBy { it.productId }.values.mapNotNull { records ->
                // A refund wins a conflicting record at the same transaction time.
                records.maxWithOrNull(compareBy<PurchaseInfo> { it.purchaseTime }
                    .thenBy { it.purchaseState == PurchaseState.REFUNDED })
                    ?.takeIf { isEligible(it, packageName) }
            }
}

