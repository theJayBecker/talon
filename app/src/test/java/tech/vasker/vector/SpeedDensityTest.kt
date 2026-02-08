package tech.vasker.vector

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.vasker.vector.obd.SpeedDensity

/**
 * Speed-density fuel flow: known idle sample should yield gph in a reasonable range.
 * MAP=39 kPa, RPM=966, IAT=16°C, TPS=12.5% (idle) → ballpark 0.1–0.5 gph.
 */
class SpeedDensityTest {

    @Test
    fun idleSample_producesGphInExpectedRange() {
        val result = SpeedDensity.computeFuelFlow(
            mapKpa = 39,
            iatC = 16.0,
            rpm = 966,
            tpsPct = 12.5f,
        )
        assertNotNull(result)
        val (galPerSec, gph) = result!!
        assertTrue("gph should be positive", gph > 0)
        assertTrue("idle gph should be in ballpark 0.1–0.5 (got $gph)", gph in 0.1..0.5)
        assertTrue("galPerSec should be consistent with gph", galPerSec > 0 && galPerSec < gph / 3600.0 * 1.1)
    }
}
