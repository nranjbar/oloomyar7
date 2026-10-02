package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class PracticeAttemptsTest {
    private fun step(kind: StepKind, pick: Int = 0) = LearningStep(
        id = "attempt-test", kind = kind, prompt = "Choose", answer = "reference",
        hint1 = "concept", hint2 = "method", explanation = "reason",
        options = listOf(OptionItem("a", true), OptionItem("b", true), OptionItem("c", false)),
        pick = pick
    )

    @Test fun incompleteSelectionIsNotAnAttempt() {
        assertFalse(PracticeAttempts.canSubmit(step(StepKind.SINGLE), emptyList(), null))
        assertFalse(PracticeAttempts.canSubmit(step(StepKind.MULTI, 2), listOf("0"), null))
    }

    @Test fun submittingTheSameChoiceAgainIsNotANewAttempt() {
        val single = step(StepKind.SINGLE)
        assertTrue(PracticeAttempts.canSubmit(single, listOf("2"), null))
        assertFalse(PracticeAttempts.canSubmit(single, listOf("2"), listOf("2")))
        assertTrue(PracticeAttempts.canSubmit(single, listOf("1"), listOf("2")))
    }

    @Test fun reselectingTheSameMultipleChoicesInAnotherOrderDoesNotCount() {
        val multiple = step(StepKind.MULTI, 2)
        assertFalse(PracticeAttempts.canSubmit(multiple, listOf("2", "0"), listOf("0", "2")))
        assertTrue(PracticeAttempts.canSubmit(multiple, listOf("1", "0"), listOf("0", "2")))
    }

    @Test fun swappedMatchRowsAreDifferentAnswers() {
        val match = step(StepKind.MATCH).copy(pairs = listOf(PairItem("one", "a"), PairItem("two", "b")))
        assertTrue(PracticeAttempts.canSubmit(match, listOf("a", "b"), listOf("b", "a")))
        assertFalse(PracticeAttempts.canSubmit(match, listOf("a", "b"), listOf("a", "b")))
    }

    @Test fun bothAttemptsAndBothHintsAreRequiredForAReferenceAnswer() {
        assertFalse(PracticeAttempts.canReveal(0, 2))
        assertFalse(PracticeAttempts.canReveal(1, 2))
        assertFalse(PracticeAttempts.canReveal(2, 0))
        assertFalse(PracticeAttempts.canReveal(2, 1))
        assertTrue(PracticeAttempts.canReveal(2, 2))
        assertTrue(PracticeAttempts.canReveal(3, 2))
        assertEquals(1, PracticeAttempts.hintLevel(1))
        assertEquals(2, PracticeAttempts.hintLevel(3))
    }

    @Test fun rowFeedbackAcceptsValidAlternativeOrderAndOnlyFlagsTheMismatch() {
        val match = step(StepKind.MATCH).copy(
            pairs = listOf(PairItem("one", "a"), PairItem("two", "b")),
            acceptedPairOrders = listOf(listOf("b", "a"))
        )
        assertTrue(WorkbookAnswers.incorrectRows(match, listOf("b", "a")).isEmpty())
        assertEquals(listOf(1), WorkbookAnswers.incorrectRows(match, listOf("a", "a")))
    }
}
