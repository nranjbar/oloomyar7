package com.oloomyar.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.*
import com.oloomyar.app.ui.theme.OloomYarTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Grade7NativeContentUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog get()=Grade7Catalog(context)
    private fun question(ch:Int,n:Int)=catalog.chapters[ch-1].questions.first { it.sourceNumber==n && it.section=="تمرین‌های اصلی" }
    @Before fun reset() {
        context.getSharedPreferences("grade7_learning_settings",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("workbook_1405_native_ui",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun open(q:ChapterQuestion,scale:Float=1f) {
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                OloomYarTheme { AuditedWorkbookApp("فصل ${q.relatedChapter}",listOf(q),store,initialQuestionId=q.id,onExit={}) }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("native-source-question").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun select(s:LearningStep,row:Int,text:String) {
        compose.onNodeWithTag("blank-${s.id}-$row").performScrollTo().performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasTestTag("cloze-choice-$text"))
        compose.onNodeWithTag("cloze-choice-$text").performClick()
    }
    private fun choose(text:String) {
        compose.onNodeWithTag("question-body").performScrollToNode(hasText(workbookBidi(text)))
        compose.onNodeWithText(workbookBidi(text)).performClick()
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,5000)
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Download/grade7-screenshots") }
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun all104QuestionsAndKeysHaveNativeContentAndNoPageScreenshotFallback() {
        val chapters=catalog.chapters.take(5)
        assertEquals(listOf(8,20,31,23,22),chapters.map { it.questions.size })
        chapters.flatMap { it.questions }.forEach { q ->
            assertTrue(q.id,q.nativeQuestion.isNotEmpty());assertTrue(q.id,q.nativeAnswer.isNotEmpty())
            assertTrue(q.id,q.nativeQuestion.none { it.asset==q.sourceQuestionVisual || it.asset==q.answerVisual })
            assertTrue(q.id,q.nativeAnswer.all { it.type in setOf("text","math","table") })
        }
        assertTrue(catalog.chapters.drop(5).flatMap { it.questions }.all { it.nativeQuestion.isEmpty() })
    }
    @Test fun everyFirstFiveQuestionRendersOnAndroidIncludingAllTableAndReactionSchemas() {
        val questions=catalog.chapters.take(5).flatMap { it.questions }
        val current=mutableStateOf(questions.first())
        compose.setContent {
            OloomYarTheme {
                key(current.value.id) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        NativeWorkbookContent(current.value,current.value.nativeQuestion,QuestionPractice(shuffleSeed=1405))
                    }
                }
            }
        }
        questions.forEach { q ->
            compose.runOnIdle { current.value=q }
            compose.onNodeWithTag("native-source-question").assertExists()
            compose.onNodeWithTag("native-reference-answer").assertDoesNotExist()
        }
    }
    @Test fun tenTableCellsCanBeEditedInPlaceAtLargeFontWithoutUnlockingAnIncompleteAnswer() {
        val q=question(5,2); val s=q.steps.single();open(q,1.5f)
        select(s,0,s.pairs[0].right);select(s,1,"کمتر")
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        compose.onNodeWithTag("blank-${s.id}-0").performScrollTo().performClick()
        compose.onNodeWithText("بستن").performClick()
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        val cleared=QuestionDraftCodec.decode(store.draft(q.id),q)
        assertEquals("",cleared.values(s)[0]);assertEquals("کمتر",cleared.values(s)[1]);assertEquals(0,cleared.shownHints)
        s.pairs.forEachIndexed { row,pair -> select(s,row,pair.right) }
        compose.onNodeWithText("بررسی پاسخ").assertIsEnabled().performClick()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled()
        compose.onNodeWithTag("native-reference-answer").assertDoesNotExist()
        compose.onNodeWithTag("reveal-answer").performClick()
        compose.onNodeWithTag("native-reference-answer").assertExists()
        compose.onNode(hasTestTag("native-source-table") and hasAnyAncestor(hasTestTag("native-reference-answer"))).assertExists()
        capture("native-01-table-font150")
    }
    @Test fun studentBuildsAllFiveBarsAndThenSeesOnlyThePrintedInstructions() {
        val q=question(5,11);val s=q.steps.single();open(q)
        compose.onNodeWithTag("student-bar-chart").performScrollTo().assertIsDisplayed()
        assertTrue(QuestionDraftCodec.decode(WorkbookProgress(context,"native_ui",listOf(q)).draft(q.id),q).values(s).isEmpty())
        s.pairs.forEachIndexed { row,pair -> select(s,row,pair.right) }
        compose.onNodeWithText("بررسی پاسخ").performClick()
        compose.onNodeWithTag("reveal-answer").performClick()
        compose.onNodeWithText(workbookBidi(q.nativeAnswer.single().text)).performScrollTo().assertIsDisplayed()
        capture("native-02-student-chart-and-print-key")
    }
    @Test fun directSolveShowsNativeFractionsAndPreservesBothStepsWithoutAnAnswerImage() {
        val q=question(2,9);open(q)
        q.steps.forEach { s -> choose(s.options.first { it.correct }.text) }
        compose.onNodeWithText("بررسی پاسخ").performClick()
        compose.onNodeWithTag("native-reference-answer").assertDoesNotExist()
        compose.onNodeWithTag("reveal-answer").performClick()
        compose.onAllNodesWithTag("native-equation").assertCountEquals(2)
        compose.onAllNodesWithTag("native-equation")[1].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(workbookBidi("36")).assertIsDisplayed()
        compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۹").assertDoesNotExist()
        capture("native-03-density-real-fractions")
    }
    @Test fun quantityDefinitionIsSelectedInsideItsTableCellAtLargeFont() {
        val q=question(2,2); val step=q.steps[2];open(q,1.7f)
        compose.onNodeWithText("انتخاب").performScrollTo().performClick()
        val correct=step.options.first { it.correct }.text
        compose.onNodeWithText(workbookBidi(correct)).performScrollTo().performClick()
        val state=QuestionDraftCodec.decode(WorkbookProgress(context,"native_ui",listOf(q)).draft(q.id),q)
        assertEquals(listOf(step.options.indexOfFirst { it.correct }.toString()),state.values(step))
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        capture("native-06-quantity-table-font170")
    }
    @Test fun scientificRasterRegionsPreserveEverySourcePixelWithoutAnotherPackagedImage() {
        val q=question(3,15)
        val crops=q.nativeQuestion.mapNotNull { it.asset }.mapNotNull { a -> RasterSource.parse(a)?.let { a to it } }
        assertEquals(2,crops.size)
        crops.forEach { (asset,r) ->
            val original=checkNotNull(SourceBitmap.load(context,r.file)); val region=checkNotNull(SourceBitmap.load(context,asset))
            assertEquals(r.width,region.width);assertEquals(r.height,region.height)
            val a=IntArray(r.width*r.height);val b=IntArray(a.size)
            original.getPixels(a,0,r.width,r.left,r.top,r.width,r.height);region.getPixels(b,0,r.width,0,0,r.width,r.height)
            assertArrayEquals(a,b)
        }
        open(q);compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        capture("native-04-scientific-map")
    }
    @Test fun oldAlloySelectionsMigrateToSeparateCellsWithoutFabricatingChoices() {
        val q=question(4,17);val s=q.steps.single()
        val old=listOf("آهن و نیکل و کروم","ساخت قاشق و چنگال","کربن و آهن","ساخت کلنگ و تیشه و تبر")
        val state=QuestionPractice(answers=mapOf(s.id to old),checked=mapOf(s.id to old),shuffleSeed=142)
        val migrated=QuestionDraftCodec.decode(QuestionDraftCodec.encode(state),q)
        assertEquals(s.pairs.map { it.right },migrated.values(s));assertTrue(migrated.correct(q));assertEquals(142,migrated.shuffleSeed)
        assertFalse(migrated.revealed)
        val equipment=question(2,15);val names=equipment.steps.first()
        val oldNames=listOf("ترازو","استوانه مدرج","انواع نیروسنج")
        val oldEquipment=QuestionPractice(answers=equipment.steps.associate { it.id to if(it.id==names.id)oldNames else it.pairs.map { p->p.right } },
            checked=equipment.steps.associate { it.id to if(it.id==names.id)oldNames else it.pairs.map { p->p.right } },shuffleSeed=142)
        val restored=QuestionDraftCodec.decode(QuestionDraftCodec.encode(oldEquipment),equipment)
        assertEquals("نیروسنج",restored.values(names)[2]);assertTrue(restored.correct(equipment));assertFalse(restored.revealed)
    }
    @Test fun questionSearchOpensTheSourceIdRatherThanTheFilteredIndex() {
        val chapter=catalog.chapters[3];val q=chapter.questions.first { it.sourceNumber==15 }
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",4).putExtra("section","تمرین‌های اصلی")
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use {
            compose.onNodeWithTag("question-search").performScrollTo().performTextInput("مغز مداد")
            compose.onNodeWithTag("question-list-${q.id}").performScrollTo().performClick()
            compose.onNodeWithTag("native-source-question").assertExists()
            assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithContentDescription("صورت سؤال ۱۵ از کتاب").assertDoesNotExist()
            capture("native-05-filtered-question")
        }
    }
}
