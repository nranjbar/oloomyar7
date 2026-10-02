package com.oloomyar.app.model

/** A new, complete answer is required for each practice attempt. */
object PracticeAttempts {
    const val VERSION = 2

    fun signature(step: LearningStep, answers: List<String>): List<String> =
        if (step.kind == StepKind.MULTI) answers.sorted() else answers

    fun canSubmit(step: LearningStep, answers: List<String>, lastSubmitted: List<String>?): Boolean =
        WorkbookAnswers.isComplete(step, answers) &&
            (lastSubmitted == null || signature(step, answers) != signature(step, lastSubmitted))

    fun hintLevel(failedAttempts: Int): Int = failedAttempts.coerceIn(0, 2)

    fun canReveal(failedAttempts: Int, shownHints: Int): Boolean =
        failedAttempts >= 2 && shownHints >= 2
}
