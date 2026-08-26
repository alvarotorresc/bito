package com.alvarotc.bito.data.backup

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.db.PauseIntervalEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.db.TargetChangeEntity
import com.alvarotc.bito.data.settings.Settings

/** Entity <-> backup DTO conversions, field-by-field, both directions (tech doc §5.1). */
fun HabitEntity.toBackup() =
    BackupHabit(
        id = id,
        name = name,
        metric = metric,
        period = period,
        direction = direction,
        target = target,
        unit = unit,
        logMode = logMode,
        step = step,
        timeBucket = timeBucket,
        timeOfDayMinutes = timeOfDayMinutes,
        reminderMinutes = reminderMinutes,
        status = status,
        createdAtMillis = createdAtMillis,
        createdOnDay = createdOnDay,
        archivedAtMillis = archivedAtMillis,
        archivedOnDay = archivedOnDay,
        sortOrder = sortOrder,
    )

fun BackupHabit.toEntity() =
    HabitEntity(
        id = id,
        name = name,
        metric = metric,
        period = period,
        direction = direction,
        target = target,
        unit = unit,
        logMode = logMode,
        step = step,
        timeBucket = timeBucket,
        timeOfDayMinutes = timeOfDayMinutes,
        reminderMinutes = reminderMinutes,
        status = status,
        createdAtMillis = createdAtMillis,
        createdOnDay = createdOnDay,
        archivedAtMillis = archivedAtMillis,
        archivedOnDay = archivedOnDay,
        sortOrder = sortOrder,
    )

fun TargetChangeEntity.toBackup() = BackupTargetChange(habitId = habitId, effectiveFromDay = effectiveFromDay, target = target)

fun BackupTargetChange.toEntity() = TargetChangeEntity(habitId = habitId, effectiveFromDay = effectiveFromDay, target = target)

fun PauseIntervalEntity.toBackup() = BackupPauseInterval(habitId = habitId, startDay = startDay, endDay = endDay, note = note)

fun BackupPauseInterval.toEntity() = PauseIntervalEntity(habitId = habitId, startDay = startDay, endDay = endDay, note = note)

fun EntryEntity.toBackup() =
    BackupEntry(id = id, habitId = habitId, logicalDay = logicalDay, value = value, createdAtMillis = createdAtMillis)

fun BackupEntry.toEntity() =
    EntryEntity(id = id, habitId = habitId, logicalDay = logicalDay, value = value, createdAtMillis = createdAtMillis)

fun DaySealEntity.toBackup() = BackupDaySeal(logicalDay = logicalDay, sealedAtMillis = sealedAtMillis)

fun BackupDaySeal.toEntity() = DaySealEntity(logicalDay = logicalDay, sealedAtMillis = sealedAtMillis)

fun PointsLedgerEntity.toBackup() =
    BackupPointsEntry(
        id = id,
        delta = delta,
        reason = reason,
        refId = refId,
        logicalDay = logicalDay,
        createdAtMillis = createdAtMillis,
    )

fun BackupPointsEntry.toEntity() =
    PointsLedgerEntity(
        id = id,
        delta = delta,
        reason = reason,
        refId = refId,
        logicalDay = logicalDay,
        createdAtMillis = createdAtMillis,
    )

fun FreezerUseEntity.toBackup() = BackupFreezerUse(id = id, habitId = habitId, protectedDay = protectedDay, usedAtMillis = usedAtMillis)

fun BackupFreezerUse.toEntity() = FreezerUseEntity(id = id, habitId = habitId, protectedDay = protectedDay, usedAtMillis = usedAtMillis)

fun BadgeEntity.toBackup() = BackupBadge(badgeId = badgeId, unlockedAtMillis = unlockedAtMillis)

fun BackupBadge.toEntity() = BadgeEntity(badgeId = badgeId, unlockedAtMillis = unlockedAtMillis)

fun CustomizationItemEntity.toBackup() =
    BackupCustomizationItem(itemId = itemId, category = category, acquiredAtMillis = acquiredAtMillis, equipped = equipped)

fun BackupCustomizationItem.toEntity() =
    CustomizationItemEntity(itemId = itemId, category = category, acquiredAtMillis = acquiredAtMillis, equipped = equipped)

fun Settings.toBackup() =
    BackupSettings(
        userName = userName,
        dayCutoffMinutes = dayCutoffMinutes,
        languageTag = languageTag,
        globalReminderMinutes = globalReminderMinutes,
        reviewTimeMinutes = reviewTimeMinutes,
        perfectDayCelebration = perfectDayCelebration,
        personality = personality,
        backupFolderUri = backupFolderUri,
        backupFrequency = backupFrequency,
        backupCopies = backupCopies,
        backupEncryption = backupEncryption,
        onboardingDone = onboardingDone,
        habiSoundsEnabled = habiSoundsEnabled,
        perfectDayCelebratedDay = perfectDayCelebratedDay,
        badgesSeenUntilMillis = badgesSeenUntilMillis,
        logSoundEnabled = logSoundEnabled,
        logHapticEnabled = logHapticEnabled,
    )

fun BackupSettings.toSettings() =
    Settings(
        userName = userName,
        dayCutoffMinutes = dayCutoffMinutes,
        languageTag = languageTag,
        globalReminderMinutes = globalReminderMinutes,
        reviewTimeMinutes = reviewTimeMinutes,
        perfectDayCelebration = perfectDayCelebration,
        personality = personality,
        backupFolderUri = backupFolderUri,
        backupFrequency = backupFrequency,
        backupCopies = backupCopies,
        backupEncryption = backupEncryption,
        onboardingDone = onboardingDone,
        habiSoundsEnabled = habiSoundsEnabled,
        perfectDayCelebratedDay = perfectDayCelebratedDay,
        badgesSeenUntilMillis = badgesSeenUntilMillis,
        logSoundEnabled = logSoundEnabled,
        logHapticEnabled = logHapticEnabled,
    )
