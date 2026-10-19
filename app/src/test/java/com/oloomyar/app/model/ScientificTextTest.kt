package com.oloomyar.app.model

import com.oloomyar.app.ui.workbookBidi
import org.junit.Assert.*
import org.junit.Test

class ScientificTextTest {
    private fun visible(text:String)=text.replace(Regex("[\\u2066-\\u2069]"),"")
    @Test fun isolatesDoNotRewriteAnyPersianWordNumberFormulaOrPunctuation() {
        listOf("آب ۱۲ cm³ و Fe₂O₃ و 3/4 و B و ۱٫۳ متر", "e⁻ ، p⁺ ، n و CO₂",
            "آهن < آلومینیم < مس", "جیوه > آلیاژ برنج > آب > چوب پنبه",
            "کربن + آهن اکسید → فلز آهن + کربن دی اکسید").forEach { source ->
            assertEquals(source,visible(workbookBidi(source)))
        }
    }
    @Test fun comparisonRetainsLiteralSignsAndScientificRunGetsLtrIsolation() {
        val output=workbookBidi("مس > آلومینیم > آهن")
        assertTrue(output.startsWith("\u2066"));assertEquals(2,output.count { it=='>' })
        assertTrue(workbookBidi("جرم 12 g").contains("\u206612 g\u2069"))
    }
}
