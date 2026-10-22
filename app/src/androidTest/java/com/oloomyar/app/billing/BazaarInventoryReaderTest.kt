package com.oloomyar.app.billing

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.vending.billing.IInAppBillingService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real Android Bundle/Binder boundary; the market account is simulated. */
@RunWith(AndroidJUnit4::class)
class BazaarInventoryReaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val sku = BazaarProducts.COMPLETE
    private fun page(product: String = sku, next: String? = null) = Bundle().apply {
        putInt("RESPONSE_CODE", 0)
        putStringArrayList("INAPP_PURCHASE_ITEM_LIST", arrayListOf(product))
        putStringArrayList("INAPP_PURCHASE_DATA_LIST", arrayListOf("{\"productId\":\"$product\"}"))
        putStringArrayList("INAPP_DATA_SIGNATURE_LIST", arrayListOf("signature-$product"))
        putString("INAPP_CONTINUATION_TOKEN", next)
    }
    private class Service(val response: (String?) -> Bundle) : IInAppBillingService.Stub() {
        val tokens = mutableListOf<String?>()
        override fun getPurchases(apiVersion: Int, packageName: String?, type: String?, continuationToken: String?): Bundle {
            assertEquals(3, apiVersion)
            assertEquals("com.oloomyar.app.grade7", packageName)
            assertEquals("inapp", type)
            tokens += continuationToken
            return response(continuationToken)
        }
        override fun isBillingSupported(apiVersion: Int, packageName: String?, type: String?) = 0
        override fun getSkuDetails(apiVersion: Int, packageName: String?, type: String?, skusBundle: Bundle?) = Bundle()
        override fun getBuyIntent(apiVersion: Int, packageName: String?, sku: String?, type: String?, developerPayload: String?) = Bundle()
        override fun consumePurchase(apiVersion: Int, packageName: String?, purchaseToken: String?) = 0
        override fun getBuyIntentV2(apiVersion: Int, packageName: String?, sku: String?, type: String?, developerPayload: String?) = Bundle()
        override fun getPurchaseConfig(apiVersion: Int) = Bundle()
        override fun getBuyIntentV3(apiVersion: Int, packageName: String?, sku: String?, developerPayload: String?, extraData: Bundle?) = Bundle()
        override fun checkTrialSubscription(packageName: String?) = Bundle()
        override fun getFeatureConfig() = Bundle()
    }
    private class Host(base: Context, val service: Service?, val bindResult: Boolean = true) : ContextWrapper(base) {
        var unbound = 0
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            assertEquals("ir.cafebazaar.pardakht.InAppBillingService.BIND", intent.action)
            assertEquals("com.farsitel.bazaar", intent.component?.packageName)
            assertEquals("com.farsitel.bazaar.inappbilling.service.InAppBillingService", intent.component?.className)
            assertEquals(Context.BIND_AUTO_CREATE, flags)
            if (bindResult && service != null) Handler(Looper.getMainLooper()).post {
                connection.onServiceConnected(requireNotNull(intent.component), service.asBinder())
            }
            return bindResult
        }
        override fun unbindService(connection: ServiceConnection) { unbound++ }
    }
    private class Result {
        val done = CountDownLatch(1)
        val callbacks = AtomicInteger()
        var receipts: List<SignedBazaarReceipt>? = null
        var error: Exception? = null
        fun await() { assertTrue("Inventory did not complete", done.await(5, TimeUnit.SECONDS)) }
    }
    private fun query(host: Host): Result {
        val result = Result()
        instrumentation.runOnMainSync {
            BazaarInventoryReader(host).query(
                succeeded = { result.receipts = it; result.callbacks.incrementAndGet(); result.done.countDown() },
                failed = { result.error = it; result.callbacks.incrementAndGet(); result.done.countDown() })
        }
        result.await()
        return result
    }

    @Test fun nativeInventoryCollectsAllPagesBeforeItsOnlyCallback() {
        val other = BazaarProducts.COMPLETE
        val service = Service { if (it == null) page(next = "next") else page(other) }
        val host = Host(instrumentation.targetContext, service)
        val result = query(host)
        assertNull(result.error)
        assertEquals(2, result.receipts?.size)
        assertEquals(listOf(null, "next"), service.tokens)
        assertEquals(1, result.callbacks.get()); assertEquals(1, host.unbound)
    }
    @Test fun nativeLaterPageErrorNeverDeliversTheFirstPageAsSuccess() {
        val host = Host(instrumentation.targetContext,
            Service { if (it == null) page(next = "next") else Bundle().apply { putLong("RESPONSE_CODE", 6L) } })
        val result = query(host)
        assertNull(result.receipts); assertTrue(result.error is BazaarServiceResponse)
        assertEquals(1, result.callbacks.get()); assertEquals(1, host.unbound)
    }
    @Test fun malformedNativeArraysAndProductIdsCannotBecomeInventory() {
        val malformed = listOf(page().apply { remove("RESPONSE_CODE") },
            page().apply { putStringArrayList("INAPP_DATA_SIGNATURE_LIST", arrayListOf()) },
            page().apply { putStringArrayList("INAPP_PURCHASE_ITEM_LIST", arrayListOf("different")) })
        malformed.forEach { bundle ->
            val result = query(Host(instrumentation.targetContext, Service { bundle }))
            assertNull(result.receipts); assertTrue(result.error is InvalidBazaarInventory)
            assertEquals(1, result.callbacks.get())
        }
    }
    @Test fun nativeBindFailureCompletesWithAnError() {
        val result = query(Host(instrumentation.targetContext, null, bindResult = false))
        assertNull(result.receipts); assertNotNull(result.error); assertEquals(1, result.callbacks.get())
    }
    @Test fun canceledNativeBindingCannotDeliverLateSuccessOrFailure() {
        val service = Service { page() }
        val host = Host(instrumentation.targetContext, service)
        val callbacks = AtomicInteger()
        instrumentation.runOnMainSync {
            BazaarInventoryReader(host).query({ callbacks.incrementAndGet() }, { callbacks.incrementAndGet() }).cancel()
        }
        instrumentation.waitForIdleSync()
        assertEquals(0, callbacks.get()); assertEquals(1, host.unbound)
        assertTrue(service.tokens.isEmpty())
    }
}

