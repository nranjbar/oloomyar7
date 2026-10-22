package com.oloomyar.app.data

import android.content.Context
import com.oloomyar.app.model.ChapterQuestion
import com.oloomyar.app.model.*

/** Each collection owns its progress; old stores are only read once per content ID. */
class WorkbookProgress(context: Context, scope: String, private val questions: List<ChapterQuestion>) {
    private val prefs = context.getSharedPreferences("workbook_1405_$scope", Context.MODE_PRIVATE)

    init {
        val edit = prefs.edit()
        questions.forEach { question ->
            if (!prefs.getBoolean("migrated_${question.id}", false)) {
                question.legacyProgress.forEach { old ->
                    val source = context.getSharedPreferences(old.store, Context.MODE_PRIVATE)
                    if (source.getBoolean("done_${old.id}", false)) edit.putBoolean("done_${question.id}", true)
                    if (source.getBoolean("perfect_${old.id}", false)) edit.putBoolean("perfect_${question.id}", true)
                    if (old.id in (source.getStringSet("mistake_ids", emptySet()) ?: emptySet())) {
                        edit.putBoolean("mistake_${question.id}", true)
                    }
                }
                edit.putBoolean("migrated_${question.id}", true)
            }
        }
        edit.apply()
    }

    fun done(id: String) = prefs.getBoolean("done_$id", false)
    fun mistake(id: String) = prefs.getBoolean("mistake_$id", false)
    fun bookmarked(id: String) = prefs.getBoolean("bookmark_$id", false)
    fun bookmark(id: String, value: Boolean) { prefs.edit().putBoolean("bookmark_$id", value).apply() }
    fun clearBookmarks() { prefs.edit().apply { questions.forEach { remove("bookmark_${it.id}") } }.apply() }
    fun snapshot() = questions.map { q ->
        val kind = runCatching { CompletionKind.valueOf(prefs.getString("completion_${q.id}", null).orEmpty()) }.getOrNull()
            ?: if (prefs.getBoolean("perfect_${q.id}", false)) CompletionKind.INDEPENDENT else if (done(q.id)) CompletionKind.LEGACY else CompletionKind.NONE
        QuestionProgress(q.id, done(q.id), kind, prefs.getStringSet("checks_${q.id}", emptySet()).orEmpty().size,
            mistake(q.id), bookmarked(q.id), prefs.getBoolean("viewed_${q.id}", false))
    }
    fun recordCheck(question: ChapterQuestion, before: QuestionPractice, after: QuestionPractice) {
        if (!before.canCheck(question) || before == after) return
        val fingerprint = "${before.shuffleSeed}:${before.signature(question)}"
        val checks = prefs.getStringSet("checks_${question.id}", emptySet()).orEmpty().toMutableSet()
        checks.add(fingerprint)
        prefs.edit().putStringSet("checks_${question.id}", checks).apply()
        if (after.correct(question)) finishPractice(question.id, if (after.shownHints == 0) CompletionKind.INDEPENDENT else CompletionKind.GUIDED)
        else markMistake(question.id)
    }
    fun recordReveal(id: String, solvedBeforeReveal: Boolean = false) {
        prefs.edit().putBoolean("viewed_$id", true).apply()
        // Looking at the key after a valid solve must not turn an independent
        // success into a mistake. Reference use before solving remains assisted.
        if (!solvedBeforeReveal) finishPractice(id, CompletionKind.REFERENCE)
    }
    private fun finishPractice(id: String, kind: CompletionKind) {
        prefs.edit().putBoolean("done_$id", true).putString("completion_$id", kind.name)
            .putBoolean("perfect_$id", kind == CompletionKind.INDEPENDENT)
            .putBoolean("mistake_$id", kind != CompletionKind.INDEPENDENT).apply()
    }
    fun save(id: String, perfect: Boolean) {
        prefs.edit().putBoolean("done_$id", true).putBoolean("perfect_$id", perfect)
            .putBoolean("mistake_$id", !perfect).remove("draft_$id").apply()
    }
    fun markMistake(id: String) { prefs.edit().putBoolean("mistake_$id", true).apply() }
    fun draft(id: String): String? = prefs.getString("draft_$id", null)
    fun saveDraft(id: String, json: String) { prefs.edit().putString("draft_$id", json).apply() }
    /** Persist the viewport before Android may terminate a background process. */
    fun checkpoint(id: String, json: String) { prefs.edit().putString("draft_$id", json).commit() }
    fun clearDraft(id: String) { prefs.edit().remove("draft_$id").apply() }
}
