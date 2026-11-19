package tj.tajikdictionary.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import tj.tajikdictionary.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest as GoogleAdRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd as GoogleInterstitial
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitial

/**
 * UMP consent, then both ad SDKs, then a Yandex -> Google interstitial (same mechanics as Somoni).
 * Nothing ad-related happens before [start], which the UI calls once onboarding (and the optional
 * placement test) is over. Banners ([PinnedBanner]) render nothing until [canShowAds] is true.
 *
 * Kept free of side effects at class-init time so JVM unit tests can touch [isInterstitialDue].
 */
object AdsManager {
    private const val TAG = "Ads"
    private const val SDK_INIT_TIMEOUT_MILLIS = 15_000L
    private const val INTERSTITIAL_LEG_TIMEOUT_MILLIS = 30_000L
    /** Loaded interstitials go stale (Google: 1 hour); drop them a little before that. */
    private const val INTERSTITIAL_MAX_AGE_MILLIS = 55 * 60_000L
    /** Only fires if the privacy form's callback is genuinely lost (the user is reading a form). */
    private const val PRIVACY_FORM_TIMEOUT_MILLIS = 5 * 60_000L

    private val scope by lazy {
        // Everything here runs from SDK callbacks and timers with no caller to catch a throw.
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, e -> Log.w(TAG, "Ads coroutine failed", e) },
        )
    }

    private lateinit var appContext: Context
    private val consentInfo: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(appContext) }
    private val tcfPrefs: SharedPreferences by lazy {
        // UMP writes the TCF strings to the default SharedPreferences ("<package>_preferences").
        appContext.getSharedPreferences("${appContext.packageName}_preferences", Context.MODE_PRIVATE)
    }

    private var consentUpdateStarted = false
    private var sdkInitStarted = false
    private var sdkInitComplete = false
    private var privacyFormShowing = false
    private var privacyFormWatchdog: Job? = null
    private var googleRequestConfig: RequestConfiguration? = null

    private val _canShowAds = MutableStateFlow(false)
    /** True once consent allows ad requests and both SDKs are initialised. */
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    /** Drives the "Ad privacy settings" row in Settings. */
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    // ---- Consent -------------------------------------------------------------------------------

    /** Requests fresh consent information once per process. Safe to call on every recomposition. */
    @MainThread
    fun start(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || consentUpdateStarted) return
        consentUpdateStarted = true
        if (!::appContext.isInitialized) {
            appContext = activity.applicationContext
            // Conservative defaults before either SDK can start.
            YandexAds.setUserConsent(false)
            YandexAds.setLocationTracking(false)
        }

        // Held weakly: UMP may call back after a rotation, and this object outlives every Activity.
        val activityRef = WeakReference(activity)
        val params = ConsentRequestParameters.Builder().apply {
            val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (debuggable && AdConfig.forceEeaGeography) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build(),
                )
            }
        }.build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                updatePrivacyRequirement()
                val current = activityRef.get()?.takeUnless { it.isDestroyed }
                if (current == null) {
                    // Rotated away mid-fetch: let the next Activity ask again instead of ads staying off.
                    consentUpdateStarted = false
                    finishConsentFlow()
                } else {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(current) { error ->
                        if (error != null) Log.w(TAG, "Consent form could not complete: ${error.message}")
                        finishConsentFlow()
                    }
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                consentUpdateStarted = false
                finishConsentFlow()
            },
        )

        // A valid choice from an earlier launch lets ads start without waiting for the network.
        if (consentInfo.canRequestAds()) initializeSdksOnce()
    }

    /** Opens the UMP privacy options form; every banner is torn down while it is up. */
    @MainThread
    fun presentPrivacyOptions(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || !::appContext.isInitialized) return
        privacyFormShowing = true
        _canShowAds.value = false

        privacyFormWatchdog?.cancel()
        privacyFormWatchdog = scope.launch {
            delay(PRIVACY_FORM_TIMEOUT_MILLIS)
            if (privacyFormShowing) {
                privacyFormShowing = false
                finishConsentFlow()
            }
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            privacyFormWatchdog?.cancel()
            privacyFormWatchdog = null
            privacyFormShowing = false
            if (error != null) Log.w(TAG, "Privacy options form could not complete: ${error.message}")
            finishConsentFlow()
        }
    }

    @MainThread
    private fun finishConsentFlow() {
        updatePrivacyRequirement()
        if (consentInfo.canRequestAds()) {
            initializeSdksOnce()
        } else {
            // False only when ads must not be requested at all. Declining the form still reports
            // true; applyPrivacySignals downgrades those requests to non-personalized instead.
            applyPrivacySignals()
            _canShowAds.value = false
        }
    }

    @MainThread
    private fun updatePrivacyRequirement() {
        _isPrivacyOptionsRequired.value = consentInfo.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    @MainThread
    private fun applyPrivacySignals() {
        val consentNotRequired = consentInfo.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
        val signals = TcfConsentSignals(
            gdprApplies = tcfPrefs.intOrNull("IABTCF_gdprApplies"),
            purposeConsents = tcfPrefs.getString("IABTCF_PurposeConsents", null),
            additionalConsent = tcfPrefs.getString("IABTCF_AddtlConsent", null),
        )
        val config = requestConfigurationFor(signals.allowsPersonalization(consentNotRequired))
        googleRequestConfig = config
        // Before initialisation the config goes in through InitializationConfig instead.
        if (sdkInitComplete) MobileAds.setRequestConfiguration(config)
        YandexAds.setUserConsent(signals.allowsYandex(consentNotRequired))
        YandexAds.setLocationTracking(false)
    }

    // ---- SDK initialisation ---------------------------------------------------------------------

    @MainThread
    private fun initializeSdksOnce() {
        applyPrivacySignals()
        if (sdkInitStarted) {
            publishAdsAvailable()
            return
        }
        sdkInitStarted = true
        scope.launch {
            val outcome = runCatching {
                withTimeoutOrNull(SDK_INIT_TIMEOUT_MILLIS) {
                    coroutineScope {
                        val google = async(Dispatchers.IO) { initializeGoogle() }
                        val yandex = async { initializeYandex() }
                        google.await()
                        yandex.await()
                    }
                    true
                } == true
            }
            outcome.exceptionOrNull()?.let { e ->
                if (e is CancellationException) throw e
                Log.w(TAG, "Ad SDK initialisation failed", e)
            }
            // Both SDKs keep initialising on their own; never strand every slot on a lost callback.
            if (outcome.getOrNull() == false) Log.w(TAG, "Ad SDK initialisation timed out")
            sdkInitComplete = true
            publishAdsAvailable()
        }
    }

    @MainThread
    private fun publishAdsAvailable() {
        if (!sdkInitComplete) return
        _canShowAds.value = !privacyFormShowing && consentInfo.canRequestAds()
        if (_canShowAds.value) loadInterstitial()
    }

    private suspend fun initializeGoogle() = suspendCancellableCoroutine { cont ->
        val builder = InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID)
        googleRequestConfig?.let { builder.setRequestConfiguration(it) }
        MobileAds.initialize(appContext, builder.build()) { if (cont.isActive) cont.resume(Unit) }
    }

    private suspend fun initializeYandex() = suspendCancellableCoroutine { cont ->
        YandexAds.initialize(appContext) { if (cont.isActive) cont.resume(Unit) }
    }

    /** DISABLED marks every Google request non-personalized; DEFAULT lets AdMob apply consent. */
    private fun requestConfigurationFor(personalizedAllowed: Boolean): RequestConfiguration {
        val builder = RequestConfiguration.Builder().setPublisherPrivacyPersonalizationState(
            if (personalizedAllowed) {
                RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
            } else {
                RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
            },
        )
        if (BuildConfig.DEBUG) {
            val testDevices = BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (testDevices.isNotEmpty()) builder.setTestDeviceIds(testDevices)
        }
        return builder.build()
    }

    // ---- Interstitial (Yandex first, Google fallback) -------------------------------------------

    private sealed interface ReadyInterstitial {
        val loadedAtMs: Long
        class Yandex(val ad: YandexInterstitial, override val loadedAtMs: Long) : ReadyInterstitial
        class Google(val ad: GoogleInterstitial, override val loadedAtMs: Long) : ReadyInterstitial
    }

    private var interstitial: ReadyInterstitial? = null
    private var yandexLoader: InterstitialAdLoader? = null
    /** Bumped per load leg; callbacks and timeouts from an older leg are ignored. */
    private var interstitialAttempt = 0
    private var isLoadingInterstitial = false
    private var roundsSinceLastInterstitial = 0
    private var lastInterstitialAtMs: Long? = null

    @MainThread
    private fun loadInterstitial() {
        val ids = AdPlacement.INTERSTITIAL.ids() ?: return
        if (!_canShowAds.value || isLoadingInterstitial) return
        interstitial?.let { if (SystemClock.elapsedRealtime() - it.loadedAtMs < INTERSTITIAL_MAX_AGE_MILLIS) return }
        interstitial = null
        if (!AdsAvailability.shouldTryAds()) return
        isLoadingInterstitial = true
        if (AdsAvailability.shouldTryYandex()) loadYandexInterstitial(ids) else loadGoogleInterstitial(ids)
    }

    @MainThread
    private fun loadYandexInterstitial(ids: AdPlacement.Ids) {
        val attempt = ++interstitialAttempt
        val onFailed = {
            if (attempt == interstitialAttempt) {
                AdsAvailability.recordYandexFailure()
                loadGoogleInterstitial(ids)
            }
        }
        scope.launch {
            delay(INTERSTITIAL_LEG_TIMEOUT_MILLIS)
            onFailed()
        }
        val loader = yandexLoader ?: InterstitialAdLoader(appContext).also { yandexLoader = it }
        loader.loadAd(
            AdRequest.Builder(ids.yandex).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: YandexInterstitial) {
                    if (attempt != interstitialAttempt) return
                    interstitialAttempt++
                    AdsAvailability.recordYandexSuccess()
                    interstitial = ReadyInterstitial.Yandex(ad, SystemClock.elapsedRealtime())
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.w(TAG, "Yandex interstitial failed: ${error.description}")
                    onFailed()
                }
            },
        )
    }

    @MainThread
    private fun loadGoogleInterstitial(ids: AdPlacement.Ids) {
        val attempt = ++interstitialAttempt
        val onFailed = {
            if (attempt == interstitialAttempt) {
                interstitialAttempt++
                AdsAvailability.recordAdsFailure()
                isLoadingInterstitial = false
            }
        }
        scope.launch {
            delay(INTERSTITIAL_LEG_TIMEOUT_MILLIS)
            onFailed()
        }
        // Next-Gen SDK callbacks arrive on a background thread.
        GoogleInterstitial.load(
            GoogleAdRequest.Builder(ids.google).build(),
            object : AdLoadCallback<GoogleInterstitial> {
                override fun onAdLoaded(ad: GoogleInterstitial) {
                    scope.launch {
                        if (attempt != interstitialAttempt) return@launch
                        interstitialAttempt++
                        AdsAvailability.recordAdsSuccess()
                        interstitial = ReadyInterstitial.Google(ad, SystemClock.elapsedRealtime())
                        isLoadingInterstitial = false
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Google interstitial failed: ${adError.message}")
                    scope.launch { onFailed() }
                }
            },
        )
    }

    /** Call once per finished game round. */
    fun recordFinishedRound() {
        roundsSinceLastInterstitial += 1
        if (::appContext.isInitialized) loadInterstitial()
    }

    /**
     * Shows an interstitial when one is due and loaded, then calls [onDone] once it is dismissed
     * (or fails to show). Calls [onDone] right away otherwise -- the user is never kept waiting.
     */
    @MainThread
    fun showInterstitialIfDue(activity: Activity?, onDone: () -> Unit) {
        val ready = interstitial
        val now = SystemClock.elapsedRealtime()
        val due = isInterstitialDue(roundsSinceLastInterstitial, lastInterstitialAtMs, now)
        val fresh = ready != null && now - ready.loadedAtMs < INTERSTITIAL_MAX_AGE_MILLIS
        if (activity == null || !_canShowAds.value || !fresh || !due) {
            onDone()
            return
        }
        interstitial = null
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                loadInterstitial()
                onDone()
            }
        }
        val markShown = {
            roundsSinceLastInterstitial = 0
            lastInterstitialAtMs = SystemClock.elapsedRealtime()
        }
        when (ready) {
            is ReadyInterstitial.Yandex -> {
                ready.ad.setAdEventListener(object : InterstitialAdEventListener {
                    override fun onAdShown() = markShown()
                    override fun onAdFailedToShow(error: AdError) = finish()
                    override fun onAdDismissed() = finish()
                    override fun onAdClicked() = Unit
                    override fun onAdImpression(data: ImpressionData?) = Unit
                })
                ready.ad.show(activity)
            }
            is ReadyInterstitial.Google -> {
                ready.ad.adEventCallback = object : InterstitialAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        scope.launch { markShown() }
                    }
                    override fun onAdDismissedFullScreenContent() {
                        scope.launch { finish() }
                    }
                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        scope.launch { finish() }
                    }
                }
                ready.ad.show(activity)
            }
            null -> onDone()
        }
    }

    /** Every [AdConfig.roundsPerInterstitial] rounds, at most once per [AdConfig.minSecondsBetweenInterstitials]. */
    internal fun isInterstitialDue(roundsSinceLast: Int, lastShownAtMs: Long?, nowMs: Long): Boolean {
        if (roundsSinceLast < AdConfig.roundsPerInterstitial) return false
        val last = lastShownAtMs ?: return true
        return nowMs - last >= AdConfig.minSecondsBetweenInterstitials * 1000
    }
}

private fun SharedPreferences.intOrNull(key: String): Int? = when (val value = all[key]) {
    is Int -> value
    is String -> value.toIntOrNull()
    else -> null
}
