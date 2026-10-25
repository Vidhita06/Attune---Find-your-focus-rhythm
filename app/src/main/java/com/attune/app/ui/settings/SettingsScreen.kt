package com.attune.app.ui.settings
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.ui.components.*
import com.attune.app.ui.profile.SwitchRow
import com.attune.app.ui.theme.*
import com.attune.app.util.AppCategoryMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel class RulesViewModel @Inject constructor(private val settings: SettingsRepository, @ApplicationContext private val ctx: Context) : ViewModel() {
    val state: StateFlow<AttuneSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttuneSettings())
    val apps = MutableStateFlow<List<InstalledApp>?>(null)
    init { viewModelScope.launch { apps.value = withContext(Dispatchers.IO) { loadApps() } } }
    private fun loadApps(): List<InstalledApp> = runCatching {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }
            .filter { it.first != ctx.packageName }.map { InstalledApp(it.first, it.second) }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
    fun setCategory(c: String, q: Boolean) = viewModelScope.launch { settings.setCategoryQueued(c, q) }
    fun setApp(p: String, a: Boolean) = viewModelScope.launch { settings.setAppAllowed(p, a) }
    fun setFlag(f: Flag, v: Boolean) = viewModelScope.launch { settings.setFlag(f, v) }
    fun addContact(n: String) = viewModelScope.launch { settings.addContact(n) }
    fun removeContact(n: String) = viewModelScope.launch { settings.removeContact(n) }
}

/** Notification rules: which categories wait during focus, which apps/contacts may interrupt, grouping and suppression. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(onBack: () -> Unit, vm: RulesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val apps by vm.apps.collectAsState()
    var contact by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    ScreenColumn {
        DetailTopBar("Notification rules", onBack)
        InfoBanner(Icons.Outlined.Info, "Rules are best-effort", "Apps label notifications differently. Attune uses the sending app, Android's category (when the app sets one) and your exceptions. It doesn't guess urgency from message text, except the optional one-time-code rule.")

        SectionTitle("Queue during focus")
        AttuneCard {
            Text("Notifications from selected categories wait in the queue. Unselected categories reach you as normal.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCategoryMapper.ALL.forEach { c -> val on = c in s.queuedCategories; FilterChip(on, { vm.setCategory(c, !on) }, { Text(AppCategoryMapper.displayName(c)) }) }
            }
        }

        SectionTitle("Exceptions")
        AttuneCard {
            SwitchRow("Allow calls and alarms", "When the app marks a notification as a call or alarm, it always reaches you.", s.allowCallsAndAlarms) { vm.setFlag(Flag.ALLOW_CALLS, it) }
            SwitchRow("Allow one-time codes", "Best-effort: lets through text containing words like “OTP” or “verification code” plus a 4–8 digit number. It may miss some codes.", s.allowOtp) { vm.setFlag(Flag.ALLOW_OTP, it) }
            SwitchRow("Filter promotions", "Only applies when the app marks a notification as promotional. Filtered items are kept under Reviewed.", s.filterPromotions) { vm.setFlag(Flag.FILTER_PROMOS, it) }
        }

        SectionTitle("Priority contacts")
        AttuneCard {
            Text("Messages whose title matches a name here reach you during focus. Many messaging apps put the sender's name in the title, but not all do.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(contact, { contact = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("e.g. Mom") })
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.addContact(contact); contact = "" }, enabled = contact.isNotBlank()) { Text("Add") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.priorityContacts.sorted().forEach { c -> InputChip(true, { vm.removeContact(c) }, { Text(c) }, trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(16.dp)) }) }
            }
        }

        SectionTitle("Grouping & suppression")
        AttuneCard {
            SwitchRow("Group repeated notifications", "Several pending notifications from the same app in one session become one card.", s.groupRepeated) { vm.setFlag(Flag.GROUP_REPEATED, it) }
            SwitchRow("Hide queued notifications from the shade", "Attune asks Android to remove queued notifications so they stop distracting you. It cannot put them back; review them in Attune's queue.", s.suppressOriginals) { vm.setFlag(Flag.SUPPRESS, it) }
        }

        SectionTitle("Apps allowed to interrupt")
        AttuneCard {
            Text("Notifications from these apps are never queued.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search apps") })
            val list = apps
            when {
                list == null -> LoadingState()
                list.isEmpty() -> Text("Couldn't read the app list on this device.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                else -> {
                    val filtered = list.filter { query.isBlank() || it.label.contains(query, true) }.sortedByDescending { it.packageName in s.allowedPackages }
                    val shown = if (showAllApps || query.isNotBlank()) filtered else filtered.take(12)
                    shown.forEach { a -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.packageName, a.label, 32.dp); Spacer(Modifier.width(10.dp))
                        Text(a.label, style = MaterialTheme.typography.bodyMedium, color = Navy, modifier = Modifier.weight(1f))
                        Switch(a.packageName in s.allowedPackages, { vm.setApp(a.packageName, it) })
                    } }
                    if (!showAllApps && query.isBlank() && filtered.size > 12) TextButton(onClick = { showAllApps = true }) { Text("Show all ${filtered.size} apps") }
                }
            }
        }
    }
}
