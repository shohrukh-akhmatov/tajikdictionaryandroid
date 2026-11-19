package tj.tajikdictionary.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdsAvailabilityTest {

    private var now = 1_000L

    @Before
    fun setUp() {
        now = 1_000L
        AdsAvailability.resetForTest()
        AdsAvailability.uptimeMillis = { now }
    }

    @Test
    fun `a single yandex failure retries shortly`() {
        AdsAvailability.recordYandexFailure()
        assertFalse(AdsAvailability.shouldTryYandex())

        now += AdsAvailability.YANDEX_BACKOFF_RETRY_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryYandex())

        now += 1
        assertTrue(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `two consecutive yandex failures escalate to five minutes`() {
        AdsAvailability.recordYandexFailure()
        now += AdsAvailability.YANDEX_BACKOFF_RETRY_MILLIS
        assertTrue(AdsAvailability.shouldTryYandex())

        AdsAvailability.recordYandexFailure()
        now += AdsAvailability.YANDEX_BACKOFF_FIRST_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryYandex())

        now += 1
        assertTrue(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `three consecutive yandex failures escalate to fifteen minutes`() {
        AdsAvailability.recordYandexFailure()
        now += AdsAvailability.YANDEX_BACKOFF_RETRY_MILLIS
        AdsAvailability.recordYandexFailure()
        now += AdsAvailability.YANDEX_BACKOFF_FIRST_MILLIS
        AdsAvailability.recordYandexFailure()

        now += AdsAvailability.YANDEX_BACKOFF_MAX_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryYandex())

        now += 1
        assertTrue(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `further yandex failures stay at fifteen minutes`() {
        repeat(4) { AdsAvailability.recordYandexFailure() }
        assertFalse(AdsAvailability.shouldTryYandex())

        now += AdsAvailability.YANDEX_BACKOFF_MAX_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryYandex())

        now += 1
        assertTrue(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `a yandex success resets the escalation to a short retry`() {
        repeat(3) { AdsAvailability.recordYandexFailure() }
        AdsAvailability.recordYandexSuccess()
        AdsAvailability.recordYandexFailure()

        now += AdsAvailability.YANDEX_BACKOFF_RETRY_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryYandex())

        now += 1
        assertTrue(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `ads are skipped only after three consecutive full failures`() {
        AdsAvailability.recordAdsFailure()
        assertTrue(AdsAvailability.shouldTryAds())

        AdsAvailability.recordAdsFailure()
        assertTrue(AdsAvailability.shouldTryAds())

        AdsAvailability.recordAdsFailure()
        assertFalse(AdsAvailability.shouldTryAds())

        now += AdsAvailability.ADS_COOLDOWN_MILLIS - 1
        assertFalse(AdsAvailability.shouldTryAds())

        now += 1
        assertTrue(AdsAvailability.shouldTryAds())
    }

    @Test
    fun `a google success closes the ads breaker but leaves yandex open`() {
        AdsAvailability.recordYandexFailure()
        repeat(AdsAvailability.ADS_FAILURE_THRESHOLD.toInt()) {
            AdsAvailability.recordAdsFailure()
        }

        AdsAvailability.recordAdsSuccess()

        assertTrue(AdsAvailability.shouldTryAds())
        assertFalse(AdsAvailability.shouldTryYandex())
    }

    @Test
    fun `a yandex success closes both breakers`() {
        AdsAvailability.recordYandexFailure()
        repeat(AdsAvailability.ADS_FAILURE_THRESHOLD.toInt()) {
            AdsAvailability.recordAdsFailure()
        }

        AdsAvailability.recordYandexSuccess()

        assertTrue(AdsAvailability.shouldTryAds())
        assertTrue(AdsAvailability.shouldTryYandex())
    }
}
