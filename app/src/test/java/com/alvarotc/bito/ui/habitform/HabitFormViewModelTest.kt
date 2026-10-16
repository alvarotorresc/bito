package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconciler: PointsReconciler

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(
        habitId: String? = null,
        objetivoSugerido: Int? = null,
    ) = HabitFormViewModel(
        habitsRepo,
        settingsRepo,
        rewardsRepo,
        reconciler,
        habitId,
        now = { fixedNow },
        zone = { utc },
        objetivoSugerido = objetivoSugerido,
    )

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
        rewardsRepo = RewardsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("habit-form-vm"))
        reconciler = PointsReconciler(DomainStateRepository(db), rewardsRepo, settings = settingsRepo)
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

            vm.save(onSaved = { saved = true })
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

            vm.save(onSaved = {})
            advanceUntilIdle()

            val created = db.habitDao().all().single { it.name == "Nuevo" }
            assertEquals(4, created.sortOrder)
        }

    @Test
    fun `the objective note opens the form with the suggested target without saving`() =
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

            val vm = newViewModel(habitId = "h1", objetivoSugerido = 12)
            advanceUntilIdle()

            assertEquals(12, vm.state.value.target)
            assertEquals("Agua", vm.state.value.name)
            // La base de datos no cambia hasta guardar.
            assertEquals(8, db.habitDao().all().single { it.id == "h1" }.target)
        }

    @Test
    fun `the suggested target saves like any edit once the user confirms`() =
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
            val vm = newViewModel(habitId = "h1", objetivoSugerido = 12)
            advanceUntilIdle()

            vm.save(onSaved = {})
            advanceUntilIdle()

            assertEquals(12, db.habitDao().all().single { it.id == "h1" }.target)
        }

    @Test
    fun `the suggested target is clamped like the stepper`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            val vm = newViewModel(habitId = "h1", objetivoSugerido = 0)
            advanceUntilIdle()

            assertEquals(1, vm.state.value.target)
        }

    @Test
    fun `without a suggestion the stored target is kept`() =
        runTest {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    createdOnDay = today,
                ),
            )
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            assertEquals(8, vm.state.value.target)
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

            vm.save(onSaved = {})
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
            vm.save(onSaved = {})
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
    fun `lowering a target from the form records the water it raises in the garden high mark`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", target = 8, createdOnDay = today - 3))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 6))
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()
            assertEquals(0.0, settingsRepo.settings.first().gardenWaterHighMark, 1e-9)

            vm.setTarget(6)
            vm.save(onSaved = {})
            advanceUntilIdle()

            val water = GardenEngine.totalWater(DomainStateRepository(db).snapshot(), today)
            assertTrue(water > 0.0)
            assertEquals(water, settingsRepo.settings.first().gardenWaterHighMark, 1e-9)
        }

    @Test
    fun `deleting an unmet habit records the water it raises in the garden high mark`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", name = "Agua", target = 8, createdOnDay = today - 3, sortOrder = 0))
            habitsRepo.create(habitEntity(id = "h2", name = "Leer", target = 8, createdOnDay = today - 3, sortOrder = 1))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 8))
            val vm = newViewModel(habitId = "h2")
            advanceUntilIdle()
            assertEquals(0.0, settingsRepo.settings.first().gardenWaterHighMark, 1e-9)

            vm.delete {}
            advanceUntilIdle()

            val water = GardenEngine.totalWater(DomainStateRepository(db).snapshot(), today)
            assertTrue(water > 0.0)
            assertEquals(water, settingsRepo.settings.first().gardenWaterHighMark, 1e-9)
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

            vm.save(onSaved = { saved = true })
            advanceUntilIdle()

            assertFalse(saved)
            assertTrue(db.habitDao().all().isEmpty())
        }

    @Test
    fun `setTarget clamps like the stepper does`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.QUANTITY) // default target 8

            vm.setTarget(500)
            assertEquals(500, vm.state.value.target)

            vm.setTarget(0)
            assertEquals(1, vm.state.value.target)
        }

    @Test
    fun `weekly times still cap at seven through direct input`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.WEEKLY_TIMES) // default target 3

            vm.setTarget(12)

            assertEquals(7, vm.state.value.target)
        }

    @Test
    fun `setTarget keeps a QUIT TOTAL target pinned at zero`() =
        runTest {
            val vm = newViewModel()
            vm.selectPreset(HabitPreset.QUIT)
            vm.selectQuitMode(QuitMode.TOTAL)

            vm.setTarget(50)

            assertEquals(0, vm.state.value.target)
        }

    @Test
    fun `a reminder time survives the round trip to the entity`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Meditar")
            vm.setReminder(9 * 60)

            vm.save(onSaved = {})
            advanceUntilIdle()

            val stored = db.habitDao().all().single()
            assertEquals(540, stored.reminderMinutes)
        }

    @Test
    fun `editing prefills the stored reminder`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", reminderMinutes = 540, createdOnDay = today))

            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            assertEquals(540, vm.state.value.reminderMinutes)
        }

    @Test
    fun `clearing the reminder writes null`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", reminderMinutes = 540, createdOnDay = today))
            val vm = newViewModel(habitId = "h1")
            advanceUntilIdle()

            vm.setReminder(null)
            vm.save(onSaved = {})
            advanceUntilIdle()

            val updated = db.habitDao().byId("h1")!!
            assertNull(updated.reminderMinutes)
        }

    @Test
    fun `a double tap on save creates exactly one habit`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Agua")

            vm.save(onSaved = {})
            vm.save(onSaved = {})
            advanceUntilIdle()

            assertEquals(1, db.habitDao().all().size)
        }

    @Test
    fun `the save button stays dead after a completed save`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Agua")

            vm.save(onSaved = {})
            advanceUntilIdle()

            assertTrue(vm.state.value.saving)

            vm.save(onSaved = {})
            advanceUntilIdle()

            assertEquals(1, db.habitDao().all().size)
        }

    @Test
    fun `the real equipped set reaches the form's avatar state`() =
        runTest {
            rewardsRepo.acquire(CustomizationItemEntity("neck-collar-flores", CustomizationCategory.NECK, fixedNow, equipped = true))
            val vm = newViewModel()

            backgroundScope.launch { vm.equipped.collect() }
            advanceUntilIdle()

            assertEquals("neck-collar-flores", vm.equipped.value.neck)
        }

    @Test
    fun `creating reports the new id to alCrear before onSaved`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Agua")
            vm.selectPreset(HabitPreset.DAILY_CHECK)
            val orden = mutableListOf<String>()

            vm.save(onSaved = { orden += "guardado" }, alCrear = { orden += "crea:$it" })
            advanceUntilIdle()

            val id = db.habitDao().all().single().id
            assertEquals(listOf("crea:$id", "guardado"), orden)
        }

    @Test
    fun `editing never calls alCrear`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", name = "Agua", createdOnDay = today))
            val vm = newViewModel("h1")
            advanceUntilIdle()
            var llamado = false
            var guardado = false

            vm.save(onSaved = { guardado = true }, alCrear = { llamado = true })
            advanceUntilIdle()

            assertTrue(guardado)
            assertFalse(llamado)
        }
}
