package com.attune.app.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.attune.app.data.Flag
import com.attune.app.data.QueueRepository
import com.attune.app.data.SettingsRepository
import com.attune.app.ui.components.LocalSnackbar
import com.attune.app.data.PendingNavigation
import com.attune.app.ui.ai.AiModeScreen
import com.attune.app.ui.insights.InsightsNav
import com.attune.app.ui.insights.InsightsTab
import com.attune.app.ui.privacy.LearnedPreferencesScreen
import com.attune.app.ui.privacy.PrivacyCenterScreen
import com.attune.app.ui.privacy.SessionTimelineScreen
import com.attune.app.ui.feedback.FeedbackScreen
import com.attune.app.ui.focus.FocusScreen
import com.attune.app.ui.home.HomeScreen
import com.attune.app.ui.insights.InsightsScreen
import com.attune.app.ui.onboarding.AccessGuideScreen
import com.attune.app.ui.onboarding.OnboardingScreen
import com.attune.app.ui.prediction.PredictionScreen
import com.attune.app.ui.profile.ProfileScreen
import com.attune.app.ui.queue.QueueScreen
import com.attune.app.ui.settings.SettingsScreen
import com.attune.app.ui.theme.*
import com.attune.core.model.NotificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class AppViewModel @Inject constructor(private val settings: SettingsRepository, queue: QueueRepository) : ViewModel() {
    val onboardingSeen: StateFlow<Boolean?> = settings.settings.map { it.onboardingSeen }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendingCount: StateFlow<Int> = queue.items.map { l -> l.count { it.status == NotificationStatus.PENDING } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun completeOnboarding() = viewModelScope.launch { settings.setFlag(Flag.ONBOARDING_SEEN, true) }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)
private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("focus", "Focus", Icons.Outlined.Timer, Icons.Filled.Timer),
    Tab("queue", "Queue", Icons.Outlined.Inbox, Icons.Filled.Inbox),
    Tab("insights", "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person))

@Composable fun AttuneNavHost(app: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val cur = entry?.destination?.route
    val pending by app.pendingCount.collectAsState()
    val seen by app.onboardingSeen.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(seen) { if (seen == false && cur != "onboarding") nav.navigate("onboarding") }

    fun goTab(route: String) = nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    fun openItem(id: String) = nav.navigate("prediction/$id")
    fun openSession(id: String) = nav.navigate("session/$id")
    val pendingTab by PendingNavigation.target.collectAsState()
    LaunchedEffect(pendingTab) { pendingTab?.let { if (TABS.any { t -> t.route == it }) goTab(it); PendingNavigation.target.value = null } }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(containerColor = WarmWhite, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (TABS.any { it.route == cur }) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                TABS.forEach { t ->
                    val sel = cur == t.route
                    NavigationBarItem(selected = sel, onClick = { goTab(t.route) }, label = { Text(t.label) },
                        icon = { BadgedBox(badge = { if (t.route == "queue" && pending > 0) Badge { Text("$pending") } }) { Icon(if (sel) t.selectedIcon else t.icon, t.label) } },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = LavenderDeep, selectedTextColor = LavenderDeep, indicatorColor = LavenderSoft,
                            unselectedIconColor = NavyFaint, unselectedTextColor = NavyFaint))
                }
            }
        }) { pad ->
            NavHost(nav, "home", Modifier.padding(pad)) {
                composable("home") { HomeScreen(onOpenFocus = { goTab("focus") }, onOpenQueue = { goTab("queue") }, onOpenInsights = { goTab("insights") },
                    onOpenItem = ::openItem, onSetupAccess = { nav.navigate("access") }, onOpenAi = { nav.navigate("ai") }, onOpenGoals = { goTab("profile") }) }
                composable("focus") { FocusScreen(onOpenQueue = { goTab("queue") }, onOpenSession = ::openSession, onSetupAccess = { nav.navigate("access") }) }
                composable("queue") { QueueScreen(onOpenPrediction = ::openItem, onSetupAccess = { nav.navigate("access") }, onOpenFocus = { goTab("focus") }) }
                composable("insights") { InsightsScreen(onStartFocus = { goTab("focus") }, onOpenSession = ::openSession) }
                composable("profile") { ProfileScreen(onOpenRules = { nav.navigate("rules") }, onOpenAccessGuide = { nav.navigate("access") }, onOpenAi = { nav.navigate("ai") },
                    onOpenPrivacy = { nav.navigate("privacy") }, onOpenLearned = { nav.navigate("learned") },
                    onOpenAttentionProfile = { InsightsNav.request.value = InsightsTab.PROFILE; goTab("insights") }) }
                composable("ai") { AiModeScreen(onBack = { nav.popBackStack() }, onOpenRules = { nav.navigate("rules") }) }
                composable("privacy") { PrivacyCenterScreen(onBack = { nav.popBackStack() }, onOpenLearned = { nav.navigate("learned") }) }
                composable("learned") { LearnedPreferencesScreen(onBack = { nav.popBackStack() }) }
                composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    SessionTimelineScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("prediction/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    PredictionScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                        onFeedback = { id -> nav.navigate("feedback/$id") { popUpTo("prediction/{id}") { inclusive = true } } })
                }
                composable("feedback/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    FeedbackScreen(it.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() })
                }
                composable("rules") { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable("access") { AccessGuideScreen(onBack = { nav.popBackStack() }) }
                composable("onboarding") { OnboardingScreen(onSetupAccess = { nav.navigate("access") },
                    onComplete = { app.completeOnboarding(); if (!nav.popBackStack("onboarding", inclusive = true)) goTab("home") }) }
            }
        }
    }
}

