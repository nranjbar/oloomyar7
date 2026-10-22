package com.oloomyar.app.billing

internal data class BazaarInventoryPage(
    val receipts: List<SignedBazaarReceipt>,
    val continuation: String?,
)

internal object BazaarInventoryPager {
    // Completion belongs to the entire inventory, never to an individual page.
    fun collect(fetch: (String?) -> BazaarInventoryPage): List<SignedBazaarReceipt> {
        val result = mutableListOf<SignedBazaarReceipt>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        repeat(100) {
            val page = fetch(token)
            result += page.receipts
            token = page.continuation?.takeIf(String::isNotBlank)
            if (token == null) return result.distinct()
            if (!seen.add(requireNotNull(token))) throw InvalidBazaarInventory()
        }
        throw InvalidBazaarInventory()
    }
}

