package tj.tajikdictionary.domain.model

import tj.tajikdictionary.core.text.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class ChallengeKind {
    MULTIPLE_CHOICE,
    WORD_BUILDER,
    TYPING,
}

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
    val todayReviewed: Int,
    val todayLearned: Int,
    val dailyGoal: Int,
    val encounteredCount: Int,
    val learningCount: Int,
    val masteredCount: Int,
    val dueNowCount: Int,
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

    fun buildRoundQueue(
        candidates: List<WordCardCandidate>,
        progressById: Map<Long, WordProgressSnapshot>,
        nowMs: Long = System.currentTimeMillis(),
        roundSize: Int = DEFAULT_ROUND_SIZE,
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

        val newOrdered = active.filter { candidate ->
            val p = progressById[candidate.entryId]
            p == null || p.lastReviewedAtMs == null
        }.sortedWith(
            compareByDescending<WordCardCandidate> { it.frequency }
                .thenBy { it.entryId },
        )

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
        val index = example.indexOf(target, ignoreCase = true)
        if (index < 0) return null
        return example.replaceRange(index, index + target.length, "____")
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

    fun reinsertIndex(remainingCount: Int): Int = min(3, max(0, remainingCount))

    fun dateKey(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()

    fun computeSummary(
        progress: List<WordProgressSnapshot>,
        logs: List<DailyStudySnapshot>,
        dailyGoal: Int,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
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

        val encountered = progress.count { it.lastReviewedAtMs != null || it.isKnownSkipped }
        val mastered = progress.count { it.masteryStage >= 4 || it.isKnownSkipped }
        val learning = max(0, encountered - mastered)
        val dueNow = progress.count {
            !it.isKnownSkipped && it.lastReviewedAtMs != null && it.nextReviewAtMs <= nowMs
        }
        val totalReviews = logs.sumOf { it.wordsReviewed }

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
            todayReviewed = todayReviewed,
            todayLearned = todayLearned,
            dailyGoal = max(1, dailyGoal),
            encounteredCount = encountered,
            learningCount = learning,
            masteredCount = mastered,
            dueNowCount = dueNow,
            badges = badges,
        )
    }
}
