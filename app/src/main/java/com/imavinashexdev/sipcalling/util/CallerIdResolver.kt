package com.imavinashexdev.sipcalling.util

import com.imavinashexdev.sipcalling.data.ContactRepository

data class CallerInfo(
    val name: String,
    val number: String,
    val hasContactMatch: Boolean
)

object CallerIdResolver {

    /**
     * Resolves the Caller ID for incoming, active, and recent calls.
     * 1. Extracts the user/extension from SIP URI (e.g. sip:064726747@10.1.3.341 -> 064726747).
     * 2. Matches against Contacts database using clean number, extracted user, or raw address.
     * 3. If matched, returns contact name and clean phone number.
     * 4. If SIP header provided a legitimate caller display name, returns it with the clean number.
     * 5. Fallback: Displays the clean phone number (e.g. 064726747) instead of raw SIP URI.
     */
    fun resolve(rawAddressOrUri: String, rawDisplayName: String? = null): CallerInfo {
        val trimmed = rawAddressOrUri.trim()
        if (trimmed.isEmpty()) {
            return CallerInfo(name = "", number = "", hasContactMatch = false)
        }

        val extractedUser = PhoneNumberNormalizer.extractUserFromSip(trimmed)
        val cleanNumber = PhoneNumberNormalizer.normalize(extractedUser)

        // 1. Check if number matches a saved contact
        val contact = ContactRepository.findContactByNumber(cleanNumber)
            ?: ContactRepository.findContactByNumber(extractedUser)
            ?: ContactRepository.findContactByNumber(trimmed)

        if (contact != null) {
            return CallerInfo(
                name = contact.name,
                number = contact.phoneNumber,
                hasContactMatch = true
            )
        }

        // 2. If SIP header provided a legitimate caller display name (e.g. "Customer Support")
        if (!rawDisplayName.isNullOrBlank() &&
            rawDisplayName != rawAddressOrUri &&
            !rawDisplayName.startsWith("sip:", ignoreCase = true)
        ) {
            val normalizedName = PhoneNumberNormalizer.normalize(rawDisplayName)
            if (!normalizedName.all { it.isDigit() }) {
                val effectiveNumber = if (cleanNumber.isNotBlank()) cleanNumber else extractedUser
                return CallerInfo(
                    name = rawDisplayName,
                    number = effectiveNumber,
                    hasContactMatch = false
                )
            }
        }

        // 3. Fallback: Clean phone number or extracted username (e.g. "064726747")
        val displayFallback = when {
            cleanNumber.isNotBlank() -> cleanNumber
            extractedUser.isNotBlank() -> extractedUser
            else -> trimmed
        }

        return CallerInfo(
            name = displayFallback,
            number = displayFallback,
            hasContactMatch = false
        )
    }
}
