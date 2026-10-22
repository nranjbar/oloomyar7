package com.oloomyar.app.billing

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature

/** Two methods run in separate Android processes by the CI persistence harness. */
@RunWith(AndroidJUnit4::class)
class BillingProcessPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture = context.getSharedPreferences("billing_process_test_only", Context.MODE_PRIVATE)

    @Test fun seedVerifiedReceiptBeforeProcessTermination() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val json = JSONObject().put("productId", BazaarProducts.COMPLETE).put("packageName", context.packageName)
            .put("purchaseState", 0).put("purchaseTime", 1000).put("purchaseToken", "cold-start-fixture").toString()
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(key.private); signer.update(json.toByteArray(Charsets.UTF_8))
        assertTrue(fixture.edit().putString("public_key", Base64.encodeToString(key.public.encoded, Base64.NO_WRAP)).commit())
        val storage = BazaarReceiptStorage(context)
        storage.load()
        assertTrue(storage.save(StoredBillingData(listOf(SignedBazaarReceipt(json, Base64.encodeToString(signer.sign(), Base64.NO_WRAP))))))
        assertTrue(File(context.noBackupFilesDir, "bazaar_verified_receipts.json").isFile)
    }

    @Test fun coldProcessUnlocksEveryPaidChapterBeforeAnyConnectionAndAfterOfflineFailure() {
        val key = requireNotNull(fixture.getString("public_key", null))
        var connects = 0
        val gateway = object : BazaarGateway {
            override fun connect(connected: () -> Unit, failed: (Exception) -> Unit, onDisconnected: () -> Unit) {
                connects++; failed(IllegalStateException("offline"))
            }
            override fun purchase(sku: String, payload: String, succeeded: (SignedBazaarReceipt) -> Unit,
                                  canceled: () -> Unit, failed: (Exception) -> Unit) = error("Must not purchase")
            override fun inventory(succeeded: (List<SignedBazaarReceipt>) -> Unit, failed: (Exception) -> Unit): BillingCancellation = error("No network")
            override fun close() = Unit
        }
        val coordinator = BillingCoordinator(context.packageName, gateway, BazaarReceiptStorage(context),
            SignedReceiptVerifier(key) { Base64.decode(it, Base64.DEFAULT) }, object : BillingScheduler {
                override fun now() = 1000L
                override fun after(delayMillis: Long, block: () -> Unit) = BillingCancellation { }
            })
        fun verify() { (1..15).forEach { assertTrue(ChapterAccessPolicy.isUnlocked(it, coordinator.state.value.purchasedSkus)) } }
        assertEquals(0, connects); verify()
        coordinator.start(); assertEquals(1, connects); verify()
        coordinator.onForeground(); assertEquals(2, connects); verify()
        coordinator.close()
        fixture.edit().clear().commit()
    }
}
