package tj.tajikdictionary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardSchedulerTest {
    @Test
    fun advancesAndResetsSm2Intervals() {
        val now = 1_000_000L
        val (first, _) = FlashcardScheduler.next(FlashcardState(), ReviewRating.GOOD, now)
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)

        val (second, _) = FlashcardScheduler.next(first, ReviewRating.GOOD, now)
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)

        val (third, dueThird) = FlashcardScheduler.next(second, ReviewRating.EASY, now)
        assertEquals(3, third.repetitions)
        assertTrue(third.intervalDays > 6)
        assertTrue(dueThird > now)

        val (reset, _) = FlashcardScheduler.next(third, ReviewRating.AGAIN, now)
        assertEquals(0, reset.repetitions)
        assertEquals(0, reset.intervalDays)
    }
}
