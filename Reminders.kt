package app.tatertots.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tatertots.R
import app.tatertots.data.AppState
import app.tatertots.data.Store
import app.tatertots.engine.Target
import app.tatertots.ui.MainActivity
import app.tatertots.ui.fmtTime
import app.tatertots.widget.SleepWidget

/**
 * Schedules (inexact, battery-friendly) alarms for:
 *  - the "start settling" reminder notification
 *  - refreshing the widget text when the settle time / predicted time passes
 * Inexact alarms can fire a few minutes late in deep sleep (Doze); fine for this use.
 */
object Reminders {
    private const val CHANNEL = "settle"
    const val ACTION_SETTLE = "app.tatertots.SETTLE"
    const val ACTION_REFRESH = "app.tatertots.REFRESH"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Settling reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nudges you to start pre-settling before the predicted nap"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun schedule(context: Context, state: AppState) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val p = state.prediction(now)
        val settle = state.settleAt(p)

        val settlePi = pi(context, ACTION_SETTLE, 1)
        am.cancel(settlePi)
        if (state.settings.remindersOn && settle != null && settle > now) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, settle, settlePi)
        }

        // Widget refreshes: at settle time and at the predicted time (text changes at both).
        val r1 = pi(context, ACTION_REFRESH, 2)
        val r2 = pi(context, ACTION_REFRESH, 3)
        val r3 = pi(context, ACTION_REFRESH, 4)
        am.cancel(r1); am.cancel(r2); am.cancel(r3)
        p?.upper?.takeIf { it > now }?.let { am.set(AlarmManager.RTC, it + 60_000, r3) }
        settle?.takeIf { it > now }?.let { am.set(AlarmManager.RTC, it, r1) }
        p?.at?.takeIf { it > now }?.let { am.set(AlarmManager.RTC, it + 60_000, r2) }
    }

    private fun pi(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun notifySettle(context: Context) {
        val state = Store.get(context)
        val p = state.prediction() ?: return
        if (p.target != Target.NAP_START || state.isAsleep) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("Time to start settling ${state.settings.babyName}")
            .setContentText("Next nap likely ${fmtTime(p.at)} (${fmtTime(p.lower)}–${fmtTime(p.upper)})")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_SETTLE -> Reminders.notifySettle(context)
        }
        SleepWidget.refreshAll(context)
        Reminders.schedule(context, Store.get(context))
    }
}

/** Alarms are wiped on reboot, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.schedule(context, Store.get(context))
            SleepWidget.refreshAll(context)
        }
    }
}
