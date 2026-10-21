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
        ).let { migrateChoiceLabels(it, question) }
            .let { migrateDentalScienceControls(it, question) }
            .let { migrateTenChapterControls(it, question) }
            .let { migrateInstrumentLabel(it, question) }
            .let { migrateAlloyCells(it, question) }
            .let { if (it.revealed && !it.canReveal(question)) it.copy(revealed = false) else it }
    }.getOrElse { QuestionPractice() }

    /** The pending source MCQ key is retained; its separate science exercise
     * now checks three tooth functions. A checked option index cannot count as
     * three answers or carry its old reveal/hint authorization into that task.
     */
    private fun migrateDentalScienceControls(state: QuestionPractice, question: ChapterQuestion?): QuestionPractice {
        if (question?.relatedChapter != 13 || question.sourceNumber != 3 || question.section != "چهارگزینه‌ای") return state
        val step=question.steps.singleOrNull() ?: return state
        val previous=state.answers[step.id] ?: state.checked[step.id] ?: return state
        if (previous.size != 1 || previous.single().toIntOrNull()?.let { it in 0..3 } != true) return state
        return state.copy(answers=state.answers+(step.id to List(3){""}),checked=state.checked-step.id,
            incorrectSubmissions=emptyList(),shownHints=0,revealed=false)
    }

    /** Apply the same label migration to drafts, checks and failed-attempt fingerprints.
     * This preserves locks/hints without counting a wording change as a new attempt.
     */
    private fun migrateChoiceLabels(state: QuestionPractice, question: ChapterQuestion?): QuestionPractice {
        if (question == null || question.steps.none { it.choiceAliases.isNotEmpty() }) return state
        fun normalize(index: Int, values: List<String>): List<String> {
            val aliases = question.steps.getOrNull(index)?.choiceAliases.orEmpty()
            return values.map { aliases[it] ?: it }
        }
        fun normalizeMap(values: Map<String, List<String>>) = values.mapValues { (id, rows) ->
            normalize(question.steps.indexOfFirst { it.id == id }, rows)
        }
        val attempts = state.incorrectSubmissions.map { attempt ->
            attempt.mapIndexed { index, rows -> normalize(index, rows) }
        }.distinct()
        return state.copy(answers=normalizeMap(state.answers),checked=normalizeMap(state.checked),
            incorrectSubmissions=attempts,shownHints=minOf(state.shownHints,attempts.size.coerceAtMost(2)))
    }

    /** Keep existing learning history when compound cells become independent.
     * Only the old preparation-order draft needs a fresh check: it did not answer
     * the new convection-direction task and cannot authorize its reference key.
     */
    private fun migrateTenChapterControls(state: QuestionPractice, question: ChapterQuestion?): QuestionPractice {
        if (question == null || question.section != "تمرین‌های اصلی") return state
        val step=question.steps.singleOrNull() ?: return state
        val id=question.relatedChapter to question.sourceNumber
        fun expand(values:List<String>):List<String> = when {
            id==(7 to 19) && values.size==5 -> values.take(3) +
                (if(values[3]=="دشت‌ها و دامنهٔ کوه‌ها")listOf("دشت‌ها","دامنهٔ کوه‌ها")else listOf(values[3],"")) +
                (if(values[4]=="چاه و قنات")listOf("چاه","قنات")else listOf(values[4],""))
            id==(7 to 20) && values.size==4 -> values.take(3) +
                (if(values[3]=="کوهستانی و شیب دار")listOf("کوهستانی","شیب دار")else listOf(values[3],""))
            id==(9 to 3) && values.size==2 -> {
                fun group(value:String)=when {
                    value.contains("باد") || value.contains("امواج") -> "تجدید پذیر"
                    value.contains("نفت") || value.contains("هسته") -> "تجدید نا پذیر"
                    else -> ""
                }
                step.classifyItems.map { group(values[if(it.category=="تجدید پذیر")0 else 1]) }
            }
            else -> values
        }
        val oldDirections=id==(10 to 13) && state.answers[step.id]?.let { values ->
            values.isNotEmpty() && values.all { value -> value.toIntOrNull()?.let { it in 0..3 }==true }
        }==true
        if(oldDirections)return state.copy(answers=state.answers+(step.id to List(4){""}),checked=state.checked-step.id,
            incorrectSubmissions=emptyList(),shownHints=0,revealed=false)
        if(id !in setOf(7 to 19,7 to 20,9 to 3))return state
        fun mapped(values:Map<String,List<String>>)=values[step.id]?.let { values+(step.id to expand(it)) } ?: values
        return state.copy(answers=mapped(state.answers),checked=mapped(state.checked),
            incorrectSubmissions=state.incorrectSubmissions.map { attempt -> attempt.map { expand(it) } }.distinct())
    }

    private fun migrateInstrumentLabel(state: QuestionPractice, question: ChapterQuestion?): QuestionPractice {
        if (question?.relatedChapter != 2 || question.sourceNumber != 15 || question.section != "تمرین‌های اصلی") return state
        val step=question.steps.first()
        fun normalize(values: List<String>)=values.mapIndexed { row,value -> if(row==2 && value=="انواع نیروسنج") "نیروسنج" else value }
        return state.copy(
            answers=state.answers[step.id]?.let { state.answers+(step.id to normalize(it)) } ?: state.answers,
            checked=state.checked[step.id]?.let { state.checked+(step.id to normalize(it)) } ?: state.checked,
            incorrectSubmissions=state.incorrectSubmissions.map { attempt -> attempt.mapIndexed { index,values -> if(index==0)normalize(values)else values } }
        )
    }

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
