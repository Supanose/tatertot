package app.tatertots.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tatertots.data.AppState
import app.tatertots.engine.Predictor
import app.tatertots.engine.SleepKind
import app.tatertots.engine.Target
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(state: AppState, now: Long, modifier: Modifier) {
    val name = state.settings.babyName
    val week = now - 7 * Predictor.DAY
    val windows = remember(state.sleeps) { Predictor.wakeWindows(state.sleeps) }.filter { it.nextStart >= week }
    val durations = remember(state.sleeps) { Predictor.napDurations(state.sleeps) }.filter { it.first >= week }.map { it.second }
    val accuracy = remember(state.sleeps) { Predictor.backtest(state.sleeps, { state.ageDays(it) }, week) }

    // Day sleep per day, last 7 days
    val today = LocalDate.now(zone())
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dayTotals = days.map { d ->
        d to state.sleeps.filter { it.kind == SleepKind.NAP && !it.startEstimated && localDate(it.start) == d }
            .sumOf { ((it.end ?: if (it.ongoing) now else it.start) - it.start) / 60_000 }
    }

    // Night wakes: night sleeps grouped by the evening they started on (00:00–12:00 counts as the previous evening)
    val nights = state.sleeps.filter { it.kind == SleepKind.NIGHT }
        .groupBy { localDate(it.start - 12 * 60 * 60_000L) }
        .toSortedMap(compareByDescending { it }).entries.take(5)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

        item {
            BigCard {
                Text("Wake windows · last 7 days", style = MaterialTheme.typography.titleMedium)
                if (windows.size < 3) Text("Need a few more naps logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val m = windows.map { it.minutes }
                    Stat("Typical", "${Predictor.median(m).roundToInt()} min")
                    Stat("Middle half", "${Predictor.quantile(m, .25).roundToInt()}–${Predictor.quantile(m, .75).roundToInt()} min")
                    Stat("Shortest / longest", "${m.min().roundToInt()} / ${m.max().roundToInt()} min")
                    val first = windows.filter { it.firstOfDay }.map { it.minutes }
                    if (first.isNotEmpty()) Stat("First window of the day", "${Predictor.median(first).roundToInt()} min (${first.size})")
                }
            }
        }

        item {
            BigCard {
                Text("Naps · last 7 days", style = MaterialTheme.typography.titleMedium)
                if (durations.isEmpty()) Text("No completed naps yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Stat("Typical length", "${Predictor.median(durations).roundToInt()} min")
                    Stat("Range", "${durations.min().roundToInt()}–${durations.max().roundToInt()} min")
                }
                Text("Day sleep per day", modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Bars(dayTotals.map { (d, v) -> d.dayOfWeek.name.take(1) to v })
            }
        }

        item {
            BigCard {
                Text("How good are the predictions?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Replays the last 7 days: what Tater would have predicted using only earlier logs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                )
                listOf(Target.NAP_START to "Nap start", Target.WAKE to "Wake-up").forEach { (t, label) ->
                    val a = accuracy[t]
                    if (a == null || a.n == 0) Stat(label, "not enough data")
                    else Stat(label, "±${a.meanAbsErrMin.roundToInt()} min avg · ${(a.within15 * 100).roundToInt()}% within 15 min")
                }
            }
        }

        if (nights.isNotEmpty()) item {
            BigCard {
                Text("Nights", style = MaterialTheme.typography.titleMedium)
                nights.forEach { (evening, sleeps) ->
                    val complete = sleeps.all { it.end != null }
                    val longest = sleeps.mapNotNull { it.durationMin }.maxOrNull()
                    Stat(
                        fmtDay(evening),
                        if (!complete) "partly logged"
                        else "${sleeps.size - 1} wakes · longest ${fmtDur(longest?.toLong() ?: 0)}",
                    )
                }
            }
        }

        item {
            Text(
                "Tater spots patterns – he isn't a doctor. Feed $name on demand, follow safe sleep advice " +
                    "(The Lullaby Trust), and trust your instincts over any prediction.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Bars(values: List<Pair<String, Long>>) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60L)
    Row(
        Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { (label, v) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(fmtDur(v), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .width(18.dp)
                        .height((70f * v / max).dp.coerceAtLeast(2.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                )
                Text(label, fontSize = 11.sp)
            }
        }
    }
}
