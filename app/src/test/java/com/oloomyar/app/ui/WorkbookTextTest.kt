package com.oloomyar.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookTextTest {
    @Test fun persianArithmeticKeepsItsOperatorLeftToRightInsidePersianProse() {
        listOf(
            "جرم کلر از تفاضل ۱۹٫۶−۷٫۷ به دست می‌آید." to "۱۹٫۶−۷٫۷",
            "سرعت از ۹۰÷۱۲۰ به دست می‌آید." to "۹۰÷۱۲۰",
            "قطر ۲×۴=۸ متر است." to "۲×۴=۸",
            "المقدار ١٩٫٦−٧٫٧ محسوب است." to "١٩٫٦−٧٫٧"
        ).forEach { (source, formula) ->
            val displayed=workbookBidi(source)
            assertTrue("formula was not isolated: $source", displayed.contains("\u2066$formula\u2069"))
        }
    }

    @Test fun ionChargesAndMixedDigitEquationsRemainLeftToRight() {
        val displayed=workbookBidi("یون Cl⁻ پایدار است؛ جرم از 19.6 − 7.7 = 11.9 g به دست می‌آید.")
        assertTrue(displayed.contains("\u2066Cl⁻\u2069"))
        assertTrue(displayed.contains("\u206619.6 − 7.7 = 11.9 g\u2069"))
    }

    @Test fun reactionArrowKeepsItsDirectionBetweenPersianSubstanceNames() {
        val displayed=workbookBidi("سدیم + کلر → سدیم کلرید")
        assertTrue(displayed.startsWith("\u2066"))
        assertTrue(displayed.contains("\u2067سدیم \u2069"))
        assertTrue(displayed.contains("\u2067 کلر \u2069"))
        assertTrue(displayed.contains("→"))
    }
}

