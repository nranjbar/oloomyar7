package com.oloomyar.app.billing

import ir.cafebazaar.poolakey.entity.PurchaseState
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class SignedReceiptVerifierTest {
    private val verifier = BillingReceiptFixtures.verifier()

    @Test fun validRsaReceiptPreservesTheSignedFields() {
        val receipt = BillingReceiptFixtures.receipt()
        val info = verifier.verify(receipt)
        assertEquals(BillingReceiptFixtures.PACKAGE, info.packageName)
        assertEquals(BazaarProducts.COMPLETE, info.productId)
        assertEquals("nonce", info.payload)
        assertEquals(PurchaseState.PURCHASED, info.purchaseState)
        assertEquals(receipt.json, info.originalJson)
        assertEquals(receipt.signature, info.dataSignature)
    }
    @Test fun changingAnySignedJsonOrSignatureIsRejected() {
        val receipt = BillingReceiptFixtures.receipt()
        listOf(receipt.copy(json = receipt.json.replace("nonce", "changed")),
            receipt.copy(signature = ""), receipt.copy(signature = "not base64")).forEach {
            assertThrows(InvalidBazaarReceipt::class.java) { verifier.verify(it) }
        }
    }
    @Test fun anotherRsaKeyCannotVerifyTheReceipt() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val other = SignedReceiptVerifier(Base64.getEncoder().encodeToString(key.public.encoded)) {
            Base64.getDecoder().decode(it)
        }
        assertTrue(other.configured)
        assertThrows(InvalidBazaarReceipt::class.java) { other.verify(BillingReceiptFixtures.receipt()) }
    }
    @Test fun missingOrInvalidSignedFieldsCannotBecomePurchases() {
        listOf("purchaseState", "purchaseTime", "purchaseToken", "productId", "packageName").forEach { field ->
            val receipt = BillingReceiptFixtures.receipt { it.remove(field) }
            assertThrows(InvalidBazaarReceipt::class.java) { verifier.verify(receipt) }
        }
        listOf(BillingReceiptFixtures.receipt(state = 3), BillingReceiptFixtures.receipt(time = 0),
            BillingReceiptFixtures.receipt(token = " "), BillingReceiptFixtures.receipt(sku = ""),
            BillingReceiptFixtures.receipt(appPackage = "")).forEach {
            assertThrows(InvalidBazaarReceipt::class.java) { verifier.verify(it) }
        }
        assertThrows(InvalidBazaarReceipt::class.java) { verifier.verify(BillingReceiptFixtures.sign("not JSON")) }
    }
    @Test fun canceledAndRefundedReceiptsRemainIneligible() {
        listOf(1, 2).forEach { state ->
            val info = verifier.verify(BillingReceiptFixtures.receipt(state = state))
            assertEquals(PurchaseState.REFUNDED, info.purchaseState)
            assertFalse(BazaarPurchasePolicy.isEligible(info, BillingReceiptFixtures.PACKAGE))
        }
    }
    @Test fun restoredOldPurchasesDoNotRequireTheCurrentCheckoutPayload() {
        val info = verifier.verify(BillingReceiptFixtures.receipt { it.remove("developerPayload"); it.remove("orderId") })
        assertEquals("", info.payload)
        assertEquals(setOf(info.productId), BazaarPurchasePolicy.restoredSkus(listOf(info), BillingReceiptFixtures.PACKAGE))
    }
}

