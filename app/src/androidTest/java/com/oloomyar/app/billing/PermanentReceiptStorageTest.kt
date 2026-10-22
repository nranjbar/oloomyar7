package com.oloomyar.app.billing

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(AndroidJUnit4::class)
class PermanentReceiptStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val product=BazaarProducts.COMPLETE
    private val key=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private fun receipt():SignedBazaarReceipt {
        val data=JSONObject().put("productId",product).put("packageName",context.packageName)
            .put("purchaseState",0).put("purchaseTime",1000).put("purchaseToken","durable-token").put("developerPayload","persisted").toString()
        val signer=Signature.getInstance("SHA1withRSA");signer.initSign(key.private);signer.update(data.toByteArray())
        return SignedBazaarReceipt(data,Base64.encodeToString(signer.sign(),Base64.NO_WRAP))
    }
    private fun verified(data:StoredBillingData):Set<String> {
        val verifier=SignedReceiptVerifier(Base64.encodeToString(key.public.encoded,Base64.NO_WRAP)) { Base64.decode(it,Base64.DEFAULT) }
        return BazaarPurchasePolicy.restoredSkus(data.receipts.map(verifier::verify),context.packageName)
    }
    @Before @After fun clear() {
        context.getSharedPreferences("bazaar_receipts_v2",Context.MODE_PRIVATE).edit().clear().commit()
        listOf("bazaar_verified_receipts.json","bazaar_verified_receipts.json.bak","bazaar_verified_receipts.json.new")
            .forEach { File(context.noBackupFilesDir,it).delete() }
    }
    @Test fun aNewStorageInstanceReadsTheVerifiedPurchaseBeforeAnyNetworkConnection() {
        val store=BazaarReceiptStorage(context);store.load()
        assertTrue(store.save(StoredBillingData(listOf(receipt()))))
        assertEquals(setOf(product),verified(BazaarReceiptStorage(context).load()))
    }
    @Test fun malformedPrimaryPreferencesRecoverFromTheAtomicBackup() {
        val store=BazaarReceiptStorage(context);store.load();store.save(StoredBillingData(listOf(receipt())))
        context.getSharedPreferences("bazaar_receipts_v2",Context.MODE_PRIVATE).edit().putString("snapshot","broken JSON").commit()
        assertEquals(setOf(product),verified(BazaarReceiptStorage(context).load()))
    }
    @Test fun losingOneCacheCopyCannotHideThePaidChapter() {
        val store=BazaarReceiptStorage(context);store.load();store.save(StoredBillingData(listOf(receipt())))
        context.getSharedPreferences("bazaar_receipts_v2",Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(setOf(product),verified(BazaarReceiptStorage(context).load()))
    }
    @Test fun malformedAtomicBackupCannotHideTheValidPrimaryPurchase() {
        val store=BazaarReceiptStorage(context);store.load();assertTrue(store.save(StoredBillingData(listOf(receipt()))))
        File(context.noBackupFilesDir,"bazaar_verified_receipts.json").writeText("broken JSON")
        assertEquals(setOf(product),verified(BazaarReceiptStorage(context).load()))
    }
    @Test fun onlyLosingBothCopiesRemovesTheReceiptAndItCanBePersistedAgainAfterRestore() {
        val original=receipt();val store=BazaarReceiptStorage(context);store.load()
        assertTrue(store.save(StoredBillingData(listOf(original))))
        clear()
        assertTrue(BazaarReceiptStorage(context).load().receipts.isEmpty())
        val restored=BazaarReceiptStorage(context);restored.load()
        assertTrue(restored.save(StoredBillingData(listOf(original))))
        assertEquals(setOf(product),verified(BazaarReceiptStorage(context).load()))
    }
    @Test fun updatedSnapshotClearsPendingCheckoutAndKeepsTheSignedReceipt() {
        val store=BazaarReceiptStorage(context);store.load()
        store.save(StoredBillingData(pending=PendingBazaarPurchase(product,"pending",1000)))
        store.save(StoredBillingData(listOf(receipt())))
        val loaded=BazaarReceiptStorage(context).load()
        assertNull(loaded.pending);assertEquals(setOf(product),verified(loaded))
    }
}

