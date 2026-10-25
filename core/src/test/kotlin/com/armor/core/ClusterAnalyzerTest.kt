package com.armor.core

import com.armor.core.group.ClusterAnalyzer
import com.armor.core.model.OutlierAssessment
import com.armor.core.support.at
import com.armor.core.support.report
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClusterAnalyzerTest {
    private val analyzer = ClusterAnalyzer()
    private val now = 1_000_000L
    private val stale = 60_000L

    @Test fun `a tight group is one cluster with no outliers`() {
        val r = analyzer.analyze(
            listOf(
                report("a", now, at(0.0, 0.0), 20.0), report("b", now, at(0.0, -40.0), 20.0),
                report("c", now, at(3.0, -90.0), 20.0), report("d", now, at(-3.0, -130.0), 20.0),
            ),
            now, stale,
        )
        assertEquals(1, r.clusters.size)
        assertEquals(4, r.clusters[0].memberIds.size)
        assertEquals(0, r.mainClusterIndex)
        assertTrue(listOf("a", "b", "c", "d").none { r.isOutlier(it) })
    }

    @Test fun `a rider far behind is the outlier`() {
        val r = analyzer.analyze(
            listOf(
                report("a", now, at(0.0, 0.0), 20.0), report("b", now, at(0.0, -50.0), 20.0),
                report("c", now, at(0.0, -100.0), 20.0), report("lost", now, at(0.0, -3_000.0), 0.0),
            ),
            now, stale,
        )
        assertEquals(2, r.clusters.size)
        assertTrue(r.isOutlier("lost"))
        assertFalse(r.isOutlier("a"))
        assertEquals(OutlierAssessment.OUTLIER, r.assess("lost"))
        assertEquals(OutlierAssessment.IN_CLUSTER, r.assess("b"))
    }

    @Test fun `chained riders form one cluster even if the ends are far apart`() {
        val r = analyzer.analyze((0 until 6).map { report("r$it", now, at(0.0, -it * 140.0), 0.0) }, now, stale)
        assertEquals(1, r.clusters.size)
    }

    @Test fun `link distance grows with group speed`() {
        val parked = analyzer.analyze(listOf(report("a", now, at(0.0, 0.0), 0.0), report("b", now, at(0.0, -380.0), 0.0)), now, stale)
        val cruising = analyzer.analyze(listOf(report("a", now, at(0.0, 0.0), 25.0), report("b", now, at(0.0, -380.0), 25.0)), now, stale)
        assertEquals(150.0, parked.linkDistanceM, 1e-9)
        assertEquals(2, parked.clusters.size)
        assertEquals(400.0, cruising.linkDistanceM, 1e-9)
        assertEquals(1, cruising.clusters.size)
    }

    @Test fun `link distance is clamped`() {
        val fast = analyzer.analyze(listOf(report("a", now, at(0.0, 0.0), 120.0), report("b", now, at(0.0, -10.0), 120.0)), now, stale)
        assertEquals(1_500.0, fast.linkDistanceM, 1e-9)
    }

    @Test fun `with no strictly largest cluster nobody is main so everyone counts as separated`() {
        val r = analyzer.analyze(listOf(report("a", now, at(0.0, 0.0)), report("b", now, at(0.0, -2_000.0))), now, stale)
        assertNull(r.mainClusterIndex)
        assertEquals(OutlierAssessment.OUTLIER, r.assess("a"))
        assertEquals(OutlierAssessment.OUTLIER, r.assess("b"))
    }

    @Test fun `stale riders are excluded and a rider with no fresh peers is UNKNOWN`() {
        val r = analyzer.analyze(
            listOf(report("me", now, at(0.0, 0.0)), report("old", now - 120_000, at(0.0, -20.0))),
            now, stale,
        )
        assertEquals(setOf("me"), r.freshIds)
        assertEquals(OutlierAssessment.UNKNOWN, r.assess("me"))
        assertEquals(OutlierAssessment.UNKNOWN, r.assess("old"))
    }

    @Test fun `empty input yields no clusters`() {
        val r = analyzer.analyze(emptyList(), now, stale)
        assertTrue(r.clusters.isEmpty())
        assertNull(r.mainClusterIndex)
    }

    @Test fun `result does not depend on input order`() {
        val reports = listOf(
            report("c", now, at(0.0, -100.0), 20.0), report("a", now, at(0.0, 0.0), 20.0),
            report("b", now, at(0.0, -50.0), 20.0), report("z", now, at(0.0, -5_000.0)),
        )
        val x = analyzer.analyze(reports, now, stale)
        val y = analyzer.analyze(reports.reversed(), now, stale)
        assertEquals(x.clusters.map { it.memberIds }, y.clusters.map { it.memberIds })
        assertEquals(x.mainClusterIndex, y.mainClusterIndex)
    }

    @Test fun `cluster hull has one vertex per corner of a square group`() {
        val r = analyzer.analyze(
            listOf(
                report("a", now, at(0.0, 0.0), 10.0), report("b", now, at(60.0, 0.0), 10.0),
                report("c", now, at(60.0, 60.0), 10.0), report("d", now, at(0.0, 60.0), 10.0), report("e", now, at(30.0, 30.0), 10.0),
            ),
            now, stale,
        )
        assertEquals(4, r.clusters[0].hull.size)
    }
}
