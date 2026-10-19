package app.tatertots.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Rules-based predictor ("rules-v1").
 *
 * Backtesting George's data (Oct 2026) showed his wake windows are very stable
 * (~80 min), and the median of his recent windows predicted nap starts as well
 * as ChatGPT did. Nap length is much noisier, so wake predictions use a wider,
 * honest range.
 */
object Predictor {
    const val VERSION = "rules-v1"

    const val MIN = 60_000L
    const val DAY = 24 * 60 * MIN

    data class Params(
        /** How many recent samples feed the median. */
        val recentSamples: Int = 8,
        /** Only use samples from the last N days (patterns shift fast at this age). */
        val lookbackDays: Int = 7,
        /** Minimum samples before personal data overrides the age-based default. */
        val minSamples: Int = 3,
        /** Minimum half-width of the nap-start range, minutes. */
        val minHalfRangeMin: Double = 10.0,
    )

    data class Window(val prevEnd: Long, val nextStart: Long, val minutes: Double, val firstOfDay: Boolean)

    // ---------------------------------------------------------------- history

    /** Gaps between a completed sleep and the next nap. Gaps are ignored if a sleep is missing in between. */
    fun wakeWindows(sleeps: List<Sleep>): List<Window> {
        val s = sleeps.sortedBy { it.start }
        val out = ArrayList<Window>()
        for (i in 1 until s.size) {
            val a = s[i - 1]
            val b = s[i]
            val aEnd = a.end ?: continue
            if (b.kind != SleepKind.NAP) continue
            if (b.startEstimated) continue
            val gap = (b.start - aEnd) / MIN.toDouble()
            if (gap < 10 || gap > 240) continue // overlapping, or probably a missing log
            out += Window(aEnd, b.start, gap, firstOfDay = a.kind == SleepKind.NIGHT)
        }
        return out
    }

    /** Durations (minutes) of completed naps. */
    fun napDurations(sleeps: List<Sleep>): List<Pair<Long, Double>> =
        sleeps.filter { it.kind == SleepKind.NAP && it.end != null && !it.startEstimated }
            .mapNotNull { n -> n.durationMin?.takeIf { it in 5.0..240.0 }?.let { n.start to it } }
            .sortedBy { it.first }

    // -------------------------------------------------------------- defaults

    /** Typical wake window by age, used until there's enough personal data. Rule of thumb, not research. */
    fun defaultWakeWindowMin(ageDays: Int?): Double = when {
        ageDays == null -> 80.0
        ageDays < 42 -> 50.0
        ageDays < 84 -> 70.0
        ageDays < 112 -> 80.0
        ageDays < 168 -> 100.0
        ageDays < 252 -> 130.0
        else -> 160.0
    }

    const val DEFAULT_NAP_MIN = 40.0

    // ------------------------------------------------------------ prediction

    /**
     * Predict the next event given everything logged so far.
     * Returns null at night (we don't predict overnight wakes) or when there's nothing to go on.
     */
    fun predict(sleeps: List<Sleep>, now: Long, ageDays: Int?, p: Params = Params()): Prediction? {
        val sorted = sleeps.sortedBy { it.start }
        val last = sorted.lastOrNull() ?: return null
        return if (last.ongoing) {
            if (last.kind == SleepKind.NIGHT) null
            else predictWake(sorted.dropLast(1), last.start, now, p)
        } else {
            val lastWake = last.end ?: return null
            predictNapStart(sorted, lastWake, last.kind == SleepKind.NIGHT, now, ageDays, p)
        }
    }

    fun predictNapStart(
        history: List<Sleep>, lastWake: Long, firstOfDay: Boolean, now: Long, ageDays: Int?, p: Params = Params(),
    ): Prediction {
        val since = now - p.lookbackDays * DAY
        val all = wakeWindows(history).filter { it.nextStart in since..lastWake }
        val first = all.filter { it.firstOfDay }
        val later = all.filter { !it.firstOfDay }
        val (pool, label) = when {
            firstOfDay && first.size >= p.minSamples -> first to "his last morning wake windows"
            !firstOfDay && later.size >= p.minSamples -> later to "his last wake windows"
            else -> all to "his last wake windows"
        }
        val sample = pool.takeLast(p.recentSamples).map { it.minutes }
        return if (sample.size >= p.minSamples) {
            val med = median(sample)
            val half = max(p.minHalfRangeMin, (quantile(sample, 0.75) - quantile(sample, 0.25)) / 2)
            Prediction(
                Target.NAP_START, lastWake + med.min(), lastWake + (med - half).min(), lastWake + (med + half).min(),
                "Median of ${label} (${sample.size}): ${med.roundToLong()} min", sample.size, firstOfDay,
            )
        } else {
            val d = defaultWakeWindowMin(ageDays)
            Prediction(
                Target.NAP_START, lastWake + d.min(), lastWake + (d - 15).min(), lastWake + (d + 15).min(),
                "Age-based default: ${d.roundToLong()} min (not enough logs yet)", sample.size, firstOfDay,
            )
        }
    }

    fun predictWake(history: List<Sleep>, napStart: Long, now: Long, p: Params = Params()): Prediction {
        val since = now - p.lookbackDays * DAY
        val sample = napDurations(history).filter { it.first in since until napStart }
            .takeLast(p.recentSamples + 2).map { it.second }
        return if (sample.size >= p.minSamples) {
            val med = median(sample)
            val lo = minOf(quantile(sample, 0.25), med - 5)
            val hi = maxOf(quantile(sample, 0.75), med + 5)
            Prediction(
                Target.WAKE, napStart + med.min(), napStart + lo.min(), napStart + hi.min(),
                "Median of his last ${sample.size} naps: ${med.roundToLong()} min", sample.size,
            )
        } else {
            Prediction(
                Target.WAKE, napStart + DEFAULT_NAP_MIN.min(), napStart + 25.0.min(), napStart + 60.0.min(),
                "Default nap length (not enough logs yet)", sample.size,
            )
        }
    }

    // -------------------------------------------------------------- backtest

    data class Accuracy(val n: Int, val meanAbsErrMin: Double, val within15: Double, val inRange: Double)

    /**
     * Replays history: for every past event, what would the model have predicted using only earlier data?
     * Recomputes automatically when logs are edited.
     */
    fun backtest(sleeps: List<Sleep>, ageDaysAt: (Long) -> Int?, since: Long, p: Params = Params()): Map<Target, Accuracy> {
        val s = sleeps.sortedBy { it.start }
        val napErr = ArrayList<Pair<Double, Boolean>>()
        val wakeErr = ArrayList<Pair<Double, Boolean>>()
        for (i in 1 until s.size) {
            val a = s[i - 1]
            val b = s[i]
            if (b.start < since || b.kind != SleepKind.NAP || b.startEstimated) continue
            val aEnd = a.end
            if (aEnd != null && (b.start - aEnd) in (10 * MIN)..(240 * MIN)) {
                val pr = predictNapStart(s.subList(0, i), aEnd, a.kind == SleepKind.NIGHT, aEnd, ageDaysAt(aEnd), p)
                napErr += (b.start - pr.at) / MIN.toDouble() to (b.start in pr.lower..pr.upper)
            }
            val bEnd = b.end
            if (bEnd != null) {
                val pr = predictWake(s.subList(0, i), b.start, b.start, p)
                wakeErr += (bEnd - pr.at) / MIN.toDouble() to (bEnd in pr.lower..pr.upper)
            }
        }
        fun acc(e: List<Pair<Double, Boolean>>) = Accuracy(
            e.size,
            if (e.isEmpty()) 0.0 else e.map { abs(it.first) }.average(),
            if (e.isEmpty()) 0.0 else e.count { abs(it.first) <= 15 } / e.size.toDouble(),
            if (e.isEmpty()) 0.0 else e.count { it.second } / e.size.toDouble(),
        )
        return mapOf(Target.NAP_START to acc(napErr), Target.WAKE to acc(wakeErr))
    }

    // ----------------------------------------------------------------- maths

    private fun Double.min(): Long = (this * MIN).roundToLong()

    fun median(xs: List<Double>): Double = quantile(xs, 0.5)

    /** Linear-interpolated quantile (same as numpy's default). */
    fun quantile(xs: List<Double>, q: Double): Double {
        require(xs.isNotEmpty())
        val s = xs.sorted()
        val pos = (s.size - 1) * q
        val lo = pos.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }
}
