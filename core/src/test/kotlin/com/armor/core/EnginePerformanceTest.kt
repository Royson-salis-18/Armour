package com.armor.core

import com.armor.core.support.RideSim
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the per-second tick: a big group must still be analysed in a few milliseconds on a laptop-class JVM. */
class EnginePerformanceTest {
    @Test fun `tick with 150 riders stays fast`() {
        val sim = RideSim()
        repeat(150) { sim.addPeer("p$it", it * 12.0, 20.0) }
        sim.run(20) // warm up the JIT
        val times = (1..50).map { measureNanoTime { sim.run(1) } / 1e6 }
        val avg = times.average()
        val worst = times.max()
        println("tick avg=%.2f ms worst=%.2f ms (150 riders)".format(avg, worst))
        assertTrue(avg < 25.0, "average tick $avg ms")
    }
}
