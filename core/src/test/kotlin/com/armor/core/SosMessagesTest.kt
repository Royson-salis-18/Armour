package com.armor.core

import com.armor.core.model.LatLon
import com.armor.core.model.SosReason
import com.armor.core.sos.SosMessages
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SosMessagesTest {
    private val here = LatLon(12.9716, 77.5946)

    @Test fun `map link keeps decimal points under a comma-decimal locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("https://maps.google.com/?q=12.971600,77.594600", SosMessages.mapsLink(here))
            assertContains(SosMessages.sos("Arjun", SosReason.MANUAL, here, 8.4, 12), "within 8 m, 12 s old")
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun `message states who what and where`() {
        val m = SosMessages.sos("Arjun", SosReason.CRASH_NO_RESPONSE, here, 8.0, 3)
        assertContains(m, "Arjun")
        assertContains(m, "crashed")
        assertContains(m, "https://maps.google.com/?q=12.971600,77.594600")
    }

    @Test fun `missing fix is stated rather than hidden`() {
        assertContains(SosMessages.sos("Arjun", SosReason.MANUAL, null, null, null), "Location unknown")
    }

    @Test fun `every message is plain ASCII`() {
        for (r in SosReason.entries) {
            val m = SosMessages.sos("Zoë नमस्ते", r, here, 8.0, 3)
            assertTrue(m.all { it.code in 32..126 }, m)
        }
        assertTrue(SosMessages.cancelled("Zoë").all { it.code in 32..126 })
    }

    @Test fun `even the longest message fits in a single 160-character SMS`() {
        val longest = "W".repeat(60) // longer than the name field allows; must be truncated, not overflow
        for (r in SosReason.entries) {
            val m = SosMessages.sos(longest, r, LatLon(-33.868820, -151.209296), 999.0, 99_999)
            assertTrue(m.length <= 160, "${r.name}: ${m.length} chars: $m")
        }
        assertTrue(SosMessages.cancelled(longest).length <= 160)
    }

    @Test fun `names are capped`() {
        val m = SosMessages.sos("W".repeat(60), SosReason.MANUAL, here, null, null)
        assertContains(m, "W".repeat(SosMessages.MAX_NAME) + " ")
        assertTrue("W".repeat(SosMessages.MAX_NAME + 1) !in m)
    }

    @Test fun `a blank name falls back to a generic one`() {
        assertContains(SosMessages.sos("   ", SosReason.MANUAL, here, null, null), "A rider")
    }
}
