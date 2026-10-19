package app.tatertots.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tatertots.data.AppState
import app.tatertots.engine.Feed
import app.tatertots.engine.Sleep
import app.tatertots.engine.SleepKind

@Composable
fun HistoryScreen(
    state: AppState, now: Long, modifier: Modifier,
    onEditSleep: (Sleep?) -> Unit, onEditFeed: (Feed?) -> Unit,
) {
    val byDay = (state.sleeps.map { Entry.S(it) } + state.feeds.map { Entry.F(it) })
        .groupBy { localDate(it.time) }
        .toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        if (byDay.isEmpty()) item { Text("Nothing logged yet.") }
        byDay.forEach { (day, entries) ->
            val naps = entries.filterIsInstance<Entry.S>().map { it.sleep }
                .filter { it.kind == SleepKind.NAP && !it.startEstimated }
            val napMin = naps.sumOf { ((it.end ?: if (it.ongoing) now else it.start) - it.start) / 60_000 }
            val feeds = entries.count { it is Entry.F }
            item(key = "h$day") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(fmtDay(day), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${naps.size} naps · ${fmtDur(napMin)} day sleep" + if (feeds > 0) " · $feeds feeds" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
            }
            items(entries.sortedByDescending { it.time }, key = { e -> if (e is Entry.S) "s" + e.sleep.id else "f" + (e as Entry.F).feed.id }) { e ->
                EntryRow(e, now, onEditSleep, onEditFeed)
            }
        }
    }
}
