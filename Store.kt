package app.tatertots.data

import android.content.Context
import app.tatertots.engine.Feed
import app.tatertots.engine.Prediction
import app.tatertots.engine.Predictor
import app.tatertots.engine.Sleep
import app.tatertots.engine.SleepKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Settings(
    val babyName: String = "Baby",
    /** Birthday as epoch day, or null if not set. */
    val birthEpochDay: Long? = null,
    /** Remind / show "start settling" this many minutes before the predicted nap. */
    val settleLeadMin: Int = 15,
    /** Sleeps starting after this time (minutes after midnight) count as bedtime / night. */
    val bedtimeAfterMin: Int = 19 * 60 + 15,
    val remindersOn: Boolean = true,
    val seedImported: Boolean = false,
)

data class AppState(
    val settings: Settings = Settings(),
    val sleeps: List<Sleep> = emptyList(),
    val feeds: List<Feed> = emptyList(),
) {
    val current: Sleep? get() = sleeps.maxByOrNull { it.start }?.takeIf { it.ongoing }
    val isAsleep: Boolean get() = current != null

    /** When the baby last woke (end of the most recent sleep), if known. */
    val lastWake: Long? get() = if (isAsleep) null else sleeps.maxByOrNull { it.start }?.end

    fun ageDays(at: Long = System.currentTimeMillis()): Int? = settings.birthEpochDay?.let {
        ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it), Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()).toInt()
    }

    fun prediction(now: Long = System.currentTimeMillis()): Prediction? =
        Predictor.predict(sleeps, now, ageDays(now))

    /** When to start pre-settling for the next nap, or null if not applicable. */
    fun settleAt(p: Prediction? = prediction()): Long? =
        p?.takeIf { it.target == app.tatertots.engine.Target.NAP_START }?.let { it.at - settings.settleLeadMin * Predictor.MIN }
}

/**
 * Local, private, single-file storage (filesDir/tatertots.json). No accounts, no network.
 * Small enough (a few thousand events per year) that rewriting the file on each change is fine.
 */
object Store {
    private lateinit var file: File
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> get() = _state

    /** Called after every change (widget refresh + alarm rescheduling hook in [app.tatertots.TaterApp]). */
    var onChanged: ((Context, AppState) -> Unit)? = null

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "tatertots.json")
        _state.value = if (file.exists()) runCatching { decode(JSONObject(file.readText())) }.getOrElse { AppState() } else AppState()
        if (!_state.value.settings.seedImported) importSeed(save = true)
    }

    fun get(context: Context): AppState { init(context); return _state.value }

    // ------------------------------------------------------------ actions

    fun startSleep(context: Context, at: Long = System.currentTimeMillis(), kind: SleepKind? = null) = mutate(context) { s ->
        if (s.isAsleep) return@mutate s
        val k = kind ?: classify(s.settings, at)
        s.copy(sleeps = s.sleeps + Sleep(newId(), at, null, kind = k))
    }

    fun wake(context: Context, at: Long = System.currentTimeMillis()) = mutate(context) { s ->
        val cur = s.current ?: return@mutate s
        s.copy(sleeps = s.sleeps.map { if (it.id == cur.id) it.copy(end = maxOf(at, it.start + 60_000)) else it })
    }

    fun toggle(context: Context) {
        if (get(context).isAsleep) wake(context) else startSleep(context)
    }

    fun logFeed(context: Context, at: Long = System.currentTimeMillis(), type: String = "Bottle", detail: String = "") = mutate(context) { s ->
        s.copy(feeds = s.feeds + Feed(newId(), at, type, detail))
    }

    fun upsertSleep(context: Context, sleep: Sleep) = mutate(context) { s ->
        val others = s.sleeps.filter { it.id != sleep.id }
        s.copy(sleeps = others + sleep)
    }

    fun deleteSleep(context: Context, id: String) = mutate(context) { s -> s.copy(sleeps = s.sleeps.filter { it.id != id }) }

    fun upsertFeed(context: Context, feed: Feed) = mutate(context) { s -> s.copy(feeds = s.feeds.filter { it.id != feed.id } + feed) }

    fun deleteFeed(context: Context, id: String) = mutate(context) { s -> s.copy(feeds = s.feeds.filter { it.id != id }) }

    fun updateSettings(context: Context, f: (Settings) -> Settings) = mutate(context) { s -> s.copy(settings = f(s.settings)) }

    fun clearAll(context: Context) = mutate(context) { s -> AppState(settings = s.settings) }

    /** (Re)import the bundled history from assets/seed.json. Merges by id, so it's safe to run twice. */
    fun reimportSeed(context: Context) { init(context); importSeed(save = true) }

    fun newId(): String = UUID.randomUUID().toString()

    fun classify(settings: Settings, at: Long): SleepKind {
        val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
        val m = t.hour * 60 + t.minute
        return if (m >= settings.bedtimeAfterMin || m < 5 * 60) SleepKind.NIGHT else SleepKind.NAP
    }

    // ----------------------------------------------------------- internals

    @Synchronized
    private fun mutate(context: Context, f: (AppState) -> AppState) {
        init(context)
        val next = f(_state.value)
        if (next == _state.value) return
        _state.value = next.copy(sleeps = next.sleeps.sortedBy { it.start }, feeds = next.feeds.sortedBy { it.time })
        persist()
        onChanged?.invoke(appContext, _state.value)
    }

    private fun persist() {
        val tmp = File(file.parentFile, "tatertots.json.tmp")
        tmp.writeText(encode(_state.value).toString())
        tmp.renameTo(file)
    }

    @Synchronized
    private fun importSeed(save: Boolean) {
        val seed = runCatching { JSONObject(appContext.assets.open("seed.json").bufferedReader().use { it.readText() }) }.getOrNull()
        val cur = _state.value
        if (seed == null) {
            _state.value = cur.copy(settings = cur.settings.copy(seedImported = true))
        } else {
            val seeded = decode(seed)
            val ids = cur.sleeps.map { it.id }.toSet()
            val fids = cur.feeds.map { it.id }.toSet()
            _state.value = cur.copy(
                settings = cur.settings.copy(
                    seedImported = true,
                    babyName = if (cur.settings.babyName == "Baby") seed.optString("babyName", "Baby") else cur.settings.babyName,
                ),
                sleeps = (cur.sleeps + seeded.sleeps.filter { it.id !in ids }).sortedBy { it.start },
                feeds = (cur.feeds + seeded.feeds.filter { it.id !in fids }).sortedBy { it.time },
            )
        }
        if (save) persist()
        onChanged?.invoke(appContext, _state.value)
    }

    // ---------------------------------------------------------------- JSON

    private fun encode(s: AppState) = JSONObject().apply {
        put("version", 1)
        put("settings", JSONObject().apply {
            put("babyName", s.settings.babyName)
            s.settings.birthEpochDay?.let { put("birthEpochDay", it) }
            put("settleLeadMin", s.settings.settleLeadMin)
            put("bedtimeAfterMin", s.settings.bedtimeAfterMin)
            put("remindersOn", s.settings.remindersOn)
            put("seedImported", s.settings.seedImported)
        })
        put("sleeps", JSONArray().apply {
            s.sleeps.forEach { z ->
                put(JSONObject().apply {
                    put("id", z.id); put("start", z.start); z.end?.let { put("end", it) }
                    put("endUnknown", z.endUnknown); put("kind", z.kind.name); put("location", z.location)
                    put("approx", z.approx); put("note", z.note); put("startEstimated", z.startEstimated)
                })
            }
        })
        put("feeds", JSONArray().apply {
            s.feeds.forEach { f ->
                put(JSONObject().apply { put("id", f.id); put("time", f.time); put("type", f.type); put("detail", f.detail); put("note", f.note) })
            }
        })
    }

    private fun decode(o: JSONObject): AppState {
        val st = o.optJSONObject("settings")
        val settings = if (st == null) Settings() else Settings(
            babyName = st.optString("babyName", "Baby"),
            birthEpochDay = if (st.has("birthEpochDay")) st.getLong("birthEpochDay") else null,
            settleLeadMin = st.optInt("settleLeadMin", 15),
            bedtimeAfterMin = st.optInt("bedtimeAfterMin", 19 * 60 + 15),
            remindersOn = st.optBoolean("remindersOn", true),
            seedImported = st.optBoolean("seedImported", false),
        )
        val sleeps = o.optJSONArray("sleeps")?.let { a ->
            (0 until a.length()).map { i ->
                val z = a.getJSONObject(i)
                Sleep(
                    id = z.getString("id"),
                    start = z.getLong("start"),
                    end = if (z.has("end") && !z.isNull("end")) z.getLong("end") else null,
                    endUnknown = z.optBoolean("endUnknown", false),
                    kind = runCatching { SleepKind.valueOf(z.optString("kind", "NAP")) }.getOrDefault(SleepKind.NAP),
                    location = z.optString("location", ""),
                    approx = z.optBoolean("approx", false),
                    note = z.optString("note", ""),
                    startEstimated = z.optBoolean("startEstimated", false),
                )
            }
        } ?: emptyList()
        val feeds = o.optJSONArray("feeds")?.let { a ->
            (0 until a.length()).map { i ->
                val f = a.getJSONObject(i)
                Feed(f.getString("id"), f.getLong("time"), f.optString("type", "Bottle"), f.optString("detail", ""), f.optString("note", ""))
            }
        } ?: emptyList()
        return AppState(settings, sleeps.sortedBy { it.start }, feeds.sortedBy { it.time })
    }

    // -------------------------------------------------------------- export

    fun exportCsv(s: AppState): String {
        val z = ZoneId.systemDefault()
        fun t(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(z).toLocalDateTime().toString().replace('T', ' ') } ?: ""
        fun q(x: String) = "\"" + x.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("record,kind,start,end,duration_min,location,approx,note\n")
        s.sleeps.forEach {
            sb.append("sleep,${it.kind},${t(it.start)},${t(it.end)},${it.durationMin?.toInt() ?: ""},${q(it.location)},${it.approx || it.startEstimated},${q(it.note)}\n")
        }
        s.feeds.forEach { sb.append("feed,${q(it.type)},${t(it.time)},,,${q(it.detail)},false,${q(it.note)}\n") }
        return sb.toString()
    }
}
