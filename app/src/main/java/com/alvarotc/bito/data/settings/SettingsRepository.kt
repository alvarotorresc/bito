package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BackupFrequency { DAILY, WEEKLY }

/**
 * App settings (tech doc §3, "DataStore"). Defaults marked provisional are
 * pending later sessions (economy/backup/M9 texts) and safe to change.
 */
data class Settings(
    val userName: String = "",
    val dayCutoffMinutes: Int = 0,
    val languageTag: String? = null,
    val globalReminderMinutes: List<Int> = emptyList(),
    val reviewTimeMinutes: Int = 21 * 60 + 30,
    val perfectDayCelebration: Boolean = true,
    val personality: Personality = Personality.NEUTRA,
    val backupFolderUri: String? = null,
    val backupFrequency: BackupFrequency = BackupFrequency.DAILY,
    val backupCopies: Int = 5,
    val backupEncryption: Boolean = false,
    val onboardingDone: Boolean = false,
    val habiSoundsEnabled: Boolean = true,
    val perfectDayCelebratedDay: Int = -1,
    val badgesSeenUntilMillis: Long = 0L,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val dayCutoffMinutes = intPreferencesKey("day_cutoff_minutes")
        val languageTag = stringPreferencesKey("language_tag")
        val globalReminderMinutes = stringPreferencesKey("global_reminder_minutes")
        val reviewTimeMinutes = intPreferencesKey("review_time_minutes")
        val perfectDayCelebration = booleanPreferencesKey("perfect_day_celebration")
        val personality = stringPreferencesKey("personality")
        val backupFolderUri = stringPreferencesKey("backup_folder_uri")
        val backupFrequency = stringPreferencesKey("backup_frequency")
        val backupCopies = intPreferencesKey("backup_copies")
        val backupEncryption = booleanPreferencesKey("backup_encryption")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val habiSoundsEnabled = booleanPreferencesKey("habi_sounds_enabled")
        val perfectDayCelebratedDay = intPreferencesKey("perfect_day_celebrated_day")
        val badgesSeenUntilMillis = longPreferencesKey("badges_seen_until_millis")
    }

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> transform(prefs.toSettings()).writeTo(prefs) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            userName = this[Keys.userName] ?: defaults.userName,
            dayCutoffMinutes = this[Keys.dayCutoffMinutes] ?: defaults.dayCutoffMinutes,
            languageTag = this[Keys.languageTag],
            globalReminderMinutes =
                this[Keys.globalReminderMinutes]
                    ?.split(",")
                    ?.filter { it.isNotEmpty() }
                    ?.map { it.toInt() }
                    ?: defaults.globalReminderMinutes,
            reviewTimeMinutes = this[Keys.reviewTimeMinutes] ?: defaults.reviewTimeMinutes,
            perfectDayCelebration = this[Keys.perfectDayCelebration] ?: defaults.perfectDayCelebration,
            personality = this[Keys.personality]?.let(Personality::valueOf) ?: defaults.personality,
            backupFolderUri = this[Keys.backupFolderUri],
            backupFrequency = this[Keys.backupFrequency]?.let(BackupFrequency::valueOf) ?: defaults.backupFrequency,
            backupCopies = this[Keys.backupCopies] ?: defaults.backupCopies,
            backupEncryption = this[Keys.backupEncryption] ?: defaults.backupEncryption,
            onboardingDone = this[Keys.onboardingDone] ?: defaults.onboardingDone,
            habiSoundsEnabled = this[Keys.habiSoundsEnabled] ?: defaults.habiSoundsEnabled,
            perfectDayCelebratedDay = this[Keys.perfectDayCelebratedDay] ?: defaults.perfectDayCelebratedDay,
            badgesSeenUntilMillis = this[Keys.badgesSeenUntilMillis] ?: defaults.badgesSeenUntilMillis,
        )
    }

    private fun Settings.writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[Keys.userName] = userName
        prefs[Keys.dayCutoffMinutes] = dayCutoffMinutes
        languageTag?.let { prefs[Keys.languageTag] = it } ?: prefs.remove(Keys.languageTag)
        prefs[Keys.globalReminderMinutes] = globalReminderMinutes.joinToString(",")
        prefs[Keys.reviewTimeMinutes] = reviewTimeMinutes
        prefs[Keys.perfectDayCelebration] = perfectDayCelebration
        prefs[Keys.personality] = personality.name
        backupFolderUri?.let { prefs[Keys.backupFolderUri] = it } ?: prefs.remove(Keys.backupFolderUri)
        prefs[Keys.backupFrequency] = backupFrequency.name
        prefs[Keys.backupCopies] = backupCopies
        prefs[Keys.backupEncryption] = backupEncryption
        prefs[Keys.onboardingDone] = onboardingDone
        prefs[Keys.habiSoundsEnabled] = habiSoundsEnabled
        prefs[Keys.perfectDayCelebratedDay] = perfectDayCelebratedDay
        prefs[Keys.badgesSeenUntilMillis] = badgesSeenUntilMillis
    }
}
