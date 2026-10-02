package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class WorkbookAnswersTest {
    private fun step(kind: StepKind) = LearningStep("test", kind, "prompt", answer = "answer", hint1 = "one", hint2 = "two", explanation = "why")

    @Test fun blanksRequireEveryPositionAndAcceptBothValidReactantOrders() {
        val value = step(StepKind.MATCH).copy(
            pairs = listOf(PairItem("blank1", "N"), PairItem("blank2", "H")),
            matchChoices = listOf("N", "H", "O"), acceptedPairOrders = listOf(listOf("H", "N")))
        assertFalse(WorkbookAnswers.isComplete(value, listOf("N")))
        assertFalse(WorkbookAnswers.isComplete(value, listOf("N", "")))
        assertTrue(WorkbookAnswers.isCorrect(value, listOf("N", "H")))
        assertTrue(WorkbookAnswers.isCorrect(value, listOf("H", "N")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("N", "N")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("O", "H")))
    }

    @Test fun repeatedCorrectWordCanFillSeveralBlanksWithoutBeingConsumed() {
        val value = step(StepKind.MATCH).copy(pairs = listOf(PairItem("Mg", "2"), PairItem("O", "2")), matchChoices = listOf("1", "2", "3"))
        assertTrue(WorkbookAnswers.isCorrect(value, listOf("2", "2")))
    }

    @Test fun twoOfThreeValidExamplesAreAcceptedButExtraOrDuplicateSelectionsAreNot() {
        val value = step(StepKind.MULTI).copy(pick = 2, options = listOf(OptionItem("wire", true), OptionItem("pan", true), OptionItem("alloy", true), OptionItem("false", false)))
        assertTrue(WorkbookAnswers.isCorrect(value, listOf("0", "2")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("0", "0")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("0", "1", "2")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("0", "3")))
    }

    @Test fun sourceRowChoicesAndVisualOrderAreEnforced() {
        val value = step(StepKind.MATCH).copy(pairs = listOf(PairItem("top", "C₂H₆"), PairItem("middle", "C₂H₄"), PairItem("bottom", "C₂H₂")), matchChoices = listOf("C₂H₂", "C₂H₄", "C₂H₆"))
        assertTrue(WorkbookAnswers.isCorrect(value, listOf("C₂H₆", "C₂H₄", "C₂H₂")))
        assertFalse(WorkbookAnswers.isCorrect(value, listOf("C₂H₂", "C₂H₄", "C₂H₆")))
        val row = step(StepKind.MATCH).copy(pairs = listOf(PairItem("type", "ionic", listOf("ionic", "molecular"))), matchChoices = listOf("ionic", "molecular", "on"))
        assertFalse(WorkbookAnswers.isComplete(row, listOf("on")))
    }

    @Test fun orderRequiresAPermutationAndClassifyRequiresEveryItem() {
        val order = step(StepKind.ORDER).copy(orderItems = listOf("A", "B", "C"), correctOrder = listOf(1, 0, 2))
        assertFalse(WorkbookAnswers.isComplete(order, listOf("0", "0", "2")))
        assertTrue(WorkbookAnswers.isCorrect(order, listOf("1", "0", "2")))
        val groups = step(StepKind.CLASSIFY).copy(categories = listOf("ionic", "molecular"), classifyItems = listOf(ClassifyItem("salt", "ionic"), ClassifyItem("sugar", "molecular")))
        assertFalse(WorkbookAnswers.isComplete(groups, listOf("ionic")))
        assertTrue(WorkbookAnswers.isCorrect(groups, listOf("ionic", "molecular")))
    }

    @Test fun independentCrystalPlacementsAreAcceptedAndOrderingDoesNotStartSolved() {
        val order=step(StepKind.ORDER).copy(orderItems=listOf("water","NaOH","CuSO₄","observe"),correctOrder=listOf(0,1,2,3),acceptedOrders=listOf(listOf(0,2,1,3)))
        assertTrue(WorkbookAnswers.isCorrect(order,listOf("0","2","1","3")))
        assertFalse(WorkbookAnswers.isCorrect(order,listOf("1","0","2","3")))
        val initial=WorkbookAnswers.initialOrder(order)
        assertTrue(WorkbookAnswers.isComplete(order,initial))
        assertFalse(WorkbookAnswers.isCorrect(order,initial))
        assertEquals(initial,WorkbookAnswers.initialOrder(order))
    }
}

