package tj.tajikdictionary.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerWaterfallReducerTest {
    @Test
    fun `the waterfall starts at Yandex`() {
        val initial = BannerWaterfallState()

        assertEquals(BannerProvider.YANDEX, initial.provider)
        assertEquals(0, initial.attemptId)
    }

    @Test
    fun `an open yandex breaker starts the waterfall at Google`() {
        val skipped = initialBannerWaterfallState(tryYandex = false)

        assertEquals(BannerProvider.GOOGLE, skipped.provider)
        assertEquals(1, skipped.attemptId)

        val exhausted = reduceBannerWaterfall(
            skipped,
            BannerWaterfallEvent.GoogleFailed(attemptId = 1),
        )
        assertEquals(BannerProvider.EXHAUSTED, exhausted.provider)
    }

    @Test
    fun `a closed yandex breaker starts the waterfall at Yandex`() {
        val normal = initialBannerWaterfallState(tryYandex = true)

        assertEquals(BannerProvider.YANDEX, normal.provider)
        assertEquals(0, normal.attemptId)
    }

    @Test
    fun `Yandex failure advances to one Google attempt`() {
        val next = reduceBannerWaterfall(
            BannerWaterfallState(),
            BannerWaterfallEvent.YandexFailed(attemptId = 0),
        )

        assertEquals(BannerProvider.GOOGLE, next.provider)
        assertEquals(1, next.attemptId)
    }

    @Test
    fun `Yandex timeout follows the same fallback path`() {
        val next = reduceBannerWaterfall(
            BannerWaterfallState(),
            BannerWaterfallEvent.YandexTimedOut(attemptId = 0),
        )

        assertEquals(BannerProvider.GOOGLE, next.provider)
        assertEquals(1, next.attemptId)
    }

    @Test
    fun `late Yandex callbacks cannot mutate the Google attempt`() {
        val google = BannerWaterfallState(
            provider = BannerProvider.GOOGLE,
            attemptId = 1,
        )

        val afterLateLoad = reduceBannerWaterfall(
            google,
            BannerWaterfallEvent.YandexLoaded(attemptId = 0),
        )
        val afterLateFailure = reduceBannerWaterfall(
            afterLateLoad,
            BannerWaterfallEvent.YandexFailed(attemptId = 0),
        )

        assertEquals(google, afterLateFailure)
    }

    @Test
    fun `a loaded Yandex creative survives later failure callbacks`() {
        val loaded = reduceBannerWaterfall(
            BannerWaterfallState(),
            BannerWaterfallEvent.YandexLoaded(attemptId = 0),
        )
        val afterFailure = reduceBannerWaterfall(
            loaded,
            BannerWaterfallEvent.YandexFailed(attemptId = 0),
        )

        assertEquals(BannerProvider.YANDEX, afterFailure.provider)
        assertTrue(afterFailure.loaded)
    }

    @Test
    fun `Google failure exhausts and collapses the slot`() {
        val google = BannerWaterfallState(
            provider = BannerProvider.GOOGLE,
            attemptId = 1,
        )
        val exhausted = reduceBannerWaterfall(
            google,
            BannerWaterfallEvent.GoogleFailed(attemptId = 1),
        )

        assertEquals(BannerProvider.EXHAUSTED, exhausted.provider)
        assertEquals(2, exhausted.attemptId)
    }

    @Test
    fun `Google timeout follows the same exhaust path`() {
        val google = BannerWaterfallState(
            provider = BannerProvider.GOOGLE,
            attemptId = 1,
        )
        val exhausted = reduceBannerWaterfall(
            google,
            BannerWaterfallEvent.GoogleTimedOut(attemptId = 1),
        )

        assertEquals(BannerProvider.EXHAUSTED, exhausted.provider)
        assertEquals(2, exhausted.attemptId)
    }

    @Test
    fun `a loaded Google creative survives later failure callbacks`() {
        val google = BannerWaterfallState(
            provider = BannerProvider.GOOGLE,
            attemptId = 1,
        )
        val loaded = reduceBannerWaterfall(
            google,
            BannerWaterfallEvent.GoogleLoaded(attemptId = 1),
        )
        val afterFailure = reduceBannerWaterfall(
            loaded,
            BannerWaterfallEvent.GoogleFailed(attemptId = 1),
        )

        assertEquals(BannerProvider.GOOGLE, afterFailure.provider)
        assertTrue(afterFailure.loaded)
    }
}
