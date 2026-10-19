package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class PdfSourceTest {
    @Test fun bitmapAssetsKeepTheirOriginalRoute() { assertNull(PdfSource.parse("g7_ch03_q15_question.webp")) }
    @Test fun multiPageQuestionKeepsThePrintedRegionOrder() {
        val value=PdfSource.parse("pdf:workbook.pdf#9,20,701.3,590,800;10,20,36,590,150.5")!!
        assertEquals("workbook.pdf",value.file);assertEquals(listOf(9,10),value.regions.map { it.page })
        assertEquals(98.7f,value.regions[0].height,.01f)
    }
    @Test fun smallFormulaOrKeyMayUseTightCoordinates() {
        val value=PdfSource.parse("pdf:answers08.pdf#2,8.6,733,38.2,757")!!
        assertEquals(1,value.regions.size);assertEquals(29.6f,value.regions[0].width,.01f)
    }
    @Test fun unsafePathsAndInvalidOrUnboundedRegionsAreRejected() {
        listOf("pdf:../workbook.pdf#1,0,0,50,50","pdf:workbook.pdf#0,0,0,50,50",
            "pdf:workbook.pdf#1.5,0,0,50,50","pdf:workbook.pdf#1,0,0,0,50",
            "pdf:workbook.pdf#1,-1,0,50,50","pdf:workbook.pdf#1,0,0,NaN,50",
            "pdf:workbook.pdf#1,0,0,99999,50").forEach { input ->
            assertTrue(input,runCatching { PdfSource.parse(input) }.isFailure)
        }
    }
}
