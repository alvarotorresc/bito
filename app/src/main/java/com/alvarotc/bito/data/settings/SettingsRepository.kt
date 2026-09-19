package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alvarotc.bito.domain.EyeRitual
import com.alvarotc.bito.domain.EyeTransition
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class BackupFrequency { DAILY, WEEKLY }

enum class AutoBackupError { FOLDER, WRITE, MISSING_KEY }

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
    val logSoundEnabled: Boolean = true,
    val logHapticEnabled: Boolean = true,
    val perfectDayCelebratedDay: Int = -1,
    val badgesSeenUntilMillis: Long = 0L,
    val lastAutoBackupAtMillis: Long? = null,
    val lastAutoBackupError: AutoBackupError? = null,
    /** 0/1/2 — caché del ritual del ojo (EyeRitual). No es la verdad: se auto-sana desde el historial. */
    val habiEyesPainted: Int = 0,
    /**
     * Día lógico en el que ya sonó la línea de «esperando el cierre» de la pantalla Habi (biblia
     * §12.2) — mismo patrón que [perfectDayCelebratedDay]: -1 = nunca se dijo. WAITING se entra
     * varias veces el mismo día (completar, desmarcar, volver a completar); sin este marcador la
     * línea se repetiría cada vez.
     */
    val habiWaitingSaidDay: Int = -1,
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
        val reviewTimeMinutes = intPreferencesKey("review_time_minutes")
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
        val habiEyesPainted = intPreferencesKey("habi_eyes_painted")
        val habiWaitingSaidDay = intPreferencesKey("habi_waiting_said_day")

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

        /**
         * Marcador de un solo uso escrito por [com.alvarotc.bito.data.backup.BackupRepository.import]:
         * el primer [com.alvarotc.bito.data.repo.PointsReconciler] posterior sana el nivel de ojo
         * EN SILENCIO (biblia §4: restaurar un backup con vida ya vivida no repite el rito). El
         * primer reconcile posterior al restore lo gasta SIEMPRE, sane o no sane algo en ese mismo
         * pase — ver `PointsReconciler.healEyeRitual`. Fuera de [Settings] por la misma razón que
         * [defaultRemindersSeeded]: el restore escribe un [Settings] entero sobre el almacén, y un
         * campo volvería a su default justo ahí.
         */
        val silentEyeHealPending = booleanPreferencesKey("silent_eye_heal_pending")
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

    /** Arma la sanación silenciosa del ritual del ojo; la llama el restore, nadie más. */
    suspend fun markRestoredForSilentEyes() {
        dataStore.edit { prefs -> prefs[Keys.silentEyeHealPending] = true }
    }

    /**
     * Gasta el marcador: devuelve true exactamente una vez tras un restore, false siempre después.
     * Reclamado dentro del mismo `edit` que lo lee, así dos reconciles simultáneos no pueden
     * llevarse los dos un true.
     */
    suspend fun claimSilentEyeHeal(): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            if (prefs[Keys.silentEyeHealPending] != true) {
                claimed = false
                return@edit
            }
            prefs.remove(Keys.silentEyeHealPending)
            claimed = true
        }
        return claimed
    }

    /**
     * Sana [Settings.habiEyesPainted] hasta [derived] y devuelve la transición SOLO si subió.
     * Monótono por [EyeRitual.heal] (nunca baja) e idempotente (un segundo pase devuelve null).
     *
     * Leer, comparar y escribir dentro de UN solo `edit`, igual que [claimNotificationPrompt]: un
     * read-modify-write repartido en dos operaciones deja que dos reconciles simultáneos lean el
     * mismo nivel guardado y se lleven los dos la misma transición — el rito del ojo se pintaría
     * dos veces. La transición se compone dentro del bloque, con el `stored` que ESE `edit` leyó.
     */
    suspend fun healEyeLevel(derived: Int): EyeTransition? {
        var transition: EyeTransition? = null
        dataStore.edit { prefs ->
            val stored = prefs[Keys.habiEyesPainted] ?: Settings().habiEyesPainted
            val healed = EyeRitual.heal(stored, derived)
            if (healed == stored) {
                transition = null
                return@edit
            }
            prefs[Keys.habiEyesPainted] = healed
            transition = EyeTransition(stored, healed)
        }
        return transition
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
            logSoundEnabled = this[Keys.logSoundEnabled] ?: defaults.logSoundEnabled,
            logHapticEnabled = this[Keys.logHapticEnabled] ?: defaults.logHapticEnabled,
            perfectDayCelebratedDay = this[Keys.perfectDayCelebratedDay] ?: defaults.perfectDayCelebratedDay,
            badgesSeenUntilMillis = this[Keys.badgesSeenUntilMillis] ?: defaults.badgesSeenUntilMillis,
            lastAutoBackupAtMillis = this[Keys.lastAutoBackupAtMillis],
            lastAutoBackupError = this[Keys.lastAutoBackupError]?.let(AutoBackupError::valueOf),
            habiEyesPainted = this[Keys.habiEyesPainted] ?: defaults.habiEyesPainted,
            habiWaitingSaidDay = this[Keys.habiWaitingSaidDay] ?: defaults.habiWaitingSaidDay,
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
        prefs[Keys.logSoundEnabled] = logSoundEnabled
        prefs[Keys.logHapticEnabled] = logHapticEnabled
        prefs[Keys.perfectDayCelebratedDay] = perfectDayCelebratedDay
        prefs[Keys.badgesSeenUntilMillis] = badgesSeenUntilMillis
        lastAutoBackupAtMillis?.let { prefs[Keys.lastAutoBackupAtMillis] = it } ?: prefs.remove(Keys.lastAutoBackupAtMillis)
        lastAutoBackupError?.let { prefs[Keys.lastAutoBackupError] = it.name } ?: prefs.remove(Keys.lastAutoBackupError)
        prefs[Keys.habiEyesPainted] = habiEyesPainted
        prefs[Keys.habiWaitingSaidDay] = habiWaitingSaidDay
    }
}
