package com.alvarotc.bito.data.backup

import com.alvarotc.bito.data.db.TimeBucket
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.serialization.Serializable

/**
 * The backup wire format (tech doc §5.1) — versioned independently of Room.
 * DTOs are deliberately separate from entities: storage may be refactored,
 * this file may not. A change that older files cannot express correctly on
 * their own bumps SCHEMA_VERSION and gets a migration in BackupCodec.decode;
 * a new field whose default already means the right thing for files written
 * before it existed needs neither.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val habits: List<BackupHabit>,
    val targetChanges: List<BackupTargetChange>,
    val pauseIntervals: List<BackupPauseInterval>,
    val entries: List<BackupEntry>,
    val daySeals: List<BackupDaySeal>,
    val pointsLedger: List<BackupPointsEntry>,
    val freezerUses: List<BackupFreezerUse>,
    val badges: List<BackupBadge>,
    val customizationItems: List<BackupCustomizationItem>,
    val settings: BackupSettings,
)

@Serializable
data class BackupHabit(
    val id: String,
    val name: String,
    val metric: Metric,
    val period: Period,
    val direction: Direction,
    val target: Int,
    val unit: String?,
    val logMode: LogMode,
    val step: Int,
    val timeBucket: TimeBucket?,
    val timeOfDayMinutes: Int?,
    val reminderMinutes: Int?,
    val status: HabitStatus,
    val createdAtMillis: Long,
    val createdOnDay: Int,
    val archivedAtMillis: Long?,
    val archivedOnDay: Int?,
    val sortOrder: Int,
)

@Serializable
data class BackupTargetChange(
    val habitId: String,
    val effectiveFromDay: Int,
    val target: Int,
)

@Serializable
data class BackupPauseInterval(
    val habitId: String,
    val startDay: Int,
    val endDay: Int?,
    val note: String?,
)

@Serializable
data class BackupEntry(
    val id: String,
    val habitId: String,
    val logicalDay: Int,
    val value: Int,
    val createdAtMillis: Long,
)

@Serializable
data class BackupDaySeal(
    val logicalDay: Int,
    val sealedAtMillis: Long,
)

@Serializable
data class BackupPointsEntry(
    val id: String,
    val delta: Int,
    val reason: PointsReason,
    val refId: String?,
    val logicalDay: Int,
    val createdAtMillis: Long,
)

@Serializable
data class BackupFreezerUse(
    val id: String,
    val habitId: String,
    val protectedDay: Int,
    val usedAtMillis: Long,
)

@Serializable
data class BackupBadge(
    val badgeId: String,
    val unlockedAtMillis: Long,
)

@Serializable
data class BackupCustomizationItem(
    val itemId: String,
    val category: CustomizationCategory,
    val acquiredAtMillis: Long,
    val equipped: Boolean,
)

/**
 * Every [com.alvarotc.bito.data.settings.Settings] field a restore must bring back — import
 * builds a FRESH Settings from this DTO (`toSettings()`), so anything missing here silently
 * reverts to its default on every restore. The only deliberate absentees are
 * `lastAutoBackupAtMillis` / `lastAutoBackupError`: device-local status about THIS install's
 * auto-backup, meaningless on the phone the file is restored to (pinned by BackupRoundTripTest's
 * "local auto backup state does not travel in exports"). Adding a field here needs no schema bump
 * as long as it carries a default that is right for files written before it existed.
 */
@Serializable
data class BackupSettings(
    val userName: String,
    val dayCutoffMinutes: Int,
    val languageTag: String?,
    val globalReminderMinutes: List<Int>,
    val reviewTimeMinutes: Int,
    val perfectDayCelebration: Boolean,
    val personality: Personality,
    val backupFolderUri: String?,
    val backupFrequency: BackupFrequency,
    val backupCopies: Int,
    val backupEncryption: Boolean,
    val onboardingDone: Boolean,
    val habiSoundsEnabled: Boolean = true,
    val perfectDayCelebratedDay: Int = -1,
    val badgesSeenUntilMillis: Long = 0L,
    val logSoundEnabled: Boolean = true,
    val logHapticEnabled: Boolean = true,
)

/** What the restore confirmation shows before anything is overwritten (§5.4). */
data class BackupPreview(
    val exportedAt: String,
    val appVersion: String,
    val habits: Int,
    val entries: Int,
)

fun BackupFile.toPreview() = BackupPreview(exportedAt, appVersion, habits.size, entries.size)
