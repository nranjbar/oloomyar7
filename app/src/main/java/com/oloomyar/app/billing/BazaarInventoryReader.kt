package com.oloomyar.app.billing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.android.vending.billing.IInAppBillingService
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Collects every page before delivering exactly one inventory result. */
internal class BazaarInventoryReader(private val context: Context) {
    fun query(succeeded: (List<SignedBazaarReceipt>) -> Unit, failed: (Exception) -> Unit): BillingCancellation {
        val stopped = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val worker = Executors.newSingleThreadExecutor()
        var bound = false
        lateinit var connection: ServiceConnection
        fun release() {
            if (bound) {
                bound = false
                try { context.unbindService(connection) } catch (ignored: Exception) { }
            }
            worker.shutdownNow()
        }
        fun failure(error: Exception) {
            if (stopped.compareAndSet(false, true)) { release(); failed(error) }
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (stopped.get()) return
                val service = IInAppBillingService.Stub.asInterface(binder)
                worker.execute {
                    try {
                        val receipts = BazaarInventoryPager.collect { token ->
                            if (stopped.get() || Thread.currentThread().isInterrupted) throw InvalidBazaarInventory()
                            val bundle = service.getPurchases(3, context.packageName, "inapp", token) ?: throw InvalidBazaarInventory()
                            val code = (bundle.get("RESPONSE_CODE") as? Number)?.toInt() ?: throw InvalidBazaarInventory()
                            if (code != 0) throw BazaarServiceResponse(code)
                            val ids = bundle.getStringArrayList("INAPP_PURCHASE_ITEM_LIST") ?: throw InvalidBazaarInventory()
                            val data = bundle.getStringArrayList("INAPP_PURCHASE_DATA_LIST") ?: throw InvalidBazaarInventory()
                            val signatures = bundle.getStringArrayList("INAPP_DATA_SIGNATURE_LIST") ?: throw InvalidBazaarInventory()
                            if (ids.size != data.size || data.size != signatures.size) throw InvalidBazaarInventory()
                            val page = data.indices.map { index ->
                                if (JSONObject(data[index]).getString("productId") != ids[index]) throw InvalidBazaarInventory()
                                SignedBazaarReceipt(data[index], signatures[index])
                            }
                            BazaarInventoryPage(page, bundle.getString("INAPP_CONTINUATION_TOKEN"))
                        }
                        handler.post { if (stopped.compareAndSet(false, true)) { release(); succeeded(receipts) } }
                    } catch (error: Exception) { handler.post { failure(error) } }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) { failure(IllegalStateException("Bazaar service disconnected")) }
            override fun onNullBinding(name: ComponentName) { failure(InvalidBazaarInventory()) }
            override fun onBindingDied(name: ComponentName) { failure(IllegalStateException("Bazaar service died")) }
        }
        try {
            val intent = Intent("ir.cafebazaar.pardakht.InAppBillingService.BIND")
                .setClassName("com.farsitel.bazaar", "com.farsitel.bazaar.inappbilling.service.InAppBillingService")
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) failure(IllegalStateException("Update Bazaar to connect to its billing service"))
        } catch (error: Exception) { failure(error) }
        return BillingCancellation { if (stopped.compareAndSet(false, true)) release() }
    }
}

