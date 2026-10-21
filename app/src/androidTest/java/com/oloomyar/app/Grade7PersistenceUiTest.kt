package com.oloomyar.app

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oloomyar.app.data.*
import com.oloomyar.app.model.*
import com.oloomyar.app.ui.workbookBidi
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.json.JSONObject

/** Invoked individually by the host harness, across real APK replacement / process termination. */
@RunWith(AndroidJUnit4::class)
class Grade7PersistenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val question get() = Grade7Chapter2Repository(context).questions.first { it.sourceNumber == 8 && it.section == "تمرین‌های اصلی" }
    private fun capture(name: String) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Download/grade7-screenshots") }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun upgradeKeepsLegacyCompletionSelectionsAndLockedHints() {
        val q = Grade7Chapter1Repository(context).questions.first()
        val progress = WorkbookProgress(context,"grade7_chapter1",Grade7Chapter1Repository(context).questions)
        val legacy = Grade7Chapter1Repository(context).questions[1]
        assertTrue("Old completion must survive install -r",progress.done(legacy.id))
        assertTrue(progress.mistake(legacy.id))
        assertEquals(CompletionKind.LEGACY,progress.snapshot().first { it.id == legacy.id }.kind)
        val state = QuestionDraftCodec.decode(progress.draft(q.id));assertEquals(7413,state.shuffleSeed)
        assertEquals(1,state.incorrectSubmissions.size);assertEquals(1,state.shownHints)
        assertEquals(listOf("دانش","کنجکاوی","فناوری"),state.values(q.steps.single()));assertFalse(state.canReveal(q))
        ActivityScenario.launch<Grade7Chapter1Activity>(Intent(context,Grade7Chapter1Activity::class.java).putExtra("question_id",q.id)).use {
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۱").assertDoesNotExist();capture("discovery-11-real-upgrade-preserved")
        }
    }
    @Test fun prepareCheckpointForRealProcessTermination() {
        val q = question; val store = WorkbookProgress(context,"grade7_chapter2",listOf(q));store.clearDraft(q.id)
        LearningPreferences(context).textScale(1.25f);LearningPreferences(context).reduceMotion(true)
        val intent = Intent(context,Grade7Chapter2Activity::class.java).putExtra("question_id",q.id)
        ActivityScenario.launch<Grade7Chapter2Activity>(intent).use {
            val wrong = q.steps.single().options.first { !it.correct }.text
            compose.onNodeWithTag("question-body").performScrollToNode(hasText(workbookBidi(wrong)))
            compose.onNodeWithText(workbookBidi(wrong)).performClick();compose.onNodeWithText("بررسی پاسخ").performClick()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            compose.onNodeWithTag("question-body").performScrollToIndex(0)
            compose.onNodeWithTag("bookmark-question").performClick()
            compose.onNodeWithTag("question-body").performScrollToNode(hasTestTag("native-hints"))
            compose.waitForIdle()
            val raw = checkNotNull(store.draft(q.id))
            assertTrue(JSONObject(raw).getInt("scrollIndex") > 0 || JSONObject(raw).getInt("scrollOffset") > 0)
            context.getSharedPreferences("grade7_checkpoint_test",Context.MODE_PRIVATE).edit().putString("expected",raw).commit()
            capture("discovery-12-before-process-termination")
        }
    }
    @Test fun upgradeRetainsCompletionButRequiresFreshAuthorizationForChangedScience() {
        val questions=Grade7Chapter1Repository(context).questions
        val q=questions.first { it.sourceNumber==7 && it.section=="تمرین‌های اصلی" }
        val progress=WorkbookProgress(context,"grade7_chapter1",questions)
        assertTrue(progress.done(q.id))
        val old=checkNotNull(progress.draft(q.id))
        assertEquals(0,JSONObject(old).optInt("contentRevision",0))
        val fresh=QuestionDraftCodec.decode(old,q)
        assertEquals(7417,fresh.shuffleSeed);assertEquals(q.practiceRevision,fresh.contentRevision)
        assertTrue(fresh.answers.isEmpty());assertTrue(fresh.checked.isEmpty())
        assertFalse(fresh.revealed);assertFalse(fresh.canReveal(q));assertEquals(0,fresh.shownHints)
        ActivityScenario.launch<Grade7Chapter1Activity>(Intent(context,Grade7Chapter1Activity::class.java).putExtra("question_id",q.id)).use {
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            compose.onNodeWithTag("workbook-reference-answer").assertDoesNotExist()
            assertTrue(progress.done(q.id))
            capture("v10-real-upgrade-scientific-draft-reset")
        }
    }
    @Test fun restoreCheckpointAfterRealProcessTermination() {
        val q = question;val store = WorkbookProgress(context,"grade7_chapter2",listOf(q))
        val expected = QuestionDraftCodec.decode(context.getSharedPreferences("grade7_checkpoint_test",Context.MODE_PRIVATE).getString("expected",null))
        assertEquals(1,expected.incorrectSubmissions.size);assertEquals(1,expected.shownHints)
        assertEquals(expected,QuestionDraftCodec.decode(store.draft(q.id)));assertTrue(store.bookmarked(q.id))
        assertEquals(q.id,LearningPreferences(context).lastLocation()!!.questionId)
        assertEquals(DisplaySettings(1.25f,true),LearningPreferences(context).display())
        val viewport=JSONObject(checkNotNull(store.draft(q.id)))
        assertTrue(viewport.getInt("scrollIndex") > 0 || viewport.getInt("scrollOffset") > 0)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitGrade7Home()
            compose.onNodeWithTag("home-start").performClick()
            compose.onNodeWithTag("reveal-answer").assertIsNotEnabled()
            compose.onNodeWithContentDescription("تصویر پاسخ سؤال ۸").assertDoesNotExist()
            assertEquals(expected,QuestionDraftCodec.decode(store.draft(q.id)));capture("discovery-13-after-process-termination")
            val scroll = compose.onNodeWithTag("question-body").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("Restored viewport must remain below the question heading",scroll.value() > 0f)
        }
    }
}
