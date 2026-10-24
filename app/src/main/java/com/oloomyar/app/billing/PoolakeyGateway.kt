package com.oloomyar.app.billing

import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest

internal class PoolakeyGateway(private val activity: ComponentActivity, private val publicKey: String) : BazaarGateway {
    private var payment: Payment? = null
    private var connection: Connection? = null
    private var epoch = 0L
    private var query: BillingCancellation? = null
    override fun connect(connected: () -> Unit, failed: (Exception) -> Unit, onDisconnected: () -> Unit) {
        close()
        val expected = epoch
        val client = Payment(activity.applicationContext, PaymentConfiguration(
            localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = publicKey),
            shouldSupportSubscription = false,
        ))
        payment = client
        val created = client.connect {
            connectionSucceed { if (epoch == expected) connected() }
            connectionFailed { if (epoch == expected) failed(asException(it)) }
            disconnected { if (epoch == expected) onDisconnected() }
        }
        if (epoch == expected) connection = created else try { created.disconnect() } catch (ignored: Exception) { }
    }
    override fun purchase(sku: String, payload: String, succeeded: (SignedBazaarReceipt) -> Unit, canceled: () -> Unit, failed: (Exception) -> Unit) {
        val expected = epoch
        val client = payment ?: throw IllegalStateException("Payment is not connected")
        client.purchaseProduct(activity.activityResultRegistry, PurchaseRequest(productId = sku, payload = payload)) {
            purchaseSucceed { if (epoch == expected) succeeded(SignedBazaarReceipt(it.originalJson, it.dataSignature)) }
            purchaseCanceled { if (epoch == expected) canceled() }
            purchaseFailed { if (epoch == expected) failed(asException(it)) }
            failedToBeginFlow { if (epoch == expected) failed(asException(it)) }
        }
    }
    override fun inventory(succeeded: (List<SignedBazaarReceipt>) -> Unit, failed: (Exception) -> Unit): BillingCancellation {
        query?.cancel()
        val expected = epoch
        val result = BazaarInventoryReader(activity.applicationContext).query(
            succeeded = { if (epoch == expected) succeeded(it) }, failed = { if (epoch == expected) failed(it) })
        query = result
        return result
    }
    override fun close() {
        ++epoch
        query?.cancel(); query = null
        val old = connection
        connection = null; payment = null
        try { old?.disconnect() } catch (ignored: Exception) { }
    }
    private fun asException(error: Throwable) = error as? Exception ?: IllegalStateException("Billing operation failed")
}

