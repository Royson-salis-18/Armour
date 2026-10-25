package com.armor.core.crash

/** Everything the filter may look at. GPS speeds are null when no fix was available. */
data class CrashFeatures(
    val peakG: Double,
    val rotationRad: Double,
    val tiltDeg: Double?,
    val preImpactSpeedMps: Double?,
    val postImpactSpeedMps: Double?,
)

sealed interface CrashVerdict {
    val reason: String

    /** Treat as a real crash: the rider gets the cancellable SOS countdown. */
    data class Confirm(override val reason: String) : CrashVerdict

    /** Drop as a false alarm. Only ever produced by an explicit rule below. */
    data class Dismiss(override val reason: String) : CrashVerdict
}

/**
 * The "assistive AI" seam: anything that can estimate how likely a candidate is a false alarm
 * (for example a TensorFlow Lite model fed [CrashFeatures]). It can only ever *reduce* alerts,
 * and only in the ambiguous middle ground defined by [CrashFilter]; it never raises one.
 */
fun interface CrashClassifier {
    fun falseAlarmProbability(features: CrashFeatures): Double
}

data class CrashFilterConfig(
    /** Below this pre-impact speed the bike counts as stopped/slow and speed evidence is not decisive. */
    val minRideSpeedMps: Double = 5.0,
    /** Post-impact speed at or below this share of pre-impact speed = the bike stopped dead: always a crash. */
    val collapseRatio: Double = 0.5,
    /** Post-impact speed at or above this share = the rider just kept riding: pothole, phone knock. */
    val continueRatio: Double = 0.7,
    /** Classifier confidence required to dismiss an ambiguous candidate. */
    val dismissProbability: Double = 0.9,
)

/**
 * Decides whether a [CrashCandidate]'s features justify starting the SOS countdown, using fixed rules
 * in a fixed order. The classifier is consulted last and only when the speed evidence is inconclusive,
 * so a model can never talk the system out of a crash where the vehicle visibly stopped.
 */
class CrashFilter(
    private val config: CrashFilterConfig = CrashFilterConfig(),
    private val classifier: CrashClassifier? = null,
) {
    fun assess(f: CrashFeatures): CrashVerdict {
        val pre = f.preImpactSpeedMps ?: return CrashVerdict.Confirm("no GPS speed before impact; cannot rule out a crash")
        if (pre >= config.minRideSpeedMps) {
            val post = f.postImpactSpeedMps
                ?: return CrashVerdict.Confirm("no GPS speed after impact; cannot rule out a crash")
            if (post <= config.collapseRatio * pre) {
                return CrashVerdict.Confirm("speed fell from %.0f to %.0f km/h after impact".format(pre * 3.6, post * 3.6))
            }
            if (post >= config.continueRatio * pre) {
                return CrashVerdict.Dismiss("rider kept riding: %.0f km/h before, %.0f km/h after impact".format(pre * 3.6, post * 3.6))
            }
        }
        val p = classifier?.falseAlarmProbability(f)
        if (p != null && p >= config.dismissProbability) {
            return CrashVerdict.Dismiss("classifier rates false-alarm probability %.2f on an ambiguous candidate".format(p))
        }
        return CrashVerdict.Confirm("ambiguous candidate; defaulting to the cancellable countdown")
    }
}
