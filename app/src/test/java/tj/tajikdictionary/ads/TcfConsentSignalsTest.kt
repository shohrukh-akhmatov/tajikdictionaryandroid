package tj.tajikdictionary.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TcfConsentSignalsTest {
    @Test
    fun `outside GDPR both storage and Yandex are allowed`() {
        val signals = TcfConsentSignals(0, null, null)

        assertTrue(signals.allowsStorage(consentNotRequired = false))
        assertTrue(signals.allowsYandex(consentNotRequired = false))
    }

    @Test
    fun `purpose one denial prevents Yandex even when vendor is listed`() {
        val signals = TcfConsentSignals(1, "0", "1~1033")

        assertFalse(signals.allowsStorage(consentNotRequired = false))
        assertFalse(signals.allowsYandex(consentNotRequired = false))
    }

    @Test
    fun `Yandex requires its additional-consent ID inside GDPR`() {
        val absent = TcfConsentSignals(1, "1", null)
        val present = TcfConsentSignals(1, "1", "1~755.1033")

        assertFalse(absent.allowsYandex(consentNotRequired = false))
        assertTrue(present.allowsYandex(consentNotRequired = false))
    }

    @Test
    fun `unknown region follows UMP not-required status conservatively`() {
        val signals = TcfConsentSignals(null, null, null)

        assertFalse(signals.allowsStorage(consentNotRequired = false))
        assertTrue(signals.allowsStorage(consentNotRequired = true))
        assertTrue(signals.allowsYandex(consentNotRequired = true))
    }

    @Test
    fun `declining inside GDPR still serves ads but not personalized ones`() {
        // Purpose 1 granted, personalization purposes 3 and 4 denied: the
        // user declined personalization, so ads must keep serving as
        // non-personalized rather than disappearing.
        val declined = TcfConsentSignals(1, "1000", "1~1033")

        assertTrue(declined.allowsStorage(consentNotRequired = false))
        assertTrue(declined.allowsYandex(consentNotRequired = false))
        assertFalse(declined.allowsPersonalization(consentNotRequired = false))
    }

    @Test
    fun `personalization needs both profile-building purposes`() {
        val onlyThree = TcfConsentSignals(1, "1010", "1~1033")
        val onlyFour = TcfConsentSignals(1, "1001", "1~1033")
        val both = TcfConsentSignals(1, "1011", "1~1033")

        assertFalse(onlyThree.allowsPersonalization(consentNotRequired = false))
        assertFalse(onlyFour.allowsPersonalization(consentNotRequired = false))
        assertTrue(both.allowsPersonalization(consentNotRequired = false))
    }

    @Test
    fun `outside GDPR personalization is allowed`() {
        assertTrue(TcfConsentSignals(0, null, null).allowsPersonalization(consentNotRequired = false))
        assertTrue(TcfConsentSignals(null, null, null).allowsPersonalization(consentNotRequired = true))
        assertFalse(TcfConsentSignals(null, null, null).allowsPersonalization(consentNotRequired = false))
    }
}
