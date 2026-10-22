package com.oloomyar.app.billing

import org.junit.Assert.*
import org.junit.Test

class BazaarInventoryPagerTest {
    private val first = SignedBazaarReceipt("first", "signature-1")
    private val second = SignedBazaarReceipt("second", "signature-2")

    @Test fun allPagesIncludingAnEmptyMiddlePageAreReturnedTogether() {
        val requests = mutableListOf<String?>()
        val pages = mapOf(null to BazaarInventoryPage(listOf(first), "next"),
            "next" to BazaarInventoryPage(emptyList(), "last"),
            "last" to BazaarInventoryPage(listOf(second), null))
        assertEquals(listOf(first, second), BazaarInventoryPager.collect { token ->
            requests += token; pages.getValue(token)
        })
        assertEquals(listOf(null, "next", "last"), requests)
    }
    @Test fun duplicateRecordsAcrossPagesAreDeduplicated() {
        assertEquals(listOf(first), BazaarInventoryPager.collect { token ->
            BazaarInventoryPage(listOf(first), if (token == null) "next" else "")
        })
    }
    @Test fun aFailedLaterPageCannotReturnEarlierPartialPurchases() {
        var calls = 0
        assertThrows(BazaarServiceResponse::class.java) {
            BazaarInventoryPager.collect { if (calls++ == 0) BazaarInventoryPage(listOf(first), "next")
                else throw BazaarServiceResponse(6) }
        }
        assertEquals(2, calls)
    }
    @Test fun continuationCyclesAndUnboundedInventoriesAreRejected() {
        assertThrows(InvalidBazaarInventory::class.java) {
            BazaarInventoryPager.collect { BazaarInventoryPage(listOf(first), "same") }
        }
        var page = 0
        assertThrows(InvalidBazaarInventory::class.java) {
            BazaarInventoryPager.collect { BazaarInventoryPage(emptyList(), "page-${++page}") }
        }
        assertEquals(100, page)
    }
    @Test fun anEmptyAuthoritativeInventoryIsACompleteResult() {
        assertTrue(BazaarInventoryPager.collect { BazaarInventoryPage(emptyList(), null) }.isEmpty())
    }
}

