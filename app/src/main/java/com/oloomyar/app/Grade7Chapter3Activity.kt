package com.oloomyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oloomyar.app.data.Grade7Chapter3Repository
import com.oloomyar.app.data.WorkbookProgress
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.theme.OloomYarTheme

class Grade7Chapter3Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Grade7Chapter3Repository(applicationContext)
        val progress = WorkbookProgress(applicationContext, "grade7_chapter3", repository.questions)
        setContent {
            OloomYarTheme {
                AuditedWorkbookApp("فصل ۳ • اتم‌ها، الفبای مواد", repository.questions, progress,
                    initialQuestionId = intent.getStringExtra("question_id"),
                    initialSection = intent.getStringExtra("section"),
                    continueSequence = intent.getBooleanExtra("continue_sequence", false), onExit = { finish() })
            }
        }
    }
}
