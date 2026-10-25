package com.armor.core

import com.armor.core.model.AlertKind
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.OutlierAssessment.IN_CLUSTER
import com.armor.core.model.OutlierAssessment.OUTLIER
import com.armor.core.model.OutlierAssessment.UNKNOWN
import com.armor.core.model.SosReason
import com.armor.core.safety.EventType
import com.armor.core.safety.Observation
import com.armor.core.safety.SafetyConfig
import com.armor.core.safety.SafetyEffect
import com.armor.core.safety.SafetyState
import com.armor.core.safety.SafetyStateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafetyStateMachineTest {
    private val s = 1_000L
    private val t0 = 100_000L

    private fun obs(a: OutlierAssessment = OUTLIER, stationarySec: Long = 0, moving: Boolean = false, intentional: Boolean = false) =
        Observation(a, stationarySec * s, moving, intentional)

    private fun List<SafetyEffect>.logs() = filterIsInstance<SafetyEffect.Log>().map { it.type }

    /** Machine driven to Verifying at [t0]. */
    private fun verifying(cfg: SafetyConfig = SafetyConfig()) =
        SafetyStateMachine("me", cfg).also { it.onTick(t0, obs(stationarySec = 90)) }

    private fun groupAlerted(): Pair<SafetyStateMachine, String> {
        val m = verifying()
        val fx = m.onTick(t0 + 30 * s, obs(stationarySec = 120))
        return m to fx.filterIsInstance<SafetyEffect.RaiseAlert>().single().alertId
    }

    // ---- stationary check ------------------------------------------------------------------

    @Test fun `separated rider is asked to confirm only after the stationary threshold`() {
        val m = SafetyStateMachine("me")
        assertTrue(m.onTick(t0, obs(stationarySec = 89)).isEmpty())
        assertIs<SafetyState.Normal>(m.state)

        val fx = m.onTick(t0 + s, obs(stationarySec = 90))
        val v = assertIs<SafetyState.Verifying>(m.state)
        assertEquals(t0 + s + 30 * s, v.deadlineMs)
        assertEquals(listOf(EventType.STATIONARY_CHECK), fx.logs())
    }

    @Test fun `a rider who is with the group is never checked`() {
        val m = SafetyStateMachine("me")
        m.onTick(t0, obs(IN_CLUSTER, stationarySec = 10_000))
        assertIs<SafetyState.Normal>(m.state)
    }

    @Test fun `solo or offline riders use the longer threshold`() {
        val m = SafetyStateMachine("me")
        m.onTick(t0, obs(UNKNOWN, stationarySec = 299))
        assertIs<SafetyState.Normal>(m.state)
        m.onTick(t0 + s, obs(UNKNOWN, stationarySec = 300))
        assertIs<SafetyState.Verifying>(m.state)
    }

    @Test fun `solo monitoring can be switched off`() {
        val m = SafetyStateMachine("me", SafetyConfig(soloMonitoring = false))
        m.onTick(t0, obs(UNKNOWN, stationarySec = 100_000))
        assertIs<SafetyState.Normal>(m.state)
    }

    @Test fun `a declared stop suppresses the check`() {
        val m = SafetyStateMachine("me")
        m.onTick(t0, obs(stationarySec = 1_000, intentional = true))
        assertIs<SafetyState.Normal>(m.state)
    }

    // ---- verification outcomes -------------------------------------------------------------

    @Test fun `answering the prompt returns to normal and silences repeat prompts for the cooldown`() {
        val m = verifying()
        val fx = m.onRiderResponded(t0 + 5 * s)
        assertIs<SafetyState.Normal>(m.state)
        assertEquals(listOf(EventType.RIDER_OK), fx.logs())

        m.onTick(t0 + 200 * s, obs(stationarySec = 500))
        assertIs<SafetyState.Normal>(m.state)
        m.onTick(t0 + 5 * s + 300 * s, obs(stationarySec = 800))
        assertIs<SafetyState.Verifying>(m.state)
    }

    @Test fun `no answer alerts the group with a stationary alert`() {
        val m = verifying()
        assertTrue(m.onTick(t0 + 30 * s - 1, obs(stationarySec = 120)).isEmpty())
        val fx = m.onTick(t0 + 30 * s, obs(stationarySec = 120))
        val g = assertIs<SafetyState.GroupAlerted>(m.state)
        val raise = fx.filterIsInstance<SafetyEffect.RaiseAlert>().single()
        assertEquals(AlertKind.STATIONARY, raise.kind)
        assertEquals(g.alertId, raise.alertId)
        assertEquals(t0 + 30 * s + 60 * s, g.deadlineMs)
    }

    @Test fun `sustained movement while verifying cancels the check`() {
        val m = verifying()
        m.onTick(t0 + 1 * s, obs(moving = true))
        m.onTick(t0 + 8 * s, obs(moving = true))
        assertIs<SafetyState.Verifying>(m.state)
        val fx = m.onTick(t0 + 9 * s, obs(moving = true))
        assertIs<SafetyState.Normal>(m.state)
        assertEquals(listOf(EventType.RIDER_RESUMED), fx.logs())
    }

    @Test fun `a brief twitch of movement does not cancel the check`() {
        val m = verifying()
        m.onTick(t0 + 1 * s, obs(moving = true))
        m.onTick(t0 + 4 * s, obs(moving = false))
        m.onTick(t0 + 6 * s, obs(moving = true))
        m.onTick(t0 + 12 * s, obs(moving = true))
        assertIs<SafetyState.Verifying>(m.state)
    }

    @Test fun `rejoining the group cancels the check`() {
        val m = verifying()
        m.onTick(t0 + 2 * s, obs(IN_CLUSTER, stationarySec = 100))
        assertIs<SafetyState.Normal>(m.state)
    }

    @Test fun `declaring a stop while being checked counts as an answer`() {
        val m = verifying()
        m.onTick(t0 + 2 * s, obs(stationarySec = 100, intentional = true))
        assertIs<SafetyState.Normal>(m.state)
    }

    // ---- group alert → SOS -----------------------------------------------------------------

    @Test fun `unacknowledged group alert escalates to SOS exactly at the deadline`() {
        val (m, alertId) = groupAlerted()
        val deadline = (m.state as SafetyState.GroupAlerted).deadlineMs
        assertTrue(m.onTick(deadline - 1, obs(stationarySec = 200)).isEmpty())

        val fx = m.onTick(deadline, obs(stationarySec = 200))
        val sos = assertIs<SafetyState.Sos>(m.state)
        assertEquals(SosReason.STATIONARY_UNACKNOWLEDGED, sos.reason)
        assertEquals(alertId, sos.alertId)
        assertEquals(SafetyEffect.DispatchSos(SosReason.STATIONARY_UNACKNOWLEDGED, alertId), fx.filterIsInstance<SafetyEffect.DispatchSos>().single())
        assertEquals(AlertKind.SOS, fx.filterIsInstance<SafetyEffect.RaiseAlert>().single().kind)
    }

    @Test fun `a group acknowledgement pauses escalation`() {
        val (m, alertId) = groupAlerted()
        val fx = m.onGroupAcknowledged(t0 + 40 * s, alertId, "Meera")
        assertIs<SafetyState.Acknowledged>(m.state)
        assertEquals(listOf(EventType.GROUP_ACK), fx.logs())

        m.onTick(t0 + 400 * s, obs(stationarySec = 1_000))
        assertIs<SafetyState.Acknowledged>(m.state)
    }

    @Test fun `an acknowledgement for a different alert is ignored`() {
        val (m, _) = groupAlerted()
        assertTrue(m.onGroupAcknowledged(t0 + 40 * s, "someone-else-1", "Meera").isEmpty())
        assertIs<SafetyState.GroupAlerted>(m.state)
    }

    @Test fun `an acknowledged alert is re-raised if the rider is still stopped when it expires`() {
        val (m, alertId) = groupAlerted()
        m.onGroupAcknowledged(t0 + 40 * s, alertId, "Meera")
        val expires = (m.state as SafetyState.Acknowledged).expiresMs
        val fx = m.onTick(expires, obs(stationarySec = 5_000))
        val g = assertIs<SafetyState.GroupAlerted>(m.state)
        assertTrue(g.alertId != alertId)
        assertTrue(fx.contains(SafetyEffect.ResolveAlert(alertId)))
    }

    @Test fun `the group reaching the rider resolves the alert`() {
        val (m, alertId) = groupAlerted()
        val fx = m.onTick(t0 + 40 * s, obs(IN_CLUSTER, stationarySec = 200))
        assertIs<SafetyState.Normal>(m.state)
        assertTrue(fx.contains(SafetyEffect.ResolveAlert(alertId)))
        assertEquals(EventType.GROUP_REACHED_RIDER, fx.logs().single())
    }

    @Test fun `rider answering after the group was alerted resolves the alert`() {
        val (m, alertId) = groupAlerted()
        val fx = m.onRiderResponded(t0 + 45 * s)
        assertIs<SafetyState.Normal>(m.state)
        assertTrue(fx.contains(SafetyEffect.ResolveAlert(alertId)))
    }

    // ---- crash -----------------------------------------------------------------------------

    @Test fun `crash starts a cancellable countdown that ends in SOS`() {
        val m = SafetyStateMachine("me")
        val fx = m.onCrash(t0, "6.0 g")
        val c = assertIs<SafetyState.CrashCountdown>(m.state)
        assertEquals(t0 + 30 * s, c.deadlineMs)
        assertEquals(listOf(EventType.CRASH), fx.logs())

        assertTrue(m.onTick(t0 + 30 * s - 1, obs(IN_CLUSTER)).isEmpty())
        val end = m.onTick(t0 + 30 * s, obs(IN_CLUSTER))
        val sos = assertIs<SafetyState.Sos>(m.state)
        assertEquals(SosReason.CRASH_NO_RESPONSE, sos.reason)
        assertTrue(end.any { it is SafetyEffect.DispatchSos })
    }

    @Test fun `rider can cancel the crash countdown and no SOS is sent`() {
        val m = SafetyStateMachine("me")
        m.onCrash(t0, "6.0 g")
        val fx = m.onRiderResponded(t0 + 5 * s)
        assertIs<SafetyState.Normal>(m.state)
        assertEquals(listOf(EventType.CRASH_CANCELLED), fx.logs())
        assertTrue(fx.none { it is SafetyEffect.DispatchSos })
        m.onTick(t0 + 60 * s, obs(IN_CLUSTER))
        assertIs<SafetyState.Normal>(m.state)
    }

    @Test fun `a crash during a group alert keeps that alert and cancelling resolves it`() {
        val (m, alertId) = groupAlerted()
        m.onCrash(t0 + 40 * s, "7.0 g")
        assertEquals(alertId, (m.state as SafetyState.CrashCountdown).priorAlertId)
        val fx = m.onRiderResponded(t0 + 45 * s)
        assertTrue(fx.contains(SafetyEffect.ResolveAlert(alertId)))
    }

    @Test fun `a crash is not suppressed by the post-answer cooldown`() {
        val m = verifying()
        m.onRiderResponded(t0 + 5 * s)
        m.onCrash(t0 + 10 * s, "6.0 g")
        assertIs<SafetyState.CrashCountdown>(m.state)
    }

    @Test fun `a second crash while counting down or in SOS changes nothing`() {
        val m = SafetyStateMachine("me")
        m.onCrash(t0, "a")
        val before = m.state
        assertTrue(m.onCrash(t0 + s, "b").isEmpty())
        assertEquals(before, m.state)
    }

    // ---- manual SOS & cancel ---------------------------------------------------------------

    @Test fun `manual SOS dispatches immediately from normal`() {
        val m = SafetyStateMachine("me")
        val fx = m.onManualSos(t0)
        assertEquals(SosReason.MANUAL, assertIs<SafetyState.Sos>(m.state).reason)
        assertTrue(fx.any { it is SafetyEffect.DispatchSos })
    }

    @Test fun `manual SOS during a group alert reuses its alert id`() {
        val (m, alertId) = groupAlerted()
        val fx = m.onManualSos(t0 + 40 * s)
        assertEquals(alertId, fx.filterIsInstance<SafetyEffect.DispatchSos>().single().alertId)
    }

    @Test fun `manual SOS twice dispatches once`() {
        val m = SafetyStateMachine("me")
        m.onManualSos(t0)
        assertTrue(m.onManualSos(t0 + s).isEmpty())
    }

    @Test fun `cancelling an SOS notifies and returns to normal`() {
        val m = SafetyStateMachine("me")
        m.onManualSos(t0)
        val alertId = (m.state as SafetyState.Sos).alertId
        val fx = m.onRiderResponded(t0 + 10 * s)
        assertIs<SafetyState.Normal>(m.state)
        assertTrue(fx.contains(SafetyEffect.CancelSos(alertId)))
        assertTrue(fx.contains(SafetyEffect.ResolveAlert(alertId)))
    }

    @Test fun `SOS ignores ticks until the rider cancels`() {
        val m = SafetyStateMachine("me")
        m.onManualSos(t0)
        assertTrue(m.onTick(t0 + 10_000 * s, obs(IN_CLUSTER, moving = true)).isEmpty())
        assertIs<SafetyState.Sos>(m.state)
    }

    @Test fun `responding when nothing is pending does nothing`() {
        val m = SafetyStateMachine("me")
        assertTrue(m.onRiderResponded(t0).isEmpty())
    }

    @Test fun `every transition into a non-normal state is explained in the log`() {
        val m = SafetyStateMachine("me")
        val all = mutableListOf<SafetyEffect>()
        all += m.onTick(t0, obs(stationarySec = 90))
        all += m.onTick(t0 + 30 * s, obs(stationarySec = 120))
        all += m.onTick(t0 + 90 * s, obs(stationarySec = 180))
        val types = all.logs()
        assertEquals(listOf(EventType.STATIONARY_CHECK, EventType.GROUP_ALERT, EventType.SOS_SENT), types)
    }
}
