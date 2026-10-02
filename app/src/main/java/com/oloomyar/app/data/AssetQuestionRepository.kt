package com.oloomyar.app.data

import android.content.Context
import com.oloomyar.app.model.*
import org.json.JSONArray
import org.json.JSONObject

abstract class AssetQuestionRepository(
    private val context: Context,
    private val assetFile: String,
    private val collectionLabel: String,
    private val expectedQuestionCount: Int,
    private val expectedStepCount: Int,
    private val allowedSections: Set<String>
) {
    val questions: List<ChapterQuestion> by lazy { loadQuestions() }

    private fun optionalText(q: JSONObject, key: String): String? =
        if (q.isNull(key)) null
        else q.optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun strings(arr: JSONArray?): List<String> = buildList {
        if (arr != null) {
            for (i in 0 until arr.length()) {
                add(arr.getString(i))
            }
        }
    }

    private fun loadQuestions(): List<ChapterQuestion> {
        val raw = context.assets
            .open("content/$assetFile")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val arr = JSONArray(raw)

        val loaded = buildList {
            for (i in 0 until arr.length()) {
                val q = arr.getJSONObject(i)
                val stepArr = q.getJSONArray("steps")

                val steps = buildList {
                    for (j in 0 until stepArr.length()) {
                        add(parseStep(stepArr.getJSONObject(j)))
                    }
                }

                add(
                    ChapterQuestion(
                        id = q.getString("id"),
                        number = q.getInt("number"),
                        section = q.getString("section"),
                        source = q.getString("source"),
                        title = q.getString("title"),
                        concept = q.getString("concept"),
                        difficulty = q.getInt("difficulty"),
                        steps = steps,
                        visual = optionalText(q, "visual"),
                        answerVisual = optionalText(q, "answerVisual"),
                        answerVisualCaption = optionalText(q, "answerVisualCaption"),
                        bookPrompt = optionalText(q, "bookPrompt") ?: q.getString("source"),
                        sourceNumber = q.optInt("sourceNumber", q.getInt("number")),
                        sourcePage = q.optInt("sourcePage"),
                        relatedChapter = q.optInt("relatedChapter"),
                        visualCaption = optionalText(q, "visualCaption"),
                        visuals = q.optJSONArray("visuals")?.let { entries ->
                            List(entries.length()) { index ->
                                val entry = entries.getJSONObject(index)
                                QuestionVisual(
                                    asset = entry.getString("asset"),
                                    caption = optionalText(entry, "caption")
                                )
                            }
                        } ?: emptyList(),
                        legacyProgress = q.optJSONArray("legacyProgress")?.let { entries ->
                            List(entries.length()) { index ->
                                val entry = entries.getJSONObject(index)
                                LegacyProgress(entry.getString("store"), entry.getString("id"))
                            }
                        } ?: emptyList()
                    )
                )
            }
        }.sortedBy { it.number }

        validateCollection(loaded)
        return loaded
    }

    private fun validateCollection(questions: List<ChapterQuestion>) {
        require(questions.size == expectedQuestionCount) {
            "$collectionLabel must contain $expectedQuestionCount questions; found ${questions.size}."
        }
        require(questions.map { it.number } == (1..expectedQuestionCount).toList()) {
            "$collectionLabel question numbers must be exactly 1..$expectedQuestionCount."
        }
        require(questions.map { it.id }.distinct().size == questions.size) {
            "$collectionLabel question IDs must be unique."
        }
        require(questions.all { it.section in allowedSections }) {
            "$collectionLabel contains a question from another content section."
        }

        val stepIds = mutableSetOf<String>()
        var stepCount = 0
        questions.forEach { q ->
            require(!q.bookPrompt.isNullOrBlank()) { "Question ${q.number} has no exact source prompt." }
            require(q.steps.isNotEmpty()) { "Question ${q.number} has no learning steps." }
            require(q.difficulty in 1..5) { "Question ${q.number} has invalid difficulty ${q.difficulty}." }

            q.steps.forEach { step ->
                stepCount++
                require(stepIds.add(step.id)) { "Duplicate $collectionLabel step id: ${step.id}" }
                require(step.prompt.isNotBlank()) { "Blank prompt in ${step.id}" }
                require(step.answer.isNotBlank()) { "Blank answer in ${step.id}" }
                require(step.hint1.isNotBlank()) { "Blank hint1 in ${step.id}" }
                require(step.hint2.isNotBlank()) { "Blank hint2 in ${step.id}" }
                require(step.hint1 != step.hint2) { "Identical hints in ${step.id}" }
                if (step.kind == StepKind.MATCH) requireValidMatchChoices(step)
            }

            (listOfNotNull(q.visual, q.answerVisual) + q.visuals.map { it.asset }).forEach { asset ->
                val folder = if (asset.lowercase().endsWith(".svg")) "svg" else "images"
                require(runCatching { context.assets.open("$folder/$asset").close() }.isSuccess) {
                    "Missing $collectionLabel asset: $folder/$asset"
                }
            }
        }
        require(stepCount == expectedStepCount) {
            "$collectionLabel must contain $expectedStepCount learning steps; found $stepCount."
        }
    }

    private fun parseStep(o: JSONObject): LearningStep {
        val kind = when (o.getString("kind")) {
            "single" -> StepKind.SINGLE
            "multi" -> StepKind.MULTI
            "match" -> StepKind.MATCH
            "classify" -> StepKind.CLASSIFY
            "order" -> StepKind.ORDER
            else -> error("Unsupported step kind: ${o.optString("kind")}")
        }

        /*
         * IMPORTANT:
         * Each interaction type has a different JSON schema.
         * Never parse a field using another interaction's schema.
         *
         * Example:
         * ORDER  -> "items": ["منیزیم", "آهن", ...]          (strings)
         * CLASSIFY -> "items": [{"text":"...", "category":"..."}] (objects)
         *
         * The previous implementation parsed every "items" array as JSONObject
         * before checking kind, which caused:
         * Value منیزیم at 0 of type java.lang.String cannot be converted to JSONObject
         */

        val options: List<OptionItem> =
            if (kind == StepKind.SINGLE || kind == StepKind.MULTI) {
                o.optJSONArray("options")?.let { a ->
                    buildList {
                        for (i in 0 until a.length()) {
                            val x = a.getJSONObject(i)
                            add(
                                OptionItem(
                                    text = x.getString("text"),
                                    correct = x.optBoolean("correct")
                                )
                            )
                        }
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }

        val pairs: List<PairItem> =
            if (kind == StepKind.MATCH) {
                o.optJSONArray("pairs")?.let { a ->
                    buildList {
                        for (i in 0 until a.length()) {
                            val x = a.getJSONObject(i)
                            add(
                                PairItem(
                                    left = x.getString("left"),
                                    right = x.getString("right"),
                                    choices = strings(x.optJSONArray("choices"))
                                )
                            )
                        }
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }

        val matchChoices: List<String> =
            if (kind == StepKind.MATCH) strings(o.optJSONArray("choices")) else emptyList()

        val categories: List<String> =
            if (kind == StepKind.CLASSIFY) {
                strings(o.optJSONArray("categories"))
            } else {
                emptyList()
            }

        val classifyItems: List<ClassifyItem> =
            if (kind == StepKind.CLASSIFY) {
                o.optJSONArray("items")?.let { a ->
                    buildList {
                        for (i in 0 until a.length()) {
                            val x = a.getJSONObject(i)
                            add(
                                ClassifyItem(
                                    text = x.getString("text"),
                                    category = x.getString("category")
                                )
                            )
                        }
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }

        val orderItems: List<String> =
            if (kind == StepKind.ORDER) {
                strings(o.optJSONArray("items"))
            } else {
                emptyList()
            }

        val correctOrder: List<Int> =
            if (kind == StepKind.ORDER) {
                buildList {
                    o.optJSONArray("correctOrder")?.let { a ->
                        for (i in 0 until a.length()) {
                            add(a.getInt(i))
                        }
                    }
                }
            } else {
                emptyList()
            }

        return LearningStep(
            id = o.getString("id"),
            kind = kind,
            prompt = o.getString("prompt"),
            options = options,
            pick = if (kind == StepKind.MULTI) o.optInt("pick") else 0,
            pairs = pairs,
            matchChoices = matchChoices,
            categories = categories,
            classifyItems = classifyItems,
            orderItems = orderItems,
            correctOrder = correctOrder,
            answer = o.getString("answer"),
            hint1 = o.getString("hint1"),
            hint2 = o.getString("hint2"),
            explanation = o.getString("explanation"),
            acceptedPairOrders = o.optJSONArray("acceptedPairOrders")?.let { orders ->
                List(orders.length()) { strings(orders.getJSONArray(it)) }
            } ?: emptyList(),
            acceptedOrders = o.optJSONArray("acceptedOrders")?.let { orders ->
                List(orders.length()) { index ->
                    val values = orders.getJSONArray(index)
                    List(values.length()) { values.getInt(it) }
                }
            } ?: emptyList()
        )
    }
}
