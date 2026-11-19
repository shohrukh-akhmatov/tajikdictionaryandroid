package tj.tajikdictionary.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacingTest {
    private val minGapMs = AdConfig.minSecondsBetweenInterstitials * 1000

    @Test
    fun notDueBeforeEnoughRounds() {
        assertFalse(AdsManager.isInterstitialDue(AdConfig.roundsPerInterstitial - 1, null, nowMs = 1_000_000))
    }

    @Test
    fun dueAfterEnoughRoundsWhenNeverShown() {
        assertTrue(AdsManager.isInterstitialDue(AdConfig.roundsPerInterstitial, null, nowMs = 1_000_000))
    }

    @Test
    fun cooldownBlocksUntilMinimumGapPasses() {
        val last = 1_000_000L
        val rounds = AdConfig.roundsPerInterstitial
        assertFalse(AdsManager.isInterstitialDue(rounds, last, nowMs = last + minGapMs - 1))
        assertTrue(AdsManager.isInterstitialDue(rounds, last, nowMs = last + minGapMs))
    }
}
