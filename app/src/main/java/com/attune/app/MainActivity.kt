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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        lifecycleScope.launch { runCatching { demo.ensureSeeded() } }
        setContent { AttuneTheme { AttuneNavHost() } }
    }

    override fun onResume() {
        super.onResume()
        access.refresh()
        lifecycleScope.launch {
            queue.onAppReturned()
            sessions.completeIfDue()
            if (sessions.getActive() != null) FocusTimerService.start(this@MainActivity)
        }
    }
}
