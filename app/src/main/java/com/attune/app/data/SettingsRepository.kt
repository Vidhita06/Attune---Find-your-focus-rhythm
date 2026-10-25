package com.attune.app.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.attune.app.BuildConfig
import com.attune.app.util.AppCategoryMapper
import com.attune.prediction.policy.PolicySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("attune_settings")

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
    val energyCheckInEnabled: Boolean = true
) {
    fun policy() = PolicySettings(queuedCategories, allowedPackages, priorityContacts, allowCallsAndAlarms, allowOtp, filterPromotions)
}

enum class Flag(val key: Preferences.Key<Boolean>) {
    ONBOARDING_SEEN(booleanPreferencesKey("onboarding_seen")),
    ALLOW_CALLS(booleanPreferencesKey("allow_calls_alarms")),
    ALLOW_OTP(booleanPreferencesKey("allow_otp")),
    GROUP_REPEATED(booleanPreferencesKey("group_repeated")),
    SUPPRESS(booleanPreferencesKey("suppress_originals")),
    FILTER_PROMOS(booleanPreferencesKey("filter_promotions")),
    DEMO(booleanPreferencesKey("demo_enabled")),
    ENERGY(booleanPreferencesKey("energy_checkin_enabled"))
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val focusKey = intPreferencesKey("default_focus_min")
    private val breakKey = intPreferencesKey("break_min")
    private val catsKey = stringSetPreferencesKey("queued_categories")
    private val allowedKey = stringSetPreferencesKey("allowed_packages")
    private val contactsKey = stringSetPreferencesKey("priority_contacts")
    private val demoSeededKey = booleanPreferencesKey("demo_seeded")
    private val demoLiveIndexKey = intPreferencesKey("demo_live_index")

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
            energyCheckInEnabled = p[Flag.ENERGY.key] ?: d.energyCheckInEnabled)
    }.distinctUntilChanged()

    suspend fun current(): AttuneSettings = settings.first()

    suspend fun setFlag(f: Flag, v: Boolean) { ctx.dataStore.edit { it[f.key] = v } }
    suspend fun setFocusMin(v: Int) { ctx.dataStore.edit { it[focusKey] = v.coerceIn(5, 180) } }
    suspend fun setBreakMin(v: Int) { ctx.dataStore.edit { it[breakKey] = v.coerceIn(1, 60) } }
    suspend fun setCategoryQueued(cat: String, queued: Boolean) { ctx.dataStore.edit {
        val cur = it[catsKey] ?: AppCategoryMapper.DEFAULT_QUEUED; it[catsKey] = if (queued) cur + cat else cur - cat } }
    suspend fun setAppAllowed(pkg: String, allowed: Boolean) { ctx.dataStore.edit {
        val cur = it[allowedKey] ?: emptySet(); it[allowedKey] = if (allowed) cur + pkg else cur - pkg } }
    suspend fun addContact(name: String) { val n = name.trim(); if (n.isEmpty()) return
        ctx.dataStore.edit { it[contactsKey] = (it[contactsKey] ?: emptySet()) + n } }
    suspend fun removeContact(name: String) { ctx.dataStore.edit { it[contactsKey] = (it[contactsKey] ?: emptySet()) - name } }

    suspend fun isDemoSeeded() = ctx.dataStore.data.first()[demoSeededKey] ?: false
    suspend fun setDemoSeeded(v: Boolean) { ctx.dataStore.edit { it[demoSeededKey] = v } }
    suspend fun nextDemoLiveIndex(): Int { var i = 0; ctx.dataStore.edit { i = it[demoLiveIndexKey] ?: 0; it[demoLiveIndexKey] = i + 1 }; return i }
}
