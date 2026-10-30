package com.imavinashexdev.sipcalling.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `test international numbers with plus are normalized with leading 0 and country code stripped`() {
        // UK (+44)
        assertEquals("07911123456", PhoneNumberNormalizer.normalize("+447911123456"))
        assertEquals("07911123456", PhoneNumberNormalizer.normalize("+44 7911 123456"))

        // Sri Lanka (+94)
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("+94771234567"))
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("+94 77 123 4567"))

        // US (+1)
        assertEquals("02025550123", PhoneNumberNormalizer.normalize("+12025550123"))
        assertEquals("02025550123", PhoneNumberNormalizer.normalize("+1 (202) 555-0123"))

        // Australia (+61)
        assertEquals("0412345678", PhoneNumberNormalizer.normalize("+61412345678"))
    }

    @Test
    fun `test international numbers with 00 prefix are normalized with leading 0`() {
        assertEquals("07911123456", PhoneNumberNormalizer.normalize("00447911123456"))
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("0094771234567"))
    }

    @Test
    fun `test numbers already starting with 0 maintain single 0 without duplication`() {
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("0771234567"))
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("077-123-4567"))
    }

    @Test
    fun `test local subscriber number missing leading 0 gets 0 prepended`() {
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("771234567"))
    }

    @Test
    fun `test typed number without plus or leading zero gets 0 prepended without removing starting digits`() {
        assertEquals("09988998899", PhoneNumberNormalizer.normalize("9988998899"))
        assertEquals("0771234567", PhoneNumberNormalizer.normalize("771234567"))
    }

    @Test
    fun `test SIP URI normalization`() {
        assertEquals(
            "sip:0771234567@sip.linphone.org",
            PhoneNumberNormalizer.normalizeSipUri("sip:+94771234567@sip.linphone.org")
        )
        assertEquals(
            "sip:07911123456@192.168.1.100",
            PhoneNumberNormalizer.normalizeSipUri("sip:00447911123456@192.168.1.100")
        )
    }

    @Test
    fun `test SIP service addresses like music and echo are preserved completely`() {
        assertEquals("sip:music@sip.linphone.org", PhoneNumberNormalizer.normalize("sip:music@sip.linphone.org"))
        assertEquals("sip:echo@sip.linphone.org", PhoneNumberNormalizer.normalize("sip:echo@sip.linphone.org"))
        assertEquals("sip:music@sip.linphone.org", PhoneNumberNormalizer.normalize("music@sip.linphone.org"))
    }

    @Test
    fun `test internal short PBX extensions are preserved`() {
        assertEquals("1001", PhoneNumberNormalizer.normalize("1001"))
        assertEquals("101", PhoneNumberNormalizer.normalize("101"))
    }

    @Test
    fun `test non-numeric SIP service words are preserved`() {
        assertEquals("echo", PhoneNumberNormalizer.normalize("echo"))
        assertEquals("music", PhoneNumberNormalizer.normalize("music"))
    }

    @Test
    fun `test formatCallerDisplay for incoming calls`() {
        assertEquals(
            "0771234567",
            PhoneNumberNormalizer.formatCallerDisplay("sip:+94771234567@domain.com")
        )
        assertEquals(
            "John Doe",
            PhoneNumberNormalizer.formatCallerDisplay("sip:+94771234567@domain.com", "John Doe")
        )
        assertEquals(
            "music",
            PhoneNumberNormalizer.formatCallerDisplay("sip:music@sip.linphone.org")
        )
    }
}