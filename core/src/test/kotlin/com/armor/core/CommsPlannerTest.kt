package com.armor.core

import com.armor.core.comms.CommsPlanner
import com.armor.core.comms.VoiceRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class CommsPlannerTest {
    @Test fun `online uses the mobile network regardless of intercom`() {
        assertEquals(VoiceRoute.MOBILE_NETWORK, CommsPlanner.select(online = true, intercomConnected = false))
        assertEquals(VoiceRoute.MOBILE_NETWORK, CommsPlanner.select(online = true, intercomConnected = true))
    }

    @Test fun `offline falls back to the helmet intercom`() {
        assertEquals(VoiceRoute.HELMET_INTERCOM, CommsPlanner.select(online = false, intercomConnected = true))
    }

    @Test fun `offline with no intercom has no voice link`() {
        assertEquals(VoiceRoute.UNAVAILABLE, CommsPlanner.select(online = false, intercomConnected = false))
    }
}
