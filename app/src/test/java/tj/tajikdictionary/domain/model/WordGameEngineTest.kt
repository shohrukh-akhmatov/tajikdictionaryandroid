package tj.tajikdictionary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WordGameEngineTest {
    @Test
    fun prioritizesOverduePeriodicReviewsAndHighFrequencyNewWords() {
        val now = 1_731_000_000_000L
        val candidates = listOf(
            WordCardCandidate(1, Language.TAJIK, "салом", "привет", PartOfSpeech.INTERJECTION, 100, "greetings", "Салом, дӯстам!", "Привет, друг!"),
            WordCardCandidate(2, Language.TAJIK, "хӯрдан", "есть", PartOfSpeech.VERB, 90, "verbs", null, null),
            WordCardCandidate(3, Language.TAJIK, "китоб", "книга", PartOfSpeech.NOUN, 80, "education", null, null),
            WordCardCandidate(4, Language.TAJIK, "об", "вода", PartOfSpeech.NOUN, 95, "food", null, null),
        )
        val overdue = WordProgressSnapshot(
            entryId = 2L,
            masteryStage = 1,
            stabilityDays = 1.0,
            repetitions = 1,
            lapses = 0,
            lastReviewedAtMs = now - 3 * WordGameEngine.DAY_MS,
            nextReviewAtMs = now - 2 * WordGameEngine.DAY_MS,
        )

        val queue = WordGameEngine.buildRoundQueue(
            candidates = candidates,
            progressById = mapOf(2L to overdue),
            nowMs = now,
            roundSize = 3,
        )
        assertEquals(3, queue.size)
        assertTrue(queue.any { it.entryId == 2L })
        assertTrue(queue.any { it.entryId == 1L })
    }

    @Test
    fun evaluatesAnswersRequeueOffsetsAndTajikNearMiss() {
        val now = 1_731_000_000_000L
        val evalGood = WordGameEngine.evaluateAnswer(
            existing = null,
            entryId = 1L,
            isCorrect = true,
            usedHintOrNearMiss = false,
            responseMs = 2_400L,
            wasRetriedInRound = false,
            nowMs = now,
        )
        assertTrue(evalGood.newlyLearned)
        assertEquals(15, evalGood.xpAwarded)
        assertEquals(1, evalGood.updated.masteryStage)
        assertTrue(evalGood.updated.stabilityDays > 1.0)

        val evalMiss = WordGameEngine.evaluateAnswer(
            existing = evalGood.updated,
            entryId = 1L,
            isCorrect = false,
            usedHintOrNearMiss = false,
            responseMs = 6_000L,
            wasRetriedInRound = false,
            nowMs = now,
        )
        assertEquals(0, evalMiss.xpAwarded)
        assertEquals(1, evalMiss.updated.lapses)
        assertTrue(evalMiss.updated.stabilityDays < evalGood.updated.stabilityDays)
        assertEquals(3, WordGameEngine.reinsertIndex(5))
        assertEquals(1, WordGameEngine.reinsertIndex(1))

        assertEquals(AnswerCheckResult.Exact, WordGameEngine.checkTypedAnswer("хӯрдан", "хӯрдан"))
        assertEquals(
            AnswerCheckResult.TajikLetterNearMiss("хӯрдан"),
            WordGameEngine.checkTypedAnswer("хурдан", "хӯрдан"),
        )
        assertEquals(AnswerCheckResult.Wrong, WordGameEngine.checkTypedAnswer("китоб", "хӯрдан"))
    }

    @Test
    fun buildsClozeAndComputesStreaksAndBadges() {
        assertEquals("____, дӯстам!", WordGameEngine.makeClozeSentence("Салом, дӯстам!", "салом"))

        val today = LocalDate.of(2026, 11, 15)
        val yesterday = today.minusDays(1)
        val nowMs = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000L
        val logs = listOf(
            DailyStudySnapshot(yesterday.toString(), 10, 4, 110, 1),
            DailyStudySnapshot(today.toString(), 5, 2, 60, 0),
        )
        val progress = listOf(
            WordProgressSnapshot(
                entryId = 1L,
                masteryStage = 4,
                stabilityDays = 14.0,
                lastReviewedAtMs = nowMs,
                nextReviewAtMs = nowMs + 14 * WordGameEngine.DAY_MS,
            ),
        )
        val summary = WordGameEngine.computeSummary(
            progress = progress,
            logs = logs,
            dailyGoal = 10,
            nowMs = nowMs,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(2, summary.currentStreakDays)
        assertEquals(170, summary.totalXp)
        assertEquals(2, summary.level)
        assertEquals(1, summary.masteredCount)
        assertTrue(summary.badges.first { it.id == "first_word" }.unlocked)
    }
}
