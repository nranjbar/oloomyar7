package com.oloomyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oloomyar.app.data.Grade7Catalog
import com.oloomyar.app.ui.Grade7ChapterGate
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.theme.OloomYarTheme

/** The same practice component and local store serve all fifteen source chapters. */
class Grade7WorkbookActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chapterId = intent.getIntExtra("chapter", 1)
        val chapter = Grade7Catalog(applicationContext).chapters.firstOrNull { it.id == chapterId }
            ?: run { finish(); return }
        setContent {
            OloomYarTheme {
                Grade7ChapterGate(chapterId) {
                AuditedWorkbookApp("فصل $chapterId • ${chapter.title}", chapter.questions, chapter.progress,
                    initialQuestionId = intent.getStringExtra("question_id"),
                    initialSection = intent.getStringExtra("section"),
                    onExit = { finish() })
                }
            }
        }
    }
}
