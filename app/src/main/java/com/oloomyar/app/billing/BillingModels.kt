package com.oloomyar.app.billing

import java.util.Locale

data class StoreProduct(
    val sku: String,
    val title: String,
    val priceToman: Int,
) {
    val displayPrice: String get() = formatToman(priceToman)
    val priceRial: Int get() = priceToman * 10
}

private fun formatToman(amount: Int): String =
    String.format(Locale.US, "%,d", amount).map { character ->
        when (character) {
            in '0'..'9' -> "۰۱۲۳۴۵۶۷۸۹"[character - '0']
            ',' -> '٬'
            else -> character
        }
    }.joinToString("") + " تومان"

object BazaarProducts {
    const val COMPLETE = "oloomyar_g7_complete"
    const val COMPLETE_PRICE_TOMAN = 195_000
    val completeProduct = StoreProduct(COMPLETE, "همهٔ فصل‌های علوم هفتم", COMPLETE_PRICE_TOMAN)
    val allSkus = setOf(COMPLETE)
    fun isActive(sku: String, purchasedSkus: Set<String>): Boolean = sku == COMPLETE && COMPLETE in purchasedSkus
}

enum class BillingConnectionState {
    CONFIGURATION_REQUIRED,
    CONNECTING,
    READY,
    UNAVAILABLE,
}

data class BillingUiState(
    val connectionState: BillingConnectionState = BillingConnectionState.CONFIGURATION_REQUIRED,
    val purchasedSkus: Set<String> = emptySet(),
    val busySku: String? = null,
    val restoring: Boolean = false,
    val message: String? = null,
    val inventoryChecked: Boolean = false,
    val warning: Boolean = false,
) {
    val canPurchase: Boolean get() = connectionState == BillingConnectionState.READY &&
        inventoryChecked && busySku == null && !restoring
    val canRestore: Boolean get() = connectionState != BillingConnectionState.CONNECTING &&
        busySku == null && !restoring
}

object ChapterAccessPolicy {
    fun isUnlocked(destinationId: Int, purchasedSkus: Set<String>): Boolean =
        destinationId in 1..15 && (destinationId == 1 || BazaarProducts.COMPLETE in purchasedSkus)
}
