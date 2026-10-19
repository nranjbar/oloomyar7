package com.oloomyar.app.data

import com.oloomyar.app.model.QuestionPractice
import com.oloomyar.app.model.ChapterQuestion
import org.json.JSONArray
import org.json.JSONObject

/** v2 drafts were per-part checks. Keep their selections, never import their answer unlocks. */
object QuestionDraftCodec {
    private fun list(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    private fun map(o: JSONObject?) = o?.keys()?.asSequence()?.associateWith { list(o.optJSONArray(it)) }.orEmpty()

    fun decode(raw: String?, question: ChapterQuestion? = null): QuestionPractice = runCatching {
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
        ).let { migrateAlloyCells(it, question) }
            .let { if (it.revealed && !it.canReveal(question)) it.copy(revealed = false) else it }
    }.getOrElse { QuestionPractice() }

    /** The same printed table now has separate metal cells instead of a whole
     * compound answer. Preserve old choices/checks rather than assigning new ones.
     */
    private fun migrateAlloyCells(state: QuestionPractice, question: ChapterQuestion?): QuestionPractice {
        if (question?.relatedChapter != 4 || question.sourceNumber != 17 || question.section != "تمرین‌های اصلی") return state
        val step=question.steps.singleOrNull()?.takeIf { it.pairs.size==6 } ?: return state
        fun expand(values: List<String>): List<String> {
            if (values.size!=4) return values
            fun metals(value: String, omitIron: Boolean): List<String> {
                val words=value.replace('\u200c',' ').split(Regex("\\s+و\\s+" )).map { it.trim() }
                val components=if(omitIron) words.filter { it!="آهن" } else words
                return List(2) { components.getOrElse(it) { "" } }
            }
            return metals(values[0],true)+listOf(values[1])+metals(values[2],false)+listOf(values[3])
        }
        val answers=state.answers[step.id]?.let { state.answers+(step.id to expand(it)) } ?: state.answers
        val checked=state.checked[step.id]?.let { state.checked+(step.id to expand(it)) } ?: state.checked
        return state.copy(answers=answers,checked=checked,incorrectSubmissions=state.incorrectSubmissions.map { attempt -> attempt.map { expand(it) } })
    }

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
