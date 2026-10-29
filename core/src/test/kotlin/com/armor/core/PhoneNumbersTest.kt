package com.armor.core

import com.armor.core.sos.PhoneNumbers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNumbersTest {
    @Test fun `indian mobiles in every common spelling become +91`() {
        listOf("9876543210", "98765 43210", "098765-43210", "09876543210", "919876543210", "91 98765 43210", "+91 98765 43210", "+91-9876543210", "0091 98765 43210")
            .forEach { assertEquals("+919876543210", PhoneNumbers.normalize(it), it) }
    }

    @Test fun `numbers that are not indian mobiles are left alone`() {
        assertEquals("112", PhoneNumbers.normalize("112"))
        assertEquals("+918022334455", PhoneNumbers.normalize("080 2233 4455")) // Bengaluru landline, international form
        assertEquals("1800123456", PhoneNumbers.normalize("1800 123 456")) // toll-free stays as dialled
        assertEquals("+447700900123", PhoneNumbers.normalize("+44 7700 900123"))
        assertEquals("", PhoneNumbers.normalize("  "))
    }

    @Test fun `other countries keep local numbers as digits`() {
        assertEquals("07700900123", PhoneNumbers.normalize("07700 900123", country = "GB"))
        assertEquals("+447700900123", PhoneNumbers.normalize("0044 7700 900123", country = "GB"))
    }

    @Test fun `the same person typed two ways matches`() {
        assertTrue(PhoneNumbers.sameNumber("+91 98765 43210", "098765-43210"))
    }
}
