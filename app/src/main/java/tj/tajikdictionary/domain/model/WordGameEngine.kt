package tj.tajikdictionary.domain.model

import tj.tajikdictionary.core.text.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ChallengeKind {
    MULTIPLE_CHOICE,
    WORD_BUILDER,
    TYPING,
}

/** CEFR-aligned Tajik vocabulary proficiency tiers. */
enum class CefrLevel(
    val code: String,
    val titleResKey: String,
    val recommendedCeilingFrequency: Int,
    val baselineKnownWords: Int,
) {
    A0("A0", "cefr_a0", 100, 0),
    A1("A1", "cefr_a1", 90, 150),
    A2("A2", "cefr_a2", 82, 500),
    B1("B1", "cefr_b1", 72, 1500),
    B2("B2", "cefr_b2", 62, 3200),
}

data class LexiconStats(
    val totalTajik: Int,
    val tier90Count: Int,
    val tier80Count: Int,
    val tier70Count: Int,
    val tierOtherCount: Int,
) {
    companion object {
        val FALLBACK = LexiconStats(
            totalTajik = 16_358,
            tier90Count = 290,
            tier80Count = 1_474,
            tier70Count = 4_536,
            tierOtherCount = 10_058,
        )
    }
}

data class FrequencyTierProgress(
    val id: String,
    val labelResKey: String,
    val knownCount: Int,
    val totalCount: Int,
)

data class DailyActivityPoint(
    val dateKey: String,
    val shortLabel: String,
    val wordsReviewed: Int,
    val studySeconds: Int,
    val metGoal: Boolean,
)

data class PlacementQuestion(
    val candidate: WordCardCandidate,
    val tier: Int,
    val choices: List<String>,
)

data class PlacementAnswer(
    val question: PlacementQuestion,
    val isCorrect: Boolean,
)

data class PlacementResult(
    val cefrLevel: CefrLevel,
    val correctCount: Int,
    val totalCount: Int,
    val estimatedKnownWords: Int,
    val textRecognitionPercent: Double,
    val knownEntryIds: List<Long>,
    val focusEntryIds: List<Long>,
)

data class WordCardCandidate(
    val entryId: Long,
    val language: Language,
    val headword: String,
    val preview: String,
    val pos: PartOfSpeech?,
    val frequency: Int,
    val topic: String?,
    val exampleTg: String?,
    val examplePartner: String?,
)

data class WordProgressSnapshot(
    val entryId: Long,
    val masteryStage: Int = 0,
    val stabilityDays: Double = 0.0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAtMs: Long? = null,
    val nextReviewAtMs: Long = 0L,
    val isKnownSkipped: Boolean = false,
)

data class DailyStudySnapshot(
    val dateKey: String,
    val wordsReviewed: Int,
    val wordsLearned: Int,
    val xpEarned: Int,
    val roundsCompleted: Int,
    val studySeconds: Int = 0,
    val correctReviews: Int = 0,
)

data class GameRoundItem(
    val card: WordCardCandidate,
    val challengeKind: ChallengeKind,
    val choices: List<String>,
    val tiles: List<String>,
    val clozeSentence: String?,
    val isNewWord: Boolean,
    val requiredCorrectInRound: Int = 1,
    val correctInRound: Int = 0,
    val hadMistakeInRound: Boolean = false,
)

sealed interface AnswerCheckResult {
    data object Exact : AnswerCheckResult
    data class TajikLetterNearMiss(val target: String) : AnswerCheckResult
    data object Wrong : AnswerCheckResult
}

data class AchievementBadge(
    val id: String,
    val titleKey: String,
    val unlocked: Boolean,
)

data class GamificationSummary(
    val totalXp: Int,
    val level: Int,
    val xpInCurrentLevel: Int,
    val xpForNextLevel: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val activeDaysCount: Int,
    val totalStudySeconds: Int,
    val totalReviews: Int,
    val overallAccuracyPercent: Int,
    val todayReviewed: Int,
    val todayLearned: Int,
    val dailyGoal: Int,
    val encounteredCount: Int,
    val learningCount: Int,
    val masteredCount: Int,
    val dueNowCount: Int,
    val estimatedKnownWords: Int,
    val textRecognitionPercent: Double,
    val cefrLevel: CefrLevel,
    val totalLexiconCount: Int,
    val tierBreakdown: List<FrequencyTierProgress>,
    val recent7Days: List<DailyActivityPoint>,
    val badges: List<AchievementBadge>,
)

data class AnswerEvaluation(
    val updated: WordProgressSnapshot,
    val xpAwarded: Int,
    val newlyLearned: Boolean,
)

/** Lingvist-inspired periodic memory scheduler and active-recall game engine. */
object WordGameEngine {
    const val DAY_MS = 86_400_000L
    const val DEFAULT_ROUND_SIZE = 10
    private const val BACKLOG_THRESHOLD = 20

    fun retrievalProbability(stabilityDays: Double, elapsedDays: Double): Double {
        val safeStability = max(0.25, stabilityDays)
        val safeElapsed = max(0.0, elapsedDays)
        return 0.9.pow(safeElapsed / safeStability)
    }

    /** Zipf-calibrated percentage of everyday Tajik text recognized for `effectiveKnownWords`. */
    fun textRecognitionPercent(effectiveKnownWords: Double): Double {
        if (effectiveKnownWords <= 0.0) return 0.0
        val pct = 98.5 * (1.0 - 1.0 / (1.0 + effectiveKnownWords / 140.0).pow(0.68))
        return min(99.0, max(0.0, pct))
    }

    fun buildRoundQueue(
        candidates: List<WordCardCandidate>,
        progressById: Map<Long, WordProgressSnapshot>,
        nowMs: Long = System.currentTimeMillis(),
        roundSize: Int = DEFAULT_ROUND_SIZE,
        targetMaxFrequency: Int? = null,
    ): List<WordCardCandidate> {
        val usable = candidates.filter { it.preview.isNotBlank() }
        if (usable.isEmpty() || roundSize <= 0) return emptyList()

        val active = usable.filter { candidate ->
            val p = progressById[candidate.entryId] ?: return@filter true
            !(p.isKnownSkipped && p.nextReviewAtMs > nowMs)
        }

        val dueSorted = active.mapNotNull { candidate ->
            val p = progressById[candidate.entryId] ?: return@mapNotNull null
            val last = p.lastReviewedAtMs ?: return@mapNotNull null
            if (p.nextReviewAtMs > nowMs) return@mapNotNull null
            val elapsedDays = (nowMs - last).toDouble() / DAY_MS.toDouble()
            val prob = retrievalProbability(p.stabilityDays, elapsedDays)
            candidate to prob
        }.sortedWith(
            compareBy<Pair<WordCardCandidate, Double>> { it.second }
                .thenByDescending { it.first.frequency },
        ).map { it.first }

        val unstudied = active.filter { candidate ->
            val p = progressById[candidate.entryId]
            p == null || p.lastReviewedAtMs == null
        }

        val newOrdered = if (targetMaxFrequency != null && targetMaxFrequency < 100) {
            val atOrBelow = unstudied
                .filter { it.frequency <= targetMaxFrequency }
                .sortedWith(
                    compareByDescending<WordCardCandidate> { it.frequency }
                        .thenBy { it.entryId },
                )
            val above = unstudied
                .filter { it.frequency > targetMaxFrequency }
                .sortedWith(
                    compareByDescending<WordCardCandidate> { it.frequency }
                        .thenBy { it.entryId },
                )
            atOrBelow + above
        } else {
            unstudied.sortedWith(
                compareByDescending<WordCardCandidate> { it.frequency }
                    .thenBy { it.entryId },
            )
        }

        val picked = ArrayList<WordCardCandidate>(roundSize)
        val pickedIds = HashSet<Long>()

        if (dueSorted.size >= BACKLOG_THRESHOLD) {
            return dueSorted.take(roundSize)
        }

        val newQuota = if (dueSorted.isEmpty()) {
            min(roundSize, 8)
        } else {
            min(newOrdered.size, min(3, max(1, roundSize / 3)))
        }

        val dueTarget = max(0, roundSize - newQuota)
        for (item in dueSorted.take(dueTarget)) {
            picked += item
            pickedIds += item.entryId
        }

        for (item in newOrdered) {
            if (picked.size >= roundSize) break
            if (pickedIds.add(item.entryId)) {
                picked += item
            }
        }

        for (item in dueSorted) {
            if (picked.size >= roundSize) break
            if (pickedIds.add(item.entryId)) {
                picked += item
            }
        }

        if (picked.isEmpty()) {
            return active.sortedWith(
                compareBy<WordCardCandidate> { progressById[it.entryId]?.stabilityDays ?: 0.0 }
                    .thenByDescending { it.frequency },
            ).take(roundSize)
        }

        return picked
    }

    fun buildDueRoundQueue(
        candidates: List<WordCardCandidate>,
        progressById: Map<Long, WordProgressSnapshot>,
        nowMs: Long = System.currentTimeMillis(),
        roundSize: Int = DEFAULT_ROUND_SIZE,
    ): List<WordCardCandidate> {
        val usable = candidates.filter { it.preview.isNotBlank() }
        if (usable.isEmpty() || roundSize <= 0) return emptyList()

        val activeStudied = usable.filter { candidate ->
            val p = progressById[candidate.entryId] ?: return@filter false
            !p.isKnownSkipped && p.lastReviewedAtMs != null
        }

        val dueSorted = activeStudied.mapNotNull { candidate ->
            val p = progressById[candidate.entryId] ?: return@mapNotNull null
            if (p.nextReviewAtMs > nowMs) return@mapNotNull null
            val elapsed = (nowMs - (p.lastReviewedAtMs ?: nowMs)).toDouble() / DAY_MS.toDouble()
            candidate to retrievalProbability(p.stabilityDays, elapsed)
        }.sortedWith(
            compareBy<Pair<WordCardCandidate, Double>> { it.second }
                .thenByDescending { it.first.frequency },
        ).map { it.first }

        if (dueSorted.size >= roundSize) return dueSorted.take(roundSize)

        val picked = dueSorted.toMutableList()
        val seen = picked.map { it.entryId }.toMutableSet()
        val soonest = activeStudied
            .filter { it.entryId !in seen }
            .sortedWith(
                compareBy<WordCardCandidate> { progressById[it.entryId]?.nextReviewAtMs ?: Long.MAX_VALUE }
                    .thenByDescending { it.frequency },
            )
        for (item in soonest) {
            if (picked.size >= roundSize) break
            picked += item
            seen += item.entryId
        }
        return picked
    }

    fun buildWeakRoundQueue(
        candidates: List<WordCardCandidate>,
        progressById: Map<Long, WordProgressSnapshot>,
        roundSize: Int = DEFAULT_ROUND_SIZE,
    ): List<WordCardCandidate> {
        val usable = candidates.filter { it.preview.isNotBlank() }
        if (usable.isEmpty() || roundSize <= 0) return emptyList()

        return usable.filter { candidate ->
            val p = progressById[candidate.entryId] ?: return@filter false
            !p.isKnownSkipped && (p.lapses > 0 || (p.lastReviewedAtMs != null && p.masteryStage <= 1) || p.stabilityDays < 2.0)
        }.sortedWith(
            compareByDescending<WordCardCandidate> { progressById[it.entryId]?.lapses ?: 0 }
                .thenBy { progressById[it.entryId]?.stabilityDays ?: 0.0 }
                .thenByDescending { it.frequency },
        ).take(roundSize)
    }

    fun challengeKind(masteryStage: Int, headword: String): ChallengeKind {
        val len = headword.trim().length
        return when {
            masteryStage <= 0 -> ChallengeKind.MULTIPLE_CHOICE
            masteryStage == 1 -> if (len in 2..14 && !headword.contains(' ')) {
                ChallengeKind.WORD_BUILDER
            } else {
                ChallengeKind.MULTIPLE_CHOICE
            }
            else -> ChallengeKind.TYPING
        }
    }

    fun makeRoundItems(
        selected: List<WordCardCandidate>,
        allPool: List<WordCardCandidate>,
        fallbackDistractors: List<String>,
        progressById: Map<Long, WordProgressSnapshot>,
    ): List<GameRoundItem> = selected.map { card ->
        val existing = progressById[card.entryId]
        val stage = existing?.masteryStage ?: 0
        GameRoundItem(
            card = card,
            challengeKind = challengeKind(stage, card.headword),
            choices = makeChoices(card, allPool, fallbackDistractors),
            tiles = makeTiles(card.headword),
            clozeSentence = makeClozeSentence(card.exampleTg, card.headword),
            isNewWord = existing?.lastReviewedAtMs == null,
        )
    }

    fun makeChoices(
        card: WordCardCandidate,
        pool: List<WordCardCandidate>,
        fallbackDistractors: List<String>,
    ): List<String> {
        val seen = HashSet<String>().apply { add(card.headword.lowercase()) }
        val distractors = ArrayList<String>(3)

        val samePos = pool
            .filter { it.entryId != card.entryId && it.pos == card.pos }
            .sortedBy { abs(it.frequency - card.frequency) }
        for (item in samePos) {
            if (seen.add(item.headword.lowercase())) {
                distractors += item.headword
                if (distractors.size == 3) break
            }
        }

        if (distractors.size < 3) {
            for (item in pool.filter { it.entryId != card.entryId }.shuffled()) {
                if (seen.add(item.headword.lowercase())) {
                    distractors += item.headword
                    if (distractors.size == 3) break
                }
            }
        }

        if (distractors.size < 3) {
            for (word in fallbackDistractors.shuffled()) {
                if (seen.add(word.lowercase())) {
                    distractors += word
                    if (distractors.size == 3) break
                }
            }
        }

        return (listOf(card.headword) + distractors).shuffled()
    }

    fun makeTiles(headword: String): List<String> {
        val trimmed = headword.trim()
        if (trimmed.isEmpty()) return emptyList()
        val chunkSize = if (trimmed.length <= 6) 1 else 2
        val tiles = ArrayList<String>()
        var idx = 0
        while (idx < trimmed.length) {
            val end = min(trimmed.length, idx + chunkSize)
            tiles += trimmed.substring(idx, end)
            idx = end
        }
        if (tiles.size <= 1) return tiles
        val shuffled = tiles.shuffled().toMutableList()
        if (shuffled == tiles) shuffled.reverse()
        return shuffled
    }

    fun makeClozeSentence(exampleTg: String?, targetHeadword: String): String? {
        val example = exampleTg?.trim().orEmpty()
        val target = targetHeadword.trim()
        if (example.isEmpty() || target.isEmpty()) return null
        val regex = Regex("(?iu)(?<!\\p{L})${Regex.escape(target)}\\p{L}*")
        val match = regex.find(example) ?: return null
        return example.replaceRange(match.range, "____")
    }

    fun checkTypedAnswer(input: String, target: String): AnswerCheckResult {
        val trimmedInput = input.trim()
        val trimmedTarget = target.trim()
        if (trimmedInput.isEmpty()) return AnswerCheckResult.Wrong
        if (trimmedInput.equals(trimmedTarget, ignoreCase = true)) {
            return AnswerCheckResult.Exact
        }
        val normInput = TextNormalizer.searchKey(trimmedInput)
        val normTarget = TextNormalizer.searchKey(trimmedTarget)
        if (normInput.isNotEmpty() && normInput == normTarget) {
            return AnswerCheckResult.TajikLetterNearMiss(trimmedTarget)
        }
        return AnswerCheckResult.Wrong
    }

    fun evaluateAnswer(
        existing: WordProgressSnapshot?,
        entryId: Long,
        isCorrect: Boolean,
        usedHintOrNearMiss: Boolean,
        responseMs: Long,
        wasRetriedInRound: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): AnswerEvaluation {
        val base = existing ?: WordProgressSnapshot(entryId = entryId)
        val wasUnlearned = base.lastReviewedAtMs == null

        if (!isCorrect) {
            val nextStability = max(0.25, base.stabilityDays * 0.35)
            return AnswerEvaluation(
                updated = base.copy(
                    masteryStage = max(0, base.masteryStage - 1),
                    stabilityDays = nextStability,
                    lapses = base.lapses + 1,
                    lastReviewedAtMs = nowMs,
                    nextReviewAtMs = nowMs + (0.25 * DAY_MS).toLong(),
                ),
                xpAwarded = 0,
                newlyLearned = false,
            )
        }

        val xp: Int
        val nextStability: Double
        val nextStage: Int
        when {
            wasRetriedInRound || usedHintOrNearMiss -> {
                nextStability = if (base.stabilityDays < 0.5) 0.5 else max(1.0, base.stabilityDays * 1.35)
                nextStage = base.masteryStage
                xp = 5
            }
            responseMs < 5_000L -> {
                val seed = if (base.stabilityDays <= 0.0) 1.0 else base.stabilityDays
                nextStability = max(1.5, seed * 2.4)
                nextStage = min(4, base.masteryStage + 1)
                xp = 15
            }
            else -> {
                val seed = if (base.stabilityDays <= 0.0) 1.0 else base.stabilityDays
                nextStability = max(1.0, seed * 1.85)
                nextStage = min(4, base.masteryStage + 1)
                xp = 10
            }
        }

        return AnswerEvaluation(
            updated = base.copy(
                masteryStage = nextStage,
                stabilityDays = nextStability,
                repetitions = base.repetitions + 1,
                lastReviewedAtMs = nowMs,
                nextReviewAtMs = nowMs + (nextStability * DAY_MS).toLong(),
            ),
            xpAwarded = xp,
            newlyLearned = wasUnlearned,
        )
    }

    fun evaluateClassicFlashcard(
        existing: WordProgressSnapshot?,
        entryId: Long,
        rating: ReviewRating,
        intervalDays: Int,
        nextDueMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<AnswerEvaluation, Boolean> {
        val base = existing ?: WordProgressSnapshot(entryId = entryId)
        val wasUnlearned = base.lastReviewedAtMs == null
        val isCorrect = rating != ReviewRating.AGAIN
        val nextStage: Int
        val nextStability: Double
        val nextLapses: Int
        val xp: Int
        when (rating) {
            ReviewRating.AGAIN -> {
                nextStage = max(0, base.masteryStage - 1)
                nextStability = max(0.25, min(0.5, max(1, intervalDays) * 0.5))
                nextLapses = base.lapses + 1
                xp = 0
            }
            ReviewRating.GOOD -> {
                nextStage = min(4, max(1, base.masteryStage + 1))
                nextStability = max(1.0, intervalDays.toDouble())
                nextLapses = base.lapses
                xp = 8
            }
            ReviewRating.EASY -> {
                nextStage = min(4, base.masteryStage + 1)
                nextStability = max(2.0, intervalDays.toDouble())
                nextLapses = base.lapses
                xp = 12
            }
        }
        val eval = AnswerEvaluation(
            updated = base.copy(
                masteryStage = nextStage,
                stabilityDays = nextStability,
                repetitions = base.repetitions + 1,
                lapses = nextLapses,
                lastReviewedAtMs = nowMs,
                nextReviewAtMs = nextDueMs,
                isKnownSkipped = false,
            ),
            xpAwarded = xp,
            newlyLearned = wasUnlearned,
        )
        return eval to isCorrect
    }

    fun reinsertIndex(remainingCount: Int): Int = min(3, max(0, remainingCount))

    private fun previewGlossTokens(preview: String): Set<String> =
        preview.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun buildPlacementQuestions(pool: List<WordCardCandidate>): List<PlacementQuestion> {
        val usable = pool.filter { it.preview.isNotBlank() }
        val tiers = listOf(
            1 to (90..100),
            2 to (80..89),
            3 to (70..79),
            4 to (40..69),
        )

        val questions = ArrayList<PlacementQuestion>(12)
        val usedIds = HashSet<Long>()

        for ((tier, range) in tiers) {
            val tierWords = usable
                .filter { it.frequency in range && it.entryId !in usedIds }
                .shuffled()
            for (candidate in tierWords.take(3)) {
                usedIds += candidate.entryId
                val candidateTokens = previewGlossTokens(candidate.preview)
                val seenMeanings = HashSet<String>().apply { add(candidate.preview.lowercase()) }
                val distractors = ArrayList<String>(3)
                for (other in usable.shuffled()) {
                    if (other.entryId == candidate.entryId) continue
                    val otherTokens = previewGlossTokens(other.preview)
                    if (candidateTokens.any { it in otherTokens }) continue
                    val key = other.preview.lowercase()
                    if (seenMeanings.add(key)) {
                        distractors += other.preview
                        if (distractors.size == 3) break
                    }
                }
                val choices = (listOf(candidate.preview) + distractors).shuffled()
                questions += PlacementQuestion(
                    candidate = candidate,
                    tier = tier,
                    choices = choices,
                )
            }
        }
        return questions
    }

    fun evaluatePlacement(answers: List<PlacementAnswer>): PlacementResult {
        fun passedTier(tier: Int): Boolean {
            val group = answers.filter { it.question.tier == tier }
            if (group.isEmpty()) return false
            val right = group.count { it.isCorrect }
            val required = min(group.size, max(2, (group.size + 1) / 2))
            return right >= required
        }

        val level = when {
            passedTier(1) && passedTier(2) && passedTier(3) && passedTier(4) -> CefrLevel.B2
            passedTier(1) && passedTier(2) && passedTier(3) -> CefrLevel.B1
            passedTier(1) && passedTier(2) -> CefrLevel.A2
            passedTier(1) -> CefrLevel.A1
            else -> CefrLevel.A0
        }

        val correctCount = answers.count { it.isCorrect }
        val knownIds = answers.filter { it.isCorrect }.map { it.question.candidate.entryId }
        val focusIds = answers.filterNot { it.isCorrect }.map { it.question.candidate.entryId }
        val estimatedWords = level.baselineKnownWords + knownIds.size
        val coverage = textRecognitionPercent(estimatedWords.toDouble())

        return PlacementResult(
            cefrLevel = level,
            correctCount = correctCount,
            totalCount = answers.size,
            estimatedKnownWords = estimatedWords,
            textRecognitionPercent = coverage,
            knownEntryIds = knownIds,
            focusEntryIds = focusIds,
        )
    }

    fun formatStudyDuration(seconds: Int): String {
        val safe = max(0, seconds)
        if (safe < 60) return "${safe}s"
        val minutes = safe / 60
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remMin = minutes % 60
        return if (remMin > 0) "${hours}h ${remMin}m" else "${hours}h"
    }

    fun dateKey(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()

    fun computeSummary(
        progress: List<WordProgressSnapshot>,
        logs: List<DailyStudySnapshot>,
        dailyGoal: Int,
        lexiconStats: LexiconStats = LexiconStats.FALLBACK,
        placementLevel: CefrLevel? = null,
        placementBaselineWords: Int = 0,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): GamificationSummary {
        val totalXp = logs.sumOf { it.xpEarned }
        val levelStep = 100
        val level = max(1, (totalXp / levelStep) + 1)
        val xpInCurrentLevel = totalXp % levelStep

        val activeDates = logs.filter { it.wordsReviewed > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.dateKey) }.getOrNull() }
            .toSet()

        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)

        var currentStreak = 0
        if (today in activeDates || yesterday in activeDates) {
            var cursor = if (today in activeDates) today else yesterday
            while (cursor in activeDates) {
                currentStreak += 1
                cursor = cursor.minusDays(1)
            }
        }

        var longestStreak = currentStreak
        var running = 0
        var prevDate: LocalDate? = null
        for (date in activeDates.sorted()) {
            running = if (prevDate != null && prevDate.plusDays(1) == date) running + 1 else 1
            longestStreak = max(longestStreak, running)
            prevDate = date
        }

        val todayKey = today.toString()
        val todayLog = logs.firstOrNull { it.dateKey == todayKey }
        val todayReviewed = todayLog?.wordsReviewed ?: 0
        val todayLearned = todayLog?.wordsLearned ?: 0
        val totalStudySeconds = logs.sumOf { it.studySeconds }
        val totalReviews = logs.sumOf { it.wordsReviewed }
        val totalCorrect = logs.sumOf { it.correctReviews }
        val accuracyPct = if (totalReviews > 0) {
            ((totalCorrect.toDouble() / totalReviews.toDouble()) * 100.0).roundToInt()
        } else {
            100
        }

        val encountered = progress.count { it.lastReviewedAtMs != null || it.isKnownSkipped }
        val mastered = progress.count { it.masteryStage >= 4 || it.isKnownSkipped }
        val learning = max(0, encountered - mastered)
        val dueNow = progress.count {
            !it.isKnownSkipped && it.lastReviewedAtMs != null && it.nextReviewAtMs <= nowMs
        }

        val weightedActiveKnown = progress.sumOf { p ->
            when {
                p.isKnownSkipped || p.masteryStage >= 4 -> 1.0
                p.masteryStage >= 2 -> 0.75
                p.masteryStage == 1 -> 0.45
                p.lastReviewedAtMs != null -> 0.2
                else -> 0.0
            }
        }
        val effectiveKnown = placementBaselineWords.toDouble() + weightedActiveKnown
        val estimatedKnownWords = effectiveKnown.roundToInt()
        val coveragePct = textRecognitionPercent(effectiveKnown)

        val vocabCefr = when {
            estimatedKnownWords >= 3000 -> CefrLevel.B2
            estimatedKnownWords >= 1400 -> CefrLevel.B1
            estimatedKnownWords >= 450 -> CefrLevel.A2
            estimatedKnownWords >= 120 -> CefrLevel.A1
            else -> CefrLevel.A0
        }
        val cefrLevel = if (placementLevel != null && placementLevel.ordinal > vocabCefr.ordinal) {
            placementLevel
        } else {
            vocabCefr
        }

        var remainingCredit = estimatedKnownWords
        val t90Known = min(lexiconStats.tier90Count, remainingCredit)
        remainingCredit = max(0, remainingCredit - t90Known)
        val t80Known = min(lexiconStats.tier80Count, remainingCredit)
        remainingCredit = max(0, remainingCredit - t80Known)
        val t70Known = min(lexiconStats.tier70Count, remainingCredit)
        remainingCredit = max(0, remainingCredit - t70Known)
        val tOtherKnown = min(lexiconStats.tierOtherCount, remainingCredit)

        val tiers = listOf(
            FrequencyTierProgress("t90", "tier_90_label", t90Known, lexiconStats.tier90Count),
            FrequencyTierProgress("t80", "tier_80_label", t80Known, lexiconStats.tier80Count),
            FrequencyTierProgress("t70", "tier_70_label", t70Known, lexiconStats.tier70Count),
            FrequencyTierProgress("t40", "tier_40_label", tOtherKnown, lexiconStats.tierOtherCount),
        )

        val logByKey = logs.associateBy { it.dateKey }
        val safeGoal = max(1, dailyGoal)
        val recent7 = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            val key = day.toString()
            val log = logByKey[key]
            val reviewed = log?.wordsReviewed ?: 0
            val label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2)
            DailyActivityPoint(
                dateKey = key,
                shortLabel = label,
                wordsReviewed = reviewed,
                studySeconds = log?.studySeconds ?: 0,
                metGoal = reviewed >= safeGoal,
            )
        }

        val badges = listOf(
            AchievementBadge("first_word", "badge_first_word", encountered >= 1),
            AchievementBadge("ten_words", "badge_ten_words", encountered >= 10),
            AchievementBadge("streak_3", "badge_streak_3", longestStreak >= 3),
            AchievementBadge("streak_7", "badge_streak_7", longestStreak >= 7),
            AchievementBadge("master_25", "badge_master_25", mastered >= 25),
            AchievementBadge("reviews_100", "badge_reviews_100", totalReviews >= 100),
        )

        return GamificationSummary(
            totalXp = totalXp,
            level = level,
            xpInCurrentLevel = xpInCurrentLevel,
            xpForNextLevel = levelStep,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            activeDaysCount = activeDates.size,
            totalStudySeconds = totalStudySeconds,
            totalReviews = totalReviews,
            overallAccuracyPercent = accuracyPct,
            todayReviewed = todayReviewed,
            todayLearned = todayLearned,
            dailyGoal = safeGoal,
            encounteredCount = encountered,
            learningCount = learning,
            masteredCount = mastered,
            dueNowCount = dueNow,
            estimatedKnownWords = estimatedKnownWords,
            textRecognitionPercent = coveragePct,
            cefrLevel = cefrLevel,
            totalLexiconCount = lexiconStats.totalTajik,
            tierBreakdown = tiers,
            recent7Days = recent7,
            badges = badges,
        )
    }
}
