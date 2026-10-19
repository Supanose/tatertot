package app.tatertots.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import app.tatertots.R
import app.tatertots.data.AppState
import app.tatertots.data.Store
import app.tatertots.engine.SleepKind
import app.tatertots.engine.Target
import app.tatertots.ui.MainActivity
import app.tatertots.ui.TaterMood
import app.tatertots.ui.fmtTime
import app.tatertots.ui.mood

/**
 * Home-screen widget: one-tap Asleep/Awake and Feed, live elapsed timer,
 * next predicted nap (or wake) with a range, and a "settle in" countdown.
 * Chronometers tick on their own, so we only need to redraw when state changes
 * or a threshold passes (scheduled in Reminders).
 */
class SleepWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, Store.get(context))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> Store.toggle(context)          // Store.onChanged refreshes widgets
            ACTION_FEED -> {
                Store.logFeed(context)
                android.widget.Toast.makeText(context, "Feed logged at ${fmtTime(System.currentTimeMillis())}", android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "app.tatertots.widget.TOGGLE"
        const val ACTION_FEED = "app.tatertots.widget.FEED"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SleepWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, Store.get(context))
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        /** Converts a wall-clock time into the elapsedRealtime base a Chronometer needs. */
        private fun chronoBase(wallMs: Long): Long =
            SystemClock.elapsedRealtime() + (wallMs - System.currentTimeMillis())

        private fun build(context: Context, s: AppState): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_sleep)
            val now = System.currentTimeMillis()
            val name = s.settings.babyName
            val cur = s.current
            val p = s.prediction(now)

            // Tater mirrors the baby's state
            v.setImageViewResource(
                R.id.w_tater,
                when (s.mood(now)) {
                    TaterMood.NIGHT -> R.drawable.tater_night
                    TaterMood.NAP -> R.drawable.tater_nap
                    TaterMood.TIRED -> R.drawable.tater_tired
                    TaterMood.AWAKE -> R.drawable.tater_awake
                },
            )

            // Header: state + elapsed timer
            val since: Long? = cur?.start ?: s.lastWake
            v.setTextViewText(
                R.id.w_title,
                when {
                    cur?.kind == SleepKind.NIGHT -> "$name · Night sleep"
                    cur != null -> "$name · Asleep"
                    else -> "$name · Awake"
                },
            )
            if (since != null) {
                v.setViewVisibility(R.id.w_elapsed, View.VISIBLE)
                v.setChronometer(R.id.w_elapsed, chronoBase(since), null, true)
                v.setChronometerCountDown(R.id.w_elapsed, false)
            } else {
                v.setViewVisibility(R.id.w_elapsed, View.GONE)
            }

            // Prediction
            v.setViewVisibility(R.id.w_settle_row, View.GONE)
            when {
                p == null && cur?.kind == SleepKind.NIGHT -> {
                    v.setTextViewText(R.id.w_main, "Sweet dreams")
                    v.setTextViewText(R.id.w_range, "Tap Awake for night wakes or morning")
                }
                p == null -> {
                    v.setTextViewText(R.id.w_main, "Log a sleep to start")
                    v.setTextViewText(R.id.w_range, "")
                }
                p.target == Target.WAKE -> {
                    v.setTextViewText(R.id.w_main, "Wake ~${fmtTime(p.at)}")
                    v.setTextViewText(R.id.w_range, "Likely ${fmtTime(p.lower)}–${fmtTime(p.upper)}")
                }
                else -> {
                    val label = if (p.firstOfDay) "First nap" else "Next nap"
                    v.setTextViewText(R.id.w_main, "$label ~${fmtTime(p.at)}")
                    v.setTextViewText(R.id.w_range, "Likely ${fmtTime(p.lower)}–${fmtTime(p.upper)}")
                    val settle = s.settleAt(p)
                    v.setViewVisibility(R.id.w_settle_row, View.VISIBLE)
                    if (settle != null && settle > now) {
                        v.setTextViewText(R.id.w_settle_label, "Start settling in ")
                        v.setViewVisibility(R.id.w_settle_chrono, View.VISIBLE)
                        v.setChronometer(R.id.w_settle_chrono, chronoBase(settle), null, true)
                        v.setChronometerCountDown(R.id.w_settle_chrono, true)
                    } else {
                        v.setViewVisibility(R.id.w_settle_chrono, View.GONE)
                        v.setTextViewText(
                            R.id.w_settle_label,
                            if (now > p.upper) "Past $name's window – watch for cues" else "Start settling now",
                        )
                    }
                }
            }

            // Buttons
            v.setTextViewText(R.id.w_toggle, if (cur != null) "Awake" else "Asleep")
            v.setOnClickPendingIntent(R.id.w_toggle, broadcast(context, ACTION_TOGGLE, 10))
            v.setOnClickPendingIntent(R.id.w_feed, broadcast(context, ACTION_FEED, 11))
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(context, 12, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            return v
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code, Intent(context, SleepWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
