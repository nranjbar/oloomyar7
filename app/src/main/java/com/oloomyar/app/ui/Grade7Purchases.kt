package com.oloomyar.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oloomyar.app.BuildConfig
import com.oloomyar.app.billing.*

@Composable
internal fun rememberGrade7Billing(): BazaarBillingManager {
    val activity = LocalContext.current as ComponentActivity
    val manager = remember(activity) { BazaarBillingManager(activity, BuildConfig.BAZAAR_RSA_PUBLIC_KEY) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(manager, lifecycle) {
        // Cached signed receipts have already been read by the constructor.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !BuildConfig.REVIEW_MODE) manager.onForeground()
        }
        lifecycle.lifecycle.addObserver(observer)
        if (!BuildConfig.REVIEW_MODE) manager.start()
        onDispose { lifecycle.lifecycle.removeObserver(observer); manager.close() }
    }
    return manager
}

@Composable
internal fun Grade7PurchasePanel(state: BillingUiState, onPurchase: () -> Unit, onRestore: () -> Unit,
                                reviewMode: Boolean = BuildConfig.REVIEW_MODE) {
    val owned = BazaarProducts.COMPLETE in state.purchasedSkus
    WorkbookPanel {
        Text(if (owned) "همهٔ فصل‌ها فعال‌اند" else "همهٔ فصل‌های علوم‌یار هفتم", style = MaterialTheme.typography.titleLarge)
        Text("فصل ۱ رایگان است. با یک خرید دائمی، فصل‌های ۲ تا ۱۵ باز می‌شوند.")
        if (owned) Text("خرید روی این دستگاه ذخیره شده؛ برای استفادهٔ بعدی و آفلاین نیازی به بازیابی نیست.")
        else {
            Text(BazaarProducts.completeProduct.displayPrice, style = MaterialTheme.typography.titleLarge)
            Button(onClick = onPurchase, enabled = !reviewMode && state.canPurchase,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("buy-complete")) {
                Text(if (state.busySku != null) "در انتظار پرداخت…" else "خرید همهٔ فصل‌ها")
            }
        }
        if (state.restoring || state.connectionState == BillingConnectionState.CONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, modifier = Modifier.testTag("billing-message")) }
        OutlinedButton(onClick = onRestore, enabled = !reviewMode && state.canRestore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("restore-purchases")) { Text("بازیابی خرید قبلی") }
        Text("پس از نصب دوباره یا روی گوشی جدید، با حساب قبلی بازار خرید را بازیابی کنید.", style = MaterialTheme.typography.bodySmall)
        if (reviewMode) Text("نسخهٔ آزمایشی: فصل‌ها برای بررسی باز هستند؛ پرداخت واقعی در نسخهٔ انتشار انجام می‌شود.")
    }
}

/** Every chapter activity, including a restored activity/task, goes through this gate. */
@Composable
internal fun Grade7ChapterGate(chapter: Int, content: @Composable () -> Unit) {
    if ((BuildConfig.REVIEW_MODE && chapter in 1..15) || chapter == 1) { content(); return }
    val activity = LocalContext.current as ComponentActivity
    val billing = rememberGrade7Billing()
    val state by billing.state.collectAsState()
    Grade7ChapterAccess(chapter, state.purchasedSkus, locked = { OloomScreen {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(18.dp)) {
            TextButton(onClick = { activity.finish() }) { Text("بازگشت") }
            Grade7PurchasePanel(state, { billing.purchase(BazaarProducts.COMPLETE) }, { billing.restorePurchases() })
        }
    } }, content = content)
}

/** The production gate is also exercised with signed fixture receipts in UI tests. */
@Composable
internal fun Grade7ChapterAccess(chapter: Int, purchasedSkus: Set<String>,
                                locked: @Composable () -> Unit, content: @Composable () -> Unit) {
    if (ChapterAccessPolicy.isUnlocked(chapter, purchasedSkus)) content() else locked()
}
