package tj.tajikdictionary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

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

    @Test
    fun zipfTextCoveragePlacementAndStudyAnalytics() {
        assertEquals(0.0, WordGameEngine.textRecognitionPercent(0.0), 0.001)
        val p100 = WordGameEngine.textRecognitionPercent(100.0)
        val p1500 = WordGameEngine.textRecognitionPercent(1500.0)
        assertTrue(p100 > 25.0 && p100 < 50.0)
        assertTrue(p1500 > p100 && p1500 < 99.0)

        assertEquals("45s", WordGameEngine.formatStudyDuration(45))
        assertEquals("12m", WordGameEngine.formatStudyDuration(720))
        assertEquals("2h 5m", WordGameEngine.formatStudyDuration(7500))

        val pool = (listOf(96, 94, 91, 88, 84, 81, 78, 74, 71, 65, 55, 45).flatMapIndexed { idx, freq ->
            listOf(
                WordCardCandidate(
                    entryId = (idx + 1).toLong(),
                    language = Language.TAJIK,
                    headword = "tg_$idx",
                    preview = "meaning_$idx",
                    pos = PartOfSpeech.NOUN,
                    frequency = freq,
                    topic = null,
                    exampleTg = null,
                    examplePartner = null,
                ),
            )
        })
        val questions = WordGameEngine.buildPlacementQuestions(pool)
        assertEquals(12, questions.size)

        val a2Answers = questions.map { q ->
            PlacementAnswer(question = q, isCorrect = q.tier <= 2)
        }
        val a2Result = WordGameEngine.evaluatePlacement(a2Answers)
        assertEquals(CefrLevel.A2, a2Result.cefrLevel)
        assertEquals(6, a2Result.knownEntryIds.size)
        assertEquals(6, a2Result.focusEntryIds.size)

        val frontierQueue = WordGameEngine.buildRoundQueue(
            candidates = pool,
            progressById = emptyMap(),
            roundSize = 4,
            targetMaxFrequency = CefrLevel.A2.recommendedCeilingFrequency,
        )
        assertTrue(frontierQueue.isNotEmpty())
        assertTrue(frontierQueue.first().frequency <= CefrLevel.A2.recommendedCeilingFrequency)

        val today = LocalDate.of(2026, 11, 15)
        val nowMs = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000L
        val logs = listOf(
            DailyStudySnapshot(today.toString(), 10, 4, 120, 1, studySeconds = 300, correctReviews = 8),
            DailyStudySnapshot(today.minusDays(1).toString(), 6, 2, 70, 0, studySeconds = 180, correctReviews = 4),
        )
        val summary = WordGameEngine.computeSummary(
            progress = emptyList(),
            logs = logs,
            dailyGoal = 10,
            placementLevel = CefrLevel.A2,
            placementBaselineWords = CefrLevel.A2.baselineKnownWords,
            nowMs = nowMs,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        )
        assertEquals(480, summary.totalStudySeconds)
        assertEquals(2, summary.activeDaysCount)
        assertEquals(75, summary.overallAccuracyPercent)
        assertEquals(CefrLevel.A2, summary.cefrLevel)
        assertEquals(7, summary.recent7Days.size)
        assertTrue(summary.recent7Days.last().metGoal)
    }
}
