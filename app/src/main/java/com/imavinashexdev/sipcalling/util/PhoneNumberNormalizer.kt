package com.imavinashexdev.sipcalling.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.regex.Pattern

/**
 * Normalizes phone numbers and SIP addresses.
 *
 * Rules:
 * 1. Full SIP URIs (e.g. sip:music@sip.linphone.org, music@sip.linphone.org) preserve their
 *    scheme and domain, allowing direct dialing of SIP endpoints and services.
 * 2. Alphanumeric service keywords (e.g. "echo", "music") are preserved.
 * 3. International phone numbers (+CC or 00CC) are normalized to national format with a leading '0'.
 * 4. Internal PBX extensions (2-4 digits) are preserved.
 */
object PhoneNumberNormalizer {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val SIP_URI_PATTERN = Pattern.compile("^(sips?:)?([^@;?]+)(@.+)?$", Pattern.CASE_INSENSITIVE)

    /**
     * Checks if an input string is a SIP address (starts with sip:/sips: or contains '@').
     */
    fun isSipAddress(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("sip:", ignoreCase = true) ||
                trimmed.startsWith("sips:", ignoreCase = true) ||
                trimmed.contains("@")
    }

    /**
     * Normalizes a phone number or SIP address.
     *
     * @param rawNumber The input number or SIP address
     * @param preserveShortExtensions If true, keeps 2-4 digit internal extensions unchanged (e.g., "1001")
     * @return Normalized destination ready for dialing
     */
    fun normalize(rawNumber: String, preserveShortExtensions: Boolean = true): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""

        // Preserve full SIP URIs (e.g. sip:music@sip.linphone.org, music@sip.linphone.org)
        if (isSipAddress(trimmed)) {
            return normalizeSipUri(trimmed)
        }

        // Non-numeric alphanumeric keywords/services (e.g. "echo", "music", "conference")
        if (trimmed.any { it.isLetter() }) {
            return trimmed
        }

        // Clean formatting characters (spaces, dashes, parentheses, dots)
        val cleaned = cleanFormatting(trimmed)
        if (cleaned.isEmpty()) return trimmed

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
            val withoutExtraZeros = cleaned.trimStart('0')
            return "0$withoutExtraZeros"
        }

        // Case 4: Typed number without '0' (e.g. 9988998899 -> 09988998899)
        return "0$cleaned"
    }

    /**
     * Normalizes a SIP URI by ensuring the scheme is present and transforming
     * numeric username portions into national format while preserving alphanumeric services.
     * Example: "sip:+447911123456@sip.linphone.org" -> "sip:07911123456@sip.linphone.org"
     * Example: "music@sip.linphone.org" -> "sip:music@sip.linphone.org"
     * Example: "sip:music@sip.linphone.org" -> "sip:music@sip.linphone.org"
     */
    fun normalizeSipUri(sipUri: String): String {
        val trimmed = sipUri.trim()
        val fullUri = if (!trimmed.startsWith("sip:", ignoreCase = true) && !trimmed.startsWith("sips:", ignoreCase = true)) {
            "sip:$trimmed"
        } else {
            trimmed
        }

        val matcher = SIP_URI_PATTERN.matcher(fullUri)
        if (matcher.matches()) {
            val scheme = matcher.group(1) ?: "sip:"
            val user = matcher.group(2) ?: ""
            val domain = matcher.group(3) ?: ""

            val normalizedUser = if (user.any { it.isLetter() }) user else normalize(user)
            return "$scheme$normalizedUser$domain"
        }
        return fullUri
    }

    /**
     * Formats an incoming caller identity for clean UI presentation.
     * Example: Displays "0771234567" instead of "+94771234567"
     */
    fun formatCallerDisplay(rawAddressOrUri: String, displayName: String? = null): String {
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
            val digits = numberWithPlus.filter { it.isDigit() }
            return if (digits.length >= 10) {
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
        val trimmed = input.trim()
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }
}
