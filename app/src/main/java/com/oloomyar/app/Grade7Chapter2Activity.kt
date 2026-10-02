package com.oloomyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.oloomyar.app.data.Grade7Chapter2Repository
import com.oloomyar.app.data.WorkbookProgress
import com.oloomyar.app.ui.AuditedWorkbookApp
import com.oloomyar.app.ui.theme.OloomYarTheme

class Grade7Chapter2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = Grade7Chapter2Repository(applicationContext)
        val progress = WorkbookProgress(applicationContext, "grade7_chapter2", repository.questions)
        setContent {
            OloomYarTheme {
                AuditedWorkbookApp("فصل ۲ • اندازه‌گیری در علوم و ابزارهای آن", repository.questions, progress, onExit = { finish() })
            }
        }
    }
}
