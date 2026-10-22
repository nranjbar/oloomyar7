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
    val acceptedOrders: List<List<Int>> = emptyList(),
    /** Verbatim source text; {{0}}, {{1}}, ... replace only the printed blank marks. */
    val clozeTemplate: String? = null,
    val allowTokenReuse: Boolean = true,
    /** Earlier practice labels mapped to their clarified equivalents; source keys are untouched. */
    val choiceAliases: Map<String, String> = emptyMap(),
    /** Printed MCQ options keep their original الف، ب، ج، د order in every session. */
    val preserveOptionOrder: Boolean = false
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
    val answerVisualCaption: String? = null,
    /** Unedited workbook crop, authoritative when PDF text extraction reorders Persian. */
    val sourceQuestionVisual: String? = null,
    /** Original PDF text layer with the old raster figure omitted when a replacement is supplied. */
    val promptVisual: String? = null,
    /** Manually checked reading transcription; scientific layouts remain in their source figure. */
    val readablePrompt: String? = null,
    val originalFigureVisual: String? = null,
    /** Editorial review note, separate from the immutable workbook question/key. */
    val sourceReviewNote: String? = null,
    /** Visually checked print text and standalone figures. No page-image fallback. */
    val nativeQuestion: List<SourceBlock> = emptyList(),
    val nativeAnswer: List<SourceBlock> = emptyList(),
    /** False only for documented scientific/figure corrections in practice. */
    val practiceUsesPrintedKey: Boolean = true,
    /** Owner-supplied learning illustration, separately labelled after the gated source answer. */
    val answerStudyVisual: String? = null,
    val answerStudyCaption: String? = null,
    /** Reviewed explanation, clearly separate from the literal printed key/art. */
    val answerReviewNote: String? = null,
    /** A scientific control change must never reuse authorization from old drafts. */
    val practiceRevision: Int = 0
)

data class SourceBlock(
    val type: String,
    val text: String = "",
    val step: Int = 0,
    val asset: String? = null,
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val widths: List<Float> = emptyList(),
    val parts: List<MathPart> = emptyList()
)
data class MathPart(val text: String = "", val numerator: String? = null, val denominator: String? = null)

data class QuestionVisual(val asset: String, val caption: String? = null)
data class LegacyProgress(val store: String, val id: String)
