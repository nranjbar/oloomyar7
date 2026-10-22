package com.oloomyar.app.billing

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

class BazaarBillingManager(activity: ComponentActivity, rsaPublicKey: String) {
    private val handler = Handler(Looper.getMainLooper())
    private val coordinator = BillingCoordinator(
        packageName = activity.packageName,
        gateway = PoolakeyGateway(activity, rsaPublicKey.trim()),
        storage = BazaarReceiptStorage(activity.applicationContext),
        verifier = SignedReceiptVerifier(rsaPublicKey) { Base64.decode(it, Base64.DEFAULT) },
        scheduler = object : BillingScheduler {
            override fun now() = SystemClock.elapsedRealtime()
            override fun after(delayMillis: Long, block: () -> Unit): BillingCancellation {
                val task = Runnable(block)
                handler.postDelayed(task, delayMillis)
                return BillingCancellation { handler.removeCallbacks(task) }
            }
        },
    )
    val state: StateFlow<BillingUiState> = coordinator.state
    fun start() = coordinator.start()
    fun purchase(sku: String) = coordinator.purchase(sku)
    fun restorePurchases(silent: Boolean = false) = coordinator.restore(silent)
    fun onForeground() = coordinator.onForeground()
    fun clearMessage() = coordinator.clearMessage()
    fun close() = coordinator.close()
}

