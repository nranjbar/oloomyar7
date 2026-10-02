package com.oloomyar.app.data

import com.oloomyar.app.model.LearningStep

internal fun requireValidMatchChoices(step: LearningStep) {
    require(step.pairs.isNotEmpty()) { "No pairs in ${step.id}" }
    require(step.matchChoices.distinct().size == step.matchChoices.size) {
        "Duplicate source word-bank choices in ${step.id}"
    }
    if (step.matchChoices.isNotEmpty()) {
        require(step.pairs.all { it.right in step.matchChoices }) {
            "A correct match is missing from the source word bank in ${step.id}"
        }
    }
    step.pairs.forEach { pair ->
        require(pair.choices.distinct().size == pair.choices.size) {
            "Duplicate row choices for ${pair.left} in ${step.id}"
        }
        if (pair.choices.isNotEmpty()) {
            require(pair.right in pair.choices) {
                "The correct answer for ${pair.left} is missing from its choices in ${step.id}"
            }
        }
    }
}

