package app.tatertots.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTest {
    private val M = Predictor.MIN
    private val t0 = 1_760_000_000_000L

    /** Naps of [napMin] separated by [windowMin] wake windows. */
    private fun day(windows: List<Int>, napMin: Int = 40): List<Sleep> {
        var t = t0
        return windows.mapIndexed { i, w ->
            t += w * M
            val s = Sleep("n$i", t, t + napMin * M)
            t += napMin * M
            s
        }
    }

    @Test fun nothingLogged_returnsNull() {
        assertNull(Predictor.predict(emptyList(), t0, 90))
    }

    @Test fun fewLogs_fallsBackToAgeDefault() {
        val one = listOf(Sleep("a", t0, t0 + 30 * M))
        val p = Predictor.predict(one, t0 + 60 * M, 91)!!
        assertEquals(Target.NAP_START, p.target)
        assertEquals(t0 + 30 * M + 80 * M, p.at)
    }

    @Test fun usesMedianOfRecentWindows() {
        val naps = day(listOf(80, 75, 85, 80, 200, 80))
        val last = naps.last()
        val p = Predictor.predict(naps, last.end!! + 5 * M, 91)!!
        // windows after the first nap: 75, 85, 80, 200, 80 -> median 80
        assertEquals(last.end!! + 80 * M, p.at)
        assertTrue(p.lower < p.at && p.at < p.upper)
    }

    @Test fun ongoingNap_predictsWake() {
        val naps = day(listOf(80, 80, 80, 80), napMin = 35)
        val start = naps.last().end!! + 80 * M
        val p = Predictor.predict(naps + Sleep("now", start, null), start + 5 * M, 91)!!
        assertEquals(Target.WAKE, p.target)
        assertEquals(start + 35 * M, p.at)
    }

    @Test fun ongoingNight_noPrediction() {
        val night = Sleep("night", t0, null, kind = SleepKind.NIGHT)
        assertNull(Predictor.predict(listOf(night), t0 + 60 * M, 91))
    }

    @Test fun unknownEnd_isNotOngoing() {
        val s = Sleep("old", t0, null, endUnknown = true)
        assertTrue(!s.ongoing)
    }

    @Test fun quantileMatchesNumpy() {
        assertEquals(2.5, Predictor.median(listOf(1.0, 2.0, 3.0, 4.0)), 1e-9)
        assertEquals(1.75, Predictor.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.25), 1e-9)
    }
}
