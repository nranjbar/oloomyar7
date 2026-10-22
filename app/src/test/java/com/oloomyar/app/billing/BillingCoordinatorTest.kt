package com.oloomyar.app.billing

import org.junit.Assert.*
import org.junit.Test

class BillingCoordinatorTest {
    @Test fun homeReadsAPurchaseSavedByAnotherActivityBeforeReconnectingOffline() {
        val f = Fixture()
        f.ready()
        f.storage.data = StoredBillingData(listOf(receipt()))
        f.controller.onForeground()
        assertEquals(setOf(sku), f.state.purchasedSkus)
        (1..15).forEach { assertTrue(ChapterAccessPolicy.isUnlocked(it, f.state.purchasedSkus)) }
        f.gateway.connections.last().disconnect()
        f.controller.onForeground()
        f.gateway.connections.last().failure(IllegalStateException("offline"))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    private val sku = BazaarProducts.COMPLETE
    private val other = "oloomyar_g9_complete"
    private fun receipt(s: String = sku, payload: String = "nonce", state: Int = 0, time: Long = 1000) =
        BillingReceiptFixtures.receipt(s, payload, state = state, time = time)

    private class Clock : BillingScheduler {
        data class Task(val at: Long, val block: () -> Unit, var canceled: Boolean = false)
        var time = 1000L
        val tasks = mutableListOf<Task>()
        override fun now() = time
        override fun after(delayMillis: Long, block: () -> Unit): BillingCancellation {
            val task = Task(time + delayMillis, block)
            tasks += task
            return BillingCancellation { task.canceled = true }
        }
        fun advance(amount: Long) {
            val end = time + amount
            while (true) {
                val task = tasks.filter { !it.canceled && it.at <= end }.minByOrNull { it.at } ?: break
                task.canceled = true
                time = task.at
                task.block()
            }
            time = end
        }
    }
    private class Storage(var data: StoredBillingData = StoredBillingData()) : BillingStorage {
        var fail = false
        var broken = false
        var writes = 0
        override fun load(): StoredBillingData { if (broken) throw IllegalStateException(); return data }
        override fun save(data: StoredBillingData): Boolean {
            writes++
            if (fail) return false
            this.data = data
            return true
        }
    }
    private class Gateway : BazaarGateway {
        data class Connect(val success: () -> Unit, val failure: (Exception) -> Unit, val disconnect: () -> Unit)
        data class Purchase(val sku: String, val payload: String, val success: (SignedBazaarReceipt) -> Unit, val cancel: () -> Unit, val failure: (Exception) -> Unit)
        data class Inventory(val success: (List<SignedBazaarReceipt>) -> Unit, val failure: (Exception) -> Unit, var canceled: Boolean = false)
        val connections = mutableListOf<Connect>()
        val purchases = mutableListOf<Purchase>()
        val inventories = mutableListOf<Inventory>()
        var throwPurchase = false
        var throwConnect = false
        var throwInventory = false
        override fun connect(connected: () -> Unit, failed: (Exception) -> Unit, onDisconnected: () -> Unit) {
            if (throwConnect) throw IllegalStateException()
            connections += Connect(connected, failed, onDisconnected)
        }
        override fun purchase(sku: String, payload: String, succeeded: (SignedBazaarReceipt) -> Unit, canceled: () -> Unit, failed: (Exception) -> Unit) {
            if (throwPurchase) throw IllegalStateException()
            purchases += Purchase(sku, payload, succeeded, canceled, failed)
        }
        override fun inventory(succeeded: (List<SignedBazaarReceipt>) -> Unit, failed: (Exception) -> Unit): BillingCancellation {
            if (throwInventory) throw IllegalStateException()
            val request = Inventory(succeeded, failed)
            inventories += request
            return BillingCancellation { request.canceled = true }
        }
        override fun close() { }
    }
    private class Fixture(data: StoredBillingData = StoredBillingData(), broken: Boolean = false) {
        val gateway = Gateway()
        val storage = Storage(data).apply { this.broken = broken }
        val clock = Clock()
        val controller = BillingCoordinator(BillingReceiptFixtures.PACKAGE, gateway, storage,
            BillingReceiptFixtures.verifier(), clock) { "nonce" }
        val state get() = controller.state.value
        fun ready(receipts: List<SignedBazaarReceipt> = emptyList()) {
            controller.start()
            gateway.connections.last().success()
            gateway.inventories.last().success(receipts)
        }
    }

    @Test fun repeatedClicksCannotLaunchTwoPurchasesOrRestoreDuringPayment() {
        val f = Fixture(); f.ready()
        f.controller.purchase(sku); f.controller.purchase(sku); f.controller.purchase(other); f.controller.restore()
        assertEquals(1, f.gateway.purchases.size)
        assertEquals(1, f.gateway.inventories.size)
        assertFalse(f.state.canPurchase); assertFalse(f.state.canRestore)
        assertEquals(sku, f.storage.data.pending?.sku)
    }
    @Test fun purchasesWaitForAnAuthoritativeInventory() {
        val f = Fixture(); f.controller.start(); f.gateway.connections.last().success()
        f.controller.purchase(sku)
        assertTrue(f.gateway.purchases.isEmpty())
        assertTrue(f.state.restoring)
    }
    @Test fun repeatedRestoresAreSerialized() {
        val f = Fixture(); f.ready(); f.controller.restore(); f.controller.restore()
        assertEquals(2, f.gateway.inventories.size)
    }
    @Test fun successfulPurchaseIsDurableAndDuplicateCallbacksAreIgnored() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        val request = f.gateway.purchases.last()
        request.success(receipt()); val writes = f.storage.writes
        request.success(receipt(other)); request.cancel(); request.failure(IllegalStateException())
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertEquals(writes, f.storage.writes)
        assertNull(f.storage.data.pending); assertNull(f.state.busySku)
        f.controller.purchase(sku); assertEquals(1, f.gateway.purchases.size)
    }
    @Test fun aMismatchedPayloadNeverGrantsDirectAccessAndIsReconciled() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        f.gateway.purchases.last().success(receipt(payload = "different"))
        assertTrue(f.state.purchasedSkus.isEmpty()); assertTrue(f.state.restoring)
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun aDifferentProductOrPackageCannotGrantTheRequestedPurchase() {
        listOf(receipt(other), BillingReceiptFixtures.receipt(appPackage = "com.other.app")).forEach { wrong ->
            val f = Fixture(); f.ready(); f.controller.purchase(sku); f.gateway.purchases.last().success(wrong)
            assertTrue(f.state.purchasedSkus.isEmpty()); assertTrue(f.state.restoring)
        }
    }
    @Test fun cancellationChecksForALostConfirmationAndCanRecoverIt() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku); f.gateway.purchases.last().cancel()
        assertFalse(f.state.canPurchase)
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertNull(f.state.busySku)
    }
    @Test fun cancellationWithNoPurchaseLeavesAllPaidContentLocked() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku); f.gateway.purchases.last().cancel()
        f.gateway.inventories.last().success(emptyList())
        assertTrue(f.state.purchasedSkus.isEmpty()); assertNull(f.storage.data.pending)
        assertTrue(f.state.canPurchase)
        assertFalse(f.state.message.orEmpty().contains("مبلغی کسر نشد"))
    }
    @Test fun alreadyOwnedOrUncertainPaymentErrorsRestoreInsteadOfRetryingPayment() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        f.gateway.purchases.last().failure(BazaarServiceResponse(7))
        assertEquals(1, f.gateway.purchases.size)
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun connectionAndPurchaseSynchronousExceptionsCannotLeaveBusyFlagsStuck() {
        val f = Fixture(); f.gateway.throwConnect = true; f.controller.start()
        assertEquals(BillingConnectionState.UNAVAILABLE, f.state.connectionState)
        f.gateway.throwConnect = false; f.ready()
        f.gateway.throwPurchase = true; f.controller.purchase(sku)
        assertNull(f.state.busySku); assertTrue(f.state.restoring)
    }
    @Test fun aQueryExceptionPreservesVerifiedCacheAndAllowsAnotherRestore() {
        val f = Fixture(); f.ready(listOf(receipt()))
        f.gateway.throwInventory = true; f.controller.restore()
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertFalse(f.state.restoring); assertTrue(f.state.canRestore); assertFalse(f.state.canPurchase)
    }
    @Test fun failedAndEmptySuccessfulRestoresBothPreserveAPaidChapter() {
        val f = Fixture(); f.ready(listOf(receipt())); f.controller.restore()
        f.gateway.inventories.last().failure(IllegalStateException())
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertFalse(f.state.canPurchase)
        f.controller.restore(); f.gateway.inventories.last().success(emptyList())
        assertEquals(setOf(sku), f.state.purchasedSkus); assertEquals(1, f.storage.data.receipts.size)
    }
    @Test fun mixedValidAndInvalidInventoryIsRejectedAtomically() {
        val f = Fixture(); f.ready(listOf(receipt()))
        val invalid = receipt(BazaarProducts.COMPLETE).copy(signature = "invalid")
        f.controller.restore(); f.gateway.inventories.last().success(listOf(receipt(other), invalid))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertFalse(f.state.restoring); assertFalse(f.state.inventoryChecked)
    }
    @Test fun timeoutAndStaleInventoryCallbacksCannotOverwriteANewerResult() {
        val f = Fixture(); f.ready(listOf(receipt())); f.controller.restore()
        val old = f.gateway.inventories.last(); f.clock.advance(30_001)
        assertFalse(f.state.restoring); assertTrue(old.canceled)
        f.controller.restore(); f.gateway.inventories.last().success(listOf(receipt(other)))
        old.success(listOf(receipt(BazaarProducts.COMPLETE)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun aTimedOutConnectionCanRetryAndIgnoresTheOldConnection() {
        val f = Fixture(); f.controller.start(); val old = f.gateway.connections.last()
        f.clock.advance(15_001); assertEquals(BillingConnectionState.UNAVAILABLE, f.state.connectionState)
        f.controller.restore(); old.success(); assertTrue(f.gateway.inventories.isEmpty())
        f.gateway.connections.last().success(); f.gateway.inventories.last().success(emptyList())
        assertTrue(f.state.canPurchase)
    }
    @Test fun disconnectDuringPaymentPersistsPendingAndReconnectRestoresIt() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        f.gateway.connections.last().disconnect()
        assertNull(f.state.busySku); assertFalse(f.state.restoring); assertNotNull(f.storage.data.pending)
        f.controller.restore(); f.gateway.connections.last().success()
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun processDeathReconcilesDurablePendingPaymentWithoutStartingAnotherPurchase() {
        val old = Fixture(); old.ready(); old.controller.purchase(sku); old.controller.close()
        val resumed = Fixture(BillingSnapshotCodec.decode(BillingSnapshotCodec.encode(old.storage.data)))
        resumed.ready(listOf(receipt()))
        assertEquals(setOf(sku), resumed.state.purchasedSkus)
        assertNull(resumed.storage.data.pending); assertTrue(resumed.gateway.purchases.isEmpty())
    }
    @Test fun checkoutTimeoutThenReturningToTheAppRechecksLatePayment() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        val old = f.gateway.purchases.last(); f.clock.advance(600_001)
        f.gateway.inventories.last().success(emptyList())
        old.success(receipt()); assertTrue(f.state.purchasedSkus.isEmpty())
        val count = f.gateway.inventories.size; f.controller.onForeground()
        assertEquals(count + 1, f.gateway.inventories.size)
        f.gateway.inventories.last().success(listOf(receipt())); assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun storageFailureBeforePaymentPreventsLaunchingTheCheckout() {
        val f = Fixture(); f.ready(); f.storage.fail = true; f.controller.purchase(sku)
        assertTrue(f.gateway.purchases.isEmpty()); assertNull(f.state.busySku)
    }
    @Test fun storageFailureAfterSuccessfulPaymentKeepsVerifiedSessionAccess() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku); f.storage.fail = true
        f.gateway.purchases.last().success(receipt())
        assertEquals(setOf(sku), f.state.purchasedSkus); assertTrue(f.state.warning)
        assertNotNull(f.storage.data.pending)
    }
    @Test fun signedCacheWorksOfflineButTamperedAndLegacyCacheCannotGrantAccess() {
        val good = receipt(); val bad = good.copy(json = good.json.replace(sku, "tampered"))
        val f = Fixture(StoredBillingData(listOf(good, bad), requiresRestore = true))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertTrue(Fixture(StoredBillingData(requiresRestore = true)).state.purchasedSkus.isEmpty())
        assertTrue(Fixture(broken = true).state.purchasedSkus.isEmpty())
    }
    @Test fun switchingAccountAndEmptyRestoreCannotRelockAPermanentBundle() {
        val f = Fixture(); f.ready(listOf(receipt(BazaarProducts.COMPLETE)))
        f.controller.restore(); f.gateway.inventories.last().success(emptyList())
        assertEquals(setOf(BazaarProducts.COMPLETE), f.state.purchasedSkus)
        assertEquals(1, f.storage.data.receipts.size)
    }
    @Test fun latestRefundRevokesOldAccessAndALaterRepurchaseCanRestoreIt() {
        val f = Fixture()
        f.ready(listOf(receipt(BazaarProducts.COMPLETE), receipt(BazaarProducts.COMPLETE, state = 2)))
        assertTrue(f.state.purchasedSkus.isEmpty())
        f.controller.restore()
        f.gateway.inventories.last().success(listOf(receipt(state = 2), receipt(time = 2000)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun owningTheBundleOrAllChaptersPreventsAnyRedundantPayment() {
        listOf(listOf(receipt(BazaarProducts.COMPLETE))).forEach {
            val f = Fixture(); f.ready(it); BazaarProducts.allSkus.forEach(f.controller::purchase)
            assertTrue(f.gateway.purchases.isEmpty())
        }
    }
    @Test fun closeIgnoresLateCallbacksAndCancelsAllTimers() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku); val old = f.gateway.purchases.last()
        f.controller.close(); old.success(receipt(BazaarProducts.COMPLETE)); f.clock.advance(1_000_000)
        assertTrue(f.state.purchasedSkus.isEmpty()); assertEquals(1, f.gateway.inventories.size)
    }
    @Test fun invalidPublicKeyNeverStartsBilling() {
        val g = Gateway(); val c = BillingCoordinator(BillingReceiptFixtures.PACKAGE, g, Storage(),
            SignedReceiptVerifier("broken") { java.util.Base64.getDecoder().decode(it) }, Clock())
        c.start(); assertTrue(g.connections.isEmpty())
        assertEquals(BillingConnectionState.CONFIGURATION_REQUIRED, c.state.value.connectionState)
    }
    @Test fun unknownProductsAndDestinationsCannotGrantAnything() {
        val f = Fixture(); f.ready(listOf(receipt("unknown"))); f.controller.purchase("unknown")
        assertTrue(f.state.purchasedSkus.isEmpty()); assertTrue(f.gateway.purchases.isEmpty())
        listOf(-1, 0, 16, 100, 103, 999).forEach {
            assertFalse(ChapterAccessPolicy.isUnlocked(it, setOf(BazaarProducts.COMPLETE)))
        }
    }
    @Test fun aFailedRecoveryRetainsPendingAndDoesNotPermitAnotherCharge() {
        val f = Fixture(); f.ready(); f.controller.purchase(sku)
        f.gateway.purchases.last().failure(IllegalStateException())
        f.gateway.inventories.last().failure(IllegalStateException())
        assertNotNull(f.storage.data.pending); assertFalse(f.state.canPurchase); assertTrue(f.state.canRestore)
        f.controller.purchase(sku)
        assertEquals(1, f.gateway.purchases.size)
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun disconnectDuringInventoryKeepsCacheAndRejectsItsLateResult() {
        val f = Fixture(); f.ready(listOf(receipt())); f.controller.restore()
        val old = f.gateway.inventories.last()
        f.gateway.connections.last().disconnect(); old.success(listOf(receipt(BazaarProducts.COMPLETE)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertFalse(f.state.restoring); assertFalse(f.state.canPurchase)
        f.controller.restore(); f.gateway.connections.last().success()
        f.gateway.inventories.last().success(listOf(receipt(other)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun foregroundRefreshesPeriodicallyWithoutLaunchingConcurrentRequests() {
        val f = Fixture(); f.ready(); f.controller.onForeground()
        assertEquals(1, f.gateway.inventories.size)
        f.clock.advance(60_000); f.controller.onForeground(); f.controller.onForeground()
        assertEquals(2, f.gateway.inventories.size)
        f.gateway.inventories.last().success(emptyList())
        f.controller.onForeground(); assertEquals(2, f.gateway.inventories.size)
    }
    @Test fun aStorageFailureDuringRestoreKeepsVerifiedSessionButBlocksPurchasing() {
        val f = Fixture(); f.ready(); f.controller.restore(); f.storage.fail = true
        f.gateway.inventories.last().success(listOf(receipt()))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertFalse(f.state.restoring); assertFalse(f.state.canPurchase); assertTrue(f.state.canRestore)
    }
    @Test fun everyPaidProductRemainsActiveAcrossOfflineColdStartsAndTimeoutsWithoutManualRestore() {
        BazaarProducts.allSkus.forEach { product ->
            val purchased=Fixture();purchased.ready();purchased.controller.purchase(product)
            purchased.gateway.purchases.last().success(receipt(product))
            val saved=purchased.storage.data;purchased.controller.close()
            repeat(3) {
                val reopened=Fixture(saved)
                assertEquals(setOf(product),reopened.state.purchasedSkus)
                assertTrue(reopened.gateway.connections.isEmpty())
                val destinations=((1..15).toList()+emptyList<Int>()).filter {
                    ChapterAccessPolicy.isUnlocked(it,setOf(product))
                }
                fun assertStillOwned() {
                    destinations.forEach { assertTrue("$product destination $it",ChapterAccessPolicy.isUnlocked(it,reopened.state.purchasedSkus)) }
                    assertEquals(saved.receipts,reopened.storage.data.receipts)
                }
                assertStillOwned()
                reopened.controller.start()
                assertStillOwned() // Even while the market connection is pending.
                if(it==0) reopened.gateway.connections.last().failure(IllegalStateException("offline"))
                else reopened.clock.advance(15_001)
                assertStillOwned()
                reopened.controller.onForeground();assertStillOwned()
                reopened.controller.close()
            }
        }
    }
    @Test fun missingReceiptNeedsRestoreButASavedReceiptDoesNotNeedAnInventoryToUnlock() {
        val cached=Fixture(StoredBillingData(listOf(receipt())))
        assertTrue(ChapterAccessPolicy.isUnlocked(5,cached.state.purchasedSkus))
        assertTrue(cached.gateway.inventories.isEmpty())
        val lost=Fixture()
        assertFalse(ChapterAccessPolicy.isUnlocked(5,lost.state.purchasedSkus))
        lost.controller.restore();lost.gateway.connections.last().success()
        lost.gateway.inventories.last().success(listOf(receipt()))
        assertTrue(ChapterAccessPolicy.isUnlocked(5,lost.state.purchasedSkus))
        assertTrue(ChapterAccessPolicy.isUnlocked(5,Fixture(lost.storage.data).state.purchasedSkus))
    }
    @Test fun partialInventoryAddsPurchasesWithoutDroppingAnyEarlierProof() {
        val f = Fixture(); f.ready(listOf(receipt()))
        f.controller.restore(); f.gateway.inventories.last().success(listOf(receipt(other)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertEquals(setOf(sku), Fixture(f.storage.data).state.purchasedSkus)
        f.controller.restore(); f.gateway.inventories.last().success(emptyList())
        assertEquals(setOf(sku), f.state.purchasedSkus)
    }
    @Test fun aReconnectWithAnEmptyInventoryCannotRevokeTheWholeBook() {
        val f = Fixture(); f.ready(listOf(receipt(BazaarProducts.COMPLETE)))
        f.gateway.connections.last().disconnect(); f.controller.onForeground()
        f.gateway.connections.last().success(); f.gateway.inventories.last().success(emptyList())
        ((1..15).toList() + emptyList<Int>()).forEach { assertTrue(ChapterAccessPolicy.isUnlocked(it, f.state.purchasedSkus)) }
        assertEquals(setOf(BazaarProducts.COMPLETE), Fixture(f.storage.data).state.purchasedSkus)
    }
    @Test fun negativeInventoryRecordsCannotUndoALocallyVerifiedPermanentPurchase() {
        val f = Fixture(); f.ready(listOf(receipt()))
        f.controller.restore(); f.gateway.inventories.last().success(listOf(receipt(state = 2)))
        assertEquals(setOf(sku), f.state.purchasedSkus)
        assertEquals(setOf(sku), Fixture(f.storage.data).state.purchasedSkus)
        f.controller.purchase(sku); assertTrue(f.gateway.purchases.isEmpty())
    }
}

