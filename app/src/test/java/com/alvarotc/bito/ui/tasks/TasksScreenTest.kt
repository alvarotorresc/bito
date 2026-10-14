package com.alvarotc.bito.ui.tasks

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import com.alvarotc.bito.ui.sistema.ConSistema
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
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
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
            ConSistema {
                TasksScreen(viewModel = vm, onBack = {}, onStartFocus = { startedId = it }, onEditTask = { editedId = it })
            }
        }
        compose.waitForIdle()
    }

    private fun sembrarUnaDeCada() {
        val weekDue = Tasks.weekDueOf(today)
        runBlocking {
            tasksRepo.create(taskEntity(id = "t-today", title = "Hoy task", dueKind = DueKind.DATE, dueDay = today, createdOnDay = today))
            tasksRepo.create(
                taskEntity(id = "t-week", title = "Semana task", dueKind = DueKind.WEEK, dueDay = weekDue, createdOnDay = today),
            )
            tasksRepo.create(
                taskEntity(id = "t-dated", title = "Plazo task", dueKind = DueKind.DATE, dueDay = today + 10, createdOnDay = today),
            )
            // Dos sueltas: la más vieja es la que Hoy escoge, no esta.
            tasksRepo.create(taskEntity(id = "loose-picked", title = "Suelta oculta", dueKind = DueKind.NONE, createdOnDay = today - 5))
            tasksRepo.create(taskEntity(id = "t-loose", title = "Suelta task", dueKind = DueKind.NONE, createdOnDay = today))
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
    }

    private fun abrirMenuDe(id: String) {
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("task-menu-$id"))
        compose.onNodeWithTag("task-menu-$id", useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun elegirEnElMenu(texto: String) {
        compose.onNodeWithText(texto, useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    fun `sections are there and Done starts folded`() {
        sembrarUnaDeCada()
        renderScreen()

        compose.onNodeWithText("Hoy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hoy task", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Esta semana", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Semana task", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Con plazo", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Plazo task", useUnmergedTree = true).assertExists()
        // La lista es más alta que la ventana: hay que bajar hasta lo que sigue.
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasText("Sin plazo"))
        compose.onNodeWithText("Sin plazo", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Suelta task", useUnmergedTree = true).assertExists()

        // Hechas empieza plegada: su fila no se ve hasta abrirla.
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasText("Hechas"))
        compose.onNodeWithText("Hecha task", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Hechas", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasText("Hecha task"))
        compose.onNodeWithText("Hecha task", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the row menu has no bring to today in this week`() {
        sembrarUnaDeCada()
        renderScreen()

        abrirMenuDe("t-week")
        compose.onNodeWithText("Empezar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Editar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Borrar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Traer a hoy", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the row menu offers bring to today for a task without deadline`() {
        sembrarUnaDeCada()
        renderScreen()

        abrirMenuDe("t-loose")
        compose.onNodeWithText("Traer a hoy", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `bringing a task to today moves it into Hoy and undo sends it back`() {
        runBlocking {
            tasksRepo.create(
                taskEntity(id = "t1", title = "Renovar el DNI", dueKind = DueKind.DATE, dueDay = today + 10, createdOnDay = today),
            )
        }
        renderScreen()
        compose.onNodeWithText("Con plazo", useUnmergedTree = true).assertExists()

        abrirMenuDe("t1")
        elegirEnElMenu("Traer a hoy")

        compose.onNodeWithText("Traída a hoy.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hoy", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Con plazo", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Con plazo", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Renovar el DNI", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `finishing a task shows undo and undo brings it back`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Meditar", createdOnDay = today))
        }
        renderScreen()

        compose.onNodeWithTag("task-done-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Deshacer", useUnmergedTree = true).assertExists()
        // Hecha y plegada: la fila ya no está en la lista.
        compose.onNodeWithText("Meditar", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Meditar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("task-done-t1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the row menu edit calls onEditTask with the row id`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Titulo original", createdOnDay = today))
        }
        renderScreen()

        abrirMenuDe("t1")
        elegirEnElMenu("Editar")

        assertEquals("t1", editedId)
    }

    @Test
    fun `the row menu start calls onStartFocus with the row id`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Meditar", createdOnDay = today))
        }
        renderScreen()

        abrirMenuDe("t1")
        elegirEnElMenu("Empezar")

        assertEquals("t1", startedId)
    }

    @Test
    fun `deleting asks for confirmation first, and only then removes the row`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea a borrar", createdOnDay = today))
        }
        renderScreen()

        abrirMenuDe("t1")
        elegirEnElMenu("Borrar")

        // El diálogo está puesto y la fila sigue ahí: todavía no se ha quitado nada.
        compose.onNodeWithText("¿Borrar esta tarea?", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Los bitos que ganaste son tuyos.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Tarea a borrar", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Borrar", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Tarea a borrar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an empty list says so instead of showing empty sections`() {
        renderScreen()

        compose.onNodeWithText("Nada pendiente.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hoy", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Sin plazo", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an overdue date reads in the second line`() {
        runBlocking {
            tasksRepo.create(
                taskEntity(id = "t1", title = "Pagar la luz", dueKind = DueKind.DATE, dueDay = today - 2, createdOnDay = today - 5),
            )
        }
        renderScreen()

        compose.onNodeWithText("vencida hace 2 días", useUnmergedTree = true).assertIsDisplayed()
    }
}
