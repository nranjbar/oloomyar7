package com.oloomyar.app

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.oloomyar.app.data.Grade7Chapter2Repository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Grade7ZoomDirectionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun physicalLeftAndRightDragsFollowTheFingerInThePersianViewer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 11 && it.section == "تمرین‌های اصلی" }
        ActivityScenario.launch<Grade7Chapter2Activity>(Intent(context, Grade7Chapter2Activity::class.java).putExtra("question_id",q.id)).use {
            compose.onNodeWithTag("question-body").performScrollToNode(hasText("بزرگ‌نمایی تصویر"))
            compose.waitUntil(10000) { compose.onAllNodes(hasText("بزرگ‌نمایی تصویر") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("بزرگ‌نمایی تصویر")[0].performClick()
            repeat(3) { compose.onNodeWithContentDescription("بزرگ‌تر").performClick() }
            val scroller = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            fun position() = scroller.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            val before = position()
            scroller.performTouchInput { swipe(Offset(width*.8f,height*.5f),Offset(width*.2f,height*.5f),500) }
            compose.waitForIdle()
            val afterLeft = position()
            assertTrue("Dragging image left must expose the right side", afterLeft > before + 20)
            scroller.performTouchInput { swipe(Offset(width*.2f,height*.5f),Offset(width*.8f,height*.5f),500) }
            compose.waitForIdle()
            assertTrue("Dragging image right must move it right", position() < afterLeft - 20)
        }
    }
}
