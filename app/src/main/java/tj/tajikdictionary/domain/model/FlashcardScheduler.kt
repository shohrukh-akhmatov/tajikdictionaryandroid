package tj.tajikdictionary.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewRating(val quality: Int) {
    AGAIN(1),
    GOOD(4),
    EASY(5),
}

data class FlashcardState(
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
)

/**
 * Pure SM-2 spaced-repetition scheduler shared in design between Android & iOS.
 */
object FlashcardScheduler {
    private const val DAY_MS = 86_400_000L

    fun next(current: FlashcardState, rating: ReviewRating, nowMs: Long): Pair<FlashcardState, Long> {
        val q = rating.quality
        val nextEf = max(
            1.3,
            current.easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)),
        )
        return if (q < 3) {
            val state = FlashcardState(repetitions = 0, intervalDays = 0, easeFactor = nextEf)
            // Re-queue in 10 minutes
            state to (nowMs + 10 * 60_000L)
        } else {
            val reps = current.repetitions + 1
            val interval = when (reps) {
                1 -> 1
                2 -> 6
                else -> max(1, (current.intervalDays * nextEf).roundToInt())
            }
            val boosted = if (rating == ReviewRating.EASY && reps == 1) 3 else interval
            val state = FlashcardState(repetitions = reps, intervalDays = boosted, easeFactor = nextEf)
            state to (nowMs + boosted * DAY_MS)
        }
    }
}
