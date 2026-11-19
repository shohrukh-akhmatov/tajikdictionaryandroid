package tj.tajikdictionary.ads

import tj.tajikdictionary.BuildConfig

/** Interstitial pacing. Ad-unit IDs come from BuildConfig (see app/build.gradle.kts, Config/AdUnits.properties). */
object AdConfig {
    /** Show an interstitial after every N finished game rounds... */
    const val roundsPerInterstitial = 3

    /** ...but never more often than this. */
    const val minSecondsBetweenInterstitials = 180L

    /** Debug only: make UMP behave as if the device were in the EEA, so the consent form shows. */
    const val forceEeaGeography = false
}

/** Every ad slot in the app, each with its own Yandex (tried first) and Google (fallback) unit. */
enum class AdPlacement(private val yandexId: String, private val googleId: String) {
    /** Pinned bottom banner on every screen except Settings and the word detail screen. */
    BANNER(BuildConfig.YANDEX_BANNER_ID, BuildConfig.ADMOB_BANNER_ID),

    /** Pinned bottom banner on the word detail screen. */
    ENTRY_BANNER(BuildConfig.YANDEX_ENTRY_BANNER_ID, BuildConfig.ADMOB_ENTRY_BANNER_ID),

    /** Full-screen ad after finished word-game rounds. */
    INTERSTITIAL(BuildConfig.YANDEX_INTERSTITIAL_ID, BuildConfig.ADMOB_INTERSTITIAL_ID),
    ;

    data class Ids(val yandex: String, val google: String)

    /** Null when ads are off in this build (release without production IDs). */
    fun ids(): Ids? = if (BuildConfig.ADS_ENABLED) Ids(yandexId, googleId) else null
}
