package com.oloomyar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.oloomyar.app.data.Grade7Catalog
import com.oloomyar.app.ui.Grade7DiscoveryApp
import com.oloomyar.app.ui.theme.OloomYarTheme

class MainActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); resumeRevision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = Grade7Catalog(applicationContext)
        setContent {
            OloomYarTheme {
                Grade7DiscoveryApp(catalog, resumeRevision) { chapter, questionId, section, sequence ->
                    val target = when (chapter) {
                        1 -> Grade7Chapter1Activity::class.java
                        2 -> Grade7Chapter2Activity::class.java
                        3 -> Grade7Chapter3Activity::class.java
                        else -> null
                    }
                    target?.let {
                        startActivity(Intent(this@MainActivity, it)
                            .putExtra("question_id", questionId).putExtra("section", section).putExtra("continue_sequence", sequence))
                    }
                }
            }
        }
    }
}
