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
import com.attune.prediction.decision.RuleAction
import com.attune.prediction.decision.RuleKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel class RulesViewModel @Inject constructor(private val settings: SettingsRepository, private val rulesRepo: RulesRepository, @ApplicationContext private val ctx: Context) : ViewModel() {
    val state: StateFlow<AttuneSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttuneSettings())
    val rules = rulesRepo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addRule(kind: RuleKind, value: String, action: RuleAction) = viewModelScope.launch { rulesRepo.add(kind, value, action, RulesRepository.labelFor(kind, value.trim(), action)) }
    fun removeRule(id: String) = viewModelScope.launch { rulesRepo.remove(id) }
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
    val rules by vm.rules.collectAsState()
    var keyword by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    ScreenColumn {
        DetailTopBar("Notification rules", onBack)
        InfoBanner(Icons.Outlined.Info, "Your rules come first", "Allow rules always win, even over the AI. Hold rules are applied too — except when the content looks clearly important or matches a safety check, then it stays visible. When an allow and a hold rule both match, allow wins.")

        SectionTitle("Emergency bypass")
        AttuneCard {
            Text("These always reach you during focus, in AI Mode and rules mode:", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            SwitchRow("Calls and alarms", "When the app marks a notification as a call or alarm.", s.allowCallsAndAlarms) { vm.setFlag(Flag.ALLOW_CALLS, it) }
            SwitchRow("One-time codes", "Text with words like “OTP” or “verification code” plus a 4–8 digit number. Best-effort; it may miss some codes.", s.allowOtp) { vm.setFlag(Flag.ALLOW_OTP, it) }
            Text("Always on: account-security alerts, emergencies (hospital, accident…) and 2+ missed calls in a row.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Text("Android limit: Attune sees a notification only after it's posted and can't change how loud it is, so a held notification may still sound or show briefly. For guaranteed silence use Android's own Do Not Disturb with its priority exceptions.",
                style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }

        SectionTitle("Keyword & category rules")
        AttuneCard {
            Text("Allow a keyword (e.g. “exam”, “interview”) so any notification containing it reaches you.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(keyword, { keyword = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Keyword") })
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.addRule(RuleKind.KEYWORD, keyword, RuleAction.ALLOW); keyword = "" }, enabled = keyword.isNotBlank()) { Text("Allow") }
            }
            Spacer(Modifier.height(6.dp))
            if (rules.isEmpty()) Text("No keyword or category rules yet. Feedback like “Always hold this type” also creates rules here.", style = MaterialTheme.typography.bodySmall, color = NavyFaint)
            rules.forEach { r -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (r.action == "ALLOW") Icons.Outlined.CheckCircle else Icons.Outlined.DoNotDisturbOn, null, tint = if (r.action == "ALLOW") LowRisk else MedRisk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text(r.label, style = MaterialTheme.typography.bodyMedium, color = Navy)
                    Text(if (r.source == "FEEDBACK") "Created from your feedback" else "Added by you", style = MaterialTheme.typography.labelMedium, color = NavyFaint) }
                IconButton(onClick = { vm.removeRule(r.id) }) { Icon(Icons.Outlined.Close, "Remove rule", tint = NavySoft) }
            } }
        }

        SectionTitle("Categories (when AI Mode is off)")
        AttuneCard {
            Text("In rules mode — or if the AI model can't load — notifications from selected categories wait in the queue. In AI Mode the model decides per message instead.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCategoryMapper.ALL.forEach { c -> val on = c in s.queuedCategories; FilterChip(on, { vm.setCategory(c, !on) }, { Text(AppCategoryMapper.displayName(c)) }) }
            }
        }

        AttuneCard {
            SwitchRow("Filter promotions", "Only applies when the app marks a notification as promotional. Filtered items are kept under Handled.", s.filterPromotions) { vm.setFlag(Flag.FILTER_PROMOS, it) }
        }

        SectionTitle("Senders")
        AttuneCard {
            Text("Choose how a sender is treated. Matching uses the notification title, which many messaging apps set to the sender's name (not all do). A sender with no entry is Normal.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Text("Always allow: reaches you during focus.  Priority: never held, ordinary messages stay visible in your queue for review (not treated as an emergency).  Hold: held during focus when the AI is confident; urgent or security wording still stays visible.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(contact, { contact = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("e.g. Mom") })
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.addContact(contact); contact = "" }, enabled = contact.isNotBlank()) { Text("Always allow") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.addRule(RuleKind.CONTACT, contact, RuleAction.PRIORITY); contact = "" }, enabled = contact.isNotBlank()) { Text("Priority") }
                OutlinedButton(onClick = { vm.addRule(RuleKind.CONTACT, contact, RuleAction.DEFER); contact = "" }, enabled = contact.isNotBlank()) { Text("Hold") }
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
