package com.oloomyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oloomyar.app.data.Grade7Chapter1Repository
import com.oloomyar.app.data.WorkbookProgress
import com.oloomyar.app.ui.Grade7ChapterGate
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.theme.OloomYarTheme

class Grade7Chapter1Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Grade7Chapter1Repository(applicationContext)
        val progress = WorkbookProgress(applicationContext, "grade7_chapter1", repository.questions)
        setContent {
            OloomYarTheme {
                Grade7ChapterGate(1) {
                AuditedWorkbookApp("فصل ۱ • تجربه و تفکر", repository.questions, progress,
                    initialQuestionId = intent.getStringExtra("question_id"),
                    initialSection = intent.getStringExtra("section"),
                    onExit = { finish() })
                }
            }
        }
    }
}
