package com.attune.app
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.attune.app.data.DemoDataRepository
import com.attune.app.data.FocusSessionRepository
import com.attune.app.data.NotificationAccessMonitor
import com.attune.app.data.PendingNavigation
import com.attune.app.data.QueueRepository
import com.attune.app.service.FocusTimerService
import com.attune.app.ui.AttuneNavHost
import com.attune.app.ui.theme.AttuneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var access: NotificationAccessMonitor
    @Inject lateinit var queue: QueueRepository
    @Inject lateinit var sessions: FocusSessionRepository
    @Inject lateinit var demo: DemoDataRepository
    @Inject lateinit var models: com.attune.app.data.ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        // Load the classifier off the main thread before anything needs it, then seed demo data (which uses it).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) { models.status; runCatching { demo.purgeIfDisabled(); demo.ensureSeeded() } }
        handleNavigation(intent)
        setContent { AttuneTheme { AttuneNavHost() } }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handleNavigation(intent) }
    private fun handleNavigation(i: android.content.Intent?) { i?.getStringExtra(PendingNavigation.EXTRA_TAB)?.let { PendingNavigation.target.value = it; i.removeExtra(PendingNavigation.EXTRA_TAB) } }

    override fun onResume() {
        super.onResume()
        access.refresh()
        lifecycleScope.launch {
            queue.onAppReturned()
            runCatching { queue.scrubOldText() } // privacy retention for handled items
            sessions.completeIfDue()
            if (sessions.getActive() != null) FocusTimerService.start(this@MainActivity)
        }
    }
}
