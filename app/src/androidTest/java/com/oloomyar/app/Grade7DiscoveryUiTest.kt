package com.oloomyar.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native navigation, real local records, accessibility and external-intent fallback. */
@RunWith(AndroidJUnit4::class)
class Grade7DiscoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() {
        listOf("grade7_learning_settings", "workbook_1405_grade7_chapter1", "workbook_1405_grade7_chapter2", "workbook_1405_grade7_chapter3")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @After fun restoreDisplay() { shell("wm size reset"); shell("wm density reset") }
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { p ->
        FileInputStream(p.fileDescriptor).bufferedReader().use { it.readText() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,5000)
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Download/grade7-screenshots") }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        val bmp = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) };bmp.recycle()
    }
    private fun hubText(text: String) { compose.onNodeWithTag("hub-list").performScrollToNode(hasText(text));compose.onNodeWithText(text).performClick() }
    private fun choose(text: String) { compose.onNodeWithTag("question-body").performScrollToNode(hasText(workbookBidi(text)));compose.onNodeWithText(workbookBidi(text)).performClick() }
    private fun rotateAndAwaitResume(scenario: ActivityScenario<Grade7Chapter2Activity>, requested: Int, configuration: Int) {
        val resumed = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is Grade7Chapter2Activity && stage == Stage.RESUMED && activity.resources.configuration.orientation == configuration) resumed.countDown()
        }
        monitor.addLifecycleCallback(callback)
        try {
            scenario.onActivity { it.requestedOrientation = requested }
            assertTrue("The rotated activity must resume before the next rotation", resumed.await(15, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(200,5000)
            scenario.onActivity { assertEquals(configuration,it.resources.configuration.orientation) }
            Log.i("Grade7Review","Rotation resumed in configuration $configuration")
        } finally { monitor.removeLifecycleCallback(callback) }
    }
    private fun correctState(q: ChapterQuestion): QuestionPractice {
        var state = QuestionPractice()
        q.steps.forEach { s ->
            val values = when(s.kind) {
                StepKind.SINGLE -> listOf(s.options.indexOfFirst { it.correct }.toString())
                StepKind.MULTI -> s.options.indices.filter { s.options[it].correct }.take(s.pick).map { it.toString() }
                StepKind.MATCH -> s.pairs.map { it.right }
                StepKind.CLASSIFY -> s.classifyItems.map { it.category }
                StepKind.ORDER -> s.correctOrder.map { it.toString() }
            }; state = state.change(s, values)
        }; return state
    }
    @Test fun freshHomeStartsTheActualFirstQuestionAndCanBookmarkIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("home-progress"))
            compose.onNodeWithTag("home-progress").assertTextEquals("۰ از ۳۱۷")
            compose.onNodeWithTag("hub-list").performScrollToIndex(0)
            compose.onNodeWithTag("home-start").assertDoesNotExist();capture("discovery-01-home-360-default")
            compose.onNodeWithTag("home-start").assertDoesNotExist()
            compose.onNodeWithTag("nav-chapters").performClick()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("chapter-1"))
            compose.onNodeWithTag("chapter-1").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("chapter-section-تمرین‌های اصلی"))
            compose.onNode(hasText("ورود به این بخش") and hasAnyAncestor(hasTestTag("chapter-section-تمرین‌های اصلی"))).performScrollTo().performClick()
            val first = Grade7Chapter1Repository(context).questions.first()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("question-list-${first.id}"))
            compose.onNodeWithTag("question-list-${first.id}").performClick()
            compose.onNodeWithTag("bookmark-question").assertIsDisplayed().performClick()
            val q = Grade7Chapter1Repository(context).questions.first()
            assertTrue(WorkbookProgress(context,"grade7_chapter1",listOf(q)).bookmarked(q.id))
            assertEquals(q.id, LearningPreferences(context).lastLocation()!!.questionId)
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
    }
    @Test fun savedDraftOpensOnlyThroughExplicitQuestionSelection() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 && it.section == "تمرین‌های اصلی" }
        val step = q.steps.single()
        val state = QuestionPractice(shuffleSeed=9142).change(step,listOf(step.options.indexOfFirst { !it.correct }.toString())).submit(q)
        val progress = WorkbookProgress(context,"grade7_chapter2",listOf(q))
        progress.saveDraft(q.id,QuestionDraftCodec.encode(state))
        LearningPreferences(context).remember(PracticeLocation(2,q.id))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("home-start").assertDoesNotExist()
            compose.onNodeWithTag("continue-last").assertDoesNotExist()
            compose.onNodeWithTag("nav-chapters").performClick()
            compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("chapter-2"))
            compose.onNodeWithTag("chapter-2").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("chapter-section-تمرین‌های اصلی"))
            compose.onNode(hasText("ورود به این بخش") and hasAnyAncestor(hasTestTag("chapter-section-تمرین‌های اصلی"))).performScrollTo().performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("question-list-${q.id}"))
            compose.onNodeWithTag("question-list-${q.id}").performClick()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            assertEquals(state,QuestionDraftCodec.decode(progress.draft(q.id)))
        }
    }
    @Test fun bookmarkReviewHasRealEntriesAndOpensTheExactQuestion() {
        val q = Grade7Chapter3Repository(context).questions.first { it.sourceNumber == 15 && it.section == "تمرین‌های اصلی" }
        WorkbookProgress(context,"grade7_chapter3",listOf(q)).bookmark(q.id,true)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-progress").performClick(); hubText("نشان‌گذاری‌ها")
            compose.onNodeWithTag("review-${q.id}").performScrollTo().performClick()
            compose.onNodeWithContentDescription("حذف نشان سؤال").assertExists()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            capture("discovery-03-bookmarked-question")
        }
    }
    @Test fun statisticsSeparateIndependentGuidedAndReferenceAnswers() {
        val chapter = Grade7Catalog(context).chapters[1]; val qs = chapter.questions.filter { it.section == "تمرین‌های اصلی" }
        val first = qs.first();val ready = correctState(first);chapter.progress.recordCheck(first, ready, ready.submit(first))
        val second = qs.first { it.sourceNumber == 8 };val step = second.steps.single();val wrong = step.options.indices.filter { !step.options[it].correct }
        var state = QuestionPractice().change(step,listOf(wrong[0].toString()));val once = state.submit(second);chapter.progress.recordCheck(second,state,once)
        state = once.change(step,listOf(wrong[1].toString()));val twice = state.submit(second);chapter.progress.recordCheck(second,state,twice)
        assertTrue(twice.canReveal(second));chapter.progress.recordReveal(second.id)
        val third = chapter.questions.first { it.section == "چهارگزینه‌ای" };val s = third.steps.single();val w = s.options.indices.first { !s.options[it].correct }
        val b = QuestionPractice().change(s,listOf(w.toString()));val checked = b.submit(third);chapter.progress.recordCheck(third,b,checked)
        val repaired = checked.change(s,listOf(s.options.indexOfFirst { it.correct }.toString()))
        chapter.progress.recordCheck(third,repaired,repaired.submit(third))
        val summary = Grade7Catalog(context).summary();assertEquals(3,summary.completed);assertEquals(1,summary.independent);assertEquals(1,summary.guided);assertEquals(1,summary.answerViewed);assertEquals(5,summary.attempts)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-progress").performClick();compose.onNodeWithTag("completed-count").assertTextEquals("۳ از ۳۱۷ سؤال کامل شده")
            capture("discovery-04-real-progress")
            hubText("مرور اشتباهات");compose.onNodeWithTag("review-${second.id}").assertExists()
        }
    }
    @Test fun largeFontReduceMotionAndFourTabsSurviveRecreationAt360dp() {
        shell("wm size 1080x2160");shell("wm density 480")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-more").performClick();hubText("تنظیمات")
            compose.onNodeWithTag("font-1.5").performClick();compose.onNodeWithTag("reduce-motion").performScrollTo().performClick()
            assertEquals(DisplaySettings(1.5f,true),LearningPreferences(context).display());scenario.recreate()
            compose.onNodeWithTag("reduce-motion").assertIsOn();capture("discovery-05-settings-360-font150")
            compose.onNodeWithTag("nav-chapters").performClick();compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("chapter-3"))
            capture("discovery-06-chapters-360-font150")
            compose.onNodeWithTag("chapter-3").performClick();compose.onNodeWithText("بخش‌های این فصل").assertExists()
        }
    }
    @Test fun homeAndSettingsRemainUsableOn393dpAndTablet() {
        shell("wm size 1080x2160");shell("wm density 440")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-chapters").assertIsDisplayed();capture("discovery-07-home-393")
        }
        shell("wm size 1200x1920");shell("wm density 240")
        LearningPreferences(context).reduceMotion(true)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-chapters").assertIsDisplayed();capture("discovery-08-tablet-800dp")
            compose.onNodeWithTag("nav-more").performClick();hubText("دربارهٔ علوم‌یار")
            compose.onNodeWithText("کتاب کار علوم تجربی پایهٔ هفتم، چاپ ۱۴۰۵، تألیف علی رنجبر، همراه با پاسخنامهٔ همان کتاب.").assertExists()
        }
    }
    @Test fun bazaarRoutingUsesOnlyGradeSevenAndHandlesEveryMissingHandler() {
        val calls = mutableListOf<Intent>()
        val web = launchBazaarReview { calls.add(it);if (calls.size == 1) throw ActivityNotFoundException() }
        assertEquals(BazaarDestination.WEB,web);assertEquals("com.farsitel.bazaar",calls[0].`package`)
        assertEquals("bazaar://details?id=${BuildConfig.APPLICATION_ID}",calls[0].dataString)
        assertEquals("https://cafebazaar.ir/app/${BuildConfig.APPLICATION_ID}",calls[1].dataString)
        assertEquals(BazaarDestination.APP,launchBazaarReview { })
        assertEquals(BazaarDestination.UNAVAILABLE,launchBazaarReview { throw ActivityNotFoundException() })
        assertEquals(BazaarDestination.UNAVAILABLE,launchBazaarReview { throw SecurityException() })
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("nav-more").performClick();compose.onNodeWithTag("hub-list").performScrollToNode(hasTestTag("bazaar-review"))
            compose.onNodeWithTag("bazaar-review").performClick()
        }
    }
    @Test fun rotatingAQuestionKeepsTheDraftAndCannotUnlockAnAnswer() {
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 && it.section == "تمرین‌های اصلی" }
        val s = q.steps.single();val wrong = s.options.first { !it.correct }
        val intent = Intent(context,Grade7Chapter2Activity::class.java).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7Chapter2Activity>(intent).use { scenario ->
            choose(wrong.text);compose.onNodeWithText("بررسی پاسخ").performClick()
            val store = WorkbookProgress(context,"grade7_chapter2",listOf(q));val before = QuestionDraftCodec.decode(store.draft(q.id))
            // Resource changes and ActivityScenario callbacks are asynchronous. Await the actual
            // resumed replacement before asking Compose to synchronize or rotating again.
            rotateAndAwaitResume(scenario,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE)
            rotateAndAwaitResume(scenario,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
            Log.i("Grade7Review","Recreating the settled portrait activity")
            scenario.recreate();compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            assertEquals(before,QuestionDraftCodec.decode(store.draft(q.id)));capture("discovery-09-rotation-draft")
        }
    }
    @Test fun replacementFiguresAreOfflineAvailableAndAnswerRemainsLocked() {
        val q = Grade7Chapter3Repository(context).questions.first { it.sourceNumber == 12 && it.section == "تمرین‌های اصلی" }
        assertEquals(listOf("g7_ch03_q12_c_question.webp","g7_ch03_q12_b_question.webp","g7_ch03_q12_a_question.webp"),q.visuals.map { it.asset })
        val intent = Intent(context,Grade7Chapter3Activity::class.java).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7Chapter3Activity>(intent).use {
            compose.waitUntil(10000) { compose.onAllNodes(hasText("بزرگ‌نمایی تصویر") and isEnabled()).fetchSemanticsNodes().size == 3 }
            repeat(3) { index -> compose.onAllNodesWithText("بزرگ‌نمایی تصویر")[index].performScrollTo().assertIsDisplayed() }
            compose.onAllNodesWithText("بزرگ‌نمایی تصویر")[0].performScrollTo()
            capture("discovery-10-new-particle-images")
            compose.onAllNodesWithText("بزرگ‌نمایی تصویر")[0].performClick();compose.onNodeWithContentDescription("بزرگ‌تر").assertIsDisplayed()
            compose.onNodeWithText("بستن").performClick();compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
        }
        val info = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID,android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it == "android.permission.INTERNET" || it == "com.google.android.gms.permission.AD_ID" })
    }
    @Test fun readingTextUsesSeventeenSpAndScalesWithoutChangingTheBookWording() {
        shell("wm size 1080x2160");shell("wm density 480")
        LearningPreferences(context).textScale(1.5f)
        val q = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 && it.section == "تمرین‌های اصلی" }
        val intent = Intent(context,Grade7Chapter2Activity::class.java).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7Chapter2Activity>(intent).use {
            compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("native-source-question"))
            val prompt = compose.onNodeWithText(workbookBidi(q.nativeQuestion.first().text)).performScrollTo().assertIsDisplayed()
            prompt.assertTextEquals(workbookBidi(q.nativeQuestion.first().text))
            val layouts = mutableListOf<TextLayoutResult>()
            prompt.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(17.sp,layouts.single().layoutInput.style.fontSize)
            assertEquals(1.5f,layouts.single().layoutInput.density.fontScale,.01f)
            compose.onNodeWithTag("reveal-answer").assertIsDisplayed().assertIsNotEnabled()
            capture("discovery-14-readable-question-360-font150")
            compose.onNodeWithText("مشاهدهٔ تصویر خود کتاب").assertDoesNotExist()
            compose.onNodeWithContentDescription("صورت سؤال ۸ از کتاب").assertDoesNotExist()

        }
        Grade7Catalog(context).chapters.flatMap { it.questions }.forEach { question ->
            assertTrue(question.id, !question.readablePrompt.isNullOrBlank() || question.steps.any { it.clozeTemplate != null } || question.sourceQuestionVisual?.startsWith("pdf:") == true)
            question.steps.filter { it.clozeTemplate != null }.forEach { step ->
                assertEquals(step.clozeTemplate,ClozeDocument.parse(step.clozeTemplate!!,step.id).toTemplate())
            }
        }
    }
}
