package com.oloomyar.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.SourceBitmap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Grade7AllChaptersUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun select(s:LearningStep,row:Int,value:String) {
        compose.onNodeWithTag("blank-${s.id}-$row").performScrollTo().performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasTestTag("cloze-choice-$value"))
        compose.onNodeWithTag("cloze-choice-$value").performClick();compose.waitForIdle()
    }
    private fun clear(c:Int) { context.getSharedPreferences("workbook_1405_grade7_chapter$c",Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun restoringAFloatingFigureDoesNotEraseTheQuestionTextOrClipTheFigure() {
        val q=Grade7Catalog(context).chapters.first { it.id==5 }.questions.first { it.sourceNumber==8 }
        assertTrue(q.sourceQuestionVisual!!.contains("|+"))
        val bmp=checkNotNull(SourceBitmap.load(context,q.sourceQuestionVisual!!))
        var textInk=0;var figureInk=0
        for(y in 0 until bmp.height step 2) for(x in 0 until bmp.width step 2) {
            val color=bmp.getPixel(x,y)
            if((color and 0x00ffffff)<0x00888888) {
                if(x>bmp.width*0.65f)textInk++
                if(x<bmp.width*0.4f && y>bmp.height*0.8f)figureInk++
            }
        }
        assertTrue("Source text erased by figure rendering",textInk>30)
        assertTrue("Lower floating figure was clipped",figureInk>10)
    }

    @Test fun lastChapterOpensWithItsSuppliedCoverAndOriginalQuestion() {
        clear(15)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("nav-chapters").performClick()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("chapter-15"))
            compose.onNodeWithContentDescription("جلد فصل 15").assertExists()
            compose.onNodeWithTag("chapter-15").performClick()
            compose.onAllNodesWithText("ورود به این بخش").onFirst().performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("سؤال ۱ •",substring=true))
            compose.onNodeWithText("سؤال ۱ •",substring=true).performClick()
            val q=Grade7Catalog(context).chapters.last().questions.first()
            compose.onNodeWithTag("blank-${q.steps.single().id}-0").assertExists()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }
    @Test fun touchingASelectedBlankClearsOnlyThatCellWithoutAnAttemptAndSurvivesRecreation() {
        clear(7)
        val chapter=Grade7Catalog(context).chapters.first { it.id==7 };val q=chapter.questions.first { it.sourceNumber==8 };val s=q.steps.single()
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",7).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use { activity ->
            select(s,0,"بیشتر");select(s,1,"بزرگ تر")
            compose.onNodeWithTag("blank-${s.id}-0").performScrollTo().performClick()
            val state=QuestionDraftCodec.decode(chapter.progress.draft(q.id))
            assertEquals(listOf("","بزرگ تر",""),state.values(s));assertTrue(state.incorrectSubmissions.isEmpty())
            assertEquals(0,state.shownHints)
            compose.onNodeWithTag("choice-sheet").assertIsDisplayed()
            compose.onNodeWithTag("cloze-choice-کمتر").performClick()
            activity.recreate()
            val restored=QuestionDraftCodec.decode(chapter.progress.draft(q.id))
            assertEquals(listOf("کمتر","بزرگ تر",""),restored.values(s));assertEquals(state.shuffleSeed,restored.shuffleSeed)
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }
    @Test fun correctRouteOpensTheOriginalAnswerAndFullWidthZoomWithoutAutoAdvance() {
        clear(15)
        val chapter=Grade7Catalog(context).chapters.last();val q=chapter.questions.first { it.sourceNumber==8 };val s=q.steps.single()
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",15).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use {
            s.pairs.forEachIndexed { i,p->select(s,i,p.right) }
            compose.onNodeWithText("بررسی پاسخ").performClick()
            compose.onNodeWithTag("reveal-answer").assertIsEnabled()
            compose.onNodeWithText("پاسخ کتاب‌کار").assertDoesNotExist()
            assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("reveal-answer").performClick()
            val description="تصویر پاسخ سؤال ۸"
            compose.waitUntil(20000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
            compose.onNodeWithText("مشاهدهٔ کامل و بزرگ‌نمایی پاسخ").performScrollTo().performClick()
            compose.onNodeWithTag("workbook-image-viewer").assertIsDisplayed()
            compose.onNodeWithText("نمایش کامل").assertIsDisplayed()
            compose.onNodeWithText("بستن").performClick()
            compose.onNodeWithTag("question-body").performScrollToIndex(0)
            compose.onNodeWithTag("previous-question").assertIsEnabled().performClick()
            assertEquals(chapter.questions.first { it.sourceNumber==7 }.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("next-question").performClick()
            assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled() // already explicitly displayed, not reset
        }
    }
    @Test fun reviewAndShareExistOnlyInMoreAndAllFifteenChaptersAreCounted() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertEquals(317,Grade7Catalog(context).chapters.sumOf { it.questions.size })
            compose.onNodeWithTag("share-app").assertDoesNotExist()
            compose.onNodeWithTag("bazaar-review").assertDoesNotExist()
            compose.onNodeWithTag("nav-more").performClick()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("share-app"))
            compose.onNodeWithTag("share-app").assertIsDisplayed()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("bazaar-review"))
            compose.onNodeWithTag("bazaar-review").assertIsDisplayed()
        }
    }
}
