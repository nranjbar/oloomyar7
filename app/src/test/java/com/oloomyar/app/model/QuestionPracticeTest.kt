package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class QuestionPracticeTest {
    private val blank = LearningStep("blank",StepKind.MATCH,"source",pairs=listOf(PairItem("a","A"),PairItem("b","B")),matchChoices=listOf("A","B","C","D"),answer="original",hint1="first",hint2="second",explanation="")
    private val single = LearningStep("single",StepKind.SINGLE,"source",options=listOf(OptionItem("right",true),OptionItem("wrong",false),OptionItem("other",false)),answer="original",hint1="first",hint2="second",explanation="")
    private fun question(vararg steps: LearningStep) = ChapterQuestion("q",1,"section","source","title","concept",1,steps.toList(),null)

    @Test fun incompleteWholeQuestionDoesNotCountAnAttempt() {
        val q=question(blank,single)
        val state=QuestionPractice().change(blank,listOf("A","C"))
        assertFalse(state.canCheck(q));assertEquals(state,state.submit(q));assertFalse(state.canReveal())
    }
    @Test fun checkMarksEachBlankAndChangingOnlyAWrongBlankReturnsItToYellow() {
        val q=question(blank)
        val checked=QuestionPractice().change(blank,listOf("A","C")).submit(q)
        assertEquals(ResponseMark.CORRECT,checked.mark(blank,0));assertEquals(ResponseMark.INCORRECT,checked.mark(blank,1))
        val edited=checked.change(blank,listOf("D","D"))
        assertEquals(listOf("A","D"),edited.values(blank));assertEquals(ResponseMark.SELECTED,edited.mark(blank,1))
        assertEquals(1,edited.incorrectSubmissions.size)
    }
    @Test fun unchangedAndPreviouslySubmittedResponsesCannotUnlockAnAnswer() {
        val q=question(single)
        val once=QuestionPractice().change(single,listOf("1")).submit(q)
        assertEquals(1,once.incorrectSubmissions.size);assertFalse(once.canCheck(q));assertFalse(once.canReveal())
        val twice=once.change(single,listOf("2")).submit(q)
        assertTrue(twice.canReveal());assertFalse(twice.revealed)
        val old=twice.change(single,listOf("1"))
        assertFalse(old.canCheck(q));assertEquals(2,old.submit(q).incorrectSubmissions.size)
    }
    @Test fun aCorrectAnswerAloneNeverRevealsTheOriginalKey() {
        val q=question(blank,single)
        val done=QuestionPractice().change(blank,listOf("A","B")).change(single,listOf("0")).submit(q)
        assertTrue(done.correct(q));assertFalse(done.canReveal());assertFalse(done.reveal().revealed)
        assertFalse(done.canCheck(q));assertEquals(done,done.change(single,listOf("1")))
    }
    @Test fun bothHintsAndAnExplicitRequestAreRequired() {
        val q=question(single)
        val twice=QuestionPractice().change(single,listOf("1")).submit(q).change(single,listOf("2")).submit(q)
        assertEquals(2,twice.shownHints);assertFalse(twice.copy(shownHints=1).canReveal())
        assertFalse(twice.revealed);assertTrue(twice.reveal().revealed)
    }
    @Test fun revealLocksTheQuestionButDoesNotNavigateOrModifyTheSource() {
        val q=question(single)
        val shown=QuestionPractice().change(single,listOf("1")).submit(q).change(single,listOf("2")).submit(q).reveal()
        assertEquals(shown,shown.change(single,listOf("0")));assertEquals("original",q.steps.single().answer)
    }
    @Test fun validRepeatedWordsAndAlternativeOrdersRemainCorrect() {
        val repeated=blank.copy(pairs=listOf(PairItem("a","A"),PairItem("b","A")))
        assertTrue(QuestionPractice().change(repeated,listOf("A","A")).submit(question(repeated)).correct(question(repeated)))
        val alternative=blank.copy(acceptedPairOrders=listOf(listOf("B","A")))
        val state=QuestionPractice().change(alternative,listOf("B","A")).submit(question(alternative))
        assertEquals(ResponseMark.CORRECT,state.mark(alternative,0));assertTrue(state.correct(question(alternative)))
    }
    @Test fun editingAndCheckingPreserveTheRandomOrderSeed() {
        val state=QuestionPractice(shuffleSeed=123).change(single,listOf("1")).submit(question(single))
        assertEquals(123,state.shuffleSeed);assertEquals(123,state.change(single,listOf("2")).shuffleSeed)
    }
    @Test fun changingOnePartCannotUnlockAnotherUnchangedWrongPart() {
        val other = single.copy(id = "other")
        val q = question(single, other)
        val first = QuestionPractice().change(single, listOf("1")).change(other, listOf("1")).submit(q)
        val second = first.change(single, listOf("2")).submit(q)
        assertEquals(2, second.hintLevel(q, single)); assertEquals(1, second.hintLevel(q, other))
        assertFalse(second.canReveal(q)); assertFalse(second.reveal(q).revealed)
        val third = second.change(other, listOf("2")).submit(q)
        assertTrue(third.canReveal(q)); assertTrue(third.reveal(q).revealed)
    }
    @Test fun aCorrectLockedPartDoesNotRequireIntentionalWrongAnswers() {
        val other = single.copy(id = "other")
        val q = question(single, other)
        val state = QuestionPractice().change(single, listOf("0")).change(other, listOf("1")).submit(q)
            .change(other, listOf("2")).submit(q)
        assertTrue(state.canReveal(q)); assertEquals(0, state.hintLevel(q, single))
        assertEquals(ResponseMark.CORRECT, state.mark(single, 0))
    }
}
