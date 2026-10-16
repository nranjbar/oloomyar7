package com.oloomyar.app.model

enum class ResponseMark { EMPTY, SELECTED, CORRECT, INCORRECT }

/** One explicit check covers every part. No navigation or answer disclosure is implicit. */
data class QuestionPractice(
    val answers: Map<String, List<String>> = emptyMap(),
    val checked: Map<String, List<String>> = emptyMap(),
    val incorrectSubmissions: List<List<List<String>>> = emptyList(),
    val shownHints: Int = 0,
    val revealed: Boolean = false,
    val shuffleSeed: Int = kotlin.random.Random.nextInt()
) {
    companion object { const val VERSION = 3 }

    fun values(step: LearningStep): List<String> = answers[step.id]
        ?: if (step.kind == StepKind.ORDER) WorkbookAnswers.initialOrder(step) else emptyList()

    fun complete(question: ChapterQuestion) = question.steps.all { WorkbookAnswers.isComplete(it, values(it)) }
    fun correct(question: ChapterQuestion) = question.steps.all { s ->
        checked[s.id]?.let { WorkbookAnswers.isCorrect(s, it) } == true
    }
    fun signature(question: ChapterQuestion) = question.steps.map { PracticeAttempts.signature(it, values(it)) }
    fun canCheck(question: ChapterQuestion) = !revealed && !correct(question) && complete(question) &&
        signature(question) !in incorrectSubmissions
    fun canReveal() = PracticeAttempts.canReveal(incorrectSubmissions.size, shownHints)

    fun mark(step: LearningStep, row: Int): ResponseMark {
        val current = values(step)
        if (current.getOrNull(row).isNullOrBlank()) return ResponseMark.EMPTY
        val last = checked[step.id] ?: return ResponseMark.SELECTED
        val same = if (step.kind == StepKind.MULTI) current.sorted() == last.sorted() else current == last
        if (step.kind == StepKind.MATCH || step.kind == StepKind.CLASSIFY) {
            if (row !in WorkbookAnswers.incorrectRows(step, last)) return ResponseMark.CORRECT
            return if (current.getOrNull(row) == last.getOrNull(row)) ResponseMark.INCORRECT else ResponseMark.SELECTED
        }
        return if (!same) ResponseMark.SELECTED
        else if (WorkbookAnswers.isCorrect(step, last)) ResponseMark.CORRECT else ResponseMark.INCORRECT
    }

    fun locked(step: LearningStep, row: Int = 0) = revealed || mark(step, row) == ResponseMark.CORRECT

    fun change(step: LearningStep, changed: List<String>): QuestionPractice {
        if (revealed) return this
        if (step.kind != StepKind.MATCH && step.kind != StepKind.CLASSIFY && locked(step)) return this
        val safe = if (step.kind == StepKind.MATCH || step.kind == StepKind.CLASSIFY) {
            val count = if (step.kind == StepKind.MATCH) step.pairs.size else step.classifyItems.size
            List(count) { row -> if (locked(step, row)) values(step).getOrElse(row) { "" } else changed.getOrElse(row) { "" } }
        } else changed
        return copy(answers = answers + (step.id to safe))
    }

    fun submit(question: ChapterQuestion): QuestionPractice {
        if (!canCheck(question)) return this
        val snapshot = question.steps.associate { it.id to values(it).toList() }
        val wrong = !question.steps.all { WorkbookAnswers.isCorrect(it, values(it)) }
        val history = if (wrong) incorrectSubmissions + listOf(signature(question)) else incorrectSubmissions
        return copy(answers = snapshot, checked = snapshot, incorrectSubmissions = history,
            shownHints = if (wrong) PracticeAttempts.hintLevel(history.size) else shownHints)
    }

    fun reveal() = if (canReveal()) copy(revealed = true) else this
}
