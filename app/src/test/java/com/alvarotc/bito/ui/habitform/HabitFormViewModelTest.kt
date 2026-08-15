package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class HabitFormViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var settingsRepo: SettingsRepository

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(habitId: String? = null) =
        HabitFormViewModel(habitsRepo, settingsRepo, habitId, now = { fixedNow }, zone = { utc })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        habitsRepo = HabitsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("habit-form-vm"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `save on a filled create form persists the habit and its initial target change`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Agua")
            vm.selectPreset(HabitPreset.QUANTITY)
            vm.setUnit("vasos")
            var saved = false

            vm.save { saved = true }
            advanceUntilIdle()

            assertTrue(saved)
            val stored = db.habitDao().all().single()
            assertEquals("Agua", stored.name)
            assertEquals(Metric.COUNT, stored.metric)
            assertEquals(8, stored.target)
            assertEquals(today, stored.createdOnDay)
            assertEquals(0, stored.sortOrder)
            val changes = db.targetChangeDao().forHabit(stored.id)
            assertEquals(listOf(today), changes.map { it.effectiveFromDay })
            assertEquals(listOf(8), changes.map { it.target })
        }

    @Test
    fun `save assigns sortOrder as max existing plus one`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", sortOrder = 0, createdOnDay = today))
            habitsRepo.create(habitEntity(id = "h2", sortOrder = 3, createdOnDay = today))
            val vm = newViewModel()
            vm.setName("Nuevo")
            vm.selectPreset(HabitPreset.DAILY_CHECK)

            vm.save {}
            advanceUntilIdle()

            val created = db.habitDao().all().single { it.name == "Nuevo" }
            assertEquals(4, created.sortOrder)
        }

    @Test
    fun `constructing with a habitId loads the entity into state`() =
        runTest {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    createdOnDay = today,
                ),
            )

            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            assertEquals("Agua", vm.state.value.name)
            assertEquals(HabitPreset.QUANTITY, vm.state.value.preset)
            assertEquals("h1", vm.state.value.editingId)
        }

    @Test
    fun `editing the target then saving updates the habit and records a new target change`() =
        runTest {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    createdOnDay = today - 5,
                ),
            )
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            vm.adjustTarget(2) // 8 -> 10
            assertEquals(10, vm.state.value.target)

            vm.save {}
            advanceUntilIdle()

            val updated = db.habitDao().byId("h1")!!
            assertEquals(10, updated.target)
            assertEquals(Metric.COUNT, updated.metric)
            assertEquals(Direction.AT_LEAST, updated.direction)
            assertEquals(today - 5, updated.createdOnDay)
            val changes = db.targetChangeDao().forHabit("h1").sortedBy { it.effectiveFromDay }
            assertEquals(2, changes.size)
            assertEquals(today, changes.last().effectiveFromDay)
            assertEquals(10, changes.last().target)
        }

    @Test
    fun `editing the name only records no new target change`() =
        runTest {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    createdOnDay = today - 5,
                ),
            )
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            vm.setName("Agua fria")
            vm.save {}
            advanceUntilIdle()

            val updated = db.habitDao().byId("h1")!!
            assertEquals("Agua fria", updated.name)
            assertEquals(8, updated.target)
            val changes = db.targetChangeDao().forHabit("h1")
            assertEquals(1, changes.size)
        }

    @Test
    fun `delete removes the habit and its entries and fires the callback`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", createdOnDay = today))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today))
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()
            var deleted = false

            vm.delete { deleted = true }
            advanceUntilIdle()

            assertTrue(deleted)
            assertNull(db.habitDao().byId("h1"))
            assertTrue(db.entryDao().all().none { it.habitId == "h1" })
        }

    @Test
    fun `adjustTarget clamps at a minimum of 1`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.QUANTITY) // default target 8

            repeat(20) { vm.adjustTarget(-1) }

            assertEquals(1, vm.state.value.target)
        }

    @Test
    fun `adjustTarget keeps a QUIT TOTAL target pinned at 0`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.QUIT)
            vm.selectQuitMode(QuitMode.TOTAL)
            assertEquals(0, vm.state.value.target)

            vm.adjustTarget(1)
            assertEquals(0, vm.state.value.target)

            vm.adjustTarget(-1)
            assertEquals(0, vm.state.value.target)
        }

    @Test
    fun `adjustTarget clamps WEEKLY_TIMES at a maximum of 7 days per week`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.WEEKLY_TIMES) // default target 3

            repeat(20) { vm.adjustTarget(1) }

            assertEquals(7, vm.state.value.target)
        }

    @Test
    fun `selectPreset resets target to the preset default`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.DURATION)
            assertEquals(20, vm.state.value.target)

            vm.selectPreset(HabitPreset.WEEKLY_TIMES)
            assertEquals(3, vm.state.value.target)
        }

    @Test
    fun `save is a no-op when the form cannot be saved`() =
        runTest {
            val vm = newViewModel()
            var saved = false

            vm.save { saved = true }
            advanceUntilIdle()

            assertFalse(saved)
            assertTrue(db.habitDao().all().isEmpty())
        }
}
