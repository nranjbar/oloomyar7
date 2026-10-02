package com.oloomyar.app.data

import android.content.Context
import com.oloomyar.app.model.ChapterQuestion

/** Each collection owns its progress; old stores are only read once per content ID. */
class WorkbookProgress(context: Context, scope: String, questions: List<ChapterQuestion>) {
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
    fun save(id: String, perfect: Boolean) {
        prefs.edit().putBoolean("done_$id", true).putBoolean("perfect_$id", perfect)
            .putBoolean("mistake_$id", !perfect).remove("draft_$id").apply()
    }
    fun markMistake(id: String) { prefs.edit().putBoolean("mistake_$id", true).apply() }
    fun draft(id: String): String? = prefs.getString("draft_$id", null)
    fun saveDraft(id: String, json: String) { prefs.edit().putString("draft_$id", json).apply() }
    fun clearDraft(id: String) { prefs.edit().remove("draft_$id").apply() }
    fun saveExam(part: String, correct: Int, total: Int) {
        prefs.edit().putInt("exam_correct_$part", correct).putInt("exam_total_$part", total).apply()
    }
    fun lastExam(part: String): Pair<Int, Int>? = if (prefs.contains("exam_total_$part"))
        prefs.getInt("exam_correct_$part", 0) to prefs.getInt("exam_total_$part", 0) else null
}

