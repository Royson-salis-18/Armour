package com.armor.core

import com.armor.core.crash.CrashClassifier
import com.armor.core.crash.CrashFeatures
import com.armor.core.crash.CrashFilter
import com.armor.core.crash.CrashVerdict
import kotlin.test.Test
import kotlin.test.assertIs

class CrashFilterTest {
    private fun f(pre: Double?, post: Double?) = CrashFeatures(peakG = 6.0, rotationRad = 3.0, tiltDeg = 80.0, preImpactSpeedMps = pre, postImpactSpeedMps = post)
    private val alwaysFalseAlarm = CrashClassifier { 0.99 }

    @Test fun `bike that stopped dead is a crash`() {
        assertIs<CrashVerdict.Confirm>(CrashFilter().assess(f(pre = 20.0, post = 2.0)))
    }

    @Test fun `bike that carried on at speed is dismissed`() {
        assertIs<CrashVerdict.Dismiss>(CrashFilter().assess(f(pre = 20.0, post = 19.0)))
    }

    @Test fun `a classifier can never override a stopped bike`() {
        assertIs<CrashVerdict.Confirm>(CrashFilter(classifier = alwaysFalseAlarm).assess(f(pre = 20.0, post = 0.0)))
    }

    @Test fun `a classifier cannot dismiss when GPS context is missing`() {
        val filter = CrashFilter(classifier = alwaysFalseAlarm)
        assertIs<CrashVerdict.Confirm>(filter.assess(f(pre = null, post = null)))
        assertIs<CrashVerdict.Confirm>(filter.assess(f(pre = 20.0, post = null)))
    }

    @Test fun `ambiguous candidates are confirmed unless a classifier is very sure`() {
        assertIs<CrashVerdict.Confirm>(CrashFilter().assess(f(pre = 0.0, post = 0.0)))
        assertIs<CrashVerdict.Confirm>(CrashFilter(classifier = { 0.5 }).assess(f(pre = 0.0, post = 0.0)))
        assertIs<CrashVerdict.Dismiss>(CrashFilter(classifier = alwaysFalseAlarm).assess(f(pre = 0.0, post = 0.0)))
    }

    @Test fun `partial slowdown in the middle band is ambiguous`() {
        assertIs<CrashVerdict.Confirm>(CrashFilter().assess(f(pre = 20.0, post = 12.0)))
        assertIs<CrashVerdict.Dismiss>(CrashFilter(classifier = alwaysFalseAlarm).assess(f(pre = 20.0, post = 12.0)))
    }
}
