package com.oloomyar.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.oloomyar.app.data.*
import com.oloomyar.app.model.StepKind
import com.oloomyar.app.model.WorkbookAnswers
import com.oloomyar.app.model.ChapterQuestion
import com.oloomyar.app.model.LearningStep
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.workbookBidi
import com.oloomyar.app.ui.theme.OloomYarTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class Grade7WorkbookUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun gradeAndProgress() {
        assumeTrue(BuildConfig.WORKBOOK_GRADE == 7)
        context.getSharedPreferences("workbook_1405_g7_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun scrollTo(text: String) {
        // Action controls stay outside the scrolling question body.
        val match = hasText(text, substring = true)
        if (compose.onAllNodes(match).fetchSemanticsNodes().size == 1 && compose.onNode(match).isDisplayed()) return
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test fun gradeSevenHomeUsesSharedDesignAndOnlyTheTwoImplementedChapters() {
        // Launch the installed app's real entry point as well as checking the
        // shared composables in the other tests. This exercises the manifest,
        // chapter activity and return navigation together.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("علوم‌یار هفتم").assertIsDisplayed()
            capture("grade7-01-home")
            scrollTo("اندازه‌گیری در علوم و ابزارهای آن")
            compose.onNodeWithText("اندازه‌گیری در علوم و ابزارهای آن").assertExists()
            compose.onNodeWithText("خرید و فعال‌سازی فصل‌ها").assertDoesNotExist()
            scrollTo("تجربه و تفکر")
            compose.onNodeWithText("تجربه و تفکر").performClick()
            compose.onNodeWithText("ورود به این بخش").assertExists()
            compose.onNodeWithText("برگشت").performClick()
            compose.onNodeWithText("علوم‌یار هفتم").assertExists()
        }
    }

    @Test fun orderedBlanksKeepTheSourceFirstSaveDraftAndCompletePractice() {
        val qs=Grade7Chapter1Repository(context).questions
        val progress=WorkbookProgress(context,"g7_ui",qs)
        compose.setContent { OloomYarTheme { AuditedWorkbookApp("فصل ۱ • تجربه و تفکر",qs,progress,onExit={}) } }
        compose.onNodeWithText("ورود به این بخش").performClick()
        scrollTo("سؤال ۱ •")
        compose.onNodeWithText("سؤال ۱ •",substring=true).performClick()
        compose.onNodeWithText("صورت اصلی سؤال کتاب").assertExists()
        capture("grade7-02-source-question")
        compose.onNodeWithText("رفتن به پاسخ تعاملی").performClick()
        for(choice in listOf("دانش","آزمایش","فناوری")) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(choice))
            compose.onNodeWithText(choice).performClick()
        }
        assertNotNull(progress.draft(qs.first().id))
        capture("grade7-03-ordered-blanks")
        scrollTo("بررسی پاسخ")
        compose.onNodeWithText("بررسی پاسخ").assertIsEnabled().performClick()
        scrollTo("پاسخ درست است")
        compose.onNodeWithText("پاسخ درست است").assertExists()
        scrollTo("ثبت تمرین و ادامه")
        compose.onNodeWithText("ثبت تمرین و ادامه").performClick()
        assertTrue(progress.done(qs.first().id))
        assertNull(progress.draft(qs.first().id))
    }

    @Test fun chapterTwoHasSeparateSectionsAndSourceFigureCanBeEnlarged() {
        val qs=Grade7Chapter2Repository(context).questions
        val progress=WorkbookProgress(context,"g7_ui",qs)
        compose.setContent { OloomYarTheme { AuditedWorkbookApp("فصل ۲ • اندازه‌گیری در علوم و ابزارهای آن",qs,progress,onExit={}) } }
        scrollTo("چهارگزینه‌ای")
        compose.onNodeWithText("چهارگزینه‌ای").assertExists()
        capture("grade7-04-chapter2-sections")
        compose.onAllNodesWithText("ورود به این بخش")[0].performScrollTo().performClick()
        scrollTo("سؤال ۱۱ •")
        compose.onNodeWithText("سؤال ۱۱ •",substring=true).performClick()
        compose.onNodeWithText("صورت اصلی سؤال کتاب").assertExists()
        scrollTo("بزرگ‌نمایی تصویر")
        compose.onNodeWithText("بزرگ‌نمایی تصویر").performClick()
        compose.onNodeWithText("بزرگ‌تر").assertExists()
        capture("grade7-05-source-figure-zoom")
        compose.onNodeWithText("بستن").performClick()
    }

    @Test fun everySourceImageDecodesAndCorrectAlternativeSelectionsAreAccepted() {
        val qs=Grade7Chapter1Repository(context).questions+Grade7Chapter2Repository(context).questions
        assertEquals(28,qs.size)
        assertEquals(41,qs.sumOf { it.steps.size })
        val images=qs.flatMap { listOfNotNull(it.visual, it.answerVisual) }.toSet()
        assertEquals(18,images.size)
        images.forEach { name ->
            val bitmap=context.assets.open("images/$name").use { BitmapFactory.decodeStream(it) }
            assertNotNull("Could not decode $name",bitmap)
            bitmap?.recycle()
        }
        qs.flatMap { it.steps }.forEach { s ->
            val answers=when(s.kind) {
                StepKind.SINGLE -> listOf(s.options.indexOfFirst { it.correct }.toString())
                StepKind.MULTI -> s.options.indices.filter { s.options[it].correct }.take(s.pick).map { it.toString() }
                StepKind.MATCH -> s.pairs.map { it.right }
                StepKind.CLASSIFY -> s.classifyItems.map { it.category }
                StepKind.ORDER -> s.correctOrder.map { it.toString() }
            }
            assertTrue(s.id,WorkbookAnswers.isComplete(s,answers))
            assertTrue(s.id,WorkbookAnswers.isCorrect(s,answers))
            assertFalse(s.id,WorkbookAnswers.isCorrect(s,emptyList()))
            if(s.kind==StepKind.MULTI) {
                val alternative=s.options.indices.filter { s.options[it].correct }.takeLast(s.pick).map { it.toString() }
                assertTrue("Valid alternative rejected: ${s.id}",WorkbookAnswers.isCorrect(s,alternative))
            }
        }
    }

    private fun openPractice(question: ChapterQuestion, fontScale: Float = 1f) {
        val progress = WorkbookProgress(context, "g7_ui", listOf(question))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                OloomYarTheme { AuditedWorkbookApp("فصل ${question.relatedChapter}", listOf(question), progress, onExit = {}) }
            }
        }
        compose.onNodeWithText("ورود به این بخش").performClick()
        scrollTo("سؤال ${fa(question.sourceNumber)} •")
        compose.onNodeWithText("سؤال ${fa(question.sourceNumber)} •", substring = true).performClick()
        // Reading a long source or figure must not hide the check/help controls.
        compose.onNodeWithText("بررسی پاسخ").assertIsDisplayed()
        compose.onNodeWithText("راهنما").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("دیدن پاسخ").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("رفتن به پاسخ تعاملی").performClick()
    }

    private fun choose(text: String) {
        // The UI isolates numeric runs for correct Persian/Latin direction.
        val displayed = workbookBidi(text)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(displayed))
        compose.onNodeWithText(displayed).performClick()
    }

    private fun checkAnswer() {
        scrollTo("بررسی پاسخ")
        compose.onNodeWithText("بررسی پاسخ").assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    private fun fa(value: Int) = value.toString().map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")

    @Test fun referenceAnswerRequiresTwoDifferentFailuresAndHintsSurviveReturningToTheQuestion() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 }
        val s = q.steps.first()
        openPractice(q)
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        compose.onNodeWithText("راهنمایی ۱").assertDoesNotExist()
        compose.onNodeWithText("راهنمایی ۲").assertDoesNotExist()
        val wrong = s.options.filter { !it.correct }
        choose(wrong[0].text)
        checkAnswer()
        compose.onNodeWithText("راهنمایی ۱").assertIsDisplayed()
        compose.onNodeWithText("راهنمایی ۲").assertDoesNotExist()
        capture("grade7-06-first-attempt-hint1")
        scrollTo("بررسی پاسخ")
        compose.onNodeWithText("بررسی پاسخ").assertIsNotEnabled()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        val progress = WorkbookProgress(context, "g7_ui", listOf(q))
        val draft = JSONObject(checkNotNull(progress.draft(q.id)))
        assertEquals(1, draft.getJSONObject("attempts").getInt(s.id))
        compose.onNodeWithText("برگشت").performClick()
        scrollTo("سؤال ۸ •")
        compose.onNodeWithText("سؤال ۸ •", substring = true).performClick()
        compose.onNodeWithText("راهنمایی ۱").assertIsDisplayed()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        choose(wrong[1].text)
        checkAnswer()
        compose.onNodeWithText("راهنمایی ۱").assertExists()
        compose.onNodeWithText("راهنمایی ۲").assertIsDisplayed()
        capture("grade7-07-second-attempt-hint2")
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsEnabled().performClick()
        compose.onNodeWithText("پاسخ و توضیح").assertExists()
    }

    @Test fun wholeAnswerImageWaitsUntilEveryPartIsFinishedAndRevealIsRequested() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 9 && it.section == "تمرین‌های اصلی" }
        val image = "تصویر پاسخ سؤال ۹"
        openPractice(q)
        compose.onNodeWithContentDescription(image).assertDoesNotExist()
        choose(q.steps[0].options.first { it.correct }.text)
        checkAnswer()
        compose.onNodeWithContentDescription(image).assertDoesNotExist()
        scrollTo("مرحلهٔ بعد")
        compose.onNodeWithText("مرحلهٔ بعد").performClick()
        val wrong = q.steps[1].options.filter { !it.correct }
        choose(wrong[0].text); checkAnswer()
        compose.onNodeWithContentDescription(image).assertDoesNotExist()
        choose(wrong[1].text); checkAnswer()
        compose.onNodeWithContentDescription(image).assertDoesNotExist()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsEnabled().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription(image).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(image).performScrollTo().assertIsDisplayed()
        capture("grade7-08-answer-image-after-parts")
    }

    @Test fun examResultsDoNotProvideAShortcutToReferenceAnswers() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 9 && it.section == "تمرین‌های اصلی" }
        val progress = WorkbookProgress(context, "g7_ui", listOf(q))
        compose.setContent { OloomYarTheme { AuditedWorkbookApp("فصل ۲", listOf(q), progress, onExit = {}) } }
        compose.onNodeWithText("ورود به این بخش").performClick()
        scrollTo("آزمون همین بخش")
        compose.onNodeWithText("آزمون همین بخش").performClick()
        choose(q.steps[0].options.first { it.correct }.text)
        scrollTo("ثبت و مرحلهٔ بعد")
        compose.onNodeWithText("ثبت و مرحلهٔ بعد").performClick()
        choose(q.steps[1].options.first { !it.correct }.text)
        scrollTo("ثبت پاسخ این سؤال")
        compose.onNodeWithText("ثبت پاسخ این سؤال").performClick()
        compose.onNodeWithText("نتیجهٔ آزمون").assertExists()
        compose.onNodeWithText(workbookBidi(q.steps[0].explanation)).assertDoesNotExist()
        compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۹").assertDoesNotExist()
        scrollTo("تمرین سؤال ۹ با راهنما")
        compose.onNodeWithText("تمرین سؤال ۹ با راهنما").performClick()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
    }

    @Test fun oldDraftRevealedBeforeTryingCannotBypassTheNewPolicy() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 }
        val s = q.steps.first()
        val progress = WorkbookProgress(context, "g7_ui", listOf(q))
        progress.saveDraft(q.id, JSONObject().apply {
            put("status", JSONObject().put(s.id, 3))
            put("hints", JSONObject().put(s.id, 2))
            put("answers", JSONObject().put(s.id, JSONArray(listOf(s.options.indexOfFirst { !it.correct }.toString()))))
        }.toString())
        openPractice(q)
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        compose.onNodeWithText("پاسخ و توضیح").assertDoesNotExist()
        compose.onNodeWithText("راهنمایی ۱").assertDoesNotExist()
    }

    private fun fillFreshMatch(step: LearningStep, values: List<String> = step.pairs.map { it.right }) {
        step.pairs.forEachIndexed { index, pair ->
            scrollTo("جای خالی ${fa(index + 1)} از ${fa(step.pairs.size)}")
            compose.onNodeWithText("جای خالی ${fa(index + 1)} از ${fa(step.pairs.size)}").assertExists()
            compose.onNodeWithText(workbookBidi(pair.left)).assertExists()
            choose(values[index])
        }
    }

    private fun changeFirstMatch(value: String) {
        compose.onNode(hasContentDescription("جای خالی ۱،", substring = true)).performScrollTo().performClick()
        choose(value)
    }

    private fun nextPart(number: Int, total: Int) {
        compose.onNodeWithText("مرحلهٔ بعد").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitForIdle()
        scrollTo("مرحلهٔ ${fa(number)} از ${fa(total)}")
        compose.onNodeWithText("مرحلهٔ ${fa(number)} از ${fa(total)}").assertExists()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        compose.onNodeWithText("راهنمایی ۱").assertDoesNotExist()
        compose.onNodeWithText("راهنمایی ۲").assertDoesNotExist()
    }

    private fun revealAfterTwoMatchAttempts(step: LearningStep) {
        val correct = step.pairs.map { it.right }
        val wrong = WorkbookAnswers.choices(step, 0).filter { it != correct.first() }
        require(wrong.size >= 2)
        fillFreshMatch(step, correct.toMutableList().also { it[0] = wrong[0] })
        checkAnswer()
        compose.onNodeWithText("راهنمایی ۱").assertIsDisplayed()
        compose.onNodeWithText("راهنمایی ۲").assertDoesNotExist()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        changeFirstMatch(wrong[1])
        checkAnswer()
        compose.onNodeWithText("راهنمایی ۲").assertIsDisplayed()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsEnabled().performClick()
        scrollTo("پاسخ و توضیح")
        compose.onNodeWithText("پاسخ و توضیح").assertExists()
    }

    private fun finishPractice() {
        scrollTo("ثبت تمرین و ادامه")
        compose.onNodeWithText("ثبت تمرین و ادامه").assertIsEnabled().performClick()
        compose.onNodeWithText("تمرین کامل شد").assertExists()
    }

    @Test fun lastChapterTwoQuestionMovesFromInstrumentNamesToQuantitiesAndShowsBothHints() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 15 }
        openPractice(q)
        fillFreshMatch(q.steps[0]); checkAnswer()
        nextPart(2, 2)
        revealAfterTwoMatchAttempts(q.steps[1])
        capture("grade7-09-last-question-quantities-answer")
        finishPractice()
    }

    @Test fun clockQuestionMovesThroughTwoMatchPartsAndTheMultipleChoicePart() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 12 }
        openPractice(q)
        revealAfterTwoMatchAttempts(q.steps[0])
        nextPart(2, 3)
        fillFreshMatch(q.steps[1]); checkAnswer()
        nextPart(3, 3)
        val step = q.steps[2]
        step.options.filter { it.correct }.take(step.pick).forEach { choose(it.text) }
        checkAnswer(); finishPractice()
    }

    @Test fun rulerQuestionMovesFromLengthsToReasonAndUnlocksItsSecondPartIndependently() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 14 }
        openPractice(q)
        revealAfterTwoMatchAttempts(q.steps[0])
        nextPart(2, 2)
        val wrong = q.steps[1].options.filter { !it.correct }
        choose(wrong[0].text); checkAnswer()
        compose.onNodeWithText("راهنمایی ۱").assertIsDisplayed()
        choose(wrong[1].text); checkAnswer()
        compose.onNodeWithText("راهنمایی ۲").assertIsDisplayed()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsEnabled().performClick()
        finishPractice()
    }

    @Test fun chapterOneTechnologyQuestionMovesFromDefinitionToThreeExamples() {
        val q = Grade7Chapter1Repository(context).questions.first { it.sourceNumber == 6 }
        openPractice(q)
        choose(q.steps[0].options.first { it.correct }.text); checkAnswer()
        nextPart(2, 2)
        val step = q.steps[1]
        step.options.filter { it.correct }.takeLast(step.pick).forEach { choose(it.text) }
        checkAnswer(); finishPractice()
    }

    @Test fun lastChapterOneQuestionCanRevealAfterTwoDifferentCompleteMultipleSelections() {
        val q = Grade7Chapter1Repository(context).questions.first { it.sourceNumber == 8 }
        val step = q.steps.single()
        openPractice(q)
        val selected = step.options.filter { it.correct }.take(step.pick - 1)
        val wrong = step.options.filter { !it.correct }
        selected.forEach { choose(it.text) }
        choose(wrong[0].text); checkAnswer()
        compose.onNodeWithText("راهنمایی ۱").assertIsDisplayed()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsNotEnabled()
        choose(wrong[0].text)
        choose(wrong[1].text); checkAnswer()
        compose.onNodeWithText("راهنمایی ۲").assertIsDisplayed()
        scrollTo("دیدن پاسخ")
        compose.onNodeWithText("دیدن پاسخ").assertIsEnabled().performClick()
        finishPractice()
    }

    @Test fun longLastQuestionKeepsActionsVisibleWithLargeTextAndHasExplicitBlankNavigation() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 15 }
        openPractice(q, fontScale = 1.4f)
        scrollTo("جای خالی بعدی")
        compose.onNodeWithText("جای خالی بعدی").assertIsEnabled().performClick()
        compose.onNodeWithText(workbookBidi(q.steps[0].pairs[1].left)).assertExists()
        compose.onNodeWithText("بررسی پاسخ").assertIsDisplayed().assertIsNotEnabled()
        scrollTo("جای خالی قبلی")
        compose.onNodeWithText("جای خالی قبلی").assertIsEnabled().performClick()
        fillFreshMatch(q.steps[0])
        compose.onNodeWithText("بررسی پاسخ").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("بررسی پاسخ").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("مرحلهٔ بعد").assertIsDisplayed().assertIsEnabled()
        capture("grade7-10-fixed-next-part-large-text")
        nextPart(2, 2)
        compose.onNodeWithText("انتخاب‌ها").assertIsDisplayed().performClick()
        fillFreshMatch(q.steps[1])
        compose.onNodeWithText("بررسی پاسخ").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ثبت تمرین و ادامه").assertIsDisplayed().assertIsEnabled()
        finishPractice()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Semantics can be ready one compositor frame before UiAutomation's
        // screen capture. Let the completed frame reach the Android window.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(400)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/grade7-screenshots")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0)
            resolver.update(uri,values,null,null)
        } finally { bitmap.recycle() }
    }
}
