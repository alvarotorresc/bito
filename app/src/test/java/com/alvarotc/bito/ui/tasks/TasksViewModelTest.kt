package com.alvarotc.bito.ui.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class TasksViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var tasksRepo: TasksRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var vm: TasksViewModel

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel() =
        TasksViewModel(
            domainStateRepo,
            tasksRepo,
            settingsRepo,
            reconciler,
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
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
        domainStateRepo = DomainStateRepository(db)
        tasksRepo = TasksRepository(db)
        settingsRepo = SettingsRepository(settingsStore("tasks-vm"))
        rewardsRepo = RewardsRepository(db)
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        vm = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.state(): TasksUiState {
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun `bringing a task to today writes BROUGHT and it shows up in today`() =
        runTest {
            // Due far enough that it does not land in Hoy on its own (DUE_SOON_DAYS = 3).
            tasksRepo.create(taskEntity(id = "t1", dueKind = DueKind.DATE, dueDay = today + 10))
            val before = state()
            assertTrue(before.datedTasks.any { it.id == "t1" })
            assertTrue(before.todayTasks.none { it.id == "t1" })

            vm.bringToToday("t1")
            advanceUntilIdle()

            val after = state()
            assertTrue(after.todayTasks.any { it.id == "t1" })
            assertTrue(after.datedTasks.none { it.id == "t1" })
            assertTrue(
                db.taskEventDao().all().any { it.taskId == "t1" && it.kind == TaskEventKind.BROUGHT && it.logicalDay == today },
            )
        }

    @Test
    fun `marking done grants its points exactly once`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1"))

            vm.markDone("t1")
            advanceUntilIdle()

            val grants = db.pointsLedgerDao().all().filter { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" }
            assertEquals(1, grants.size)
            assertEquals(3, grants.single().delta)

            // Marking done again (e.g. a stray retap) must not grant a second time.
            vm.markDone("t1")
            advanceUntilIdle()

            assertEquals(
                1,
                db.pointsLedgerDao().all().count { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" },
            )
        }

    @Test
    fun `marking done records the id for the Habi snackbar, and consuming it clears it`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1"))

            assertNull(vm.lastTaskDone.value)

            vm.markDone("t1")
            advanceUntilIdle()

            assertEquals("t1", vm.lastTaskDone.value)

            vm.consumeTaskDone()

            assertNull(vm.lastTaskDone.value)
        }

    @Test
    fun `editing the title does not touch the events`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1", dueKind = DueKind.DATE, dueDay = today + 10))
            vm.bringToToday("t1")
            advanceUntilIdle()
            val eventsBefore = db.taskEventDao().all()
            assertTrue(eventsBefore.isNotEmpty())

            vm.edit("t1", "Llamar a Hacienda", "Tener el DNI a mano", DueKind.DATE, today + 10)
            advanceUntilIdle()

            assertEquals("Llamar a Hacienda", db.taskDao().byId("t1")?.title)
            assertEquals(eventsBefore, db.taskEventDao().all())
        }

    @Test
    fun `editing the deadline does not grant points again`() =
        runTest {
            // Overdue: done late is worth 3, not 5.
            tasksRepo.create(taskEntity(id = "t1", dueKind = DueKind.DATE, dueDay = today - 1))

            vm.markDone("t1")
            advanceUntilIdle()
            val grantAfterDone = db.pointsLedgerDao().all().single { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" }
            assertEquals(3, grantAfterDone.delta)

            // Push the deadline to today, which would now make it "on time" (worth 5) if recomputed.
            vm.edit("t1", "Llamar al banco", null, DueKind.DATE, today)
            advanceUntilIdle()

            val grantsAfterEdit = db.pointsLedgerDao().all().filter { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" }
            assertEquals(1, grantsAfterEdit.size)
            assertEquals(3, grantsAfterEdit.single().delta)
        }

    @Test
    fun `choosing this week again re-anchors to the current week`() =
        runTest {
            val currentSunday = Tasks.weekDueOf(today)
            val staleSunday = Tasks.weekDueOf(today - 14)
            tasksRepo.create(taskEntity(id = "t1", dueKind = DueKind.WEEK, dueDay = currentSunday, createdOnDay = today - 14))

            vm.edit("t1", "Preparar la mudanza", null, DueKind.WEEK, staleSunday)
            advanceUntilIdle()

            assertEquals(currentSunday, db.taskDao().byId("t1")?.dueDay)

            // create() re-anchors the same way: no screen can hand it a stale Sunday either.
            vm.create("Pedir cita", null, DueKind.WEEK, staleSunday)
            advanceUntilIdle()

            val created = db.taskDao().all().single { it.title == "Pedir cita" }
            assertEquals(currentSunday, created.dueDay)
            assertEquals(today, created.createdOnDay)
        }

    @Test
    fun `deleting takes the events and leaves the ledger alone`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1"))
            vm.bringToToday("t1")
            advanceUntilIdle()
            vm.markDone("t1")
            advanceUntilIdle()
            assertTrue(db.taskEventDao().all().any { it.taskId == "t1" })
            assertTrue(db.pointsLedgerDao().all().any { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" })

            vm.delete("t1")
            advanceUntilIdle()

            assertNull(db.taskDao().byId("t1"))
            assertTrue(db.taskEventDao().all().none { it.taskId == "t1" })
            assertTrue(db.pointsLedgerDao().all().any { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" })
        }
}
