package com.oloomyar.app.model

/** Pure evaluation shared by practice, exams and regression tests. */
object WorkbookAnswers {
    fun isComplete(step: LearningStep, answers: List<String>): Boolean = when (step.kind) {
        StepKind.SINGLE -> answers.size == 1 && answers[0].toIntOrNull()?.let { it in step.options.indices } == true
        StepKind.MULTI -> answers.size == step.pick && answers.distinct().size == answers.size &&
            answers.all { answer -> answer.toIntOrNull()?.let { it in step.options.indices } == true }
        StepKind.MATCH -> answers.size == step.pairs.size && answers.indices.all { index ->
            answers[index] in choices(step, index)
        }
        StepKind.CLASSIFY -> answers.size == step.classifyItems.size && answers.all { it in step.categories }
        StepKind.ORDER -> answers.size == step.orderItems.size &&
            answers.mapNotNull { it.toIntOrNull() }.sorted() == step.orderItems.indices.toList()
    }

    fun isCorrect(step: LearningStep, answers: List<String>): Boolean {
        if (!isComplete(step, answers)) return false
        return when (step.kind) {
            StepKind.SINGLE, StepKind.MULTI -> answers.all { step.options[it.toInt()].correct }
            StepKind.MATCH -> answers == step.pairs.map { it.right } || answers in step.acceptedPairOrders
            StepKind.CLASSIFY -> answers == step.classifyItems.map { it.category }
            StepKind.ORDER -> answers.map { it.toInt() }.let { it == step.correctOrder || it in step.acceptedOrders }
        }
    }

    fun choices(step: LearningStep, row: Int): List<String> =
        step.pairs[row].choices.ifEmpty { step.matchChoices.ifEmpty { step.pairs.map { it.right }.distinct() } }

    /** Identify rows to revisit, without exposing their reference answers. */
    fun incorrectRows(step: LearningStep, answers: List<String>): List<Int> {
        val references = when (step.kind) {
            StepKind.MATCH -> listOf(step.pairs.map { it.right }) + step.acceptedPairOrders
            StepKind.CLASSIFY -> listOf(step.classifyItems.map { it.category })
            else -> return emptyList()
        }
        return references.map { reference ->
            reference.indices.filter { answers.getOrNull(it) != reference[it] }
        }.minByOrNull { it.size }.orEmpty()
    }

    fun initialOrder(step: LearningStep): List<String> {
        val shuffled = step.orderItems.indices.shuffled(kotlin.random.Random(step.id.hashCode()))
        val accepted = step.acceptedOrders + listOf(step.correctOrder)
        val start = shuffled.indices.map { shift -> shuffled.drop(shift) + shuffled.take(shift) }
            .firstOrNull { it !in accepted } ?: shuffled
        return start.map { it.toString() }
    }
}
