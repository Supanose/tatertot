package app.tatertots.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

fun zone(): ZoneId = ZoneId.systemDefault()

fun fmtTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(timeFmt)

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

fun fmtDay(d: LocalDate): String = when (d) {
    LocalDate.now(zone()) -> "Today"
    LocalDate.now(zone()).minusDays(1) -> "Yesterday"
    else -> d.format(dayFmt)
}

/** "1h 05m" / "42m". */
fun fmtDur(minutes: Long): String {
    val m = abs(minutes)
    return if (m >= 60) "${m / 60}h ${"%02d".format(m % 60)}m" else "${m}m"
}

fun fmtDurMs(ms: Long): String = fmtDur(ms / 60_000)
