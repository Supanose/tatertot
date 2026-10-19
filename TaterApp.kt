package app.tatertots

import android.app.Application
import app.tatertots.data.Store
import app.tatertots.system.Reminders
import app.tatertots.widget.SleepWidget

class TaterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Store.onChanged = { ctx, state ->
            SleepWidget.refreshAll(ctx)
            Reminders.schedule(ctx, state)
        }
        Store.init(this)
    }
}
