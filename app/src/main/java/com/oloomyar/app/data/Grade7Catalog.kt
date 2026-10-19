package com.oloomyar.app.data

import android.content.Context
import com.oloomyar.app.model.*
import org.json.JSONArray

data class ChapterCollection(val id: Int, val title: String, val questions: List<ChapterQuestion>, val progress: WorkbookProgress) {
    fun summary() = LearningSummary(progress.snapshot())
}
class Grade7Catalog(context: Context) {
    private val manifest = context.assets.open("content/catalog.json").bufferedReader().use { JSONArray(it.readText()) }
    val chapters = (0 until manifest.length()).map { index ->
        val entry = manifest.getJSONObject(index)
        val id = entry.getInt("id")
        val questions = when (id) {
            1 -> Grade7Chapter1Repository(context).questions
            2 -> Grade7Chapter2Repository(context).questions
            3 -> Grade7Chapter3Repository(context).questions
            else -> AssetQuestionRepository(context, "chapter$id.json", "Grade 7 chapter $id workbook 1405",
                entry.getInt("questions"), entry.getInt("steps"), setOf("تمرین‌های اصلی", "چهارگزینه‌ای")).questions
        }
        ChapterCollection(id, entry.getString("title"), questions, WorkbookProgress(context, "grade7_chapter$id", questions))
    }.also { require(it.map { c -> c.id } == (1..15).toList()) }
    fun summary() = LearningSummary(chapters.flatMap { it.progress.snapshot() })
    fun validLocation(location: PracticeLocation?) = location?.takeIf { l -> chapters.any { c -> c.id == l.chapter && c.questions.any { it.id == l.questionId } } }
}
