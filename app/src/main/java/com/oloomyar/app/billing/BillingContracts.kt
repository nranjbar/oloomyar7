package com.oloomyar.app.billing

import ir.cafebazaar.poolakey.entity.PurchaseInfo

internal fun interface BillingCancellation { fun cancel() }

internal interface BillingScheduler {
    fun now(): Long
    fun after(delayMillis: Long, block: () -> Unit): BillingCancellation
}

internal data class SignedBazaarReceipt(val json: String, val signature: String)
internal data class PendingBazaarPurchase(val sku: String, val payload: String, val createdAt: Long)
internal data class StoredBillingData(
    val receipts: List<SignedBazaarReceipt> = emptyList(),
    val pending: PendingBazaarPurchase? = null,
    val requiresRestore: Boolean = false,
)

internal interface BillingStorage {
    fun load(): StoredBillingData
    fun save(data: StoredBillingData): Boolean
}

internal interface BazaarReceiptVerifier {
    val configured: Boolean
    fun verify(receipt: SignedBazaarReceipt): PurchaseInfo
}

internal interface BazaarGateway {
    fun connect(connected: () -> Unit, failed: (Exception) -> Unit, onDisconnected: () -> Unit)
    fun purchase(
        sku: String, payload: String,
        succeeded: (SignedBazaarReceipt) -> Unit,
        canceled: () -> Unit, failed: (Exception) -> Unit,
    )
    fun inventory(
        succeeded: (List<SignedBazaarReceipt>) -> Unit,
        failed: (Exception) -> Unit,
    ): BillingCancellation
    fun close()
}

internal class InvalidBazaarReceipt : IllegalArgumentException("Invalid signed purchase record")
internal class InvalidBazaarInventory : IllegalStateException("Incomplete or malformed inventory")
internal class BazaarServiceResponse(val code: Int) : IllegalStateException("Billing response $code")

