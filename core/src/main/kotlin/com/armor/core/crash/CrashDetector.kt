package com.armor.core.crash

import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

data class CrashConfig(
    /** Accelerometer magnitude (including gravity) that opens an observation window, in g. */
    val impactG: Double = 4.0,
    /** How long after the impact orientation change is observed. */
    val observeWindowMs: Long = 1_500,
    /** Integrated gyroscope rotation within the window that counts as "sudden orientation change". */
    val minRotationRad: Double = 2.0,
    /** Change in gravity direction (before vs. after) that counts as "sudden orientation change". */
    val minTiltDeg: Double = 60.0,
    /** Final slice of the window averaged to estimate the settled orientation. */
    val settleMs: Long = 300,
    /** Time constant of the quiet-time gravity baseline. */
    val baselineTauMs: Long = 1_500,
    /** Samples within this band around 1 g (in g) feed the baseline; impacts and tumbling do not. */
    val quietBandG: ClosedFloatingPointRange<Double> = 0.7..1.3,
    /** Wait after a reported candidate before another can be opened. */
    val cooldownMs: Long = 5_000,
)

/** An impact followed by a sudden orientation change. Not yet a confirmed crash — see [CrashFilter]. */
data class CrashCandidate(
    val impactTimeMs: Long,
    val peakG: Double,
    val rotationRad: Double,
    /** Null when no quiet baseline existed yet (e.g. the app had just started). */
    val tiltDeg: Double?,
)

/**
 * Deterministic crash detection from accelerometer + gyroscope only (no camera, no ML):
 * a high-g impact opens a short window; if the phone also rotates or ends up in a different
 * orientation than its pre-impact baseline, a [CrashCandidate] is emitted.
 *
 * Time is supplied by the caller. Feed samples in timestamp order from a single thread.
 */
class CrashDetector(private val config: CrashConfig = CrashConfig()) {
    private data class V3(val x: Double, val y: Double, val z: Double) {
        val norm get() = sqrt(x * x + y * y + z * z)
        infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    }

    private var baseline: V3? = null
    private var lastQuietMs = 0L

    private var windowStartMs = -1L
    private var peakG = 0.0
    private var rotationRad = 0.0
    private val tail = ArrayDeque<Pair<Long, V3>>()
    private var cooldownUntilMs = 0L
    private var lastGyroMs = 0L

    val isObserving: Boolean get() = windowStartMs >= 0

    fun onAccel(tMs: Long, x: Double, y: Double, z: Double): CrashCandidate? {
        val v = V3(x, y, z)
        val g = v.norm / G
        if (!isObserving) {
            if (g >= config.impactG && tMs >= cooldownUntilMs) {
                windowStartMs = tMs; peakG = g; rotationRad = 0.0
                tail.clear(); tail.addLast(tMs to v)
            } else if (g in config.quietBandG) {
                updateBaseline(tMs, v)
            }
            return null
        }
        if (g > peakG) peakG = g
        tail.addLast(tMs to v)
        while (tail.isNotEmpty() && tMs - tail.first().first > config.settleMs) tail.removeFirst()
        return finishIfDue(tMs)
    }

    fun onGyro(tMs: Long, x: Double, y: Double, z: Double) {
        if (isObserving && lastGyroMs > 0) {
            val dt = min(tMs - lastGyroMs, 100L).coerceAtLeast(0)
            rotationRad += sqrt(x * x + y * y + z * z) * dt / 1000.0
        }
        lastGyroMs = tMs
    }

    /** Lets a window close on time even if the accelerometer stalls. */
    fun poll(nowMs: Long): CrashCandidate? = if (isObserving) finishIfDue(nowMs) else null

    fun reset() {
        baseline = null; windowStartMs = -1; tail.clear(); cooldownUntilMs = 0; lastGyroMs = 0
    }

    private fun finishIfDue(nowMs: Long): CrashCandidate? {
        if (nowMs - windowStartMs < config.observeWindowMs) return null
        val start = windowStartMs
        windowStartMs = -1

        val base = baseline
        val tilt = if (base != null && tail.isNotEmpty()) {
            val n = tail.size
            val end = V3(tail.sumOf { it.second.x } / n, tail.sumOf { it.second.y } / n, tail.sumOf { it.second.z } / n)
            val denom = base.norm * end.norm
            if (denom < 1e-9) null else Math.toDegrees(acos((base dot end).div(denom).coerceIn(-1.0, 1.0)))
        } else null
        tail.clear()

        val rotated = rotationRad >= config.minRotationRad || (tilt != null && tilt >= config.minTiltDeg)
        cooldownUntilMs = nowMs + if (rotated) config.cooldownMs else COOLDOWN_AFTER_REJECT_MS
        return if (rotated) CrashCandidate(start, peakG, rotationRad, tilt) else null
    }

    private fun updateBaseline(tMs: Long, v: V3) {
        val b = baseline
        if (b == null) {
            baseline = v
        } else {
            val dt = (tMs - lastQuietMs).coerceIn(1, 1_000).toDouble()
            val a = dt / (config.baselineTauMs + dt)
            baseline = V3(b.x + a * (v.x - b.x), b.y + a * (v.y - b.y), b.z + a * (v.z - b.z))
        }
        lastQuietMs = tMs
    }

    companion object {
        const val G = 9.80665
        private const val COOLDOWN_AFTER_REJECT_MS = 500L
    }
}
