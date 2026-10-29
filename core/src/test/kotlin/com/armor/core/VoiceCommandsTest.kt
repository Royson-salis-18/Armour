package com.armor.core

import com.armor.core.model.HazardType
import com.armor.core.model.QuickStatus
import com.armor.core.model.StopReason
import com.armor.core.voice.VoiceCommands
import com.armor.core.voice.VoiceIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandsTest {
    private fun lead(s: String) = VoiceCommands.parse(s, isLead = true)
    private fun rider(s: String) = VoiceCommands.parse(s, isLead = false)

    @Test fun `lead calls a team stop with a reason`() {
        assertEquals(VoiceIntent.TeamStop(StopReason.FUEL), lead("Everyone stop for fuel"))
        assertEquals(VoiceIntent.TeamStop(StopReason.FOOD), lead("let's stop for lunch"))
        assertEquals(VoiceIntent.TeamStop(StopReason.BREAK), lead("team stop, we need a break"))
        assertEquals(VoiceIntent.TeamStop(StopReason.WEATHER), lead("everyone pull over, it's raining"))
    }

    @Test fun `lead can stop just themselves`() {
        assertEquals(VoiceIntent.StopSelf(QuickStatus.FUEL), lead("just me, stopping for petrol"))
        assertEquals(VoiceIntent.StopSelf(QuickStatus.MECHANICAL), lead("I'm stopping, puncture, only me"))
    }

    @Test fun `a rider's stop is only ever for themselves`() {
        assertEquals(VoiceIntent.StopSelf(QuickStatus.FUEL), rider("stop for fuel"))
        assertEquals(VoiceIntent.StopSelf(QuickStatus.BREAK), rider("everyone stop for a break"))
    }

    @Test fun `only the lead can release the team`() {
        assertEquals(VoiceIntent.ResumeTeam, lead("ride on"))
        assertNull(rider("ride on"))
    }

    @Test fun `sos by voice is only a request to confirm`() {
        assertEquals(VoiceIntent.SosRequest, rider("SOS"))
        assertEquals(VoiceIntent.SosRequest, rider("I crashed"))
    }

    @Test fun `okay and need a hand`() {
        assertEquals(VoiceIntent.ImOkay, rider("I'm okay"))
        assertEquals(VoiceIntent.NeedHand, rider("I need a hand"))
    }

    @Test fun `free text becomes a message`() {
        assertEquals(VoiceIntent.Say("running five minutes behind"), rider("tell everyone running five minutes behind"))
        assertEquals(VoiceIntent.Say("chai at the next dhaba"), lead("message chai at the next dhaba"))
    }

    @Test fun `nonsense and lookalikes are not guessed at`() {
        assertNull(rider("the weather is lovely today"))
        assertNull(rider(""))
        assertNull(rider("sosig rolls"))
    }

    @Test fun `the wake word starts a command and background talk is ignored`() {
        assertEquals("stop for fuel", VoiceCommands.afterWakeWord("Armor, stop for fuel"))
        assertEquals("status", VoiceCommands.afterWakeWord("hey armour status"))
        assertEquals("", VoiceCommands.afterWakeWord("Armor"))
        assertEquals(null, VoiceCommands.afterWakeWord("lets get some fuel"))
    }

    @Test fun `call and text a saved contact by name`() {
        val people = listOf("Mom", "Ravi Kumar")
        assertEquals(VoiceIntent.CallContact("Mom"), VoiceCommands.parse("call mom", false, people))
        assertEquals(VoiceIntent.CallContact("Ravi Kumar"), VoiceCommands.parse("ring Ravi", false, people))
        assertEquals(VoiceIntent.TextContact("Mom", "running late"), VoiceCommands.parse("text Mom running late", false, people))
        assertEquals(VoiceIntent.TextContact("Ravi Kumar", "on my way"), VoiceCommands.parse("message ravi kumar on my way", false, people))
        assertEquals(null, VoiceCommands.parse("call Steve", false, people), "unknown names are not guessed")
        assertEquals(VoiceIntent.Say("wait at the toll"), VoiceCommands.parse("tell everyone wait at the toll", false, people))
        assertEquals(VoiceIntent.SosRequest, VoiceCommands.parse("call an ambulance", false, people))
    }

    @Test fun `status messages and hazards`() {
        assertEquals(VoiceIntent.Status, rider("status"))
        assertEquals(VoiceIntent.ReadMessages, rider("read messages"))
        assertEquals(VoiceIntent.Hazard(HazardType.POTHOLE), rider("pothole ahead"))
        assertEquals(VoiceIntent.Hazard(HazardType.POLICE), rider("police checkpoint"))
        assertEquals(VoiceIntent.Hazard(HazardType.ANIMAL), rider("watch out cows on the road"))
        assertEquals(VoiceIntent.SosRequest, rider("I had an accident"))
    }

    @Test fun `yes and no`() {
        assertEquals(true, VoiceCommands.yesNo("yes send it"))
        assertEquals(false, VoiceCommands.yesNo("no cancel"))
        assertEquals(null, VoiceCommands.yesNo("what"))
    }
}
