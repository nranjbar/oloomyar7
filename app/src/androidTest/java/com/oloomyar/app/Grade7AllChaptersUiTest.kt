package com.oloomyar.app

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
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
    private fun capture(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,5000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Download/grade7-screenshots")
        }
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun select(s:LearningStep,row:Int,value:String) {
        compose.onNodeWithTag("blank-${s.id}-$row").performScrollTo().performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasTestTag("cloze-choice-$value"))
        compose.onNodeWithTag("cloze-choice-$value").performClick();compose.waitForIdle()
    }
    private fun clear(c:Int) { context.getSharedPreferences("workbook_1405_grade7_chapter$c",Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun suppliedLaunchArtworkNeedsNoButtonAndAutomaticallyOpensHome() {
        compose.mainClock.autoAdvance=false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                compose.waitUntil(10000) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.onAllNodesWithTag("launch-artwork").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("launch-artwork").assertIsDisplayed().assertHasNoClickAction()
                compose.onNodeWithText("شروع").assertDoesNotExist()
                compose.onNodeWithTag("hub-list").assertDoesNotExist()
                compose.mainClock.advanceTimeBy(1000)
                compose.onNodeWithTag("launch-artwork").assertIsDisplayed()
                capture("grade7-launch-supplied-art-no-start")
                compose.mainClock.advanceTimeBy(2100)
                compose.awaitGrade7Home()
                compose.onNodeWithTag("launch-splash").assertDoesNotExist()
                compose.mainClock.autoAdvance=true
                activity.recreate()
                compose.awaitGrade7Home()
                compose.onNodeWithTag("launch-splash").assertDoesNotExist()
            }
        } finally { compose.mainClock.autoAdvance=true }
    }

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
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-chapters").performClick()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("chapter-15"))
            compose.onNodeWithContentDescription("جلد فصل 15").assertExists()
            capture("grade7-15-supplied-cover")
            compose.onNodeWithTag("chapter-15").performClick()
            compose.onNodeWithTag("chapter-study-cover").assertExists()
            capture("v09-chapter-15-study-cover")
            compose.onNodeWithTag("chapter-study-continue").assertDoesNotExist()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("chapter-section-تمرین‌های اصلی"))
            compose.onNodeWithTag("chapter-section-تمرین‌های اصلی").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("سؤال ۱", substring = true))
            compose.onAllNodesWithText("سؤال ۱", substring = true)[0].performClick()
            val q=Grade7Catalog(context).chapters.last().questions.first()
            compose.onNodeWithTag("blank-${q.steps.single().id}-0").assertExists()
            compose.onNodeWithTag("native-source-question").assertExists()
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
            capture("grade7-7-clear-one-blank")
            compose.onNodeWithTag("cloze-choice-کمتر").performClick()
            activity.recreate()
            val restored=QuestionDraftCodec.decode(chapter.progress.draft(q.id))
            assertEquals(listOf("کمتر","بزرگ تر",""),restored.values(s));assertEquals(state.shuffleSeed,restored.shuffleSeed)
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }
    @Test fun correctRouteOpensTheNativeLiteralKeyAndFigureZoomWithoutAutoAdvance() {
        clear(15)
        val chapter=Grade7Catalog(context).chapters.last();val q=chapter.questions.first { it.sourceNumber==8 };val s=q.steps.single()
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",15).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use {
            s.pairs.forEachIndexed { i,p->select(s,i,p.right) }
            compose.onNodeWithText("بررسی پاسخ").performClick()
            compose.onNodeWithTag("reveal-answer").assertIsEnabled()
            compose.onNodeWithText("پاسخ کتاب‌کار").assertExists()
            assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("reveal-answer").performClick()
            compose.onNodeWithTag("native-reference-answer").assertExists()
            compose.onNodeWithText(com.oloomyar.app.ui.workbookBidi(q.nativeAnswer.single().text)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۸").assertDoesNotExist()
            val result=chapter.progress.snapshot().first { it.id==q.id }
            assertEquals(CompletionKind.INDEPENDENT,result.kind);assertFalse(result.mistake);assertTrue(result.viewedAnswer)
            capture("grade7-15-solved-original-answer")
            val figure=q.nativeQuestion.first { it.type=="figure" }.asset!!
            val figureTag="workbook-image-enlarge-$figure"
            compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag(figureTag))
            compose.onNodeWithTag(figureTag).performScrollTo()
            compose.waitUntil(10000) { compose.onAllNodesWithTag(figureTag).fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==false }
            compose.onNodeWithTag(figureTag).performClick()
            compose.onNodeWithTag("workbook-image-viewer").assertIsDisplayed()
            compose.onNodeWithText("نمایش کامل").assertIsDisplayed()
            compose.onNodeWithText("بستن").performClick()
            compose.onNodeWithTag("question-body").performScrollToIndex(0)
            compose.onNodeWithTag("previous-question").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(10000) { LearningPreferences(context).lastLocation()?.questionId==chapter.questions.first { it.sourceNumber==7 }.id }
            assertEquals(chapter.questions.first { it.sourceNumber==7 }.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("next-question").assertIsDisplayed().performClick()
            compose.waitUntil(10000) { LearningPreferences(context).lastLocation()?.questionId==q.id }
            assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("workbook-reference-answer"))
            compose.onNodeWithTag("workbook-reference-answer").assertExists() // correct answer survives returning
            compose.onNodeWithTag("reveal-answer").assertIsEnabled() // shortcut to the same already-visible answer
        }
    }
    @Test fun reviewAndShareExistOnlyInMoreAndAllFifteenChaptersAreCounted() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
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
