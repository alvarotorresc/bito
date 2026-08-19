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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupCodecTest {
    private fun sampleFile() =
        BackupFile(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            appVersion = "0.3.0",
            exportedAt = "2026-08-15T10:00:00Z",
            habits =
                listOf(
                    BackupHabit(
                        id = "agua", name = "Beber agua", metric = Metric.COUNT, period = Period.DAY,
                        direction = Direction.AT_LEAST, target = 8, unit = "vasos", logMode = LogMode.COUNTER,
                        step = 1, timeBucket = TimeBucket.MORNING, timeOfDayMinutes = null,
                        reminderMinutes = null, status = HabitStatus.ACTIVE, createdAtMillis = 1L,
                        createdOnDay = 0, archivedAtMillis = null, archivedOnDay = null, sortOrder = 0,
                    ),
                ),
            targetChanges = listOf(BackupTargetChange("agua", 0, 8)),
            pauseIntervals = listOf(BackupPauseInterval("agua", 3, null, "viaje")),
            entries = listOf(BackupEntry("e1", "agua", 0, 3, 2L)),
            daySeals = listOf(BackupDaySeal(0, 3L)),
            pointsLedger = listOf(BackupPointsEntry("p1", 1, PointsReason.HABIT_DONE, "agua:0", 0, 4L)),
            freezerUses = listOf(BackupFreezerUse("f1", "agua", 2, 5L)),
            badges = listOf(BackupBadge("first-habit", 6L)),
            customizationItems = listOf(BackupCustomizationItem("hat", CustomizationCategory.UPPER, 7L, true)),
            settings =
                BackupSettings(
                    userName = "Alvaro", dayCutoffMinutes = 180, languageTag = "es",
                    globalReminderMinutes = listOf(540, 1290), reviewTimeMinutes = 1290,
                    perfectDayCelebration = false, personality = Personality.SARGENTO,
                    backupFolderUri = null, backupFrequency = BackupFrequency.WEEKLY,
                    backupCopies = 3, backupEncryption = false, onboardingDone = true,
                ),
        )

    @Test
    fun `encode then decode returns an identical file`() {
        val file = sampleFile()
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun `encoded backup is human readable json`() {
        val text = BackupCodec.encode(sampleFile())
        assertTrue(text.contains("\n")) // pretty-printed (§5.1 data sovereignty)
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertTrue(text.contains("\"Beber agua\""))
    }

    @Test
    fun `decode rejects garbage`() {
        assertFailsWith<BackupFormatException> { BackupCodec.decode("not json at all") }
        assertFailsWith<BackupFormatException> { BackupCodec.decode("{\"schemaVersion\": 1}") }
    }

    @Test
    fun `decode rejects a newer schema version`() {
        val newer = BackupCodec.encode(sampleFile().copy(schemaVersion = 99))
        assertFailsWith<BackupFormatException> { BackupCodec.decode(newer) }
    }

    @Test
    fun `preview counts what the confirmation sheet shows`() {
        val preview = BackupCodec.decode(BackupCodec.encode(sampleFile())).toPreview()
        assertEquals(BackupPreview("2026-08-15T10:00:00Z", "0.3.0", 1, 1), preview)
    }
}
