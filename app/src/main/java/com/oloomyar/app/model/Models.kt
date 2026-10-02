package com.oloomyar.app.model

enum class StepKind { SINGLE, MULTI, MATCH, CLASSIFY, ORDER }

data class OptionItem(val text: String, val correct: Boolean)
data class PairItem(
    val left: String,
    val right: String,
    /** Optional source-defined choices for this row (used by ordered blank questions). */
    val choices: List<String> = emptyList()
)
data class ClassifyItem(val text: String, val category: String)

data class LearningStep(
    val id: String,
    val kind: StepKind,
    val prompt: String,
    val options: List<OptionItem> = emptyList(),
    val pick: Int = 0,
    val pairs: List<PairItem> = emptyList(),
    /** Complete source word bank for MATCH interactions; preserved in source order. */
    val matchChoices: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val classifyItems: List<ClassifyItem> = emptyList(),
    val orderItems: List<String> = emptyList(),
    val correctOrder: List<Int> = emptyList(),
    val answer: String,
    val hint1: String,
    val hint2: String,
    val explanation: String,
    /** Equally valid source orders, e.g. the two reactants of ammonia. */
    val acceptedPairOrders: List<List<String>> = emptyList(),
    val acceptedOrders: List<List<Int>> = emptyList()
)

data class ChapterQuestion(
    val id: String,
    val number: Int,
    val section: String,
    val source: String,
    val title: String,
    val concept: String,
    val difficulty: Int,
    val steps: List<LearningStep>,
    val visual: String?,
    val answerVisual: String? = null,
    /** Exact wording of the source workbook question, shown before the interactive adaptation. */
    val bookPrompt: String? = null,
    val sourceNumber: Int = number,
    val sourcePage: Int = 0,
    val relatedChapter: Int = 0,
    val visualCaption: String? = null,
    /** Additional source figures when one workbook question contains several distinct images. */
    val visuals: List<QuestionVisual> = emptyList(),
    val legacyProgress: List<LegacyProgress> = emptyList(),
    val answerVisualCaption: String? = null
)

data class QuestionVisual(val asset: String, val caption: String? = null)
data class LegacyProgress(val store: String, val id: String)
