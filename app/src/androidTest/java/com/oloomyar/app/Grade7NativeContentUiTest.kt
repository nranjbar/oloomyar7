package com.oloomyar.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.json.JSONObject
import org.json.JSONArray
import android.provider.MediaStore
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
    /** Send a real touch only after the control is inside the question viewport. */
    private fun controlInQuestion(tag:String):SemanticsNodeInteraction {
        val control=compose.onNodeWithTag(tag)
        control.performScrollTo()
        val bounds=control.getUnclippedBoundsInRoot()
        val body=compose.onNodeWithTag("question-body")
        val viewport=body.getUnclippedBoundsInRoot()
        val delta=with(compose.density) { ((bounds.top+bounds.bottom-viewport.top-viewport.bottom)/2).toPx() }
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,delta) }
        return control.assertIsDisplayed()
    }
    private fun select(s:LearningStep,row:Int,text:String) {
        controlInQuestion("blank-${s.id}-$row").performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasTestTag("cloze-choice-$text"))
        compose.onNodeWithTag("cloze-choice-$text").assertIsDisplayed().performClick()
    }
    private fun choose(text:String) {
        compose.onNodeWithTag("question-body").performScrollToNode(hasText(workbookBidi(text)))
        compose.onNodeWithText(workbookBidi(text)).performClick()
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,5000)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)
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
        controlInQuestion("blank-${s.id}-0").performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasText("بستن"))
        compose.onNodeWithText("بستن").assertIsDisplayed().performClick()
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        val cleared=QuestionDraftCodec.decode(store.draft(q.id),q)
        assertEquals("",cleared.values(s)[0]);assertEquals("کمتر",cleared.values(s)[1]);assertEquals(0,cleared.shownHints)
        s.pairs.forEachIndexed { row,pair -> select(s,row,pair.right) }
        val completed=QuestionDraftCodec.decode(store.draft(q.id),q)
        assertEquals(s.pairs.map { it.right },completed.values(s))
        assertEquals(0,completed.shownHints)
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
        controlInQuestion("table-choice-${step.id}").performClick()
        val correctIndex=step.options.indexOfFirst { it.correct }
        val choiceTag="table-option-${step.id}-$correctIndex"
        compose.onNodeWithTag("table-choice-sheet-${step.id}").performScrollToNode(hasTestTag(choiceTag))
        compose.onNodeWithTag(choiceTag).assertIsDisplayed().assertTextEquals(workbookBidi(step.options[correctIndex].text)).performClick()
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
    private fun filled(q:ChapterQuestion):QuestionPractice {
        var state=QuestionPractice(shuffleSeed=1405)
        q.steps.forEach { step ->
            val value=when(step.kind) {
                StepKind.MATCH -> step.pairs.map { it.right }
                StepKind.SINGLE -> listOf(step.options.indexOfFirst { it.correct }.toString())
                StepKind.MULTI -> step.options.indices.filter { step.options[it].correct }.take(step.pick).map { it.toString() }
                StepKind.ORDER -> step.correctOrder.map { it.toString() }
                StepKind.CLASSIFY -> step.classifyItems.map { it.category }
            }
            state=state.change(step,value)
        }
        return state
    }
    private data class AuditFrame(val q:ChapterQuestion,val answer:Boolean=false,val width:Int=320,val font:Float=1f)
    @Test fun all104FilledQuestionsAndAnswersHaveNoOverlappingBlanksOrClippedTablesAtPhoneWidths() {
        val questions=catalog.chapters.take(5).flatMap { it.questions }
        val current=mutableStateOf(AuditFrame(questions.first()))
        compose.setContent {
            val frame=current.value
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,frame.font)) {
                OloomYarTheme {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart) {
                        key(frame) {
                            Column(Modifier.width(frame.width.dp).verticalScroll(rememberScrollState()).padding(horizontal=34.dp).testTag("layout-audit-viewport")) {
                                NativeWorkbookContent(frame.q,if(frame.answer)frame.q.nativeAnswer else frame.q.nativeQuestion,
                                    if(frame.answer)null else filled(frame.q))
                            }
                        }
                    }
                }
            }
        }
        val report=JSONArray()
        val issues=mutableListOf<String>()
        val controls=SemanticsMatcher("editable source control") {
            val tag=it.config.getOrElse(SemanticsProperties.TestTag) { "" }
            tag.startsWith("blank-") || tag.startsWith("table-choice-")
        }
        val textNodes=SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        for((width,font) in listOf(320 to 1f,360 to 1.5f,320 to 2f)) {
            questions.forEach { q ->
                for(answer in listOf(false,true)) {
                    compose.runOnIdle { current.value=AuditFrame(q,answer,width,font) }
                    compose.waitForIdle()
                    var controlCount=0
                    val before=issues.size
                    try {
                    val rootTag=if(answer)"native-reference-answer" else "native-source-question"
                    val root=compose.onNodeWithTag(rootTag).fetchSemanticsNode()
                    val bounds=Rect(root.positionInRoot,root.size.toSize())
                    val nodes=compose.onAllNodes(controls).fetchSemanticsNodes()
                    val rectangles=nodes.map { it.config[SemanticsProperties.TestTag] to Rect(it.positionInRoot,it.size.toSize()) }
                    controlCount=rectangles.size
                    rectangles.forEach { (tag,rect) ->
                        assertTrue("${q.id} $width/$font $tag extends past the question: $rect / $bounds",rect.left>=bounds.left-1f && rect.right<=bounds.right+1f)
                    }
                    rectangles.forEachIndexed { i,(left,a) ->
                        rectangles.drop(i+1).forEach { (right,b) ->
                            val overlapX=minOf(a.right,b.right)-maxOf(a.left,b.left)
                            val overlapY=minOf(a.bottom,b.bottom)-maxOf(a.top,b.top)
                            assertFalse("${q.id} $width/$font overlaps $left and $right: $a / $b",overlapX>1f && overlapY>1f)
                        }
                    }
                    compose.onAllNodesWithTag("native-source-table").fetchSemanticsNodes().forEach { table ->
                        assertTrue("${q.id} table exceeds portrait width",table.positionInRoot.x>=bounds.left-1f && table.positionInRoot.x+table.size.width<=bounds.right+1f)
                    }
                    val count=compose.onAllNodes(textNodes,useUnmergedTree=true).fetchSemanticsNodes().size
                    repeat(count) { index ->
                        val layouts=mutableListOf<TextLayoutResult>()
                        compose.onAllNodes(textNodes,useUnmergedTree=true)[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        layouts.forEach { layout -> assertFalse("${q.id} $width/$font clips text ${layout.layoutInput.text}; constraints=${layout.layoutInput.constraints}; size=${layout.size}; overflow=${layout.didOverflowWidth}/${layout.didOverflowHeight}; lines=${layout.lineCount}; font=${layout.layoutInput.style.fontSize}; line=${layout.layoutInput.style.lineHeight}",layout.hasVisualOverflow) }
                    }
                    if(width==320 && font==1f && !answer && q.canonicalForAudit() in setOf("g7_ch02_q02","g7_ch02_q15","g7_ch03_q17","g7_ch03_q21","g7_ch05_q02")) {
                        if(q.canonicalForAudit()=="g7_ch03_q21") compose.onNodeWithTag("blank-${q.steps.first().id}-0").performScrollTo()
                        capture("layout-${q.canonicalForAudit()}")
                    }
                    } catch (error:AssertionError) {
                        issues.add("${q.id} answer=$answer width=$width font=$font: ${error.message}")
                        if(issues.size<=4) capture("layout-error-${q.canonicalForAudit()}-$width-$font-${if(answer)"key" else "question"}")
                    }
                    report.put(JSONObject().put("question",q.id).put("answer",answer).put("screenWidthDp",width).put("fontScale",font).put("controls",controlCount).put("status",if(issues.size==before)"PASS" else "FAIL").put("error",if(issues.size==before)JSONObject.NULL else issues.last()))
                }
            }
        }
        assertEquals(624,report.length())
        val values=ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME,"native-layout-audit.json")
            put(MediaStore.Downloads.MIME_TYPE,"application/json")
            put(MediaStore.Downloads.RELATIVE_PATH,"Download/grade7-screenshots")
        }
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(JSONObject().put("status",if(issues.isEmpty())"PASS" else "FAIL").put("cases",report).put("failures",JSONArray(issues)).toString(2).toByteArray()) }
        assertTrue(issues.take(40).joinToString("\n"),issues.isEmpty())
    }
    private fun ChapterQuestion.canonicalForAudit()="g7_ch${relatedChapter.toString().padStart(2,'0')}_${if(section=="چهارگزینه‌ای")"mcq" else "q"}${sourceNumber.toString().padStart(2,'0')}"

    @Test fun hintsForMultiplePartsHaveOneHeadingPerLevelAndStayPartSpecific() {
        val q=question(2,2)
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        var state=filled(q)
        val first=q.steps[0];val second=q.steps[1]
        state=state.change(first,state.values(first).toMutableList().apply { this[0]=first.pairs[0].choices.first { it!=first.pairs[0].right } })
        state=state.change(second,state.values(second).toMutableList().apply { this[0]=second.pairs[0].choices.first { it!=second.pairs[0].right } }).submit(q)
        store.saveDraft(q.id,QuestionDraftCodec.encode(state))
        open(q)
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("native-hints"))
        compose.onAllNodesWithText("راهنمایی ۱").assertCountEquals(1)
        compose.onNodeWithTag("native-hint-2").assertDoesNotExist()
        compose.onNodeWithText(workbookBidi(first.prompt)).assertExists()
        compose.onNodeWithText(workbookBidi(second.prompt)).assertExists()
        capture("layout-hints-two-parts-first-level")
        select(first,0,first.pairs[0].choices.first { it!=first.pairs[0].right && it!=state.values(first)[0] })
        select(second,0,second.pairs[0].choices.first { it!=second.pairs[0].right && it!=state.values(second)[0] })
        compose.onNodeWithText("بررسی پاسخ").performClick()
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("native-hints"))
        compose.onAllNodesWithText("راهنمایی ۱").assertCountEquals(1)
        compose.onAllNodesWithText("راهنمایی ۲").assertCountEquals(1)
        compose.onNodeWithTag("reveal-answer").assertIsEnabled()
        capture("layout-hints-two-parts-second-level")
    }
    @Test fun firstFiveChapterPagesShowTheSuppliedCoverAndActualProgress() {
        val chapter=catalog.chapters[2]
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",3)
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use {
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("جلد فصل 3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("chapter-study-cover").assertExists()
            compose.onNodeWithTag("chapter-study-progress").performScrollTo().assertTextEquals("${workbookFa(chapter.questions.count { chapter.progress.done(it.id) })} از ۳۱ سؤال")
            capture("layout-chapter-3-cover-progress")
            compose.onNodeWithTag("chapter-study-continue").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag("native-source-question").assertExists()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }

}
