package app.tatertots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tatertots.data.AppState
import app.tatertots.data.Store
import app.tatertots.engine.Feed
import app.tatertots.engine.Sleep
import app.tatertots.engine.SleepKind
import app.tatertots.engine.Target
import java.time.LocalDate

/** A row in a timeline: either a sleep or a feed. */
sealed interface Entry {
    val time: Long
    data class S(val sleep: Sleep) : Entry { override val time get() = sleep.start }
    data class F(val feed: Feed) : Entry { override val time get() = feed.time }
}

@Composable
fun TodayScreen(
    state: AppState, now: Long, modifier: Modifier,
    onEditSleep: (Sleep?) -> Unit, onEditFeed: (Feed?) -> Unit,
) {
    val ctx = LocalContext.current
    val name = state.settings.babyName
    val cur = state.current
    val p = state.prediction(now)

    val today = LocalDate.now(zone())
    val startOfDay = today.atStartOfDay(zone()).toInstant().toEpochMilli()
    val entries: List<Entry> = (
        state.sleeps.filter { it.start >= startOfDay || (it.end ?: if (it.ongoing) now else it.start) >= startOfDay }.map { Entry.S(it) } +
            state.feeds.filter { it.time >= startOfDay }.map { Entry.F(it) }
        ).sortedByDescending { it.time }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header + status with Tater
        item {
            Text("Tater Tots", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            BigCard {
                val since = cur?.start ?: state.lastWake
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tater(state.mood(now), Modifier.size(112.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when {
                                cur?.kind == SleepKind.NIGHT -> "$name is asleep for the night"
                                cur != null -> "$name is napping"
                                else -> "$name is awake"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            since?.let { fmtDurMs(now - it) } ?: "–",
                            fontSize = 40.sp, fontWeight = FontWeight.Bold,
                        )
                        if (since != null) Text("since ${fmtTime(since)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- prediction
        if (p != null) item {
            BigCard {
                val isNap = p.target == Target.NAP_START
                Text(if (!isNap) "Likely to wake" else if (p.firstOfDay) "First nap" else "Next nap", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("~${fmtTime(p.at)}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Likely ${fmtTime(p.lower)}–${fmtTime(p.upper)}")
                if (isNap) {
                    val settle = state.settleAt(p)!!
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            settle > now -> "Start settling at ${fmtTime(settle)} (in ${fmtDurMs(settle - now)})"
                            now <= p.upper -> "Start settling now"
                            else -> "Past $name's usual window – watch for tired cues"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(p.basis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        // ---- actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cur != null) {
                    BigButton("Woke up") { Store.wake(ctx) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("Asleep", Modifier.weight(2f)) { Store.startSleep(ctx) }
                        OutlinedButton(
                            onClick = { Store.startSleep(ctx, kind = SleepKind.NIGHT) },
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) { Text("Bedtime") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Store.logFeed(ctx) }, modifier = Modifier.weight(1f)) { Text("Log feed now") }
                    OutlinedButton(onClick = { onEditSleep(null) }, modifier = Modifier.weight(1f)) { Text("Add past sleep") }
                }
                if (state.lastWake == null && cur == null && state.sleeps.isNotEmpty()) {
                    Text(
                        "Tip: log this morning's wake time (Add past sleep → Night) so the first nap can be predicted.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                }
            }
        }

        item { Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (entries.isEmpty()) item { Text("Nothing logged yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(entries, key = { e -> if (e is Entry.S) "s" + e.sleep.id else "f" + (e as Entry.F).feed.id }) { e ->
            EntryRow(e, now, onEditSleep, onEditFeed)
        }
    }
}

@Composable
fun EntryRow(e: Entry, now: Long, onEditSleep: (Sleep?) -> Unit, onEditFeed: (Feed?) -> Unit) {
    when (e) {
        is Entry.S -> {
            val s = e.sleep
            val end = when {
                s.ongoing -> "now"
                s.endUnknown -> "?"
                else -> fmtTime(s.end!!)
            }
            val dur = (s.end ?: if (s.ongoing) now else null)?.let { fmtDurMs(it - s.start) }
            val parts = listOfNotNull(
                if (s.kind == SleepKind.NIGHT) "Night" else "Nap",
                dur,
                s.location.takeIf { it.isNotBlank() },
                "approx".takeIf { s.approx || s.startEstimated },
            )
            RowCard(onClick = { onEditSleep(s) }) {
                Text("${if (s.startEstimated) "?" else fmtTime(s.start)} – $end", fontWeight = FontWeight.SemiBold)
                Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        is Entry.F -> {
            val f = e.feed
            RowCard(onClick = { onEditFeed(f) }) {
                Text(fmtTime(f.time), fontWeight = FontWeight.SemiBold)
                Text(listOf("Feed", f.type, f.detail).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BigCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(20.dp)) { content() } }
}

@Composable
fun RowCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) { Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() } }
}

@Composable
fun BigButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(),
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
