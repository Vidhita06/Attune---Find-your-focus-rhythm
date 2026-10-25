package com.attune.app.ui.onboarding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes

@Composable fun OnboardingScreen(onSetupAccess: () -> Unit, onComplete: () -> Unit) {
    ScreenColumn {
        Spacer(Modifier.height(16.dp))
        ScreenHeader("Welcome", "Attune", "Your attention digital twin. Focus first; review notifications on your terms.")
        Feature(Icons.Outlined.Timer, "Focus sessions", "Start a timed session. It keeps running if you leave the app.")
        Feature(Icons.Outlined.Inbox, "Smart queue", "Notifications that arrive during focus wait in a queue instead of interrupting you.")
        Feature(Icons.Outlined.Psychology, "Before you open", "See an estimate of time away and recovery before you decide — clearly labelled as an estimate.")
        Feature(Icons.Outlined.Lock, "Local-first", "No account, no server. Everything is stored on this device.")
        InfoBanner(Icons.Outlined.Notifications, "Notification access (optional)", "Needed to queue real notifications. You can also try everything with labelled demo samples.", BannerTone.INFO, "Set up access", onSetupAccess)
        Button(onClick = onComplete, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("Get started") }
        val q = Quotes.forOnboarding(); InspirationQuote(q.text, q.author)
    }
}
@Composable private fun Feature(icon: ImageVector, title: String, body: String) {
    AttuneCard { Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Lavender); Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.titleMedium, color = Navy); Text(body, style = MaterialTheme.typography.bodySmall, color = NavySoft) }
    } }
}
