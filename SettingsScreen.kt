package app.tatertots.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.tatertots.data.AppState
import app.tatertots.data.Store
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val s = state.settings
    var name by remember(s.babyName) { mutableStateOf(s.babyName) }
    var lead by remember(s.settleLeadMin) { mutableStateOf(s.settleLeadMin.toFloat()) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        BigCard {
            Text("Your baby", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                name, { name = it; if (it.isNotBlank()) Store.updateSettings(ctx) { st -> st.copy(babyName = it.trim()) } },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val bday = s.birthEpochDay?.let { LocalDate.ofEpochDay(it) }
            LabeledButton("Birthday (used before there's enough data)", bday?.let { fmtDay(it) + " ${it.year}" } ?: "Set birthday") {
                val d = bday ?: LocalDate.now().minusMonths(3)
                DatePickerDialog(ctx, { _, y, m, day ->
                    Store.updateSettings(ctx) { st -> st.copy(birthEpochDay = LocalDate.of(y, m + 1, day).toEpochDay()) }
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            state.ageDays()?.let { Text("${it / 7} weeks ${it % 7} days old", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        BigCard {
            Text("Settling", style = MaterialTheme.typography.titleMedium)
            Text("Start settling ${lead.toInt()} min before the predicted nap")
            Slider(
                value = lead, onValueChange = { lead = it }, valueRange = 0f..30f, steps = 5,
                onValueChangeFinished = { Store.updateSettings(ctx) { st -> st.copy(settleLeadMin = lead.toInt()) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminder notification", Modifier.weight(1f))
                Switch(s.remindersOn, { on -> Store.updateSettings(ctx) { st -> st.copy(remindersOn = on) } })
            }
            val bt = s.bedtimeAfterMin
            LabeledButton("Sleeps after this time count as bedtime", "%02d:%02d".format(bt / 60, bt % 60)) {
                TimePickerDialog(ctx, { _, h, m -> Store.updateSettings(ctx) { st -> st.copy(bedtimeAfterMin = h * 60 + m) } }, bt / 60, bt % 60, true).show()
            }
        }

        BigCard {
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything stays on this phone. Nothing is uploaded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
            OutlinedButton(onClick = {
                val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, "tater-tots-${LocalDate.now()}.csv").apply { writeText(Store.exportCsv(state)) }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                val send = Intent(Intent.ACTION_SEND).setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "Export sleep log"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
            OutlinedButton(onClick = { Store.reimportSeed(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Re-import starting history")
            }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete all logs", color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            "Tater Tots v0.1 · predictions use the median of recent wake windows and nap lengths. " +
                "Patterns, not prescriptions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all logs?") },
        text = { Text("This removes every sleep and feed on this phone. Export first if you want a copy.") },
        confirmButton = { TextButton(onClick = { Store.clearAll(ctx); confirmClear = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
