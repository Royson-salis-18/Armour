package com.armor.core.group

import com.armor.core.geo.ConvexHull
import com.armor.core.geo.Geo
import com.armor.core.geo.LocalFrame
import com.armor.core.model.LatLon
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.RiderReport

/**
 * Riders are "together" when chained by hops no longer than a link distance that grows with
 * group speed (a group at 90 km/h naturally spreads over more road than one at a standstill):
 * `link = clamp(baseLinkM + headwayS * medianSpeed, minLinkM, maxLinkM)`.
 */
data class ClusterConfig(
    val baseLinkM: Double = 100.0,
    val headwayS: Double = 12.0,
    val minLinkM: Double = 150.0,
    val maxLinkM: Double = 1_500.0,
)

data class Cluster(
    val memberIds: List<String>,
    val centroid: LatLon,
    /** Convex hull of the members, for drawing the group boundary. One or two points for tiny clusters. */
    val hull: List<LatLon>,
)

data class ClusterResult(
    val clusters: List<Cluster>,
    /** Index into [clusters] of the unique largest cluster, or null if none is strictly largest. */
    val mainClusterIndex: Int?,
    val linkDistanceM: Double,
    val freshIds: Set<String>,
) {
    private val mainMembers: Set<String> =
        mainClusterIndex?.let { clusters[it].memberIds.toSet() } ?: emptySet()

    fun isOutlier(id: String): Boolean = id in freshIds && id !in mainMembers

    /**
     * Judges [selfId] against the group. Needs at least one other rider with fresh data;
     * otherwise the answer is [OutlierAssessment.UNKNOWN] rather than a guess.
     *
     * If no cluster is strictly the largest (e.g. two riders who have drifted apart) nobody is
     * "main", so everybody counts as separated — the conservative reading for a safety check.
     */
    fun assess(selfId: String): OutlierAssessment {
        if (selfId !in freshIds) return OutlierAssessment.UNKNOWN
        if (freshIds.size < 2) return OutlierAssessment.UNKNOWN
        return if (isOutlier(selfId)) OutlierAssessment.OUTLIER else OutlierAssessment.IN_CLUSTER
    }
}

class ClusterAnalyzer(private val config: ClusterConfig = ClusterConfig()) {

    fun analyze(reports: List<RiderReport>, nowMs: Long, staleAfterMs: Long): ClusterResult {
        val fresh = reports
            .filter { nowMs - it.fix.timeMs <= staleAfterMs }
            .sortedBy { it.id }
        if (fresh.isEmpty()) return ClusterResult(emptyList(), null, config.minLinkM, emptySet())

        val speeds = fresh.map { it.fix.speedMps }.sorted()
        val median = if (speeds.size % 2 == 1) speeds[speeds.size / 2] else (speeds[speeds.size / 2 - 1] + speeds[speeds.size / 2]) / 2
        val link = (config.baseLinkM + config.headwayS * median).coerceIn(config.minLinkM, config.maxLinkM)

        // Single-linkage clustering via union-find; n is a riding group, so O(n^2) is fine.
        val parent = IntArray(fresh.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var c = i
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        for (i in fresh.indices) for (j in i + 1 until fresh.size) {
            if (Geo.distanceM(fresh[i].fix.position, fresh[j].fix.position) <= link) parent[find(i)] = find(j)
        }
        val groups = fresh.indices.groupBy { find(it) }.values
            .map { idxs -> idxs.map { fresh[it] } }
            .sortedWith(compareByDescending<List<RiderReport>> { it.size }.thenBy { it.first().id })

        val clusters = groups.map { members ->
            val frame = LocalFrame(members.first().fix.position)
            val pts = members.map { frame.toPoint(it.fix.position) }
            val cx = pts.sumOf { it.x } / pts.size
            val cy = pts.sumOf { it.y } / pts.size
            Cluster(
                memberIds = members.map { it.id },
                centroid = frame.toLatLon(com.armor.core.geo.Point(cx, cy)),
                hull = ConvexHull.of(pts).map { frame.toLatLon(it) },
            )
        }
        val main = when {
            clusters.isEmpty() -> null
            clusters.size == 1 -> 0
            clusters[0].memberIds.size > clusters[1].memberIds.size -> 0
            else -> null
        }
        return ClusterResult(clusters, main, link, fresh.map { it.id }.toSet())
    }
}
