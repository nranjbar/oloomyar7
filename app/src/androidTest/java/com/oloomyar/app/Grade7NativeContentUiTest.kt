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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
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
    @Test fun all317QuestionsAndKeysHaveNativeContentAndNoPageScreenshotFallback() {
        val chapters=catalog.chapters
        assertEquals(listOf(8,20,31,23,22,21,34,26,18,29,13,20,17,15,20),chapters.map { it.questions.size })
        chapters.flatMap { it.questions }.forEach { q ->
            assertTrue(q.id,q.nativeQuestion.isNotEmpty());assertTrue(q.id,q.nativeAnswer.isNotEmpty())
            assertTrue(q.id,q.nativeQuestion.none { it.asset==q.sourceQuestionVisual || it.asset==q.answerVisual })
            assertTrue(q.id,q.nativeAnswer.all { it.type in setOf("text","math","table","figure") })
        }
    }
    @Test fun everyQuestionRendersOnAndroidIncludingAllFifteenChapterSchemas() {
        val questions=catalog.chapters.flatMap { it.questions }
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
            android.util.Log.d("Grade7NativeReview","empty-source ${q.id}")
            compose.runOnIdle { current.value=q }
            compose.onNodeWithTag("native-source-question").assertExists()
            compose.onNodeWithTag("native-reference-answer").assertDoesNotExist()
            q.nativeQuestion.filter { it.type=="figure" }.forEach { block ->
                val tag="workbook-image-enlarge-${block.asset}"
                compose.waitUntil(10000) {
                    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false
                }
                compose.onNodeWithTag(tag).assertIsEnabled()
            }
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
        assertEquals(listOf("g7_ch03_q15_question.webp"),q.nativeQuestion.filter { it.type=="figure" }.map { it.asset })
        // Retain the decoder's two exact-region checks while the owner now asks
        // to show the complete original concept map before the controls.
        val crops=listOf("crop:g7_ch03_q15_question.webp#1850,105,2140,605",
            "crop:g7_ch03_q15_question.webp#1490,315,1830,450").map { it to checkNotNull(RasterSource.parse(it)) }
        assertEquals(2,crops.size)
        crops.forEach { (asset,r) ->
            val original=checkNotNull(SourceBitmap.load(context,r.file)); val region=checkNotNull(SourceBitmap.load(context,asset))
            assertEquals(r.width,region.width);assertEquals(r.height,region.height)
            val a=IntArray(r.width*r.height);val b=IntArray(a.size)
            original.getPixels(a,0,r.width,r.left,r.top,r.width,r.height);region.getPixels(b,0,r.width,0,0,r.width,r.height)
            assertArrayEquals(a,b)
        }
        open(q);compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        val fullTag="workbook-image-enlarge-g7_ch03_q15_question.webp"
        compose.waitUntil(10000) { compose.onAllNodesWithTag(fullTag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        val afterImage=compose.onNodeWithTag(fullTag).getUnclippedBoundsInRoot().bottom
        val firstBlank=compose.onNodeWithTag("blank-${q.steps.single().id}-0").getUnclippedBoundsInRoot().top
        assertTrue("The complete supplied carbon map must precede the interactive editor",afterImage<=firstBlank)
        assertEquals(8,q.steps.single().pairs.size)
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
    @Test fun all317FilledQuestionsAndAnswersHaveNoOverlappingBlanksOrClippedTablesAtPhoneWidths() {
        val questions=catalog.chapters.flatMap { it.questions }
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
        var clippingOracleChecked=false
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
                        layouts.forEach { layout ->
                            val input=layout.layoutInput
                            // Plain Text's semantics rebuilds MultiParagraph at its original
                            // loose maxWidth but returns the actual, narrower layoutSize.
                            // Re-measure inside that real paint box before checking overflow.
                            // AndroidX ParagraphLayoutCache.slowCreateTextLayoutResultOrNull.
                            val measurer=TextMeasurer(input.fontFamilyResolver,input.density,input.layoutDirection,cacheSize=0)
                            if(!clippingOracleChecked && input.text.isNotEmpty()) {
                                assertTrue("The oracle must reject real width clipping",measurer.measure(input.text,style=input.style,softWrap=false,constraints=Constraints(maxWidth=1)).didOverflowWidth)
                                assertTrue("The oracle must reject real height clipping",measurer.measure(input.text,style=input.style,constraints=Constraints(maxWidth=layout.size.width,maxHeight=1)).didOverflowHeight)
                                clippingOracleChecked=true
                            }
                            val painted=measurer.measure(
                                text=input.text,style=input.style,overflow=input.overflow,softWrap=input.softWrap,
                                maxLines=input.maxLines,placeholders=input.placeholders,
                                constraints=Constraints(minWidth=layout.size.width,maxWidth=layout.size.width,maxHeight=layout.size.height))
                            assertFalse("${q.id} $width/$font clips text ${input.text}; paintBox=${layout.size}; measured=${painted.size}; overflow=${painted.didOverflowWidth}/${painted.didOverflowHeight}; lines=${painted.lineCount}",painted.hasVisualOverflow)
                        }
                    }
                    if(width==320 && font==1f && !answer && q.canonicalForAudit() in setOf("g7_ch02_q02","g7_ch02_q15","g7_ch03_q17","g7_ch03_q21","g7_ch05_q02","g7_ch06_q06","g7_ch07_q19","g7_ch08_q12","g7_ch08_q18","g7_ch09_q03","g7_ch10_q16","g7_ch11_q06","g7_ch12_q17","g7_ch13_mcq03","g7_ch14_q10","g7_ch15_q02","g7_ch15_q12")) {
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
        assertTrue("Real clipping negative controls must run",clippingOracleChecked)
        assertEquals(1902,report.length())
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

    @Test fun trueFalseChoicesStayEnabledAndReusableWithoutMovingOtherAnswers() {
        val q=question(1,2);val s=q.steps.single();open(q)
        select(s,0,"غلط");select(s,1,"غلط");select(s,2,"غلط")
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        assertEquals(listOf("غلط","غلط","غلط"),QuestionDraftCodec.decode(store.draft(q.id),q).values(s))
        select(s,0,"صحیح");select(s,1,"صحیح")
        compose.onNodeWithText("بررسی پاسخ").performClick()
        assertTrue(QuestionDraftCodec.decode(store.draft(q.id),q).correct(q))
    }

    @Test fun clarifiedBalloonChoicesStayAvailableForBothContainersAndClearOnlyOneBlank() {
        val q=question(3,22);val s=q.steps.single();open(q,1.5f)
        val hot=s.pairs[0].right;val cold=s.pairs[1].right
        assertFalse(WorkbookAnswers.choices(s,0).any { it.contains("این اتفاق") || it.contains("برعکس") })
        select(s,0,hot);select(s,1,hot)
        val store=WorkbookProgress(context,"native_ui",listOf(q))
        assertEquals(listOf(hot,hot),QuestionDraftCodec.decode(store.draft(q.id),q).values(s))
        select(s,1,cold)
        capture("v08-balloon-clear-choices")
        assertEquals(listOf(hot,cold),QuestionDraftCodec.decode(store.draft(q.id),q).values(s))
        controlInQuestion("blank-${s.id}-0").performClick()
        compose.onNodeWithTag("choice-sheet").performScrollToNode(hasText("بستن"))
        compose.onNodeWithText("بستن").performClick()
        assertEquals(listOf("",cold),QuestionDraftCodec.decode(store.draft(q.id),q).values(s))
        select(s,0,hot)
        compose.onNodeWithText("بررسی پاسخ").performClick()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled()
    }

    @Test fun elevenEnergySourcesCanReuseBothClassificationChoicesInsideTheCompleteTable() {
        val q=question(9,3);val s=q.steps.single();assertEquals(StepKind.CLASSIFY,s.kind);open(q,1.5f)
        s.classifyItems.forEachIndexed { row,item ->select(s,row,item.category) }
        compose.onNodeWithText("بررسی پاسخ").performClick()
        val state=QuestionDraftCodec.decode(WorkbookProgress(context,"native_ui",listOf(q)).draft(q.id),q)
        assertTrue(state.correct(q));assertEquals(11,state.values(s).size)
        compose.onNodeWithTag("reveal-answer").assertIsEnabled()
        capture("v08-energy-classification-complete")
    }

    @Test fun rewrittenChoicesAndSplitCellsMigrateWithoutUnlockingOldWrongSubmissions() {
        val q=question(3,22);val s=q.steps.single()
        val old=listOf("هوای داخل بطری گرم شده و حجمش بیشتر می شود و به داخل بادکنک می رود و بادکنک باد می شود.","این اتفاق نمی افتد و برعکس است.")
        val state=QuestionPractice(answers=mapOf(s.id to old),checked=mapOf(s.id to old),shuffleSeed=982)
        val migrated=QuestionDraftCodec.decode(QuestionDraftCodec.encode(state),q)
        assertEquals(s.pairs.map { it.right },migrated.values(s));assertTrue(migrated.correct(q));assertEquals(982,migrated.shuffleSeed)
        val free=question(7,19);val fs=free.steps.single()
        val compound=listOf("آزاد","نفوذ پذیر","نفوذ ناپذیر","دشت‌ها و دامنهٔ کوه‌ها","چاه و قنات")
        val previous=QuestionPractice(answers=mapOf(fs.id to compound),checked=mapOf(fs.id to compound),shuffleSeed=983)
        val restored=QuestionDraftCodec.decode(QuestionDraftCodec.encode(previous),free)
        assertEquals(fs.pairs.map { it.right },restored.values(fs));assertTrue(restored.correct(free))
        val confined=question(7,20);val cs=confined.steps.single()
        val cv=listOf("تحت فشار","نفوذ پذیر","نفوذ ناپذیر","کوهستانی و شیب دار")
        assertEquals(cs.pairs.map { it.right },QuestionDraftCodec.decode(QuestionDraftCodec.encode(QuestionPractice(answers=mapOf(cs.id to cv))),confined).values(cs))
    }

    @Test fun newDirectionsRequireFreshCheckingAndLegacyClassificationKeepsItsSelections() {
        val q=question(10,13);val s=q.steps.single()
        val old=QuestionPractice(answers=mapOf(s.id to listOf("0","1","2","3")),checked=mapOf(s.id to listOf("0","1","2","3")),shownHints=2,revealed=true,shuffleSeed=778)
        val restored=QuestionDraftCodec.decode(QuestionDraftCodec.encode(old),q)
        assertEquals(List(4){""},restored.values(s));assertFalse(restored.canReveal(q));assertFalse(restored.revealed);assertEquals(778,restored.shuffleSeed)
        val energy=question(9,3);val es=energy.steps.single()
        val groups=listOf("باد، خورشید، امواج دریا، جزر و مد، زمین گرمایی، سوخت زیستی، برق آبی","گاز طبیعی، زغال سنگ، نفت و سوخت هسته‌ای")
        val previous=QuestionPractice(answers=mapOf(es.id to groups),checked=mapOf(es.id to groups),shuffleSeed=779)
        val migrated=QuestionDraftCodec.decode(QuestionDraftCodec.encode(previous),energy)
        assertEquals(es.classifyItems.map { it.category },migrated.values(es));assertTrue(migrated.correct(energy))
    }

    @Test fun documentedScientificCorrectionsKeepTheLiteralReferenceKeyAndDoNotPromoteOldWrongChoices() {
        val hydro=question(9,15);val s=hydro.steps.single()
        assertEquals("مولد (ژنراتور)",s.pairs[1].right);assertEquals("توربین",s.pairs[2].right)
        assertFalse(hydro.practiceUsesPrintedKey);assertNotNull(hydro.sourceReviewNote)
        assertTrue(hydro.nativeAnswer.single().text.contains("۱- توربین"))
        val old=s.pairs.map { it.right }.toMutableList().apply { set(1,"توربین");set(2,"مولد (ژنراتور)") }
        val previous=QuestionPractice(answers=mapOf(s.id to old),checked=mapOf(s.id to old),revealed=true)
        val migrated=QuestionDraftCodec.decode(QuestionDraftCodec.encode(previous),hydro)
        assertEquals("توربین",migrated.values(s)[1]);assertFalse(migrated.correct(hydro));assertFalse(migrated.revealed)
        val mcq=catalog.chapters[9].questions.first { it.section=="چهارگزینه‌ای" && it.sourceNumber==4 }
        assertEquals(2,mcq.steps.single().options.indexOfFirst { it.correct })
        assertEquals("الف",mcq.nativeAnswer.single().text);assertFalse(mcq.practiceUsesPrintedKey)
        val tea=question(10,21)
        assertTrue(tea.steps.single().pairs[3].right.contains("به‌تنهایی"))
        assertTrue(tea.steps.single().pairs[4].right.contains("استتار"));assertFalse(tea.practiceUsesPrintedKey)
        val iceCream=catalog.chapters[7].questions.first { it.section=="چهارگزینه‌ای" && it.sourceNumber==3 }
        assertTrue(iceCream.steps.single().options.all { it.text.endsWith("کیلوژول") })
        val heating=catalog.chapters[9].questions.first { it.section=="چهارگزینه‌ای" && it.sourceNumber==2 }
        assertTrue(heating.steps.single().options.first().correct)
        assertFalse(heating.steps.single().options.any { it.text.contains("زیرا") })
    }

    @Test fun chapterTenUsesTheNewCoverAndContinuesIntoItsNativeQuestion() {
        val intent=Intent(context,Grade7WorkbookActivity::class.java).putExtra("chapter",10)
        ActivityScenario.launch<Grade7WorkbookActivity>(intent).use {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("chapter-study-cover").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("chapter-study-cover").assertExists()
            capture("v08-chapter-10-cover")
            compose.onNodeWithTag("chapter-study-continue").performScrollTo().performClick()
            compose.onNodeWithTag("native-source-question").assertExists()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }

    @Test fun newEmbeddedScientificFiguresKeepEveryDecodedSourcePixelOnAndroid() {
        val records=context.assets.open("content/native-scientific-extractions.json").bufferedReader().use { JSONArray(it.readText()) }
        assertEquals(19,records.length())
        repeat(records.length()) { index ->
            val record=records.getJSONObject(index)
            val bitmap=checkNotNull(SourceBitmap.load(context,record.getString("asset")))
            assertEquals(record.getInt("width"),bitmap.width);assertEquals(record.getInt("height"),bitmap.height)
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val bytes=ByteArray(pixels.size*4)
            pixels.forEachIndexed { i,pixel ->
                bytes[i*4]=(pixel shr 16).toByte();bytes[i*4+1]=(pixel shr 8).toByte()
                bytes[i*4+2]=pixel.toByte();bytes[i*4+3]=(pixel ushr 24).toByte()
            }
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(record.getString("asset"),record.getString("decodedRgbaSha256"),digest)
            if(record.has("displayCrop")) {
                val crop=record.getJSONArray("displayCrop")
                val asset="crop:${record.getString("asset")}#${crop.getInt(0)},${crop.getInt(1)},${crop.getInt(2)},${crop.getInt(3)}"
                val shown=checkNotNull(SourceBitmap.load(context,asset))
                assertEquals(crop.getInt(2)-crop.getInt(0),shown.width);assertEquals(crop.getInt(3)-crop.getInt(1),shown.height)
                assertEquals(bitmap.getPixel(5,5),shown.getPixel(5,5))
            }
        }
    }

    @Test fun rapidRealQuestionNavigationKeepsSourceIdentityAndDisposesPendingImageLoads() {
        val questions=listOf(question(6,6),question(7,20),question(8,4),question(8,17),question(10,18))
        val store=WorkbookProgress(context,"native_ui",questions)
        val shown=mutableStateOf(true)
        compose.setContent {
            OloomYarTheme {
                if(shown.value)AuditedWorkbookApp("مرور شکل‌ها",questions,store,initialQuestionId=questions.first().id,onExit={})
                else androidx.compose.material3.Text("مرور بسته شد",Modifier.testTag("navigation-disposed"))
            }
        }
        fun assertCurrent(index:Int) {
            val expected=questions[index]
            // performClick dispatches the touch; synchronize with the resulting
            // composition before reading a preference written by LaunchedEffect.
            compose.onNodeWithText("سؤال ${workbookFa(expected.sourceNumber)} • ${expected.title}").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(expected.id,LearningPreferences(context).lastLocation()!!.questionId)
            }
        }
        assertCurrent(0)
        repeat(3) {
            for(index in 1 until questions.size) {
                compose.onNodeWithTag("next-question").assertIsEnabled().performClick()
                assertCurrent(index)
            }
            for(index in questions.lastIndex-1 downTo 0) {
                compose.onNodeWithTag("previous-question").assertIsEnabled().performClick()
                assertCurrent(index)
            }
        }
        compose.onNodeWithTag("previous-question").assertIsNotEnabled()
        compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        compose.runOnIdle { shown.value=false }
        compose.onNodeWithTag("navigation-disposed").assertExists()
        compose.onNodeWithTag("native-source-question").assertDoesNotExist()
    }

    private fun bitmapRgbaDigest(bitmap:Bitmap):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        val pixels=IntArray(bitmap.width);val bytes=ByteArray(bitmap.width*4)
        for(y in 0 until bitmap.height) {
            bitmap.getPixels(pixels,0,bitmap.width,0,y,bitmap.width,1)
            pixels.forEachIndexed { x,pixel ->
                bytes[x*4]=(pixel shr 16).toByte();bytes[x*4+1]=(pixel shr 8).toByte()
                bytes[x*4+2]=pixel.toByte();bytes[x*4+3]=(pixel ushr 24).toByte()
            }
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @Test fun all59OwnerScientificIllustrationsRetainTheirOriginalPixelsOnAndroid() {
        val rows=context.assets.open("content/owner-scientific-art.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("scientific") }
        assertEquals(59,rows.length())
        repeat(rows.length()) { index ->
            val row=rows.getJSONObject(index);val asset=row.getString("asset")
            val bitmap=checkNotNull(SourceBitmap.load(context,asset)) { asset }
            assertEquals(row.getInt("width"),bitmap.width);assertEquals(row.getInt("height"),bitmap.height)
            assertEquals(asset,row.getString("decodedRgbaSha256"),bitmapRgbaDigest(bitmap))
        }
    }
    @Test fun suppliedAnswerArtWaitsForCorrectSubmissionAndExplicitReveal() {
        val q=question(2,9);val asset=checkNotNull(q.answerStudyVisual)
        val store=WorkbookProgress(context,"native_ui",listOf(q));store.saveDraft(q.id,QuestionDraftCodec.encode(filled(q).submit(q)))
        open(q)
        val tag="workbook-image-enlarge-$asset"
        compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("state-art-correct"))
        compose.onNodeWithTag("state-art-correct").assertIsDisplayed()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled().performClick()
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag(tag))
        compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag(tag).assertIsEnabled()
        compose.onAllNodesWithTag("native-equation").assertCountEquals(2)
        capture("v08-supplied-density-answer")
    }
    @Test fun suppliedAnswerArtUsesTheSameGateAfterTwoDistinctFailuresAndBothHintIcons() {
        val q=question(1,5);val s=q.steps.single();val right=filled(q)
        val wrong=s.matchChoices.filter { it!=s.pairs.first().right }.take(2);assertEquals(2,wrong.size)
        var state=right.change(s,right.values(s).toMutableList().apply { this[0]=wrong[0] }).submit(q)
        state=state.change(s,state.values(s).toMutableList().apply { this[0]=wrong[1] }).submit(q)
        assertTrue(state.canReveal(q));assertEquals(2,state.shownHints)
        WorkbookProgress(context,"native_ui",listOf(q)).saveDraft(q.id,QuestionDraftCodec.encode(state))
        open(q,1.5f);val tag="workbook-image-enlarge-${q.answerStudyVisual}"
        compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("native-hints"))
        compose.onAllNodesWithTag("state-art-hint_lightbulb").assertCountEquals(2)
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("state-art-incorrect"))
        compose.onNodeWithTag("state-art-incorrect").assertIsDisplayed()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled().performClick()
        compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag(tag))
        compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag(tag).assertIsEnabled()
        capture("v08-supplied-hinted-answer")
    }

    @Test fun pendingDentalPrintKeyHasASeparateScientificTaskAndOldOptionCannotUnlockIt() {
        val q=catalog.chapters[12].questions.first { it.section=="چهارگزینه‌ای" && it.sourceNumber==3 }
        val s=q.steps.single()
        assertEquals(StepKind.MATCH,s.kind);assertEquals(listOf("آسیا","پیش","نیش"),s.pairs.map { it.right })
        assertEquals("ج",q.nativeAnswer.single().text);assertFalse(q.practiceUsesPrintedKey)
        assertTrue(q.sourceReviewNote!!.contains("تأیید مؤلف"))
        val old=QuestionPractice(answers=mapOf(s.id to listOf("2")),checked=mapOf(s.id to listOf("2")),
            incorrectSubmissions=listOf(listOf(listOf("0")),listOf(listOf("1"))),shownHints=2,revealed=true,shuffleSeed=913)
        val migrated=QuestionDraftCodec.decode(QuestionDraftCodec.encode(old),q)
        assertEquals(913,migrated.shuffleSeed);assertEquals(List(3){""},migrated.values(s))
        assertFalse(migrated.canReveal(q));assertFalse(migrated.revealed);assertEquals(0,migrated.shownHints)
        open(q,1.5f)
        select(s,0,"آسیا");select(s,1,"پیش")
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        select(s,2,"نیش")
        compose.onNodeWithText("بررسی پاسخ").assertIsEnabled().performClick()
        compose.onNodeWithTag("native-reference-answer").assertDoesNotExist()
        compose.onNodeWithTag("reveal-answer").assertIsEnabled().performClick()
        compose.onNodeWithTag("native-reference-answer").assertExists()
        compose.onNodeWithText(workbookBidi("ج")).performScrollTo().assertIsDisplayed()
        capture("v09-dental-literal-key-and-science")
    }

    @Test fun oldPrintedBiologyClaimsAreNotPromotedToTheReviewedScientificAnswers() {
        for((q,row,obsolete) in listOf(
            Triple(question(11,3),3,"غشا، هسته و میان یاخته"),
            Triple(question(12,10),0,"نرسیدن بعضی آمینو اسیدهای ضروری و کمبود پروتئین"),
            Triple(question(13,3),1,"لولهٔ انسان بزرگ‌تر و پیچیده‌تر، با باریک روده و فراخ روده؛ پرنده یک روده دارد")
        )) {
            val s=q.steps.single();val values=s.pairs.map { it.right }.toMutableList().apply { this[row]=obsolete }
            val old=QuestionPractice(answers=mapOf(s.id to values),checked=mapOf(s.id to values),revealed=true,shuffleSeed=914)
            val restored=QuestionDraftCodec.decode(QuestionDraftCodec.encode(old),q)
            assertEquals(obsolete,restored.values(s)[row]);assertFalse(restored.correct(q));assertFalse(restored.revealed)
            assertEquals(914,restored.shuffleSeed);assertFalse(q.practiceUsesPrintedKey)
            assertTrue(QuestionPractice().change(s,s.pairs.map { it.right }).submit(q).correct(q))
        }
    }

}
