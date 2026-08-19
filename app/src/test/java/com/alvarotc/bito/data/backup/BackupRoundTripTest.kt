package com.alvarotc.bito.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.TimeBucket
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.targetChangeEntity
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var backup: BackupRepository

    private val seededSettings =
        Settings(
            userName = "Álvaro",
            dayCutoffMinutes = 180,
            languageTag = "es",
            globalReminderMinutes = listOf(8 * 60, 22 * 60),
            reviewTimeMinutes = 22 * 60,
            perfectDayCelebration = false,
            personality = Personality.SARGENTO,
            backupFolderUri = "content://tree/backups",
            backupFrequency = BackupFrequency.WEEKLY,
            backupCopies = 3,
            backupEncryption = true,
            onboardingDone = true,
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            ) { context.filesDir.resolve("t-${UUID.randomUUID()}.preferences_pb") }
        settingsRepo = SettingsRepository(store)
        backup = BackupRepository(db, settingsRepo, "0.3.0-test")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedEverything() {
        // Habit with every nullable set.
        db.habitDao().upsert(
            habitEntity(
                id = "h1",
                unit = "vasos",
                timeBucket = TimeBucket.MORNING,
                reminderMinutes = 30,
                status = HabitStatus.ARCHIVED,
                archivedAtMillis = 5_000L,
                archivedOnDay = DAY_ZERO + 20,
                sortOrder = 1,
            ),
        )
        // Habit with all nullables null.
        db.habitDao().upsert(
            habitEntity(
                id = "h2",
                unit = null,
                timeBucket = null,
                timeOfDayMinutes = null,
                reminderMinutes = null,
                status = HabitStatus.ACTIVE,
                archivedAtMillis = null,
                archivedOnDay = null,
                sortOrder = 0,
            ),
        )

        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1", effectiveFromDay = DAY_ZERO + 5, target = 10))
        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1", effectiveFromDay = DAY_ZERO, target = 8))
        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h2", effectiveFromDay = DAY_ZERO, target = 3))

        // One closed pause with a note, one open pause without.
        db.pauseIntervalDao().upsert(
            pauseIntervalEntity(habitId = "h1", startDay = DAY_ZERO, endDay = DAY_ZERO + 3, note = "viaje"),
        )
        db.pauseIntervalDao().upsert(
            pauseIntervalEntity(habitId = "h1", startDay = DAY_ZERO + 10, endDay = null, note = null),
        )

        db.entryDao().insert(entryEntity(id = "e2", habitId = "h1", logicalDay = DAY_ZERO + 1, value = 2))
        db.entryDao().insert(entryEntity(id = "e1", habitId = "h2", logicalDay = DAY_ZERO, value = 1))

        db.daySealDao().insert(daySealEntity(logicalDay = DAY_ZERO + 1, sealedAtMillis = 2_000L))
        db.daySealDao().insert(daySealEntity(logicalDay = DAY_ZERO, sealedAtMillis = 1_000L))

        // Ledger rows: one with refId, one without.
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(id = "p1", reason = PointsReason.HABIT_DONE, refId = "h1:$DAY_ZERO", logicalDay = DAY_ZERO),
        )
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(id = "p2", reason = PointsReason.BUY_ITEM, refId = null, logicalDay = DAY_ZERO + 1),
        )

        db.freezerUseDao().insert(freezerUseEntity(id = "f1", habitId = "h1", protectedDay = DAY_ZERO))
        db.badgeDao().insert(BadgeEntity("first-week", 6_000L))
        db.customizationItemDao().upsert(
            customizationItemEntity(itemId = "hat-basic", category = CustomizationCategory.UPPER, equipped = true),
        )

        settingsRepo.update { seededSettings }
    }

    private suspend fun snapshotAllTables(): List<List<Any>> =
        listOf(
            db.habitDao().all().sortedBy { it.toString() },
            db.targetChangeDao().all().sortedBy { it.toString() },
            db.pauseIntervalDao().all().sortedBy { it.toString() },
            db.entryDao().all().sortedBy { it.toString() },
            db.daySealDao().all().sortedBy { it.toString() },
            db.pointsLedgerDao().all().sortedBy { it.toString() },
            db.freezerUseDao().all().sortedBy { it.toString() },
            db.badgeDao().all().sortedBy { it.toString() },
            db.customizationItemDao().all().sortedBy { it.toString() },
        )

    @Test
    fun `export - wipe - import restores the exact same state`() =
        runTest {
            seedEverything()
            val exported = backup.exportJson(nowMillis = 1_000L)
            val before = snapshotAllTables()

            // Pollute: prove import REPLACES, not merges.
            db.habitDao().upsert(habitEntity(id = "intruder"))
            settingsRepo.update { it.copy(userName = "Nadie") }

            backup.import(exported)

            assertEquals(before, snapshotAllTables())
            assertEquals(seededSettings, settingsRepo.settings.first())
            assertEquals(exported, backup.exportJson(nowMillis = 1_000L))
        }

    @Test
    fun `import of an invalid file changes nothing`() =
        runTest {
            seedEverything()
            val before = snapshotAllTables()
            assertFailsWith<BackupFormatException> { backup.import("garbage") }
            assertEquals(before, snapshotAllTables())
        }

    @Test
    fun `preview reports the file's own counts`() =
        runTest {
            seedEverything()
            val preview = backup.preview(backup.exportJson(nowMillis = 1_000L))
            assertEquals(2, preview.habits)
            assertEquals("0.3.0-test", preview.appVersion)
        }

    // Neither seeded habit in seedEverything() ever carries a non-null timeOfDayMinutes
    // (the entity invariant forbids setting it alongside timeBucket, and the round-trip's
    // two-habit shape is pinned by the `preview.habits == 2` assertion above). Cover the
    // field directly on the mapper instead, both directions.
    @Test
    fun `habit mapper round-trips a non-null timeOfDayMinutes`() {
        val entity = habitEntity(id = "h3", timeBucket = null, timeOfDayMinutes = 450, reminderMinutes = 540)
        val backupHabit = entity.toBackup()
        assertEquals(450, backupHabit.timeOfDayMinutes)
        assertEquals(entity, backupHabit.toEntity())
    }
}
