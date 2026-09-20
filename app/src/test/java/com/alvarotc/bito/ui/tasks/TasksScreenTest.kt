package com.alvarotc.bito.ui.tasks

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

/** Drives the real [TasksScreen] over an in-memory Room database, following [com.alvarotc.bito.ui.settings.ArchivedScreenTest]'s harness. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Same phone-sized viewport as TodayScreenTest: a default Robolectric window is too short for
// this LazyColumn to compose every section, so a lower one would just never exist to assert on.
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TasksScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z, same fixture as TasksViewModelTest
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var tasksRepo: TasksRepository
    private lateinit var vm: TasksViewModel
    private var startedId: String? = null
    private var editedId: String? = null

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "tasks-screen.preferences_pb") }

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
        val domainStateRepo = DomainStateRepository(db)
        tasksRepo = TasksRepository(db)
        val settingsRepo = SettingsRepository(settingsStore())
        val rewardsRepo = RewardsRepository(db)
        val reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        vm =
            TasksViewModel(
                domainStateRepo,
                tasksRepo,
                settingsRepo,
                reconciler,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun renderScreen() {
        compose.setContent {
            BitoTheme {
                TasksScreen(viewModel = vm, onBack = {}, onStartFocus = { startedId = it }, onEditTask = { editedId = it })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the five sections show their tasks`() {
        val weekDue = Tasks.weekDueOf(today)
        runBlocking {
            // Hoy: due exactly today, regardless of Tasks.todayTasks' other five doors.
            tasksRepo.create(taskEntity(id = "t-today", title = "Hoy task", dueKind = DueKind.DATE, dueDay = today, createdOnDay = today))
            // Esta semana: a WEEK task always leaves Hoy for here (buildTasksUiState's own filter),
            // whatever day of the week "today" happens to land on.
            tasksRepo.create(
                taskEntity(id = "t-week", title = "Semana task", dueKind = DueKind.WEEK, dueDay = weekDue, createdOnDay = today),
            )
            // Con plazo: past DUE_SOON_DAYS (3), so Hoy never claims it.
            tasksRepo.create(
                taskEntity(id = "t-dated", title = "Plazo task", dueKind = DueKind.DATE, dueDay = today + 10, createdOnDay = today),
            )
            // Sin plazo: two loose tasks so the older one (not this one) is Habi's pick for Hoy.
            tasksRepo.create(taskEntity(id = "loose-picked", title = "Suelta oculta", dueKind = DueKind.NONE, createdOnDay = today - 5))
            tasksRepo.create(taskEntity(id = "t-loose", title = "Suelta task", dueKind = DueKind.NONE, createdOnDay = today))
            // Hechas: status alone decides this section, independent of dueKind.
            tasksRepo.create(
                taskEntity(
                    id = "t-done",
                    title = "Hecha task",
                    dueKind = DueKind.NONE,
                    status = TaskStatus.DONE,
                    createdOnDay = today - 1,
                    doneOnDay = today,
                    doneAtMillis = fixedNow,
                ),
            )
        }
        renderScreen()

        compose.onNodeWithText("Today", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hoy task", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("This week", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Semana task", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("With a deadline", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Plazo task", useUnmergedTree = true).assertExists()
        // The list is taller than the test viewport past this point — LazyColumn only composes
        // what fits, so scrolling is what actually brings the rest into the semantics tree.
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasText("No deadline"))
        compose.onNodeWithText("No deadline", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Suelta task", useUnmergedTree = true).assertExists()

        // Hechas starts folded: its row is not on screen until the toggle expands it.
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-done-toggle"))
        compose.onNodeWithText("Hecha task", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("tasks-done-toggle", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasText("Hecha task"))
        compose.onNodeWithText("Hecha task", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `bringing a task to today moves it into the today section`() {
        runBlocking {
            tasksRepo.create(
                taskEntity(id = "t1", title = "Renovar el DNI", dueKind = DueKind.DATE, dueDay = today + 10, createdOnDay = today),
            )
        }
        renderScreen()

        compose.onNodeWithTag("task-bring-t1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("task-bring-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Renovar el DNI", useUnmergedTree = true).assertExists()
        // Hoy rows never offer "bring to today" — its disappearance is the row's own proof it moved.
        compose.onNodeWithTag("task-bring-t1", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * D12 revoked: editing no longer opens a sheet inline on this screen — it navigates to
     * TaskFormScreen's own route instead (BitoNavHostTest's own "editing from the tasks list
     * reaches the full-screen form with the task preloaded" proves that route actually renders the
     * preloaded fields). This screen's own responsibility shrinks to the same shape "starting a
     * task calls onStartFocus with its id" already proves for onStartFocus: the row menu's "editar"
     * calls [onEditTask] with the right id, nothing about the form itself.
     */
    @Test
    fun `the row menu edit calls onEditTask with the row id`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Titulo original", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // Not performClick(): a button inside a ModalBottomSheet does not receive synthesized
        // touch gestures under this Robolectric harness (DetailScreenTest, BitoNavHostTest) —
        // invoking the row's own OnClick semantics action directly is what proves the tap works.
        compose.onNodeWithTag("task-menu-edit", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        assertEquals("t1", editedId)
    }

    @Test
    fun `deleting asks for confirmation first, and only then removes the row`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea a borrar", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("task-menu-delete", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        // The confirmation sheet is up and the row is still there — nothing has been removed yet.
        compose.onNodeWithTag("task-delete-confirm", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Tarea a borrar", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("task-delete-confirm-yes", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        compose.onNodeWithText("Tarea a borrar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the delete row in the row menu still exists and opens the confirm sheet`() {
        // Coherence fix: the row now tints its icon and text Peligro (like HabitFormScreen's own
        // delete button) instead of the same Tinta as "editar" — Compose's semantics tree carries
        // no color, so this only re-proves the row is still there, tagged, and still wired to
        // onDelete after gaining the tint parameter; the visual half is not screenshot-tested here.
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea a borrar", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("task-menu-delete", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Delete", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("task-menu-delete", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        compose.onNodeWithTag("task-delete-confirm", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an empty list says so instead of showing empty sections`() {
        renderScreen()

        compose.onNodeWithText("Nothing pending.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Today", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("No deadline", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `starting a task calls onStartFocus with its id`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Meditar", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-start-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("t1", startedId)
    }

    @Test
    fun `marking a task done from the list says so, same phrase as Hoy`() {
        // Default settings: NEUTRA and a blank name, same fixture FocusScreenTest relies on for
        // its own "champ" fallback.
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Meditar", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-done-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Done, champ.", useUnmergedTree = true).assertExists()
    }
}
