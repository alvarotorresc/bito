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
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var journal: JournalRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var useCase: ReminderUseCase

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    /** A hand-built pending, unfailed card of [kind] — for [quickTargetOf], not the DB harness. */
    private fun card(
        kind: CardKind,
        step: Int = 1,
    ) = HabitCardUi(
        id = "h1",
        name = "Agua",
        kind = kind,
        progress = 0,
        target = 1,
        unit = null,
        step = step,
        direction = Direction.AT_LEAST,
        period = Period.DAY,
        doneToday = false,
        failed = false,
        streak = 0,
    )

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
        journal = JournalRepository(db)
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
    fun `the review slot goes silent once today is sealed, even with an unlogged habit`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            journal.sealDay(today, fixedNow)

            val outcome = useCase.evaluate("REVIEW", "")

            assertTrue(outcome is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.REVIEW, (outcome as ReminderUseCase.Outcome.Silent).slot.kind)
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
            assertEquals("Meditar", remindHabit.name)
            val target = remindHabit.target
            assertNotNull(target)
            assertEquals("h1", target!!.habitId)
            assertTrue(target.isCheck)
            assertEquals(SlotKind.HABIT, remindHabit.slot.kind)

            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))

            val done = useCase.evaluate("HABIT", "h1")
            assertTrue(done is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.HABIT, (done as ReminderUseCase.Outcome.Silent).slot.kind)
        }

    @Test
    fun `a habit slot for a counter habit reports a non-check quick target`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    step = 2,
                    reminderMinutes = 600,
                    createdOnDay = today,
                ),
            )

            val outcome = useCase.evaluate("HABIT", "h1")

            assertTrue(outcome is ReminderUseCase.Outcome.RemindHabit)
            val remindHabit = outcome as ReminderUseCase.Outcome.RemindHabit
            val target = remindHabit.target
            assertNotNull(target)
            assertFalse(target!!.isCheck)
            assertEquals(2, target.amount)
        }

    @Test
    fun `a habit slot for a duration habit reports no quick target while still pending`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Meditar",
                    metric = Metric.DURATION,
                    direction = Direction.AT_LEAST,
                    target = 30,
                    unit = "min",
                    reminderMinutes = 600,
                    createdOnDay = today,
                ),
            )

            val outcome = useCase.evaluate("HABIT", "h1")

            assertTrue(outcome is ReminderUseCase.Outcome.RemindHabit)
            val remindHabit = outcome as ReminderUseCase.Outcome.RemindHabit
            assertEquals("Meditar", remindHabit.name)
            assertNull(remindHabit.target)
        }

    @Test
    fun `a habit slot whose card is missing from today stays silent`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    reminderMinutes = 600,
                    createdOnDay = today + 5,
                ),
            )

            val outcome = useCase.evaluate("HABIT", "h1")

            assertTrue(outcome is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.HABIT, (outcome as ReminderUseCase.Outcome.Silent).slot.kind)
        }

    @Test
    fun `a global remind carries the voice it will speak with and the day's progress`() =
        runTest(dispatcher) {
            settingsRepo.update {
                it.copy(globalReminderMinutes = listOf(480), personality = Personality.SARGENTO, userName = "Álvaro")
            }
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
            habitsRepo.create(
                habitEntity(
                    id = "h2",
                    name = "Leer",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h2", logicalDay = today, value = 1))

            val outcome = useCase.evaluate("GLOBAL", "480")

            assertTrue(outcome is ReminderUseCase.Outcome.Remind)
            val remind = outcome as ReminderUseCase.Outcome.Remind
            assertEquals(Personality.SARGENTO, remind.personality)
            assertEquals("Álvaro", remind.userName)
            assertEquals(1, remind.payload.doneCount)
            assertEquals(2, remind.payload.totalCount)
            assertEquals(listOf("Agua"), remind.payload.pendingNames)
        }

    @Test
    fun `a review outcome carries the voice and how many rows are left to decide`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(personality = Personality.CHEERLEADER, userName = "Álvaro") }
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )

            val outcome = useCase.evaluate("REVIEW", "")

            assertTrue(outcome is ReminderUseCase.Outcome.Review)
            val review = outcome as ReminderUseCase.Outcome.Review
            assertEquals(1, review.pendingCount)
            assertEquals(Personality.CHEERLEADER, review.personality)
            assertEquals("Álvaro", review.userName)
        }

    @Test
    fun `a check card yields a done quick target`() {
        val target = quickTargetOf(card(CardKind.CHECK))
        assertEquals(QuickTarget("h1", "Agua", 1, isCheck = true), target)
    }

    @Test
    fun `a counter card yields a quick target sized to its step`() {
        val target = quickTargetOf(card(CardKind.COUNTER, step = 3))
        assertEquals(QuickTarget("h1", "Agua", 3, isCheck = false), target)
    }

    @Test
    fun `a duration card yields no quick target`() {
        assertNull(quickTargetOf(card(CardKind.DURATION)))
    }

    @Test
    fun `an abstinence card yields no quick target`() {
        assertNull(quickTargetOf(card(CardKind.ABSTINENCE)))
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

    @Test
    fun `a habit slot whose card is clean today stays silent`() =
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

            val outcome = useCase.evaluate("HABIT", "h1")

            assertTrue(outcome is ReminderUseCase.Outcome.Silent)
            assertEquals(SlotKind.HABIT, (outcome as ReminderUseCase.Outcome.Silent).slot.kind)
        }
}
