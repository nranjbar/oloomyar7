package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class LearningSummaryTest {
    @Test fun aNewInstallHasNoInventedProgressOrAwards() {
        val s = LearningSummary(listOf(QuestionProgress("q")))
        assertEquals(0, s.completed); assertEquals(0, s.attempts); assertTrue(s.badges.isEmpty()); assertEquals(0f, s.fraction, .0001f)
    }
    @Test fun lookingAtAKeyIsNotIndependentSolving() {
        val s = LearningSummary(listOf(QuestionProgress("q", true, CompletionKind.REFERENCE, 2, true, false, true)))
        assertEquals(1, s.completed); assertEquals(0, s.independent); assertEquals(1, s.answerViewed); assertEquals(2, s.attempts)
        assertFalse("نخستین کشف" in s.badges)
    }
    @Test fun legacyCompletionWithoutEvidenceDoesNotInventIndependentSolvesOrAttempts() {
        val s = LearningSummary(listOf(QuestionProgress("old", true, CompletionKind.LEGACY)))
        assertEquals(1, s.completed); assertEquals(0, s.independent); assertEquals(0, s.attempts)
    }
    @Test fun guidedIndependentAndBookmarksUseOnlyTheirOwnRecords() {
        val s = LearningSummary(listOf(QuestionProgress("a", true, CompletionKind.INDEPENDENT, 1),
            QuestionProgress("b", true, CompletionKind.GUIDED, 3, true, true), QuestionProgress("c", bookmarked = true)))
        assertEquals(2, s.completed); assertEquals(1, s.independent); assertEquals(1, s.guided)
        assertEquals(4, s.attempts); assertEquals(2, s.bookmarks); assertEquals(1, s.mistakes)
        assertEquals(2f / 3f, s.fraction, .0001f); assertTrue("نخستین کشف" in s.badges)
    }
}
