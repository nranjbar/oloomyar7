package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class ClozeDocumentTest {
    @Test fun persianLatinUnitsPunctuationAndSourceSpacingRoundTripExactly() {
        val source = "الف– با یکای {{0}} اندازه می گیریم .\nb– A,B,C و ۱۲ cm³ = {{1}} ."
        val doc = ClozeDocument.parse(source, "step-1405")
        assertEquals(source, doc.toTemplate())
        assertEquals(listOf("step-1405-blank-0", "step-1405-blank-1"), doc.tokens.filterIsInstance<ClozeToken.Blank>().map { it.id })
        assertTrue(doc.tokens.filterIsInstance<ClozeToken.Text>().any { "و ۱۲ cm³" in it.value })
    }
    @Test fun adjacentSlotsAndLiteralPrintedDotsAreNotLost() {
        val source = "................{{0}}{{1}} و ...."
        assertEquals(source,ClozeDocument.parse(source,"s").toTemplate())
        assertEquals(2,ClozeDocument.parse(source,"s").tokens.filterIsInstance<ClozeToken.Blank>().size)
    }
    @Test fun sourceWithoutSlotsRemainsUnchanged() {
        val source = "صورت سؤال ( ص ) و ( غ )"
        assertEquals(source,ClozeDocument.parse(source,"s").toTemplate())
    }
}
