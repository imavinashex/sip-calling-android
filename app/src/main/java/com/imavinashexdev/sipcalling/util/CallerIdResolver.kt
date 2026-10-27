package com.imavinashexdev.sipcalling.util

import com.imavinashexdev.sipcalling.data.ContactRepository

data class CallerInfo(
    val name: String,
    val number: String,
    val hasContactMatch: Boolean
)

object CallerIdResolver {

    /**
     * Resolves the Caller ID for incoming and active calls.
     * Looks up the normalized number in the Contacts database.
     * If matched, returns the contact's name as the caller title and the normalized '0' number.
     * If not matched, returns the formatted '0' number.
     */
    fun resolve(rawAddressOrUri: String, rawDisplayName: String? = null): CallerInfo {
        val normalized = PhoneNumberNormalizer.normalize(rawAddressOrUri)

        // 1. Check if number matches a saved contact
        val contact = ContactRepository.findContactByNumber(rawAddressOrUri)
        if (contact != null) {
            return CallerInfo(
                name = contact.name,
                number = contact.phoneNumber,
                hasContactMatch = true
            )
        }

        // 2. If SIP header provided a legitimate caller display name (e.g. "Customer Support")
        if (!rawDisplayName.isNullOrBlank() && rawDisplayName != rawAddressOrUri) {
            val normalizedName = PhoneNumberNormalizer.normalize(rawDisplayName)
            if (!normalizedName.all { it.isDigit() }) {
                return CallerInfo(
                    name = rawDisplayName,
                    number = normalized,
                    hasContactMatch = false
                )
            }
        }

        // 3. Fallback: Normalized '0' prefixed number
        val fallbackDisplay = if (normalized.isNotBlank()) normalized else rawAddressOrUri
        return CallerInfo(
            name = fallbackDisplay,
            number = fallbackDisplay,
            hasContactMatch = false
        )
    }
}
