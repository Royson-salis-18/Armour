package com.armor.core

import com.armor.core.engine.ArrivalWatch
import com.armor.core.engine.BatteryWatch
import com.armor.core.engine.EngineEffect
import com.armor.core.engine.FatigueWatch
import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.HazardType
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.MedicalInfo
import com.armor.core.sos.SosMessages
import com.armor.core.support.RideSim
import com.armor.core.support.at
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafetyExtrasTest {
    private fun fix(t: Long, speed: Double, pos: LatLon = LatLon(12.0, 77.0)) = LocationFix(t, pos, speed, null, 5.0)

    @Test fun `fatigue reminds once after two hours of riding and resets after a real break`() {
        val w = FatigueWatch()
        var reminders = 0
        var t = 0L
        repeat(2 * 60 * 60 + 5) { if (w.onFix(fix(t, 20.0))) reminders++; t += 1000 }
        assertEquals(1, reminders)
        repeat(11 * 60) { w.onFix(fix(t, 0.0)); t += 1000 } // 11 min stop
        assertEquals(0, w.riddenSinceBreakMs)
    }

    @Test fun `short stops do not count as a break`() {
        val w = FatigueWatch()
        var t = 0L
        repeat(60 * 60) { w.onFix(fix(t, 20.0)); t += 1000 }
        repeat(3 * 60) { w.onFix(fix(t, 0.0)); t += 1000 } // traffic light, fuel top-up
        assertTrue(w.riddenSinceBreakMs >= 59 * 60_000)
    }

    @Test fun `battery raises once when low and clears when charging`() {
        val b = BatteryWatch()
        assertNull(b.onLevel(40, false))
        assertEquals(BatteryWatch.Change.RAISE, b.onLevel(15, false))
        assertNull(b.onLevel(12, false))
        assertEquals(BatteryWatch.Change.CLEAR, b.onLevel(13, true))
        assertNull(b.onLevel(10, true))
    }

    @Test fun `arrival reports arrived near the destination, or late past the expected time`() {
        val dest = LatLon(13.3702, 77.6835)
        val a = ArrivalWatch()
        a.set(dest, "Nandi Hills", expectedAtMs = 1_000_000)
        assertNull(a.check(LatLon(13.0, 77.5), 900_000))
        assertEquals(ArrivalWatch.Event.ARRIVED, a.check(LatLon(13.3705, 77.6833), 950_000))
        val late = ArrivalWatch()
        late.set(dest, "Nandi Hills", expectedAtMs = 1_000_000)
        assertNull(late.check(LatLon(13.0, 77.5), 1_000_000 + 10 * 60_000))
        assertEquals(ArrivalWatch.Event.LATE, late.check(LatLon(13.0, 77.5), 1_000_000 + 21 * 60_000))
        assertNull(late.check(LatLon(13.0, 77.5), 1_000_000 + 40 * 60_000), "late is reported once")
    }

    @Test fun `a reported hazard is published and listed, and never takes over the screen`() {
        val s = RideSim().apply { addPeer("arjun", 30.0, 15.0); run(3) }
        s.engine.reportHazard(HazardType.OIL, s.now)
        s.run(1)
        val a = s.effectsOf<EngineEffect.PublishAlert>().single().alert
        assertEquals(AlertKind.HAZARD, a.kind)
        assertEquals("OIL", a.detail)
        assertEquals(1, s.snapshot!!.hazards.size)
        // someone else's hazard
        s.engine.onGroupAlerts(listOf(GroupAlert("h2", "arjun", "Arjun", AlertKind.HAZARD, at(0.0, 300.0), s.now, detail = "POTHOLE")), s.now)
        s.run(1)
        assertFalse(s.snapshot!!.needsAttention)
        assertEquals(2, s.snapshot!!.hazards.size)
    }

    @Test fun `low battery is shared with the group once`() {
        val s = RideSim().apply { addPeer("arjun", 30.0, 15.0); run(2) }
        s.engine.onBattery(14, false, s.now)
        s.engine.onBattery(13, false, s.now)
        s.run(1)
        val a = s.effectsOf<EngineEffect.PublishAlert>().single().alert
        assertEquals(AlertKind.LOW_BATTERY, a.kind)
        s.engine.onBattery(13, true, s.now)
        s.run(1)
        assertEquals(a.id, s.effectsOf<EngineEffect.ResolveAlert>().single().alertId)
    }

    @Test fun `only the lead can set a regroup point`() {
        val s = RideSim().apply { addPeer("arjun", 30.0, 15.0); run(2) }
        s.engine.setRegroupPoint(at(0.0, 2000.0), "Fuel station", s.now)
        s.run(1)
        assertNull(s.snapshot!!.regroup)
        s.engine.setLead("me")
        s.engine.setRegroupPoint(at(0.0, 2000.0), "Fuel station", s.now)
        s.run(1)
        assertEquals("Fuel station", assertNotNull(s.snapshot!!.regroup).detail)
    }

    @Test fun `paused crash detection ignores an impact and resumes when riding`() {
        val s = RideSim().apply { run(2) }
        s.engine.setCrashDetectionPaused(true, s.now)
        s.feedCrashSignature()
        s.run(20)
        assertTrue(s.effectsOf<EngineEffect.DispatchSos>().isEmpty())
        assertTrue(s.snapshot!!.crashDetectionPaused || s.snapshot!!.self!!.speedMps >= 6.0)
    }

    @Test fun `medical text fits one sms and is skipped when empty`() {
        assertNull(SosMessages.medical("Royson", MedicalInfo()))
        val m = assertNotNull(SosMessages.medical("Royson", MedicalInfo("O+", "Penicillin", "Asthma", "Inhaler")))
        assertTrue(m.length <= 160, m)
        assertTrue("Blood O+" in m)
        val long = assertNotNull(SosMessages.medical("Royson", MedicalInfo("O+", "x".repeat(300))))
        assertEquals(160, long.length)
    }

    @Test fun `google maps directions link uses dot decimals`() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=13.370200,77.683500&travelmode=driving", SosMessages.directionsLink(LatLon(13.3702, 77.6835)))
    }
}
