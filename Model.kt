package app.tatertots.engine

/**
 * Core data types. Pure Kotlin (no Android imports) so the prediction engine
 * can be unit-tested on any JVM. All times are epoch milliseconds.
 */

enum class SleepKind { NAP, NIGHT }

data class Sleep(
    val id: String,
    val start: Long,
    /** Null while the sleep is ongoing, or when the end was never recorded (see [endUnknown]). */
    val end: Long?,
    /** True when the end was never logged (imported/history). Such a sleep is NOT treated as ongoing. */
    val endUnknown: Boolean = false,
    val kind: SleepKind = SleepKind.NAP,
    val location: String = "",
    val approx: Boolean = false,
    val note: String = "",
    /** True when only the wake time was logged and [start] is a placeholder. Excluded from nap-length stats. */
    val startEstimated: Boolean = false,
) {
    val ongoing: Boolean get() = end == null && !endUnknown
    val durationMin: Double? get() = end?.let { (it - start) / 60_000.0 }
}

data class Feed(
    val id: String,
    val time: Long,
    val type: String = "Bottle",
    val detail: String = "",
    val note: String = "",
)

enum class Target { NAP_START, WAKE }

data class Prediction(
    val target: Target,
    val at: Long,
    val lower: Long,
    val upper: Long,
    /** Human-readable explanation, e.g. "median of his last 8 wake windows". */
    val basis: String,
    val sampleSize: Int,
    /** True when the next sleep is likely to be the first after a night (morning nap). */
    val firstOfDay: Boolean = false,
)
