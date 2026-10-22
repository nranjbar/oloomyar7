package com.oloomyar.app.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Main-thread coordinator; callbacks and timers are delivered on the main thread. */
internal class BillingCoordinator(
    private val packageName: String,
    private val gateway: BazaarGateway,
    private val storage: BillingStorage,
    private val verifier: BazaarReceiptVerifier,
    private val scheduler: BillingScheduler,
    private val payload: () -> String = { UUID.randomUUID().toString() },
) {
    private var records = emptyList<SignedBazaarReceipt>()
    private var pending: PendingBazaarPurchase? = null
    private var closed = false
    private var serial = 0L
    private var connectionEpoch = 0L
    private var operation: Long? = null
    private var timer: BillingCancellation? = null
    private var inventoryCancel: BillingCancellation? = null
    private var lastRestore = Long.MIN_VALUE
    private var refreshAfterPayment = false
    private val mutableState = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = mutableState.asStateFlow()

    init {
        val stored = try { storage.load() } catch (error: Exception) { StoredBillingData(requiresRestore = true) }
        var invalid = stored.requiresRestore
        val valid = stored.receipts.mapNotNull {
            try { verifier.verify(it) } catch (error: Exception) { invalid = true; null }
        }
        records = select(valid.filter { BazaarPurchasePolicy.isEligible(it, packageName) })
        pending = stored.pending?.takeIf { it.sku in BazaarProducts.allSkus && it.payload.isNotBlank() }
        mutableState.value = BillingUiState(
            purchasedSkus = skus(records),
            message = if (invalid || pending != null) "برای تأیید خریدهای قبلی، به بازار وصل شوید و بازیابی را بزنید." else null,
            warning = invalid || pending != null,
        )
        // Upgrade an existing validated cache to both durable copies before
        // any network operation, including on the first offline launch.
        if (records.isNotEmpty()) save()
    }

    fun start() {
        if (closed) return
        refreshCachedReceipts()
        if (operation != null) return
        if (state.value.connectionState == BillingConnectionState.READY) {
            restore(silent = true)
            return
        }
        if (!verifier.configured) {
            update { it.copy(connectionState = BillingConnectionState.CONFIGURATION_REQUIRED,
                message = "امکان پرداخت در این نسخه فراهم نیست؛ با پشتیبانی برنامه تماس بگیرید.", warning = true) }
            return
        }
        val epoch = ++connectionEpoch
        update { it.copy(connectionState = BillingConnectionState.CONNECTING, message = "در حال اتصال به بازار…", warning = false) }
        val id = begin(15_000) { connectionFailed(epoch, "اتصال به بازار طول کشید؛ دوباره بازیابی را بزنید.") }
        try {
            gateway.connect(
                connected = {
                    if (current(id) && epoch == connectionEpoch) {
                        finish(id)
                        update { it.copy(connectionState = BillingConnectionState.READY) }
                        restore(silent = true)
                    }
                },
                failed = { if (current(id)) connectionFailed(epoch, BillingErrors.message(it)) },
                onDisconnected = { connectionFailed(epoch, "ارتباط با بازار قطع شد؛ برای بررسی خرید، دوباره بازیابی را بزنید.") },
            )
        } catch (error: Exception) {
            if (current(id)) connectionFailed(epoch, BillingErrors.message(error))
        }
    }

    fun onForeground() {
        if (closed) return
        refreshCachedReceipts()
        if (operation != null) return
        if (state.value.connectionState != BillingConnectionState.READY) start()
        else if (refreshAfterPayment || pending != null || lastRestore == Long.MIN_VALUE || scheduler.now() - lastRestore >= 60_000) {
            refreshAfterPayment = false
            restore(silent = true)
        }
    }

    fun purchase(sku: String) {
        if (closed || sku !in BazaarProducts.allSkus || operation != null) return
        refreshCachedReceipts()
        if (BazaarProducts.isActive(sku, state.value.purchasedSkus)) {
            notice("این محتوا از قبل برای شما فعال است.")
            return
        }
        if (!state.value.canPurchase) {
            notice("پیش از خرید، اتصال و بازیابی خریدهای قبلی را کامل کنید.", warning = true)
            restore()
            return
        }
        pending = PendingBazaarPurchase(sku, payload(), scheduler.now())
        if (!save()) {
            pending = null
            notice("ذخیرهٔ وضعیت خرید ممکن نشد؛ فضای دستگاه را بررسی و دوباره تلاش کنید.", warning = true)
            return
        }
        update { it.copy(busySku = sku, message = "در انتظار نتیجهٔ پرداخت بازار…", warning = false) }
        val id = begin(10 * 60_000) {
            purchaseUncertain("نتیجهٔ پرداخت دریافت نشد؛ خریدهای بازار را دوباره بررسی می‌کنیم.")
        }
        val expected = requireNotNull(pending)
        refreshAfterPayment = true
        try {
            gateway.purchase(sku, expected.payload,
                succeeded = { receipt ->
                    if (current(id)) {
                        val info = try { verifier.verify(receipt) } catch (error: Exception) { null }
                        if (info == null || !BazaarPurchasePolicy.isEligible(info, packageName) ||
                            info.productId != sku || info.payload != expected.payload
                        ) {
                            purchaseUncertain("تأیید نتیجهٔ پرداخت کامل نشد؛ خریدهای بازار را بررسی می‌کنیم.")
                        } else {
                            finish(id)
                            records = records.filterNot { verifier.verify(it).productId == sku } + receipt
                            pending = null
                            refreshAfterPayment = false
                            val saved = save()
                            update { it.copy(purchasedSkus = skus(records), busySku = null,
                                message = if (saved) "خرید با موفقیت فعال شد." else "خرید فعال شد؛ ذخیره روی دستگاه ناموفق بود. پس از اجرای دوباره، بازیابی را بزنید.",
                                warning = !saved) }
                        }
                    }
                },
                canceled = { if (current(id)) purchaseUncertain("پرداخت لغو شد؛ خریدهای تأییدشدهٔ بازار را بررسی می‌کنیم.") },
                failed = { if (current(id)) purchaseUncertain(BillingErrors.message(it)) },
            )
        } catch (error: Exception) {
            if (current(id)) purchaseUncertain(BillingErrors.message(error))
        }
    }

    fun restore(silent: Boolean = false, reason: String? = null) {
        if (closed || operation != null) return
        if (state.value.connectionState != BillingConnectionState.READY) { start(); return }
        update { it.copy(restoring = true, message = reason ?: "در حال بررسی خریدهای بازار…", warning = false) }
        val recovery = pending
        val id = begin(30_000) { inventoryFailed("بازیابی به‌موقع کامل نشد؛ اینترنت و بازار را بررسی و دوباره تلاش کنید.") }
        try {
            val handle = gateway.inventory(
                succeeded = { receipts ->
                    if (current(id)) {
                        try {
                            // A non-consumable receipt is durable evidence. Empty inventories,
                            // account changes and reconnects must never erase a paid chapter.
                            // Verify the complete response before adding any new entitlement.
                            val incoming = BazaarPurchasePolicy.restoredPurchases(receipts.map(verifier::verify), packageName)
                            val verified = BazaarPurchasePolicy.restoredPurchases(records.map(verifier::verify) + incoming, packageName)
                            val selected = verified.map { SignedBazaarReceipt(it.originalJson, it.dataSignature) }
                            val owned = verified.mapTo(mutableSetOf()) { it.productId }
                            finish(id)
                            records = selected
                            pending = null
                            val saved = save()
                            lastRestore = scheduler.now()
                            val message = when {
                                !saved -> "خریدها بررسی شدند؛ ذخیره روی دستگاه ناموفق بود. بعداً دوباره بازیابی کنید."
                                recovery != null && BazaarProducts.isActive(recovery.sku, owned) -> "خرید قبلی شما تأیید و فعال شد."
                                recovery != null -> "خرید تأییدشده‌ای برای این پرداخت پیدا نشد. اگر مبلغ کسر شده، بعداً بازیابی یا با پشتیبانی پیگیری کنید."
                                owned.isEmpty() && !silent -> "هنوز خرید تأییدشده‌ای پیدا نشد؛ با حساب خریدتان وارد بازار شوید."
                                silent -> null
                                else -> "خریدهای تأییدشده فعال‌اند و روی این دستگاه حفظ می‌شوند."
                            }
                            update { it.copy(purchasedSkus = owned, restoring = false, busySku = null,
                                inventoryChecked = saved, message = message,
                                warning = !saved || (recovery != null && !BazaarProducts.isActive(recovery.sku, owned))) }
                        } catch (error: Exception) {
                            if (current(id)) inventoryFailed(BillingErrors.message(error))
                        }
                    }
                },
                failed = { if (current(id)) inventoryFailed(BillingErrors.message(it)) },
            )
            if (current(id)) inventoryCancel = handle else handle.cancel()
        } catch (error: Exception) {
            if (current(id)) inventoryFailed(BillingErrors.message(error))
        }
    }

    fun clearMessage() = update { it.copy(message = null, warning = false) }

    fun close() {
        if (closed) return
        closed = true
        ++connectionEpoch
        operation?.let(::finish)
        try { gateway.close() } catch (ignored: Exception) { }
    }

    private fun connectionFailed(epoch: Long, message: String) {
        if (closed || epoch != connectionEpoch) return
        ++connectionEpoch
        operation?.let(::finish)
        try { gateway.close() } catch (ignored: Exception) { }
        update { it.copy(connectionState = BillingConnectionState.UNAVAILABLE, busySku = null,
            restoring = false, inventoryChecked = false, message = message, warning = true) }
    }

    private fun purchaseUncertain(message: String) {
        operation?.let(::finish)
        update { it.copy(busySku = null, inventoryChecked = false, message = message, warning = true) }
        restore(reason = message)
    }

    private fun inventoryFailed(message: String) {
        operation?.let(::finish)
        // A failed or partial query cannot revoke cached purchases or grant new ones.
        update { it.copy(restoring = false, busySku = null, inventoryChecked = false, message = message, warning = true) }
    }

    private fun begin(timeout: Int, expired: () -> Unit): Long {
        val id = ++serial
        operation = id
        timer = scheduler.after(timeout.toLong()) { if (current(id)) expired() }
        return id
    }
    private fun current(id: Long) = !closed && operation == id
    private fun finish(id: Long) {
        if (operation != id) return
        operation = null
        timer?.cancel(); timer = null
        inventoryCancel?.cancel(); inventoryCancel = null
    }
    private fun refreshCachedReceipts() {
        val cached = try { storage.load().receipts } catch (ignored: Exception) { emptyList() }
        val verified = cached.mapNotNull { receipt ->
            try { verifier.verify(receipt) } catch (ignored: Exception) { null }
        }
        records = select(records.map(verifier::verify) + verified.filter { BazaarPurchasePolicy.isEligible(it, packageName) })
        update { it.copy(purchasedSkus = skus(records)) }
    }
    private fun save(): Boolean = try { storage.save(StoredBillingData(records, pending)) } catch (error: Exception) { false }
    private fun select(infos: List<ir.cafebazaar.poolakey.entity.PurchaseInfo>) =
        BazaarPurchasePolicy.restoredPurchases(infos, packageName).map { SignedBazaarReceipt(it.originalJson, it.dataSignature) }
    private fun skus(receipts: List<SignedBazaarReceipt>) = receipts.mapTo(mutableSetOf()) { verifier.verify(it).productId }
    private fun notice(message: String, warning: Boolean = false) = update { it.copy(message = message, warning = warning) }
    private fun update(block: (BillingUiState) -> BillingUiState) { if (!closed) mutableState.value = block(mutableState.value) }
}

internal object BillingErrors {
    fun message(error: Exception): String = when (error) {
        is InvalidBazaarReceipt -> "اعتبار خرید تأیید نشد؛ فقط از بازار رسمی استفاده و دوباره بازیابی کنید."
        is InvalidBazaarInventory -> "پاسخ بازیابی ناقص بود؛ خریدهای قبلی حفظ شدند. دوباره تلاش کنید."
        is BazaarServiceResponse -> when (error.code) {
            1 -> "عملیات پرداخت لغو شد؛ خریدهای قبلی را بازیابی کنید."
            2, 6 -> "بازار پاسخ نداد؛ اینترنت را بررسی و دوباره تلاش کنید."
            3 -> "پرداخت برای این حساب در دسترس نیست؛ ورود به بازار و نسخهٔ آن را بررسی کنید."
            4 -> "این محصول فعلاً در بازار در دسترس نیست؛ با پشتیبانی برنامه تماس بگیرید."
            5 -> "تنظیمات این محصول در بازار معتبر نیست؛ با پشتیبانی برنامه تماس بگیرید."
            7 -> "این محتوا قبلاً خریداری شده است؛ خریدهای قبلی را بازیابی کنید."
            8 -> "بازار مالکیت این محصول را تأیید نکرد؛ دوباره بازیابی کنید."
            else -> "بازار خطا برگرداند؛ دوباره بازیابی یا با پشتیبانی پیگیری کنید."
        }
        else -> when (error.javaClass.simpleName) {
            "BazaarNotFoundException" -> "برنامهٔ رسمی بازار را نصب کنید و وارد حساب خریدتان شوید."
            "IAPNotSupportedException", "BazaarNotSupportedException" -> "بازار را به‌روز کنید؛ پرداخت در نسخهٔ فعلی در دسترس نیست."
            "PurchaseHijackedException", "SecurityException" -> "امنیت پرداخت تأیید نشد؛ فقط از بازار رسمی استفاده کنید."
            else -> "عملیات کامل نشد؛ اینترنت و حساب بازار را بررسی و دوباره بازیابی کنید."
        }
    }
}

