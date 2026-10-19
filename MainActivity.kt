package app.tatertots.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tatertots.data.Store
import app.tatertots.engine.Feed
import app.tatertots.engine.Sleep
import kotlinx.coroutines.delay

/** Which sleep/feed is open in an edit dialog. A blank id means "new". */
sealed interface Editing {
    data class SleepEdit(val sleep: Sleep?) : Editing
    data class FeedEdit(val feed: Feed?) : Editing
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        setContent { TaterTheme { Root() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Filled.Home),
    Tab("History", Icons.Filled.DateRange),
    Tab("Insights", Icons.Filled.Info),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
private fun Root() {
    val state by Store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Editing?>(null) }

    // Re-render every 20 s so "awake for" and countdowns stay fresh.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(20_000) }
    }
    LaunchedEffect(state) { now = System.currentTimeMillis() }

    // Ask for notification permission (Android 13+) for the settling reminder.
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        val onEditSleep: (Sleep?) -> Unit = { editing = Editing.SleepEdit(it) }
        val onEditFeed: (Feed?) -> Unit = { editing = Editing.FeedEdit(it) }
        when (tab) {
            0 -> TodayScreen(state, now, m, onEditSleep, onEditFeed)
            1 -> HistoryScreen(state, now, m, onEditSleep, onEditFeed)
            2 -> InsightsScreen(state, now, m)
            else -> SettingsScreen(state, m)
        }
    }

    when (val e = editing) {
        is Editing.SleepEdit -> EditSleepDialog(state, e.sleep, onDismiss = { editing = null })
        is Editing.FeedEdit -> EditFeedDialog(e.feed, onDismiss = { editing = null })
        null -> {}
    }
}
