package com.oloomyar.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.workbookBidi
import com.oloomyar.app.ui.theme.OloomYarTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.rules.TestName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Grade7WorkbookUiTest {
    @get:Rule val compose=createComposeRule()
    @get:Rule val testName=TestName()
    @After fun captureFinalUi() { capture("after-${testName.methodName}") }
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val allQuestions get()=Grade7Chapter1Repository(context).questions+Grade7Chapter2Repository(context).questions+Grade7Chapter3Repository(context).questions
    private fun fa(v:Int)=v.toString().map { if(it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it }.joinToString("")

    @Before fun reset() {
        context.getSharedPreferences("workbook_1405_g7_ui",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun open(q:ChapterQuestion, fontScale:Float=1f, progress:WorkbookProgress=WorkbookProgress(context,"g7_ui",listOf(q))) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                OloomYarTheme { AuditedWorkbookApp("فصل ${q.relatedChapter}",listOf(q),progress,onExit={}) }
            }
        }
        compose.onNodeWithText("ورود به این بخش").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("سؤال ${fa(q.sourceNumber)} •",substring=true))
        compose.onNodeWithText("سؤال ${fa(q.sourceNumber)} •",substring=true).performClick()
        compose.onNodeWithTag("reveal-answer").assertIsDisplayed().assertIsNotEnabled()
    }
    private fun blank(s:LearningStep,row:Int)=compose.onNodeWithTag("blank-${s.id}-$row")
    private fun selectBlank(s:LearningStep,row:Int,value:String) {
        blank(s,row).performScrollTo().performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasTestTag("cloze-choice-$value"))
        compose.onNodeWithTag("cloze-choice-$value").performClick()
        compose.waitForIdle()
    }
    private fun choose(value:String) {
        val match=hasText(workbookBidi(value))
        compose.onNodeWithTag("question-body").performScrollToNode(match)
        compose.onNode(match).performClick()
    }
    private fun check() { compose.onNodeWithText("بررسی پاسخ").assertIsEnabled().performClick();compose.waitForIdle() }
    private fun capture(name:String) {
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Download/grade7-screenshots") }
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun realHomeOpensTheThreeImplementedChapters() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("علوم‌یار هفتم").assertIsDisplayed();capture("grade7-01-home")
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("اتم‌ها، الفبای مواد"))
            compose.onNodeWithText("اتم‌ها، الفبای مواد").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("چهارگزینه‌ای"))
            compose.onNodeWithText("چهارگزینه‌ای").assertExists()
        }
    }
    @Test fun blankSelectionStaysAtTheChosenBlankAndIsYellowBeforeChecking() {
        val q=Grade7Chapter1Repository(context).questions.first();val s=q.steps.single();open(q)
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        selectBlank(s,0,"دانش")
        blank(s,0).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"انتخاب شده: دانش"))
        blank(s,1).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"بی‌پاسخ"))
        compose.onNodeWithTag("choice-sheet").assertDoesNotExist();capture("grade7-02-inline-selection")
    }
    @Test fun fullCheckColorsRowsLocksCorrectOnesAndNeedsTwoDistinctFailures() {
        val q=Grade7Chapter1Repository(context).questions.first();val s=q.steps.single();open(q)
        listOf("دانش","کنجکاوی","فناوری").forEachIndexed { i,v->selectBlank(s,i,v) };check()
        blank(s,0).assertIsNotEnabled()
        blank(s,1).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"اشتباه: کنجکاوی"))
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled();capture("grade7-03-per-blank-feedback")
        selectBlank(s,1,"فرضیه سازی");check()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled()
        compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۱").assertDoesNotExist()
        compose.onNodeWithTag("reveal-answer").performClick()
        compose.onNodeWithTag("question-body").performScrollToNode(hasText("پاسخ کتاب‌کار"))
        compose.onNodeWithText("پاسخ کتاب‌کار").assertExists();capture("grade7-04-original-answer")
    }
    @Test fun correctSubmissionWaitsForExplicitNextAndDoesNotRevealTheKey() {
        val q=Grade7Chapter1Repository(context).questions.first();val s=q.steps.single();open(q)
        listOf("دانش","آزمایش","فناوری").forEachIndexed { i,v->selectBlank(s,i,v) };check()
        compose.onNodeWithText("سؤال بعدی").assertIsDisplayed()
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        compose.onNodeWithText("پاسخ کتاب‌کار").assertDoesNotExist()
        compose.onNodeWithText("تمرین کامل شد").assertDoesNotExist()
        compose.onNodeWithText("سؤال بعدی").performClick()
        assertTrue(WorkbookProgress(context,"g7_ui",listOf(q)).done(q.id))
    }
    @Test fun selectionsFeedbackHintsAndRandomSeedSurviveLeavingAndReturning() {
        val q=Grade7Chapter1Repository(context).questions.first();val s=q.steps.single();open(q)
        listOf("دانش","کنجکاوی","فناوری").forEachIndexed { i,v->selectBlank(s,i,v) };check()
        val progress=WorkbookProgress(context,"g7_ui",listOf(q));val before=QuestionDraftCodec.decode(progress.draft(q.id))
        compose.onNodeWithContentDescription("برگشت").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("سؤال ۱ •",substring=true))
        compose.onNodeWithText("سؤال ۱ •",substring=true).performClick()
        val after=QuestionDraftCodec.decode(progress.draft(q.id))
        assertEquals(before.answers,after.answers);assertEquals(before.checked,after.checked)
        assertEquals(before.shuffleSeed,after.shuffleSeed);assertEquals(1,after.shownHints)
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
    }
    @Test fun chapterThreeAllowsTheRepeatedSourceWordAndKeepsAllEightBlanks() {
        val q=Grade7Chapter3Repository(context).questions.first();val s=q.steps.single();open(q)
        s.pairs.forEachIndexed { i,p->selectBlank(s,i,p.right) };check()
        compose.onNodeWithText("سؤال بعدی").assertIsEnabled()
        val draft=QuestionDraftCodec.decode(WorkbookProgress(context,"g7_ui",listOf(q)).draft(q.id))
        assertEquals(2,draft.values(s).count { it=="اتم" });assertTrue(draft.correct(q))
        capture("grade7-05-chapter3-inline")
    }
    @Test fun allPartsMustBeAnsweredBeforeTheWholeQuestionCanBeChecked() {
        val q=Grade7Chapter2Repository(context).questions.first { it.sourceNumber==9 && it.section=="تمرین‌های اصلی" };open(q)
        val wrong0=q.steps[0].options.filter { !it.correct };val wrong1=q.steps[1].options.first { !it.correct }
        choose(wrong0[0].text);compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        choose(wrong1.text);check();compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        choose(wrong0[1].text);check();compose.onNodeWithTag("reveal-answer").assertIsEnabled()
        compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۹").assertDoesNotExist()
        compose.onNodeWithTag("reveal-answer").performClick()
        compose.onNodeWithTag("question-body").performScrollToNode(hasText("پاسخ کتاب‌کار"))
        capture("grade7-06-original-formula")
    }
    @Test fun enlargedPersianFontKeepsTheBlankAndActionControlsUsable() {
        val q=Grade7Chapter1Repository(context).questions.first();open(q,1.7f)
        selectBlank(q.steps.single(),0,"دانش")
        compose.onNodeWithText("بررسی پاسخ").assertIsDisplayed()
        compose.onNodeWithTag("reveal-answer").assertIsDisplayed();capture("grade7-07-large-font")
    }
    @Test fun oldDraftSelectionsSurviveButOldRevealAndAttemptsCannotUnlockTheAnswer() {
        val q=Grade7Chapter2Repository(context).questions.first { it.sourceNumber==8 && it.section=="تمرین‌های اصلی" }
        val progress=WorkbookProgress(context,"g7_ui",listOf(q))
        progress.saveDraft(q.id,"""{"answers":{"${q.steps.single().id}":["1"]},"status":{"${q.steps.single().id}":3},"attempts":{"${q.steps.single().id}":2},"hints":{"${q.steps.single().id}":2}}""")
        open(q,progress=progress)
        val draft=QuestionDraftCodec.decode(progress.draft(q.id));assertEquals(listOf("1"),draft.values(q.steps.single()))
        assertFalse(draft.canReveal());assertFalse(draft.revealed)
    }
    @Test fun allFiftyNineOriginalQuestionsAndIsolatedAnswersDecodeAndAllControlsHaveAValidAnswer() {
        val qs=allQuestions;assertEquals(59,qs.size);assertEquals(76,qs.sumOf { it.steps.size })
        qs.flatMap { listOfNotNull(it.sourceQuestionVisual,it.answerVisual,it.visual,it.promptVisual)+it.visuals.map { v->v.asset } }.toSet().forEach { name->
            val bmp=context.assets.open("images/$name").use { BitmapFactory.decodeStream(it) };assertNotNull(name,bmp);bmp!!.recycle()
        }
        qs.forEach { q->
            var state=QuestionPractice()
            q.steps.forEach { s->
                val answers=when(s.kind) {
                    StepKind.SINGLE->listOf(s.options.indexOfFirst { it.correct }.toString())
                    StepKind.MULTI->s.options.indices.filter { s.options[it].correct }.take(s.pick).map { it.toString() }
                    StepKind.MATCH->s.pairs.map { it.right }
                    StepKind.CLASSIFY->s.classifyItems.map { it.category }
                    StepKind.ORDER->s.correctOrder.map { it.toString() }
                };state=state.change(s,answers)
            }
            assertTrue(q.id,state.complete(q));assertTrue(q.id,state.submit(q).correct(q))
        }
    }
    @Test fun draftSerializationRetainsHistoryAndOnlyAcceptsAValidReveal() {
        val q=Grade7Chapter2Repository(context).questions.first { it.sourceNumber==8 && it.section=="تمرین‌های اصلی" };val s=q.steps.single()
        val wrong=s.options.indices.filter { !s.options[it].correct }
        val state=QuestionPractice(shuffleSeed=42).change(s,listOf(wrong[0].toString())).submit(q).change(s,listOf(wrong[1].toString())).submit(q)
        assertEquals(state,QuestionDraftCodec.decode(QuestionDraftCodec.encode(state)))
        val shown=state.reveal();assertEquals(shown,QuestionDraftCodec.decode(QuestionDraftCodec.encode(shown)))
        assertFalse(QuestionDraftCodec.decode("""{"questionPracticeVersion":3,"revealed":true}""").revealed)
    }
    @Test fun examResultDoesNotProvideAReferenceAnswerShortcut() {
        val q=Grade7Chapter2Repository(context).questions.first { it.sourceNumber==8 && it.section=="تمرین‌های اصلی" }
        open(q)
        compose.onNodeWithContentDescription("برگشت").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("آزمون همین بخش"))
        compose.onNodeWithText("آزمون همین بخش").performClick()
        choose(q.steps.single().options.first { !it.correct }.text)
        compose.onNodeWithText("ثبت پاسخ این سؤال").performClick()
        compose.onNodeWithText("نتیجهٔ آزمون").assertExists()
        compose.onNodeWithText("پاسخ کتاب‌کار").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("تمرین سؤال ۸ با راهنما"))
        compose.onNodeWithText("تمرین سؤال ۸ با راهنما").performClick()
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
    }
    @Test fun sourceFigureCanBeEnlargedWithoutShowingAnyAnswer() {
        val q=Grade7Chapter2Repository(context).questions.first { it.sourceNumber==11 && it.section=="تمرین‌های اصلی" };open(q)
        compose.onNodeWithTag("question-body").performScrollToNode(hasText("بزرگ‌نمایی تصویر"))
        compose.onAllNodesWithText("بزرگ‌نمایی تصویر")[0].performScrollTo().performClick()
        compose.onNodeWithText("بزرگ‌تر").assertIsDisplayed();capture("grade7-08-source-zoom")
        compose.onNodeWithText("بستن").performClick();compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
    }
}
