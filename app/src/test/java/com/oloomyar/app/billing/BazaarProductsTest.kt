package com.oloomyar.app.billing
import org.junit.Assert.*
import org.junit.Test
class BazaarProductsTest {
    @Test fun exactlyOnePermanentProductAtTheAgreedPrice() {
        assertEquals(setOf("oloomyar_g7_complete"), BazaarProducts.allSkus)
        assertEquals(195_000, BazaarProducts.completeProduct.priceToman)
        assertEquals(1_950_000, BazaarProducts.completeProduct.priceRial)
        assertEquals("۱۹۵٬۰۰۰ تومان", BazaarProducts.completeProduct.displayPrice)
        assertFalse(BazaarProducts.isActive("oloomyar_g9_complete", setOf(BazaarProducts.COMPLETE)))
    }
}
