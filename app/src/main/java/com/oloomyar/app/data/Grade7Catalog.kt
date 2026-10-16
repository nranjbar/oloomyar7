package com.oloomyar.app.data

import android.content.Context
import com.oloomyar.app.model.*

data class ChapterCollection(val id: Int, val title: String, val questions: List<ChapterQuestion>, val progress: WorkbookProgress) {
    fun summary() = LearningSummary(progress.snapshot())
}
class Grade7Catalog(context: Context) {
    val chapters = listOf(
        Triple(1, "تجربه و تفکر", Grade7Chapter1Repository(context).questions),
        Triple(2, "اندازه‌گیری در علوم و ابزارهای آن", Grade7Chapter2Repository(context).questions),
        Triple(3, "اتم‌ها، الفبای مواد", Grade7Chapter3Repository(context).questions)
    ).map { (id, title, questions) -> ChapterCollection(id, title, questions, WorkbookProgress(context, "grade7_chapter$id", questions)) }
    fun summary() = LearningSummary(chapters.flatMap { it.progress.snapshot() })
    fun validLocation(location: PracticeLocation?) = location?.takeIf { l -> chapters.any { c -> c.id == l.chapter && c.questions.any { it.id == l.questionId } } }
}
