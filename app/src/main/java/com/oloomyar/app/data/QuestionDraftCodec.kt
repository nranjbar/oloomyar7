package com.oloomyar.app.data

import com.oloomyar.app.model.QuestionPractice
import org.json.JSONArray
import org.json.JSONObject

/** v2 drafts were per-part checks. Keep their selections, never import their answer unlocks. */
object QuestionDraftCodec {
    private fun list(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    private fun map(o: JSONObject?) = o?.keys()?.asSequence()?.associateWith { list(o.optJSONArray(it)) }.orEmpty()

    fun decode(raw: String?): QuestionPractice = runCatching {
        val o = JSONObject(raw ?: "{}")
        val current = o.optInt("questionPracticeVersion") == QuestionPractice.VERSION
        val failures = if (current) o.optJSONArray("incorrectSubmissions") else null
        QuestionPractice(
            answers = map(o.optJSONObject("answers")),
            checked = if (current) map(o.optJSONObject("checked")) else emptyMap(),
            incorrectSubmissions = if (failures == null) emptyList() else List(failures.length()) { i ->
                val attempt = failures.getJSONArray(i)
                List(attempt.length()) { j -> list(attempt.getJSONArray(j)) }
            },
            shownHints = if (current) o.optInt("shownHints") else 0,
            revealed = current && o.optBoolean("revealed"),
            shuffleSeed = if (o.has("shuffleSeed")) o.getInt("shuffleSeed") else kotlin.random.Random.nextInt()
        ).let { if (it.revealed && !it.canReveal()) it.copy(revealed = false) else it }
    }.getOrElse { QuestionPractice() }

    fun encode(state: QuestionPractice) = JSONObject().apply {
        put("questionPracticeVersion", QuestionPractice.VERSION)
        put("answers", JSONObject().apply { state.answers.forEach { (k,v) -> put(k, JSONArray(v)) } })
        put("checked", JSONObject().apply { state.checked.forEach { (k,v) -> put(k, JSONArray(v)) } })
        put("incorrectSubmissions", JSONArray().apply {
            state.incorrectSubmissions.forEach { rows -> put(JSONArray().apply { rows.forEach { put(JSONArray(it)) } }) }
        })
        put("shownHints", state.shownHints)
        put("revealed", state.revealed)
        put("shuffleSeed", state.shuffleSeed)
    }.toString()
}
