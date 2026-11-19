package tj.tajikdictionary.ads

/**
 * The IAB TCF strings UMP stores, translated into each network's own switch. The Next-Gen Google
 * Mobile Ads SDK does not read them itself, and Yandex only takes a single boolean.
 */
internal data class TcfConsentSignals(
    val gdprApplies: Int?,
    val purposeConsents: String?,
    val additionalConsent: String?,
) {
    fun allowsStorage(consentNotRequired: Boolean): Boolean = when (gdprApplies) {
        0 -> true
        1 -> purposeConsents.hasConsentFor(1)
        else -> consentNotRequired
    }

    fun allowsYandex(consentNotRequired: Boolean): Boolean {
        if (!allowsStorage(consentNotRequired)) return false
        if (gdprApplies != 1) return true
        return additionalConsent.hasAdditionalConsentFor(YANDEX_ADDITIONAL_CONSENT_ID)
    }

    /**
     * Personalized ads need TCF purposes 3 and 4 (build/select a personalized ads profile) on top of
     * purpose 1 storage. Anything less still serves ads -- just non-personalized ones.
     */
    fun allowsPersonalization(consentNotRequired: Boolean): Boolean {
        if (gdprApplies != 1) return consentNotRequired || gdprApplies == 0
        return allowsStorage(consentNotRequired) &&
            purposeConsents.hasConsentFor(3) &&
            purposeConsents.hasConsentFor(4)
    }

    private fun String?.hasConsentFor(oneBasedId: Int): Boolean =
        this != null && length >= oneBasedId && this[oneBasedId - 1] == '1'

    private fun String?.hasAdditionalConsentFor(vendorId: Int): Boolean {
        val consentedVendors = this?.split('~')?.getOrNull(1).orEmpty()
        return consentedVendors.split('.').any { it.toIntOrNull() == vendorId }
    }

    private companion object {
        // Yandex's Google Additional Consent ID, not an IAB GVL vendor ID.
        private const val YANDEX_ADDITIONAL_CONSENT_ID = 1033
    }
}
