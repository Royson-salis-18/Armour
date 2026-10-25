package com.armor.core.engine

import com.armor.core.crash.CrashConfig
import com.armor.core.crash.CrashFilterConfig
import com.armor.core.group.ClusterConfig
import com.armor.core.motion.StationaryConfig
import com.armor.core.safety.SafetyConfig

/**
 * Every tunable in one place. Defaults are conservative starting points chosen for the logic to be
 * explainable; they have not been calibrated on real rides and should be tuned in the field.
 */
data class ArmorConfig(
    val cluster: ClusterConfig = ClusterConfig(),
    val stationary: StationaryConfig = StationaryConfig(),
    val crash: CrashConfig = CrashConfig(),
    val crashFilter: CrashFilterConfig = CrashFilterConfig(),
    val safety: SafetyConfig = SafetyConfig(),
    /** A peer not heard from for this long is excluded from group analysis and shown as stale. */
    val peerStaleAfterMs: Long = 60_000,
    /** After an impact, wait this long to see whether the bike actually stopped before judging it. */
    val crashAssessDelayMs: Long = 4_000,
    /** Group alerts older than this are not shown to other riders. */
    val alertMaxAgeMs: Long = 1_800_000,
) {
    /** Short timers so the whole stop → alert → SOS flow can be demonstrated in under a minute. */
    fun forDemo() = copy(
        safety = safety.copy(
            groupStationaryMs = 8_000,
            soloStationaryMs = 8_000,
            verifyTimeoutMs = 12_000,
            groupAckTimeoutMs = 15_000,
            crashCountdownMs = 12_000,
            responseCooldownMs = 20_000,
            resumeMovingMs = 4_000,
        ),
        crashAssessDelayMs = 2_500,
    )

    fun withImpactThreshold(g: Double) = copy(crash = crash.copy(impactG = g))
}
