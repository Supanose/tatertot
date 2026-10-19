package app.tatertots.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tatertots.data.AppState
import app.tatertots.data.Store
import app.tatertots.engine.Feed
import app.tatertots.engine.Sleep
import app.tatertots.engine.SleepKind
import java.time.Instant
import java.time.ZonedDateTime

private val LOCATIONS = listOf("Cot", "Pram", "Cuddles", "Car", "Sling")
private val FEED_TYPES = listOf("Bottle", "Breast", "Offered / refused")

private fun pickDateTime(ctx: Context, ms: Long, dateToo: Boolean, onPicked: (Long) -> Unit) {
    val z = Instant.ofEpochMilli(ms).atZone(zone())
    val pickTime = { base: ZonedDateTime ->
        TimePickerDialog(ctx, { _, h, m ->
            onPicked(base.withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli())
        }, base.hour, base.minute, true).show()
    }
    if (dateToo) {
        DatePickerDialog(ctx, { _, y, mo, d -> pickTime(z.withYear(y).withMonth(mo + 1).withDayOfMonth(d)) },
            z.year, z.monthValue - 1, z.dayOfMonth).show()
    } else pickTime(z)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditSleepDialog(state: AppState, sleep: Sleep?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val isNew = sleep == null
    // Only the most recent sleep can be "still asleep" (and a new one only if nothing is ongoing).
    val isLatest = if (sleep == null) !state.isAsleep else state.sleeps.maxByOrNull { it.start }?.id == sleep.id

    var start by remember { mutableLongStateOf(sleep?.start ?: (now - 40 * 60_000)) }
    var end by remember { mutableLongStateOf(sleep?.end ?: now) }
    var ongoing by remember { mutableStateOf(sleep?.ongoing ?: false) }
    var endUnknown by remember { mutableStateOf(sleep?.endUnknown ?: false) }
    var kind by remember { mutableStateOf(sleep?.kind ?: Store.classify(state.settings, start)) }
    var location by remember { mutableStateOf(sleep?.location ?: "") }
    var note by remember { mutableStateOf(sleep?.note ?: "") }
    var approx by remember { mutableStateOf(sleep?.approx ?: false) }
    var startEstimated by remember { mutableStateOf(sleep?.startEstimated ?: false) }

    // End is picked as a time; if it lands before the start, it's the next day.
    val endFixed = if (end <= start) end + 24 * 60 * 60_000L else end
    val dur = (endFixed - start) / 60_000
    val error = when {
        !ongoing && !endUnknown && dur > 16 * 60 -> "That's over 16 hours – check the times"
        ongoing && start > now -> "Start is in the future"
        ongoing && state.sleeps.any { it.id != sleep?.id && it.start > start } -> "A later sleep is logged – this one can't still be ongoing"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add sleep" else "Edit sleep") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == SleepKind.NAP, { kind = SleepKind.NAP }, label = { Text("Nap") })
                    FilterChip(kind == SleepKind.NIGHT, { kind = SleepKind.NIGHT }, label = { Text("Night") })
                }
                LabeledButton("Fell asleep", "${fmtDay(localDate(start))} ${fmtTime(start)}") {
                    pickDateTime(ctx, start, dateToo = true) { start = it; startEstimated = false }
                }
                if (!ongoing && !endUnknown) {
                    LabeledButton("Woke up", "${fmtTime(endFixed)} · ${fmtDur(dur)}") {
                        pickDateTime(ctx, endFixed, dateToo = false) { end = it }
                    }
                }
                if (isLatest) CheckRow("Still asleep", ongoing) { ongoing = it; if (it) endUnknown = false }
                if (!isNew && sleep?.endUnknown == true) CheckRow("Wake time not recorded", endUnknown) { endUnknown = it; if (it) ongoing = false }
                CheckRow("Times are approximate", approx) { approx = it }

                Text("Where", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LOCATIONS.forEach { l ->
                        FilterChip(location == l, { location = if (location == l) "" else l }, label = { Text(l) })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                val s = Sleep(
                    id = sleep?.id ?: Store.newId(),
                    start = start,
                    end = if (ongoing || endUnknown) null else endFixed,
                    endUnknown = endUnknown && !ongoing,
                    kind = kind, location = location, approx = approx, note = note.trim(),
                    startEstimated = startEstimated,
                )
                Store.upsertSleep(ctx, s)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteSleep(ctx, sleep!!.id); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditFeedDialog(feed: Feed?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var time by remember { mutableLongStateOf(feed?.time ?: System.currentTimeMillis()) }
    var type by remember { mutableStateOf(feed?.type ?: "Bottle") }
    var detail by remember { mutableStateOf(feed?.detail ?: "") }
    var note by remember { mutableStateOf(feed?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (feed == null) "Add feed" else "Edit feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledButton("Time", "${fmtDay(localDate(time))} ${fmtTime(time)}") {
                    pickDateTime(ctx, time, dateToo = true) { time = it }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FEED_TYPES.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t) }) }
                }
                OutlinedTextField(detail, { detail = it }, label = { Text("Amount / how it went") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.upsertFeed(ctx, Feed(feed?.id ?: Store.newId(), time, type, detail.trim(), note.trim()))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (feed != null) TextButton(onClick = { Store.deleteFeed(ctx, feed.id); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun LabeledButton(label: String, value: String, onClick: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(value) }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label)
    }
}
