package com.oloomyar.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.oloomyar.app.billing.*
import com.oloomyar.app.ui.Grade7ChapterAccess
import com.oloomyar.app.ui.Grade7PurchasePanel
import com.oloomyar.app.ui.theme.OloomYarTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Grade7PurchaseAccessUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productionGateProtectsEveryPaidChapterAndImmediatelyUsesCachedOwnership() {
        val chapter = mutableIntStateOf(1)
        val purchases = mutableStateOf(emptySet<String>())
        compose.setContent { OloomYarTheme {
            Grade7ChapterAccess(chapter.intValue, purchases.value,
                locked = { Text("نیاز به خرید") }, content = { Text("محتوای فصل") })
        } }
        compose.onNodeWithText("محتوای فصل").assertExists()
        (2..15).forEach { id ->
            compose.runOnIdle { chapter.intValue = id }
            compose.onNodeWithText("نیاز به خرید").assertExists()
            compose.onNodeWithText("محتوای فصل").assertDoesNotExist()
        }
        // Grade 9 ownership, made-up individual products and pending states
        // cannot pass the production gate.
        compose.runOnIdle { purchases.value = setOf("oloomyar_g9_complete", "oloomyar_g7_ch15") }
        compose.onNodeWithText("محتوای فصل").assertDoesNotExist()
        compose.runOnIdle { purchases.value = setOf(BazaarProducts.COMPLETE) }
        (1..15).forEach { id ->
            compose.runOnIdle { chapter.intValue = id }
            compose.onNodeWithText("محتوای فصل").assertExists()
            compose.onNodeWithText("نیاز به خرید").assertDoesNotExist()
        }
        compose.runOnIdle { chapter.intValue = 16 }
        compose.onNodeWithText("محتوای فصل").assertDoesNotExist()
    }

    @Test fun oneProductPurchasePanelShowsThePriceAndSerializesItsButtons() {
        val state = mutableStateOf(BillingUiState(connectionState = BillingConnectionState.READY, inventoryChecked = true))
        var purchases = 0
        var restores = 0
        compose.setContent { OloomYarTheme {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Grade7PurchasePanel(state.value,
                    onPurchase = { purchases++; state.value = state.value.copy(busySku = BazaarProducts.COMPLETE) },
                    onRestore = { restores++ }, reviewMode = false)
            }
        } }
        compose.onNodeWithText("۱۹۵٬۰۰۰ تومان").assertExists()
        compose.onNodeWithTag("buy-complete").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("buy-complete").assertIsNotEnabled()
        compose.onNodeWithTag("restore-purchases").assertIsNotEnabled()
        assertEquals(1, purchases); assertEquals(0, restores)
        compose.runOnIdle { state.value = state.value.copy(purchasedSkus = setOf(BazaarProducts.COMPLETE), busySku = null,
            connectionState = BillingConnectionState.UNAVAILABLE, inventoryChecked = false) }
        compose.onNodeWithTag("buy-complete").assertDoesNotExist()
        compose.onNodeWithText("همهٔ فصل‌ها فعال‌اند").assertExists()
        compose.onNodeWithTag("restore-purchases").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, restores)
    }
}
