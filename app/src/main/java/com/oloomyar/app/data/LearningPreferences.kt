package com.oloomyar.app.data

import android.content.Context
import android.content.SharedPreferences

data class DisplaySettings(val textScale: Float = 1f, val reduceMotion: Boolean = false)
data class PracticeLocation(val chapter: Int, val questionId: String)

/** Small local-only preferences, independent from the existing chapter stores. */
class LearningPreferences(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("grade7_learning_settings", Context.MODE_PRIVATE)
    fun display() = DisplaySettings(preferences.getFloat("text_scale", 1f).coerceIn(1f, 1.5f), preferences.getBoolean("reduce_motion", false))
    fun textScale(value: Float) { preferences.edit().putFloat("text_scale", value.coerceIn(1f, 1.5f)).apply() }
    fun reduceMotion(value: Boolean) { preferences.edit().putBoolean("reduce_motion", value).apply() }
    fun lastLocation(): PracticeLocation? {
        val chapter = preferences.getInt("last_chapter", 0)
        val id = preferences.getString("last_question", null) ?: return null
        return PracticeLocation(chapter, id).takeIf { chapter in 1..15 }
    }
    fun remember(location: PracticeLocation) {
        preferences.edit().putInt("last_chapter", location.chapter).putString("last_question", location.questionId).apply()
    }
}
