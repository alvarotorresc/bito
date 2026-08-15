package com.alvarotc.bito.ui.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderUseCaseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var useCase: ReminderUseCase

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("reminder-use-case"))
        useCase = ReminderUseCase(domainStateRepo, habitsRepo, settingsRepo, now = { fixedNow }, zone = { utc })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a global slot with pending habits yields a payload`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(globalReminderMinutes = listOf(480)) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )

            val outcome = useCase.evaluate("GLOBAL", "480")

            assertTrue(outcome is ReminderUseCase.Outcome.Remind)
            val remind = outcome as ReminderUseCase.Outcome.Remind
            assertEquals(listOf("Agua"), remind.payload.pendingNames)
            assertEquals(SlotKind.GLOBAL, remind.slot.kind)
            assertEquals("480", remind.slot.key)
        }

    @Test
    fun `a global slot with everything done stays silent but reschedules`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(globalReminderMinutes = listOf(480)) }
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))

            val outcome = useCase.evaluate("GLOBAL", "480")

            assertTrue(outcome is ReminderUseCase.Outcome.Silent)
            val silent = outcome as ReminderUseCase.Outcome.Silent
            assertEquals(SlotKind.GLOBAL, silent.slot.kind)
            assertEquals("480", silent.slot.key)
        }

    @Test
    fun `a deleted reminder hour is stale and never reschedules`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(globalReminderMinutes = emptyList()) }
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )

            val outcome = useCase.evaluate("GLOBAL", "480")

            assertEquals(ReminderUseCase.Outcome.Stale, outcome)
        }

    @Test
    fun `the review slot fires only while something is unsealed`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )

            val pending = useCase.evaluate("REVIEW", "")
            assertTrue(pending is ReminderUseCase.Outcome.Review)
            assertEquals(SlotKind.REVIEW, (pending as ReminderUseCase.Outcome.Review).slot.kind)

            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))

            val settled = useCase.evaluate("REVIEW", "")
            assertTrue(settled is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.REVIEW, (settled as ReminderUseCase.Outcome.Silent).slot.kind)
        }

    @Test
    fun `a habit slot goes silent once that habit is done today`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Meditar",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    reminderMinutes = 600,
                    createdOnDay = today,
                ),
            )

            val pending = useCase.evaluate("HABIT", "h1")
            assertTrue(pending is ReminderUseCase.Outcome.RemindHabit)
            val remindHabit = pending as ReminderUseCase.Outcome.RemindHabit
            assertEquals("h1", remindHabit.target.habitId)
            assertEquals("Meditar", remindHabit.target.name)
            assertEquals(SlotKind.HABIT, remindHabit.slot.kind)

            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))

            val done = useCase.evaluate("HABIT", "h1")
            assertTrue(done is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.HABIT, (done as ReminderUseCase.Outcome.Silent).slot.kind)
        }

    @Test
    fun `a habit slot whose card failed today stays silent`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "No fumar",
                    metric = Metric.CHECK,
                    direction = Direction.ZERO,
                    period = Period.DAY,
                    reminderMinutes = 600,
                    createdOnDay = today,
                ),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))

            val outcome = useCase.evaluate("HABIT", "h1")

            assertTrue(outcome is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.HABIT, (outcome as ReminderUseCase.Outcome.Silent).slot.kind)
        }
}
