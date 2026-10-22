package com.oloomyar.app.billing
import org.junit.Assert.*
import org.junit.Test
class BazaarPurchasePolicyTest {
    @Test fun validBundleUnlocksAllChaptersAndWrongAppOrProductDoesNot() {
        val verifier = BillingReceiptFixtures.verifier()
        fun owned(receipt: SignedBazaarReceipt) = BazaarPurchasePolicy.restoredPurchases(listOf(verifier.verify(receipt)), BillingReceiptFixtures.PACKAGE).map { it.productId }.toSet()
        val owned = owned(BillingReceiptFixtures.receipt())
        (1..15).forEach { assertTrue(ChapterAccessPolicy.isUnlocked(it, owned)) }
        assertTrue(owned(BillingReceiptFixtures.receipt("oloomyar_g9_complete")).isEmpty())
        assertTrue(owned(BillingReceiptFixtures.receipt(appPackage="wrong.app")).isEmpty())
        assertTrue(owned(BillingReceiptFixtures.receipt(state=2)).isEmpty())
    }
}
