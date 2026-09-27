package com.alvarotc.bito.ui.tasks

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.FocusClock
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.settings.FocusStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.notifications.FocusPresence
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Drives the real [FocusScreen] over an in-memory Room database plus a real [FocusStore],
 * following [TasksScreenTest]'s harness — a single [UnconfinedTestDispatcher] backs Room's
 * executors, the DataStore scopes and [Dispatchers.Main], so a ViewModel write and the
 * recomposition it causes settle inside a plain `compose.waitForIdle()`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FocusScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
    private var storeIndex = 0

    private lateinit var db: BitoDatabase
    private lateinit var tasksRepo: TasksRepository
    private lateinit var focusStore: FocusStore

    // Every FocusViewModel keeps a tick loop alive in its own viewModelScope (delay(1_000) sin
    // fin) — sin cancelarlo, se queda corriendo entre tests bajo el mismo Main compartido.
    private val liveViewModels = mutableListOf<FocusViewModel>()

    private class NoopFocusPresence : FocusPresence {
        override fun show(
            taskTitle: String,
            endsAtMillis: Long,
        ) = Unit

        override fun clear() = Unit
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "focus-screen-${storeIndex++}-$name.preferences_pb") }

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
        tasksRepo = TasksRepository(db)
        focusStore = FocusStore(store("focus"))
    }

    @After
    fun tearDown() {
        liveViewModels.forEach { it.viewModelScope.cancel() }
        liveViewModels.clear()
        Dispatchers.resetMain()
        db.close()
    }

    private fun newViewModel(taskId: String?): FocusViewModel {
        val domainStateRepo = DomainStateRepository(db)
        val settingsRepo = SettingsRepository(store("settings"))
        val rewardsRepo = RewardsRepository(db)
        val reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        return FocusViewModel(
            tasksRepo,
            focusStore,
            settingsRepo,
            domainStateRepo,
            rewardsRepo,
            reconciler,
            NoopFocusPresence(),
            taskId,
            now = { fixedNow },
            elapsed = { 0L },
            zone = { utc },
        ).also { liveViewModels += it }
    }

    private fun render(
        vm: FocusViewModel,
        onClose: () -> Unit = {},
        onKeepOther: () -> Unit = {},
    ) {
        compose.setContent {
            BitoTheme {
                FocusScreen(viewModel = vm, onClose = onClose, onKeepOther = onKeepOther)
            }
        }
        compose.waitForIdle()
    }

    /**
     * [performClick] reports success against a button inside a [androidx.compose.material3.ModalBottomSheet]
     * under this Robolectric harness but never actually runs its callback (documented in
     * [com.alvarotc.bito.ui.BitoNavHostTest] and [com.alvarotc.bito.ui.detail.DetailScreenTest]) —
     * invoking the node's own OnClick semantics action directly is what proves the busy sheet's
     * two buttons actually wire through.
     */
    private fun tapText(text: String) {
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
    }

    @Test
    fun `at rest it shows the task, its first step and the three lengths`() {
        runBlocking {
            tasksRepo.create(
                taskEntity(id = "t1", title = "Llamar al banco", firstStep = "Buscar el telefono", createdOnDay = today),
            )
        }
        val vm = newViewModel("t1")

        render(vm)

        compose.onNodeWithText("Llamar al banco", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Buscar el telefono", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("5 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("10 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("25 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("focus-start", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `starting shows the countdown and the five buttons`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        render(vm)

        compose.onNodeWithTag("focus-start", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("10:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("I'm done", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+15", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Something came up, I'll stop", useUnmergedTree = true).assertExists()
        // The entrance line, spoken once for a session actually started FROM this tap — default
        // spec is NEUTRA with a blank userName (habi_name_fallback = "champ").
        compose.onNodeWithText("I'm here, champ. Go ahead.", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("focus-start", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a finished countdown still shows zero and the five buttons`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            // Seeds an already-expired 5-minute session directly (same shape FocusViewModelTest's
            // own "reopening on an expired session" uses) — the very first uiState emission
            // already reads remaining == 0, no tick loop to drive inside a compose test.
            focusStore.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = fixedNow - 6 * 60_000L,
                    endsAtMillis = fixedNow - 60_000L,
                    endsAtElapsed = -60_000L,
                    bootMillis = FocusClock.bootSignatureOf(fixedNow - 6 * 60_000L, 0L),
                ),
            )
        }
        val vm = newViewModel("t1")

        render(vm)

        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("00:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("I'm done", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+15", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Something came up, I'll stop", useUnmergedTree = true).assertExists()
        // This screen did not start the session (it was seeded directly, as a re-entry onto an
        // already-live one would look) — Habi never said anything, so no entrance line here.
        compose.onNodeWithText("I'm here, champ. Go ahead.", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `with a session of another task it offers to keep or to switch`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea uno", createdOnDay = today))
            tasksRepo.create(taskEntity(id = "t2", title = "Tarea dos", createdOnDay = today))
            // A live session for t1, running (not expired) — the exact conflict shape
            // FocusViewModelTest's "starting another task while one runs" seeds by calling
            // start() on a first VM; seeded directly here since no first screen needs rendering.
            focusStore.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = fixedNow,
                    endsAtMillis = fixedNow + 10 * 60_000L,
                    endsAtElapsed = 10 * 60_000L,
                    bootMillis = FocusClock.bootSignatureOf(fixedNow, 0L),
                ),
            )
        }
        val vm = newViewModel("t2")
        var keptOther = false
        render(vm, onKeepOther = { keptOther = true })

        compose.onNodeWithText("Something's already running", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Keep going with Tarea uno", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Drop it and start this", useUnmergedTree = true).assertExists()

        tapText("Keep going with Tarea uno")
        compose.waitForIdle()

        assertTrue(keptOther)
        assertEquals("t1", runBlocking { focusStore.session.first() }?.taskId) // keepOther() writes nothing

        tapText("Drop it and start this")
        compose.waitForIdle()

        assertEquals("t2", runBlocking { focusStore.session.first() }?.taskId)
        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertExists()
        // "Dejarla y empezar esta" DOES start a session from this screen, same as "Empezar" —
        // the entrance line belongs here too, for t2 (not t1's, which never got one).
        compose.onNodeWithText("I'm here, champ. Go ahead.", useUnmergedTree = true).assertExists()
    }

    /** [SemanticsActions.OnClick] directly — a button inside this sheet's own [tapText] caveat. */
    private fun tapTag(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
    }

    @Test
    fun `dismissing the busy sheet by its scrim closes the screen and writes nothing`() {
        runBlocking {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea uno", createdOnDay = today))
            tasksRepo.create(taskEntity(id = "t2", title = "Tarea dos", createdOnDay = today))
            focusStore.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = fixedNow,
                    endsAtMillis = fixedNow + 10 * 60_000L,
                    endsAtElapsed = 10 * 60_000L,
                    bootMillis = FocusClock.bootSignatureOf(fixedNow, 0L),
                ),
            )
        }
        val vm = newViewModel("t2")
        var closed = false
        var keptOther = false
        render(vm, onClose = { closed = true }, onKeepOther = { keptOther = true })

        compose.onNodeWithTag("focus-busy-sheet", useUnmergedTree = true).assertExists()

        // El scrim del ModalBottomSheet de Material3 no expone un click semantico normal (no hay
        // performClick posible bajo este harness, ni tampoco un pointerInput sintetizable) — su
        // unica pista accesible es su propia descripcion de accesibilidad, "Close sheet", con su
        // OnClick semantica detras. Descartar por ahi es la misma salida que el boton atras del
        // sistema: onDismissRequest = onClose (ver FocusScreen.kt).
        compose
            .onNodeWithContentDescription("Close sheet")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        assertTrue(closed)
        assertFalse(keptOther)
        // Descartar no es "seguir con la otra": no escribe nada, t1 sigue siendo quien corre.
        assertEquals("t1", runBlocking { focusStore.session.first() }?.taskId)
    }

    @Test
    fun `Otro opens a minutes sheet and confirming selects it, forty minutes and all`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        render(vm)

        compose.onNodeWithText("Other", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Other", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("focus-custom-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextInput("40")
        compose.waitForIdle()
        tapTag("focus-custom-save")
        compose.waitForIdle()

        compose.onNodeWithTag("focus-custom-sheet", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("40 min", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("focus-start", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("40:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a custom value out of range keeps Guardar disabled`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        render(vm)

        compose.onNodeWithText("Other", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextInput("0")
        compose.waitForIdle()

        compose.onNodeWithTag("focus-custom-save", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `mas Otro extends the running session by whatever minutes were typed`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        render(vm)
        compose.onNodeWithTag("focus-start", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("+ Other", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("focus-custom-input", useUnmergedTree = true).performTextInput("20")
        compose.waitForIdle()
        tapTag("focus-custom-save")
        compose.waitForIdle()

        // Default DEFAULT_MINUTES (10:00) plus the 20 typed in the sheet.
        compose.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `finishing shows the done phrase, same as Hoy, and only then closes`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        var closed = false
        render(vm, onClose = { closed = true })

        compose.onNodeWithTag("focus-start", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("focus-done", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // Up but not gone yet: the phrase is showing and onClose has not fired.
        compose.onNodeWithTag("focus-done-bubble", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Done, champ.", useUnmergedTree = true).assertExists()
        assertFalse(closed)

        dispatcher.scheduler.advanceTimeBy(1_500)
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()

        assertTrue(closed)
    }

    @Test
    fun `giving up closes in silence, no done phrase`() {
        runBlocking { tasksRepo.create(taskEntity(id = "t1", createdOnDay = today)) }
        val vm = newViewModel("t1")
        var closed = false
        render(vm, onClose = { closed = true })

        compose.onNodeWithTag("focus-start", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("focus-give-up", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertTrue(closed)
        compose.onNodeWithTag("focus-done-bubble", useUnmergedTree = true).assertDoesNotExist()
    }
}
