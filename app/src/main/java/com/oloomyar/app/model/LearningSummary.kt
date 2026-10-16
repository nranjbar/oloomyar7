package com.oloomyar.app.model

enum class CompletionKind { NONE, INDEPENDENT, GUIDED, REFERENCE, LEGACY }
data class QuestionProgress(
    val id: String,
    val done: Boolean = false,
    val kind: CompletionKind = CompletionKind.NONE,
    val attempts: Int = 0,
    val mistake: Boolean = false,
    val bookmarked: Boolean = false,
    val viewedAnswer: Boolean = false
)
data class LearningSummary(val questions: List<QuestionProgress>) {
    val total get() = questions.size
    val completed get() = questions.count { it.done }
    val independent get() = questions.count { it.kind == CompletionKind.INDEPENDENT }
    val guided get() = questions.count { it.kind == CompletionKind.GUIDED }
    val answerViewed get() = questions.count { it.viewedAnswer }
    val attempts get() = questions.sumOf { it.attempts }
    val mistakes get() = questions.count { it.mistake }
    val bookmarks get() = questions.count { it.bookmarked }
    val fraction get() = if (total == 0) 0f else completed.toFloat() / total
    val badges get() = buildList {
        if (independent > 0) add("نخستین کشف")
        if (independent + guided >= 5) add("پنج گام یادگیری")
        if (total > 0 && completed == total) add("مسیر کامل")
    }
}
