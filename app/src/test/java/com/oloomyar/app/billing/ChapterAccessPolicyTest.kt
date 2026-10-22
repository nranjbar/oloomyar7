package com.oloomyar.app.billing
import org.junit.Assert.*
import org.junit.Test
class ChapterAccessPolicyTest {
    @Test fun onlyChapterOneIsFreeAndBundleUnlocksAllPaidChapters() {
        (1..15).forEach {
            assertEquals(it == 1, ChapterAccessPolicy.isUnlocked(it, emptySet()))
            assertTrue(ChapterAccessPolicy.isUnlocked(it, setOf(BazaarProducts.COMPLETE)))
            assertEquals(it == 1, ChapterAccessPolicy.isUnlocked(it, setOf("oloomyar_g9_complete", "oloomyar_g7_ch02")))
        }
        listOf(-1,0,16,101,102).forEach { assertFalse(ChapterAccessPolicy.isUnlocked(it, setOf(BazaarProducts.COMPLETE))) }
    }
}
