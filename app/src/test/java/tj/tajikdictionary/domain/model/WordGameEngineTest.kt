package tj.tajikdictionary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Latin input and single typos on longer words are near misses, not failures.
        assertEquals(
            AnswerCheckResult.TajikLetterNearMiss("салом"),
            WordGameEngine.checkTypedAnswer("salom", "салом"),
        )
        assertEquals(
            AnswerCheckResult.TajikLetterNearMiss("хӯрдан"),
            WordGameEngine.checkTypedAnswer("хӯрдн", "хӯрдан"),
        )
        assertEquals(
            AnswerCheckResult.TajikLetterNearMiss("китоб"),
            WordGameEngine.checkTypedAnswer("китоп", "китоб"),
        )
        // Two edits, or a typo on a short word, still count as wrong.
        assertEquals(AnswerCheckResult.Wrong, WordGameEngine.checkTypedAnswer("кит", "китоб"))
        assertEquals(AnswerCheckResult.Wrong, WordGameEngine.checkTypedAnswer("дор", "дар"))
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

    @Test
    fun makeClozeRespectsWordBoundariesAndDoesNotCorruptSubstrings() {
        val sentence = WordGameEngine.makeClozeSentence("Дар китобхона он мехонад.", "он")
        assertTrue(sentence != null)
        assertTrue(sentence!!.contains("китобхона"))
        assertFalse(sentence.contains("китобх____"))
        assertEquals("Дар китобхона ____ мехонад.", sentence)
    }

    @Test
    fun dueAndWeakQueuesPrioritizeCorrectSubsets() {
        val now = 1_731_000_000_000L
        val pool = listOf(
            WordCardCandidate(1, Language.TAJIK, "як", "один", PartOfSpeech.NUMERAL, 100, null, null, null),
            WordCardCandidate(2, Language.TAJIK, "ду", "два", PartOfSpeech.NUMERAL, 95, null, null, null),
            WordCardCandidate(3, Language.TAJIK, "се", "три", PartOfSpeech.NUMERAL, 90, null, null, null),
        )
        val progress = mapOf(
            1L to WordProgressSnapshot(1L, masteryStage = 1, stabilityDays = 0.5, repetitions = 1, lapses = 3, lastReviewedAtMs = now - WordGameEngine.DAY_MS, nextReviewAtMs = now - 1000L),
            2L to WordProgressSnapshot(2L, masteryStage = 4, stabilityDays = 180.0, repetitions = 5, lapses = 0, lastReviewedAtMs = now - WordGameEngine.DAY_MS, nextReviewAtMs = now + 180 * WordGameEngine.DAY_MS, isKnownSkipped = true),
            3L to WordProgressSnapshot(3L, masteryStage = 2, stabilityDays = 4.0, repetitions = 2, lapses = 0, lastReviewedAtMs = now - WordGameEngine.DAY_MS, nextReviewAtMs = now + WordGameEngine.DAY_MS),
        )
        val due = WordGameEngine.buildDueRoundQueue(pool, progress, nowMs = now, roundSize = 5)
        assertEquals(listOf(1L, 3L), due.map { it.entryId })

        val weak = WordGameEngine.buildWeakRoundQueue(pool, progress, roundSize = 5)
        assertEquals(listOf(1L), weak.map { it.entryId })
    }

    @Test
    fun placementSingleItemTierCanPassAndClassicFlashcardUpdatesMastery() {
        val q1 = PlacementQuestion(
            candidate = WordCardCandidate(10, Language.TAJIK, "салом", "привет", PartOfSpeech.INTERJECTION, 100, null, null, null),
            tier = 1,
            choices = listOf("привет", "вода", "дом", "книга"),
        )
        val singleTierResult = WordGameEngine.evaluatePlacement(listOf(PlacementAnswer(q1, true)))
        assertEquals(CefrLevel.A1, singleTierResult.cefrLevel)

        val now = 1_731_000_000_000L
        val (evalGood, learned) = WordGameEngine.evaluateClassicFlashcard(
            existing = null,
            entryId = 10L,
            rating = ReviewRating.GOOD,
            intervalDays = 1,
            nextDueMs = now + WordGameEngine.DAY_MS,
            nowMs = now,
        )
        assertTrue(learned)
        assertEquals(1, evalGood.updated.masteryStage)
        assertTrue(evalGood.xpAwarded > 0)
    }
}
