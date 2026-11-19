package tj.tajikdictionary.ads

import java.util.concurrent.atomic.AtomicLong

/**
 * App-wide circuit breaker for the banner waterfall (Yandex -> Google).
 *
 * Every screen change remounts the pinned banner. Without a back-off, each remount re-opens a doomed request on a network whose answer the
 * app already knows, and each one pays its own full-length timeout before
 * moving on. Two independent, escalating cooldowns stop that:
 *
 *  - Yandex leg: the first Yandex failure retries shortly
 *    ([YANDEX_BACKOFF_RETRY_MILLIS]), a second consecutive failure backs off
 *    for [YANDEX_BACKOFF_FIRST_MILLIS], and anything beyond backs off for
 *    [YANDEX_BACKOFF_MAX_MILLIS] and stays there, so the fallback (Google)
 *    still runs in the meantime. A Yandex success resets the escalation.
 *  - Whole waterfall: after [ADS_FAILURE_THRESHOLD] consecutive placements
 *    where both providers failed, ads are skipped entirely for
 *    [ADS_COOLDOWN_MILLIS]. A success from either provider resets it.
 */
object AdsAvailability {

    private val yandexFailures = AtomicLong(0)
    private val yandexCooldownUntil = AtomicLong(0)

    private val adsFailures = AtomicLong(0)
    private val adsCooldownUntil = AtomicLong(0)

    /** Clock is injectable so tests do not have to sleep through a cooldown. */
    @Volatile
    internal var uptimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    /** False while the Yandex breaker is open -- callers should start at Google. */
    fun shouldTryYandex(): Boolean = uptimeMillis() >= yandexCooldownUntil.get()

    /** False while the ads breaker is open -- callers should skip the slot entirely. */
    fun shouldTryAds(): Boolean = uptimeMillis() >= adsCooldownUntil.get()

    fun recordYandexSuccess() {
        yandexFailures.set(0)
        yandexCooldownUntil.set(0)
        recordAdsSuccess()
    }

    fun recordAdsSuccess() {
        adsFailures.set(0)
        adsCooldownUntil.set(0)
    }

    fun recordYandexFailure() {
        val failures = yandexFailures.incrementAndGet()
        yandexCooldownUntil.set(uptimeMillis() + yandexBackoffFor(failures))
    }

    fun recordAdsFailure() {
        if (adsFailures.incrementAndGet() >= ADS_FAILURE_THRESHOLD) {
            adsFailures.set(0)
            adsCooldownUntil.set(uptimeMillis() + ADS_COOLDOWN_MILLIS)
        }
    }

    internal fun resetForTest() {
        yandexFailures.set(0)
        yandexCooldownUntil.set(0)
        adsFailures.set(0)
        adsCooldownUntil.set(0)
    }

    /**
     * Escalating back-off for the Yandex leg: the first no-fill retries
     * shortly, a second consecutive one backs off for a few minutes, and
     * anything beyond stays at the longest.
     */
    private fun yandexBackoffFor(consecutiveFailures: Long): Long =
        when (consecutiveFailures) {
            1L -> YANDEX_BACKOFF_RETRY_MILLIS
            2L -> YANDEX_BACKOFF_FIRST_MILLIS
            else -> YANDEX_BACKOFF_MAX_MILLIS
        }

    internal const val YANDEX_BACKOFF_RETRY_MILLIS = 10 * 1000L
    internal const val YANDEX_BACKOFF_FIRST_MILLIS = 5 * 60 * 1000L
    internal const val YANDEX_BACKOFF_MAX_MILLIS = 15 * 60 * 1000L
    internal const val ADS_FAILURE_THRESHOLD = 3L
    internal const val ADS_COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L
}
