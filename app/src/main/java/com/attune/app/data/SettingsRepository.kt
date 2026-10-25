package com.attune.app.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.attune.app.BuildConfig
import com.attune.app.util.AppCategoryMapper
import com.attune.prediction.decision.DecisionSettings
import com.attune.prediction.decision.OutsideFocus
import com.attune.prediction.policy.PolicySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("attune_settings")

/** What happens to held notifications when a session ends, or when you pause or take a break. */
enum class AfterFocus { SUMMARY_NOTIFICATION, QUIET }

data class AttuneSettings(
    val loaded: Boolean = false,
    val onboardingSeen: Boolean = false,
    val defaultFocusMin: Int = 25,
    val breakMin: Int = 5,
    val queuedCategories: Set<String> = AppCategoryMapper.DEFAULT_QUEUED,
    val allowedPackages: Set<String> = emptySet(),
    val priorityContacts: Set<String> = emptySet(),
    val allowCallsAndAlarms: Boolean = true,
    val allowOtp: Boolean = true,
    val groupRepeated: Boolean = true,
    val suppressOriginals: Boolean = true,
    val filterPromotions: Boolean = false,
    val demoEnabled: Boolean = BuildConfig.DEMO_MODE_DEFAULT,
    val energyCheckInEnabled: Boolean = true,
    // v3
    val aiMode: Boolean = true,
    val outsideFocus: OutsideFocus = OutsideFocus.IGNORE,
    val afterFocus: AfterFocus = AfterFocus.SUMMARY_NOTIFICATION,
    val hideSensitivePreviews: Boolean = true,
    val coachEnabled: Boolean = true,
    val scheduleSuggestions: Boolean = true,
    /** 0 = no goal. */
    val dailyGoalMin: Int = 0,
    val weeklyGoalMin: Int = 0
) {
    fun policy() = PolicySettings(queuedCategories, allowedPackages, priorityContacts, allowCallsAndAlarms, allowOtp, filterPromotions)
    fun decisionSettings() = DecisionSettings(aiMode, policy(), outsideFocus)
}

enum class Flag(val key: Preferences.Key<Boolean>) {
    ONBOARDING_SEEN(booleanPreferencesKey("onboarding_seen")),
    ALLOW_CALLS(booleanPreferencesKey("allow_calls_alarms")),
    ALLOW_OTP(booleanPreferencesKey("allow_otp")),
    GROUP_REPEATED(booleanPreferencesKey("group_repeated")),
    SUPPRESS(booleanPreferencesKey("suppress_originals")),
    FILTER_PROMOS(booleanPreferencesKey("filter_promotions")),
    DEMO(booleanPreferencesKey("demo_enabled")),
    ENERGY(booleanPreferencesKey("energy_checkin_enabled")),
    AI_MODE(booleanPreferencesKey("ai_mode")),
    HIDE_SENSITIVE(booleanPreferencesKey("hide_sensitive_previews")),
    COACH(booleanPreferencesKey("coach_enabled")),
    SCHEDULE(booleanPreferencesKey("schedule_suggestions"))
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val focusKey = intPreferencesKey("default_focus_min")
    private val breakKey = intPreferencesKey("break_min")
    private val catsKey = stringSetPreferencesKey("queued_categories")
    private val allowedKey = stringSetPreferencesKey("allowed_packages")
    private val contactsKey = stringSetPreferencesKey("priority_contacts")
    private val demoSeededKey = booleanPreferencesKey("demo_seeded")
    private val demoSeedVersionKey = intPreferencesKey("demo_seed_version")
    private val demoLiveIndexKey = intPreferencesKey("demo_live_index")
    private val outsideKey = stringPreferencesKey("outside_focus")
    private val afterKey = stringPreferencesKey("after_focus")
    private val dailyGoalKey = intPreferencesKey("daily_goal_min")
    private val weeklyGoalKey = intPreferencesKey("weekly_goal_min")

    val settings: Flow<AttuneSettings> = ctx.dataStore.data.catch { emit(emptyPreferences()) }.map { p ->
        val d = AttuneSettings()
        AttuneSettings(
            loaded = true,
            onboardingSeen = p[Flag.ONBOARDING_SEEN.key] ?: false,
            defaultFocusMin = p[focusKey] ?: d.defaultFocusMin,
            breakMin = p[breakKey] ?: d.breakMin,
            queuedCategories = p[catsKey] ?: d.queuedCategories,
            allowedPackages = p[allowedKey] ?: emptySet(),
            priorityContacts = p[contactsKey] ?: emptySet(),
            allowCallsAndAlarms = p[Flag.ALLOW_CALLS.key] ?: d.allowCallsAndAlarms,
            allowOtp = p[Flag.ALLOW_OTP.key] ?: d.allowOtp,
            groupRepeated = p[Flag.GROUP_REPEATED.key] ?: d.groupRepeated,
            suppressOriginals = p[Flag.SUPPRESS.key] ?: d.suppressOriginals,
            filterPromotions = p[Flag.FILTER_PROMOS.key] ?: d.filterPromotions,
            demoEnabled = p[Flag.DEMO.key] ?: d.demoEnabled,
            energyCheckInEnabled = p[Flag.ENERGY.key] ?: d.energyCheckInEnabled,
            aiMode = p[Flag.AI_MODE.key] ?: d.aiMode,
            outsideFocus = runCatching { OutsideFocus.valueOf(p[outsideKey] ?: "") }.getOrDefault(d.outsideFocus),
            afterFocus = runCatching { AfterFocus.valueOf(p[afterKey] ?: "") }.getOrDefault(d.afterFocus),
            hideSensitivePreviews = p[Flag.HIDE_SENSITIVE.key] ?: d.hideSensitivePreviews,
            coachEnabled = p[Flag.COACH.key] ?: d.coachEnabled,
            scheduleSuggestions = p[Flag.SCHEDULE.key] ?: d.scheduleSuggestions,
            dailyGoalMin = p[dailyGoalKey] ?: d.dailyGoalMin,
            weeklyGoalMin = p[weeklyGoalKey] ?: d.weeklyGoalMin)
    }.distinctUntilChanged()

    suspend fun current(): AttuneSettings = settings.first()

    suspend fun setFlag(f: Flag, v: Boolean) { ctx.dataStore.edit { it[f.key] = v } }
    suspend fun setFocusMin(v: Int) { ctx.dataStore.edit { it[focusKey] = v.coerceIn(5, 180) } }
    suspend fun setBreakMin(v: Int) { ctx.dataStore.edit { it[breakKey] = v.coerceIn(1, 60) } }
    suspend fun setOutsideFocus(v: OutsideFocus) { ctx.dataStore.edit { it[outsideKey] = v.name } }
    suspend fun setAfterFocus(v: AfterFocus) { ctx.dataStore.edit { it[afterKey] = v.name } }
    suspend fun setDailyGoal(min: Int) { ctx.dataStore.edit { it[dailyGoalKey] = min.coerceIn(0, 600) } }
    suspend fun setWeeklyGoal(min: Int) { ctx.dataStore.edit { it[weeklyGoalKey] = min.coerceIn(0, 4200) } }
    suspend fun setCategoryQueued(cat: String, queued: Boolean) { ctx.dataStore.edit {
        val cur = it[catsKey] ?: AppCategoryMapper.DEFAULT_QUEUED; it[catsKey] = if (queued) cur + cat else cur - cat } }
    suspend fun setAppAllowed(pkg: String, allowed: Boolean) { ctx.dataStore.edit {
        val cur = it[allowedKey] ?: emptySet(); it[allowedKey] = if (allowed) cur + pkg else cur - pkg } }
    suspend fun addContact(name: String) { val n = name.trim(); if (n.isEmpty()) return
        ctx.dataStore.edit { it[contactsKey] = (it[contactsKey] ?: emptySet()) + n } }
    suspend fun removeContact(name: String) { ctx.dataStore.edit { it[contactsKey] = (it[contactsKey] ?: emptySet()) - name } }

    suspend fun isDemoSeeded() = ctx.dataStore.data.first()[demoSeededKey] ?: false
    suspend fun setDemoSeeded(v: Boolean) { ctx.dataStore.edit { it[demoSeededKey] = v } }
    suspend fun demoSeedVersion() = ctx.dataStore.data.first()[demoSeedVersionKey] ?: 1
    suspend fun setDemoSeedVersion(v: Int) { ctx.dataStore.edit { it[demoSeedVersionKey] = v } }
    suspend fun nextDemoLiveIndex(): Int { var i = 0; ctx.dataStore.edit { i = it[demoLiveIndexKey] ?: 0; it[demoLiveIndexKey] = i + 1 }; return i }
}
