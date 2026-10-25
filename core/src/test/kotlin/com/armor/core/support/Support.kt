package com.armor.core.support

import com.armor.core.engine.ArmorConfig
import com.armor.core.engine.EngineEffect
import com.armor.core.engine.RideEngine
import com.armor.core.engine.RideSnapshot
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode

val ORIGIN = LatLon(12.9716, 77.5946)
private val frame = LocalFrame(ORIGIN)

/** A point [eastM] east and [northM] north of the fixed test origin. */
fun at(eastM: Double, northM: Double): LatLon = frame.toLatLon(Point(eastM, northM))

fun fix(t: Long, pos: LatLon, speed: Double = 0.0, acc: Double = 5.0) = LocationFix(t, pos, speed, null, acc)

fun report(id: String, t: Long, pos: LatLon, speed: Double = 0.0, status: QuickStatus? = null, safety: SafetyCode = SafetyCode.NORMAL) =
    RiderReport(id, id, fix(t, pos, speed), status, safety)

/**
 * Replays a ride on a straight road heading north, one second per step. Riders are described by how far
 * along the road they are and how fast they move; "me" is the engine under test.
 */
class RideSim(config: ArmorConfig = ArmorConfig()) {
    class Rider(val id: String, var north: Double, var speed: Double, var status: QuickStatus? = null)

    val engine = RideEngine("me", "Me", config)
    val me = Rider("me", 0.0, 0.0)
    val others = mutableListOf<Rider>()
    var now = 10_000_000L
    var peersReporting = true
    var snapshot: RideSnapshot? = null
    val effects = mutableListOf<EngineEffect>()

    fun addPeer(id: String, north: Double, speed: Double) = Rider(id, north, speed).also { others += it }

    fun run(seconds: Int) = repeat(seconds) { stepOneSecond() }

    /** Runs until [done] holds or [maxSeconds] pass; returns whether it held. */
    fun runUntil(maxSeconds: Int, done: () -> Boolean): Boolean {
        repeat(maxSeconds) {
            if (done()) return true
            stepOneSecond()
        }
        return done()
    }

    private fun stepOneSecond() {
        now += 1_000
        (listOf(me) + others).forEach { it.north += it.speed }
        engine.onLocation(fix(now, at(0.0, me.north), me.speed))
        if (peersReporting) engine.onPeers(others.map { report(it.id, now, at(0.0, it.north), it.speed, it.status) })
        snapshot = engine.tick(now)
        effects += engine.drainEffects()
    }

    inline fun <reified T : EngineEffect> effectsOf(): List<T> = effects.filterIsInstance<T>()

    /** Queues a crash's sensor signature starting at the current time: quiet, a hard impact, then tumbling to a new orientation. */
    fun feedCrashSignature(peakG: Double = 6.0, rotate: Boolean = true) {
        val g = 9.80665
        var t = now - 2_000
        while (t < now) { engine.onAccel(t, 0.0, 0.0, g); t += 20 }
        // impact
        engine.onAccel(now, 0.0, 0.0, peakG * g)
        engine.onAccel(now + 20, peakG * g * 0.8, 0.0, 0.0)
        var i = 2
        while (i < 100) {
            val tt = now + i * 20L
            val settled = rotate && i > 30
            if (settled) engine.onAccel(tt, g, 0.0, 0.0) else engine.onAccel(tt, 0.0, 0.0, g)
            if (rotate && i < 40) engine.onGyro(tt, 0.0, 0.0, 5.0) else engine.onGyro(tt, 0.0, 0.0, 0.0)
            i++
        }
    }
}
