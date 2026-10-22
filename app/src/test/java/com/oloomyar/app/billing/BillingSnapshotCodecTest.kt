package com.oloomyar.app.billing

import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class BillingSnapshotCodecTest {
    @Test fun signedReceiptsAndPendingPaymentRoundTripInOneSnapshot() {
        val data = StoredBillingData(listOf(BillingReceiptFixtures.receipt()),
            PendingBazaarPurchase(BazaarProducts.COMPLETE, "unique-checkout", 12345))
        assertEquals(data, BillingSnapshotCodec.decode(BillingSnapshotCodec.encode(data)))
    }
    @Test fun completedPaymentDoesNotLeaveAnOldPendingRecord() {
        val encoded = BillingSnapshotCodec.encode(StoredBillingData(listOf(BillingReceiptFixtures.receipt())))
        assertNull(BillingSnapshotCodec.decode(encoded).pending)
    }
    @Test fun missingOrMalformedSnapshotsAreRejectedForAutomaticRecovery() {
        listOf("not JSON", "{}", "{\"receipts\":[{}]}", "{\"receipts\":\"sku-list\"}").forEach {
            assertThrows(JSONException::class.java) { BillingSnapshotCodec.decode(it) }
        }
    }
}

