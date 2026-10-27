package com.imavinashexdev.sipcalling.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.regex.Pattern

/**
 * Normalizes phone numbers to standard national format with a leading '0',
 * stripping any pre-existing international country codes (+CC or 00CC).
 *
 * Rules:
 * 1. Strips any leading '+' or '00' international country code and prefixes with '0'.
 *    Example: +447911123456 -> 07911123456
 *    Example: +94771234567  -> 0771234567
 *    Example: 0094771234567 -> 0771234567
 *    Example: +12025550123  -> 02025550123
 * 2. If the number already starts with '0', maintains the single leading '0' (never 00...).
 *    Example: 0771234567    -> 0771234567
 * 3. If an incoming number arrives without leading '0' or country code (e.g., from trunk),
 *    prepends '0'.
 *    Example: 771234567     -> 0771234567
 * 4. Preserves short internal PBX extensions (e.g. 100, 1001) when preserveShortExtensions is true.
 * 5. Handles SIP URIs (sip:+447911123456@domain.com -> sip:07911123456@domain.com).
 */
object PhoneNumberNormalizer {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val SIP_URI_PATTERN = Pattern.compile("^(sips?:)?([^@;?]+)(@.+)?$", Pattern.CASE_INSENSITIVE)

    /**
     * Normalizes a phone number to start with '0', removing any country code.
     *
     * @param rawNumber The input number or SIP address
     * @param preserveShortExtensions If true, keeps 2-4 digit internal extensions unchanged (e.g., "1001")
     * @return Normalized phone number starting with '0' (or the original if non-numeric/extension)
     */
    fun normalize(rawNumber: String, preserveShortExtensions: Boolean = true): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""

        // Extract user part if it's a full SIP URI (e.g. sip:123@domain -> 123)
        val extractedUser = extractUserFromSip(trimmed)

        // Clean formatting characters (spaces, dashes, parentheses, dots)
        val cleaned = cleanFormatting(extractedUser)
        if (cleaned.isEmpty()) return trimmed

        // If it contains letters (e.g., "echo", "music", "conference"), it's not a phone number
        if (cleaned.any { it.isLetter() }) {
            return extractedUser
        }

        // If it's a short extension (typically 2-4 digits, e.g. 101, 1001)
        if (preserveShortExtensions && cleaned.length in 2..4 && !cleaned.startsWith("+") && !cleaned.startsWith("00")) {
            return cleaned
        }

        // Case 1: Starts with international '+' (e.g. +447911123456, +94771234567)
        if (cleaned.startsWith("+")) {
            return formatWithLeadingZeroFromInternational(cleaned)
        }

        // Case 2: Starts with international '00' (e.g. 00447911123456, 0094771234567)
        if (cleaned.startsWith("00") && cleaned.length > 5) {
            val withPlus = "+" + cleaned.substring(2)
            return formatWithLeadingZeroFromInternational(withPlus)
        }

        // Case 3: Already starts with '0' (e.g. 09988998899, 0771234567)
        if (cleaned.startsWith("0")) {
            // Ensure single leading zero
            val withoutExtraZeros = cleaned.trimStart('0')
            return "0$withoutExtraZeros"
        }

        // Case 4: Typed number without '0' (e.g. 9988998899 -> 09988998899)
        // Never remove any starting digits; simply prepend '0'.
        return "0$cleaned"
    }

    /**
     * Normalizes a SIP URI by transforming the username portion into the '0' prefixed national format.
     * Example: "sip:+447911123456@sip.linphone.org" -> "sip:07911123456@sip.linphone.org"
     */
    fun normalizeSipUri(sipUri: String): String {
        val trimmed = sipUri.trim()
        val matcher = SIP_URI_PATTERN.matcher(trimmed)
        if (matcher.matches()) {
            val scheme = matcher.group(1) ?: "sip:"
            val user = matcher.group(2) ?: ""
            val domain = matcher.group(3) ?: ""

            val normalizedUser = normalize(user)
            return "$scheme$normalizedUser$domain"
        }
        return sipUri
    }

    /**
     * Formats an incoming caller identity for clean UI presentation.
     * Example: Displays "0771234567" instead of "+94771234567"
     */
    fun formatCallerDisplay(rawAddressOrUri: String, displayName: String? = null): String {
        // If display name is present and not equal to the raw address, check if it's a number
        if (!displayName.isNullOrBlank() && displayName != rawAddressOrUri) {
            val normalizedName = normalize(displayName)
            if (normalizedName.startsWith("0") && normalizedName.all { it.isDigit() }) {
                return normalizedName
            }
            return displayName
        }

        val extracted = extractUserFromSip(rawAddressOrUri)
        return normalize(extracted)
    }

    private fun formatWithLeadingZeroFromInternational(numberWithPlus: String): String {
        try {
            val parsed: PhoneNumber = phoneUtil.parse(numberWithPlus, null)
            val nationalNumber = parsed.nationalNumber.toString()
            return "0$nationalNumber"
        } catch (_: Exception) {
            // Regex fallback if libphonenumber cannot parse:
            // Strip leading '+' or '00', remove first 1-3 digits if total length >= 10, prefix '0'
            val digits = numberWithPlus.filter { it.isDigit() }
            return if (digits.length >= 10) {
                // Assume 2-digit country code fallback
                "0" + digits.drop(2)
            } else {
                "0$digits"
            }
        }
    }

    private fun extractUserFromSip(input: String): String {
        var result = input
        if (result.startsWith("sip:", ignoreCase = true)) {
            result = result.substring(4)
        } else if (result.startsWith("sips:", ignoreCase = true)) {
            result = result.substring(5)
        }
        val atIndex = result.indexOf('@')
        if (atIndex != -1) {
            result = result.substring(0, atIndex)
        }
        val semicolonIndex = result.indexOf(';')
        if (semicolonIndex != -1) {
            result = result.substring(0, semicolonIndex)
        }
        return result
    }

    private fun cleanFormatting(input: String): String {
        // Keep '+' only if at the beginning, strip all whitespace, dashes, parens, dots
        val trimmed = input.trim()
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }
}

