package tj.tajikdictionary.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A banner pinned to the bottom of the screen, with Somoni's app-side Yandex -> Google waterfall.
 *
 * - One provider view at a time: Yandex is destroyed before Google enters composition.
 * - The slot takes no height until a creative has loaded, so an offline user (this is an
 *   offline-first dictionary) never sees an empty strip, and the content does not jump twice.
 * - A terminal failure (both networks) collapses the slot for the rest of this screen visit.
 */
@Composable
fun PinnedBanner(placement: AdPlacement, modifier: Modifier = Modifier) {
    val canShowAds by AdsManager.canShowAds.collectAsState()
    if (!canShowAds || !AdsAvailability.shouldTryAds()) return
    val ids = remember(placement) { placement.ids() } ?: return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.roundToInt()
        if (widthDp < MIN_BANNER_WIDTH_DP) return@BoxWithConstraints

        var waterfall by remember(placement, widthDp) {
            // Skip Yandex while its breaker is open: saves every slot a full timeout before Google.
            mutableStateOf(initialBannerWaterfallState(AdsAvailability.shouldTryYandex()))
        }
        if (waterfall.provider == BannerProvider.EXHAUSTED) return@BoxWithConstraints

        val scope = rememberCoroutineScope()
        val eventReducer = rememberUpdatedState<(BannerWaterfallEvent) -> Unit> { event ->
            val previous = waterfall
            val next = reduceBannerWaterfall(previous, event)
            if (next != previous) {
                when (event) {
                    is BannerWaterfallEvent.YandexLoaded -> AdsAvailability.recordYandexSuccess()
                    is BannerWaterfallEvent.GoogleLoaded -> AdsAvailability.recordAdsSuccess()
                    is BannerWaterfallEvent.YandexFailed,
                    is BannerWaterfallEvent.YandexTimedOut,
                    -> AdsAvailability.recordYandexFailure()
                    else -> Unit
                }
                if (next.provider == BannerProvider.EXHAUSTED) AdsAvailability.recordAdsFailure()
            }
            waterfall = next
        }
        // Next-Gen Google SDK callbacks arrive on a background thread.
        val dispatchEvent = remember(scope, eventReducer) {
            { event: BannerWaterfallEvent ->
                scope.launch(Dispatchers.Main.immediate) { eventReducer.value(event) }
                Unit
            }
        }

        val attemptId = waterfall.attemptId
        LaunchedEffect(placement, widthDp, waterfall.provider, attemptId, waterfall.loaded) {
            if (waterfall.loaded) return@LaunchedEffect
            delay(BANNER_LOAD_TIMEOUT_MILLIS)
            when (waterfall.provider) {
                BannerProvider.YANDEX -> {
                    Log.w(TAG, "Yandex banner timed out for $placement")
                    dispatchEvent(BannerWaterfallEvent.YandexTimedOut(attemptId))
                }
                BannerProvider.GOOGLE -> {
                    Log.w(TAG, "Google banner timed out for $placement")
                    dispatchEvent(BannerWaterfallEvent.GoogleTimedOut(attemptId))
                }
                BannerProvider.EXHAUSTED -> Unit
            }
        }

        val loaded = waterfall.loaded
        // Measured and attached at full size (so the SDKs load normally) but reports zero height
        // and is clipped until a creative is there.
        val bannerModifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val height = if (loaded) placeable.height else 0
                layout(placeable.width, height) { placeable.place(0, 0) }
            }

        key(placement, waterfall.provider, attemptId, widthDp) {
            when (waterfall.provider) {
                BannerProvider.GOOGLE -> GoogleAnchoredBanner(
                    adUnitId = ids.google,
                    widthDp = widthDp,
                    modifier = bannerModifier,
                    onLoaded = { dispatchEvent(BannerWaterfallEvent.GoogleLoaded(attemptId)) },
                    onFailed = { error ->
                        Log.w(TAG, "Google banner failed for $placement: ${error.message}")
                        dispatchEvent(BannerWaterfallEvent.GoogleFailed(attemptId))
                    },
                )
                BannerProvider.YANDEX -> YandexStickyBanner(
                    adUnitId = ids.yandex,
                    widthDp = widthDp,
                    modifier = bannerModifier,
                    onLoaded = { dispatchEvent(BannerWaterfallEvent.YandexLoaded(attemptId)) },
                    onFailed = { error ->
                        Log.w(TAG, "Yandex banner failed for $placement: ${error.description}")
                        dispatchEvent(BannerWaterfallEvent.YandexFailed(attemptId))
                    },
                )
                BannerProvider.EXHAUSTED -> Unit
            }
        }
    }
}

@Composable
private fun GoogleAnchoredBanner(
    adUnitId: String,
    widthDp: Int,
    modifier: Modifier,
    onLoaded: () -> Unit,
    onFailed: (LoadAdError) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    AndroidView(
        factory = {
            AdView(activity).apply {
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
                loadAd(
                    BannerAdRequest.Builder(adUnitId, adSize).build(),
                    object : AdLoadCallback<BannerAd> {
                        override fun onAdLoaded(ad: BannerAd) = onLoaded()
                        override fun onAdFailedToLoad(adError: LoadAdError) = onFailed(adError)
                    },
                )
            }
        },
        modifier = modifier,
        onReset = null,
        onRelease = { it.destroy() },
    )
}

@Composable
private fun YandexStickyBanner(
    adUnitId: String,
    widthDp: Int,
    modifier: Modifier,
    onLoaded: () -> Unit,
    onFailed: (AdRequestError) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            BannerAdView(context).apply {
                setAdSize(BannerAdSize.sticky(context, widthDp))
                setBannerAdEventListener(
                    object : BannerAdEventListener {
                        override fun onAdLoaded() = onLoaded()
                        override fun onAdFailedToLoad(error: AdRequestError) = onFailed(error)
                        override fun onAdClicked() = Unit
                        override fun onImpression(impressionData: ImpressionData?) = Unit
                    },
                )
                loadAd(AdRequest.Builder(adUnitId).build())
            }
        },
        modifier = modifier,
        onReset = null,
        onRelease = { it.destroy() },
    )
}

internal enum class BannerProvider { GOOGLE, YANDEX, EXHAUSTED }

internal data class BannerWaterfallState(
    val provider: BannerProvider = BannerProvider.YANDEX,
    val attemptId: Int = 0,
    val loaded: Boolean = false,
)

/**
 * Starts at Google (attempt 1, the id the Yandex leg would have advanced to) when the Yandex
 * breaker is open, so late callbacks from an earlier attempt still cannot touch this one.
 */
internal fun initialBannerWaterfallState(tryYandex: Boolean): BannerWaterfallState =
    if (tryYandex) BannerWaterfallState() else BannerWaterfallState(provider = BannerProvider.GOOGLE, attemptId = 1)

internal sealed interface BannerWaterfallEvent {
    val attemptId: Int

    data class YandexLoaded(override val attemptId: Int) : BannerWaterfallEvent
    data class YandexFailed(override val attemptId: Int) : BannerWaterfallEvent
    data class YandexTimedOut(override val attemptId: Int) : BannerWaterfallEvent
    data class GoogleLoaded(override val attemptId: Int) : BannerWaterfallEvent
    data class GoogleFailed(override val attemptId: Int) : BannerWaterfallEvent
    data class GoogleTimedOut(override val attemptId: Int) : BannerWaterfallEvent
}

internal fun reduceBannerWaterfall(state: BannerWaterfallState, event: BannerWaterfallEvent): BannerWaterfallState {
    if (event.attemptId != state.attemptId) return state
    return when (event) {
        is BannerWaterfallEvent.YandexLoaded ->
            if (state.provider == BannerProvider.YANDEX) state.copy(loaded = true) else state
        is BannerWaterfallEvent.YandexFailed,
        is BannerWaterfallEvent.YandexTimedOut,
        -> if (state.provider == BannerProvider.YANDEX && !state.loaded) {
            BannerWaterfallState(provider = BannerProvider.GOOGLE, attemptId = state.attemptId + 1)
        } else {
            state
        }
        is BannerWaterfallEvent.GoogleLoaded ->
            if (state.provider == BannerProvider.GOOGLE) state.copy(loaded = true) else state
        is BannerWaterfallEvent.GoogleFailed,
        is BannerWaterfallEvent.GoogleTimedOut,
        -> if (state.provider == BannerProvider.GOOGLE && !state.loaded) {
            BannerWaterfallState(provider = BannerProvider.EXHAUSTED, attemptId = state.attemptId + 1)
        } else {
            state
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "BannerWaterfall"
private const val MIN_BANNER_WIDTH_DP = 32
private const val BANNER_LOAD_TIMEOUT_MILLIS = 30_000L
