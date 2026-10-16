package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BackupFrequency { DAILY, WEEKLY }

enum class AutoBackupError { FOLDER, WRITE, MISSING_KEY }

/** Sonido de fondo de la sesión (decisión 5). La vista previa suena en Ajustes; el uso es de la pieza 5. */
enum class SessionBackground { RAIN, BROWN, CAFE, NONE }

/** Rangos de las claves de la pieza 2 (spec §5.1.3). Se acotan al leer, así un valor fuera de rango nunca llega a la interfaz. */
object SettingsRanges {
    val HABI_MORNING = 6 * 60..11 * 60
    val HABI_NIGHT = 19 * 60..23 * 60 + 30
    val DAY_MINUTE = 0..24 * 60 - 1
    val POMO_FOCUS = 5..90
    val POMO_SHORT = 1..30
    val POMO_LONG = 5..45
    val POMO_ROUNDS = 2..8
    val LEVEL = 0..10
}

/**
 * App settings (tech doc §3, "DataStore"). Defaults marked provisional are
 * pending later sessions (economy/backup/M9 texts) and safe to change.
 */
data class Settings(
    val userName: String = "",
    val dayCutoffMinutes: Int = 0,
    val languageTag: String? = null,
    val globalReminderMinutes: List<Int> = emptyList(),
    val habiNightMinutes: Int = 21 * 60 + 30,
    val perfectDayCelebration: Boolean = true,
    val personality: Personality = Personality.NEUTRA,
    val backupFolderUri: String? = null,
    val backupFrequency: BackupFrequency = BackupFrequency.DAILY,
    val backupCopies: Int = 5,
    val backupEncryption: Boolean = false,
    val onboardingDone: Boolean = false,
    val habiSoundsEnabled: Boolean = true,
    val logSoundEnabled: Boolean = true,
    val logHapticEnabled: Boolean = true,
    val perfectDayCelebratedDay: Int = -1,
    val badgesSeenUntilMillis: Long = 0L,
    val lastAutoBackupAtMillis: Long? = null,
    val lastAutoBackupError: AutoBackupError? = null,
    val taskNoticesEnabled: Boolean = true,
    /** M11 (§4.6). Las 22:00 quedan a media hora del repaso y apuntan al modo Dormir. */
    val breathingReminderEnabled: Boolean = false,
    val breathingReminderTimeMinutes: Int = 22 * 60,
    val breathingMusicEnabled: Boolean = false,
    val breathingLastMode: BreathingMode = BreathingMode.CALM,
    /** Pieza 1 (D11): el máximo de agua que ha tenido el jardín. Las etapas no bajan aunque se corrija el pasado. */
    val gardenWaterHighMark: Double = 0.0,
    /** Pieza 4 (§10.2). Hora del aviso de Habi de la mañana (6:00 a 11:00); la de la noche es [habiNightMinutes]. */
    val habiMorningMinutes: Int = 8 * 60 + 30,
    /** No molestar propio (D10): solo se guarda hasta la pieza 5. */
    val dndEnabled: Boolean = false,
    val dndFromMinutes: Int = 23 * 60,
    val dndToMinutes: Int = 7 * 60 + 30,
    /** Pomodoro (D9): 4 bloques de 25 + 5 y descanso largo de 15. Solo se guarda hasta la pieza 5. */
    val pomoFocus: Int = 25,
    val pomoShort: Int = 5,
    val pomoLong: Int = 15,
    val pomoRounds: Int = 4,
    val pomoAutoBreak: Boolean = true,
    /** Interruptor general de sonido (decisión 2) y volúmenes por canal de 0 a 10 (D3). */
    val soundEnabled: Boolean = true,
    val volHabi: Int = 8,
    val volEffects: Int = 7,
    val volAmbient: Int = 6,
    val vibStrength: Int = 7,
    val sessionBackground: SessionBackground = SessionBackground.RAIN,
    /** «Reducir animaciones» propio; el del sistema (escala 0) se suma en `rememberReducido`. */
    val reduceMotion: Boolean = false,
    /** Pieza 3 (§6.1): lo último celebrado del libro. Fuera de la copia. */
    val celebratedLedgerMark: MarcaLibro? = null,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    companion object {
        /** GLOBAL reminder hours seeded once per install on a store that never had any: 12:00 and 19:00. */
        val DEFAULT_GLOBAL_REMINDER_MINUTES = listOf(12 * 60, 19 * 60)
    }

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val dayCutoffMinutes = intPreferencesKey("day_cutoff_minutes")
        val languageTag = stringPreferencesKey("language_tag")
        val globalReminderMinutes = stringPreferencesKey("global_reminder_minutes")
        val habiNightMinutes = intPreferencesKey("habi_night_minutes")
        val perfectDayCelebration = booleanPreferencesKey("perfect_day_celebration")
        val personality = stringPreferencesKey("personality")
        val backupFolderUri = stringPreferencesKey("backup_folder_uri")
        val backupFrequency = stringPreferencesKey("backup_frequency")
        val backupCopies = intPreferencesKey("backup_copies")
        val backupEncryption = booleanPreferencesKey("backup_encryption")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val habiSoundsEnabled = booleanPreferencesKey("habi_sounds_enabled")
        val logSoundEnabled = booleanPreferencesKey("log_sound_enabled")
        val logHapticEnabled = booleanPreferencesKey("log_haptic_enabled")
        val perfectDayCelebratedDay = intPreferencesKey("perfect_day_celebrated_day")
        val badgesSeenUntilMillis = longPreferencesKey("badges_seen_until_millis")
        val lastAutoBackupAtMillis = longPreferencesKey("last_auto_backup_at_millis")
        val lastAutoBackupError = stringPreferencesKey("last_auto_backup_error")
        val taskNoticesEnabled = booleanPreferencesKey("task_notices_enabled")
        val breathingReminderEnabled = booleanPreferencesKey("breathing_reminder_enabled")
        val breathingReminderTimeMinutes = intPreferencesKey("breathing_reminder_time_minutes")
        val breathingMusicEnabled = booleanPreferencesKey("breathing_music_enabled")
        val breathingLastMode = stringPreferencesKey("breathing_last_mode")
        val gardenWaterHighMark = doublePreferencesKey("garden_water_high_mark")
        val habiMorningMinutes = intPreferencesKey("habi_morning_minutes")
        val dndEnabled = booleanPreferencesKey("dnd_enabled")
        val dndFromMinutes = intPreferencesKey("dnd_from_minutes")
        val dndToMinutes = intPreferencesKey("dnd_to_minutes")
        val pomoFocus = intPreferencesKey("pomo_focus")
        val pomoShort = intPreferencesKey("pomo_short")
        val pomoLong = intPreferencesKey("pomo_long")
        val pomoRounds = intPreferencesKey("pomo_rounds")
        val pomoAutoBreak = booleanPreferencesKey("pomo_auto_break")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val volHabi = intPreferencesKey("vol_habi")
        val volEffects = intPreferencesKey("vol_effects")
        val volAmbient = intPreferencesKey("vol_ambient")
        val vibStrength = intPreferencesKey("vib_strength")
        val sessionBackground = stringPreferencesKey("session_background")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val celebratedLedgerMark = stringPreferencesKey("celebrated_ledger_mark")

        /**
         * One-shot marker for [seedDefaultReminders]. Deliberately NOT a [Settings] field: a
         * backup restore writes a whole [Settings] over the store
         * (`BackupSettings.toSettings()` builds a fresh one), and a field would come back at
         * its default there — re-seeding hours on top of whatever the restore brought. Living
         * outside [Settings], the marker survives every [update] and every restore untouched.
         */
        val defaultRemindersSeeded = booleanPreferencesKey("default_reminders_seeded")

        /**
         * One-shot marker for [claimNotificationPrompt], outside [Settings] for the same reason
         * as [defaultRemindersSeeded] — and here the "survives a restore" half matters twice
         * over: restoring a backup must not carry a *different* install's "already asked" over
         * to this device, where the permission has never been requested at all.
         */
        val notificationPromptClaimed = booleanPreferencesKey("notification_prompt_claimed")
    }

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> transform(prefs.toSettings()).writeTo(prefs) }
    }

    /**
     * Seeds [DEFAULT_GLOBAL_REMINDER_MINUTES] into [Settings.globalReminderMinutes], at most
     * once per install: a clean install has no reminder hours at all, so nothing ever fires
     * until someone visits Ajustes — this gives it a sensible midday + evening presence out of
     * the box. The seeded hours are ordinary configured hours (editable and deletable in
     * Ajustes), and the [Keys.defaultRemindersSeeded] marker guarantees they never resurrect:
     * not after the user deletes them, and not after a backup restore (see the key's doc).
     * A store that already has hours only gets the marker — the user configured, nothing to seed.
     * Single atomic edit, so no interleaving [update] can slip between check and write.
     */
    suspend fun seedDefaultReminders() {
        dataStore.edit { prefs ->
            if (prefs[Keys.defaultRemindersSeeded] == true) return@edit
            prefs[Keys.defaultRemindersSeeded] = true
            if (prefs[Keys.globalReminderMinutes].isNullOrEmpty()) {
                prefs[Keys.globalReminderMinutes] = DEFAULT_GLOBAL_REMINDER_MINUTES.joinToString(",")
            }
        }
    }

    /**
     * Whether [claimNotificationPrompt] has already been spent, without spending it — the claim
     * itself is destructive, so this is the only way to ask "has the app asked yet?" (tests
     * asserting the prompt fired, and anything that later wants to reason about it, read here).
     */
    internal val notificationPromptClaimed: Flow<Boolean> =
        dataStore.data.map { it[Keys.notificationPromptClaimed] == true }

    /**
     * Claims the single POST_NOTIFICATIONS prompt an install gets: returns true exactly once,
     * false on every later call. Android itself stops showing the system dialog after two
     * refusals, so the app only ever asks unprompted once — after that the notice in Ajustes
     * (which links to the app's notification settings) is the recovery path, not another dialog.
     *
     * Claimed in the same atomic edit that reads the marker, so two callers racing on different
     * routes can't both come back true. The `claimed` flag is written from inside the edit
     * block rather than derived from the returned [Preferences] — which can't tell "this call
     * set it" from "it was already set" — and DataStore applies this transform through its
     * single writer, so it lands once per call; a failed write throws out of [dataStore.edit]
     * instead of returning a claim the store never took.
     */
    suspend fun claimNotificationPrompt(): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            if (prefs[Keys.notificationPromptClaimed] == true) {
                claimed = false
                return@edit
            }
            prefs[Keys.notificationPromptClaimed] = true
            claimed = true
        }
        return claimed
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
            habiNightMinutes = (this[Keys.habiNightMinutes] ?: defaults.habiNightMinutes).coerceIn(SettingsRanges.HABI_NIGHT),
            perfectDayCelebration = this[Keys.perfectDayCelebration] ?: defaults.perfectDayCelebration,
            personality = this[Keys.personality]?.let(Personality::valueOf) ?: defaults.personality,
            backupFolderUri = this[Keys.backupFolderUri],
            backupFrequency = this[Keys.backupFrequency]?.let(BackupFrequency::valueOf) ?: defaults.backupFrequency,
            backupCopies = this[Keys.backupCopies] ?: defaults.backupCopies,
            backupEncryption = this[Keys.backupEncryption] ?: defaults.backupEncryption,
            onboardingDone = this[Keys.onboardingDone] ?: defaults.onboardingDone,
            habiSoundsEnabled = this[Keys.habiSoundsEnabled] ?: defaults.habiSoundsEnabled,
            logSoundEnabled = this[Keys.logSoundEnabled] ?: defaults.logSoundEnabled,
            logHapticEnabled = this[Keys.logHapticEnabled] ?: defaults.logHapticEnabled,
            perfectDayCelebratedDay = this[Keys.perfectDayCelebratedDay] ?: defaults.perfectDayCelebratedDay,
            badgesSeenUntilMillis = this[Keys.badgesSeenUntilMillis] ?: defaults.badgesSeenUntilMillis,
            lastAutoBackupAtMillis = this[Keys.lastAutoBackupAtMillis],
            lastAutoBackupError = this[Keys.lastAutoBackupError]?.let(AutoBackupError::valueOf),
            taskNoticesEnabled = this[Keys.taskNoticesEnabled] ?: defaults.taskNoticesEnabled,
            breathingReminderEnabled = this[Keys.breathingReminderEnabled] ?: defaults.breathingReminderEnabled,
            breathingReminderTimeMinutes = this[Keys.breathingReminderTimeMinutes] ?: defaults.breathingReminderTimeMinutes,
            breathingMusicEnabled = this[Keys.breathingMusicEnabled] ?: defaults.breathingMusicEnabled,
            // Por nombre: un modo que esta version no conoce (una copia de una version futura, un
            // fichero tocado a mano) cae a CALM en vez de tumbar la lectura de todos los ajustes.
            breathingLastMode =
                this[Keys.breathingLastMode]?.let { name -> runCatching { BreathingMode.valueOf(name) }.getOrNull() }
                    ?: defaults.breathingLastMode,
            gardenWaterHighMark = this[Keys.gardenWaterHighMark] ?: defaults.gardenWaterHighMark,
            habiMorningMinutes = (this[Keys.habiMorningMinutes] ?: defaults.habiMorningMinutes).coerceIn(SettingsRanges.HABI_MORNING),
            dndEnabled = this[Keys.dndEnabled] ?: defaults.dndEnabled,
            dndFromMinutes = (this[Keys.dndFromMinutes] ?: defaults.dndFromMinutes).coerceIn(SettingsRanges.DAY_MINUTE),
            dndToMinutes = (this[Keys.dndToMinutes] ?: defaults.dndToMinutes).coerceIn(SettingsRanges.DAY_MINUTE),
            pomoFocus = (this[Keys.pomoFocus] ?: defaults.pomoFocus).coerceIn(SettingsRanges.POMO_FOCUS),
            pomoShort = (this[Keys.pomoShort] ?: defaults.pomoShort).coerceIn(SettingsRanges.POMO_SHORT),
            pomoLong = (this[Keys.pomoLong] ?: defaults.pomoLong).coerceIn(SettingsRanges.POMO_LONG),
            pomoRounds = (this[Keys.pomoRounds] ?: defaults.pomoRounds).coerceIn(SettingsRanges.POMO_ROUNDS),
            pomoAutoBreak = this[Keys.pomoAutoBreak] ?: defaults.pomoAutoBreak,
            soundEnabled = this[Keys.soundEnabled] ?: defaults.soundEnabled,
            volHabi = (this[Keys.volHabi] ?: defaults.volHabi).coerceIn(SettingsRanges.LEVEL),
            volEffects = (this[Keys.volEffects] ?: defaults.volEffects).coerceIn(SettingsRanges.LEVEL),
            volAmbient = (this[Keys.volAmbient] ?: defaults.volAmbient).coerceIn(SettingsRanges.LEVEL),
            vibStrength = (this[Keys.vibStrength] ?: defaults.vibStrength).coerceIn(SettingsRanges.LEVEL),
            sessionBackground =
                this[Keys.sessionBackground]?.let { name -> runCatching { SessionBackground.valueOf(name) }.getOrNull() }
                    ?: defaults.sessionBackground,
            reduceMotion = this[Keys.reduceMotion] ?: defaults.reduceMotion,
            celebratedLedgerMark = MarcaLibro.decodificar(this[Keys.celebratedLedgerMark]),
        )
    }

    private fun Settings.writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[Keys.userName] = userName
        prefs[Keys.dayCutoffMinutes] = dayCutoffMinutes
        languageTag?.let { prefs[Keys.languageTag] = it } ?: prefs.remove(Keys.languageTag)
        prefs[Keys.globalReminderMinutes] = globalReminderMinutes.joinToString(",")
        prefs[Keys.habiNightMinutes] = habiNightMinutes
        prefs[Keys.perfectDayCelebration] = perfectDayCelebration
        prefs[Keys.personality] = personality.name
        backupFolderUri?.let { prefs[Keys.backupFolderUri] = it } ?: prefs.remove(Keys.backupFolderUri)
        prefs[Keys.backupFrequency] = backupFrequency.name
        prefs[Keys.backupCopies] = backupCopies
        prefs[Keys.backupEncryption] = backupEncryption
        prefs[Keys.onboardingDone] = onboardingDone
        prefs[Keys.habiSoundsEnabled] = habiSoundsEnabled
        prefs[Keys.logSoundEnabled] = logSoundEnabled
        prefs[Keys.logHapticEnabled] = logHapticEnabled
        prefs[Keys.perfectDayCelebratedDay] = perfectDayCelebratedDay
        prefs[Keys.badgesSeenUntilMillis] = badgesSeenUntilMillis
        lastAutoBackupAtMillis?.let { prefs[Keys.lastAutoBackupAtMillis] = it } ?: prefs.remove(Keys.lastAutoBackupAtMillis)
        lastAutoBackupError?.let { prefs[Keys.lastAutoBackupError] = it.name } ?: prefs.remove(Keys.lastAutoBackupError)
        prefs[Keys.taskNoticesEnabled] = taskNoticesEnabled
        prefs[Keys.breathingReminderEnabled] = breathingReminderEnabled
        prefs[Keys.breathingReminderTimeMinutes] = breathingReminderTimeMinutes
        prefs[Keys.breathingMusicEnabled] = breathingMusicEnabled
        prefs[Keys.breathingLastMode] = breathingLastMode.name
        prefs[Keys.gardenWaterHighMark] = gardenWaterHighMark
        prefs[Keys.habiMorningMinutes] = habiMorningMinutes
        prefs[Keys.dndEnabled] = dndEnabled
        prefs[Keys.dndFromMinutes] = dndFromMinutes
        prefs[Keys.dndToMinutes] = dndToMinutes
        prefs[Keys.pomoFocus] = pomoFocus
        prefs[Keys.pomoShort] = pomoShort
        prefs[Keys.pomoLong] = pomoLong
        prefs[Keys.pomoRounds] = pomoRounds
        prefs[Keys.pomoAutoBreak] = pomoAutoBreak
        prefs[Keys.soundEnabled] = soundEnabled
        prefs[Keys.volHabi] = volHabi
        prefs[Keys.volEffects] = volEffects
        prefs[Keys.volAmbient] = volAmbient
        prefs[Keys.vibStrength] = vibStrength
        prefs[Keys.sessionBackground] = sessionBackground.name
        prefs[Keys.reduceMotion] = reduceMotion
        celebratedLedgerMark?.let { prefs[Keys.celebratedLedgerMark] = it.codificar() } ?: prefs.remove(Keys.celebratedLedgerMark)
    }
}
