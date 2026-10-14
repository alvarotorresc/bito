package com.alvarotc.bito.ui

import android.app.Application
import android.app.NotificationManager
import android.os.SystemClock
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.R
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.FocusClock
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Guarda la navegación de la app: Inicio como única raíz, sin barra inferior, y las rutas que cuelgan de él.
 * Uses a bare [Application] (not the manifest's BitoApp) so onCreate()'s AppStartup.start() —
 * which builds its own AppContainer — never opens a second DataStore on the same settings file
 * as the one built explicitly below.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = Application::class)
class BitoNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        // NavRequests is a process-wide singleton (T11): a route left pending here would leak
        // into whichever test class runs next in this JVM fork.
        NavRequests.consume()
    }

    /** El texto de la cabecera de una pantalla de ruta (Ajustes, formularios). Hoy y Camino se comprueban por sus tags. */
    private fun screenTitleNode(text: String) = compose.onNode(hasText(text), useUnmergedTree = true)

    /**
     * [performClick] reports success against a button inside a [androidx.compose.material3.ModalBottomSheet]
     * under this Robolectric harness but never actually runs its callback — the same finding
     * [com.alvarotc.bito.ui.detail.DetailScreenTest] already documents on its own sheet clicks.
     * Invoking the node's own OnClick semantics action directly is what actually proves the tap
     * wires through, for the create-choice and task-form sheets below (T14).
     */
    private fun tapText(text: String) {
        // Merged tree (no useUnmergedTree): the OnClick action lives on the Button ancestor that
        // merges its Text child's semantics, not on the raw Text node itself.
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
    }

    /**
     * Espera a que la pila llegue a [ruta]. Con el bucle de Robolectric en pausa, lo que viaja por
     * `viewModelScope` (guardar un formulario) solo avanza cuando el test lo deja correr: de ahí el
     * `waitForIdle` dentro del sondeo, que un `waitUntil` a secas no hace.
     */
    private fun esperarRuta(
        nav: NavHostController,
        ruta: String,
    ) {
        val limite = System.currentTimeMillis() + 10_000
        while (compose.runOnUiThread { nav.currentDestination?.route } != ruta) {
            check(System.currentTimeMillis() < limite) { "La pila no llegó a $ruta" }
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    /** Pulsa el nodo con ese tag invocando su acción, para los botones que el toque sintetizado no alcanza. */
    private fun tapTag(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
    }

    /** Abre el segundo paso de la hoja del «+» y elige [tag] (`create-focus` o `create-breathing`). */
    private fun elegirSesion(tag: String) {
        val addLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.nav_new_habit)
        compose.onNodeWithContentDescription(addLabel, useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-session", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tapTag("create-session")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tapTag(tag)
    }

    /** The conditional start gates on Settings' first emission (loading -> today/onboarding);
     * wait for that placeholder to clear before any assertion, the same way the settings-reminder
     * test below already has to wait out a DataStore-backed emission that waitForIdle() alone
     * doesn't pump. */
    private fun waitPastLoadingGate() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("app-loading", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Every test below except the onboarding-route ones themselves assumes a completed
     * onboarding (landing on "today") — a fresh container otherwise defaults onboardingDone to
     * false and the conditional start would open on "onboarding" instead. */
    private suspend fun completeOnboarding(container: AppContainer) {
        container.settings.update { it.copy(onboardingDone = true) }
    }

    private lateinit var container: AppContainer

    /** Monta la app y le da el `NavController` a [alNav] para poder leer la pila. */
    private fun setContentWithNav(
        seed: suspend AppContainer.() -> Unit = {},
        alNav: (NavHostController) -> Unit,
    ) {
        container = AppContainer(ApplicationProvider.getApplicationContext<Application>())
        runBlocking {
            completeOnboarding(container)
            container.seed()
        }
        compose.setContent {
            BitoTheme {
                val nav = rememberNavController()
                alNav(nav)
                BitoNavHost(container, nav)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    /** Monta la app con un despachador de «atrás» propio, que el test pulsa desde fuera. */
    private fun setContentWithBack(backOwner: FakeBackDispatcherOwner) {
        container = AppContainer(ApplicationProvider.getApplicationContext<Application>())
        runBlocking { completeOnboarding(container) }
        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                    BitoNavHost(container)
                }
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    private fun setContent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        container = AppContainer(app)
        runBlocking { completeOnboarding(container) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    /** Like [setContent], but [seed] runs against the container's real database first. */
    private fun setContentSeeded(seed: suspend AppContainer.() -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        runBlocking {
            completeOnboarding(container)
            container.seed()
        }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    /**
     * Same limitation [com.alvarotc.bito.ui.review.ReviewScreenTest]'s own copy documents:
     * `createComposeRule()` has no exposed `.activity` to read a working
     * [OnBackPressedDispatcherOwner] back off of, so the only deterministic way to drive a system
     * back press under this harness is to provide the composition OUR OWN dispatcher instance and
     * invoke it ourselves from outside. Compose Navigation's own `NavHost` registers its back
     * handling against whatever [LocalOnBackPressedDispatcherOwner] it finds, so wrapping the
     * whole [BitoNavHost] in this owner (rather than a single screen, as `ReviewScreenTest` does)
     * lets a test drive the NAV GRAPH's own back stack, not just one screen's `BackHandler`.
     */
    private class FakeBackDispatcherOwner : OnBackPressedDispatcherOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = lifecycleRegistry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()

        init {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    @Test
    fun `the app opens on Today with the pill on Today and there is no bottom bar`() {
        setContent()
        compose.onNodeWithTag("inicio").assertExists()
        compose.onNodeWithTag("pildora-hoy").assertIsSelected()
        compose.onAllNodesWithTag("bottom-bar").assertCountEquals(0)
    }

    @Test
    fun `the pill opens Camino and back returns to Today`() {
        val backOwner = FakeBackDispatcherOwner()
        setContentWithBack(backOwner)
        compose.onNodeWithTag("pildora-camino").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pildora-camino").assertIsSelected()
        compose.onNodeWithTag("pildora-hoy").assertIsNotSelected()
        compose.onNodeWithTag("inicio-camino", useUnmergedTree = true).assertExists()
        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("pildora-hoy").assertIsSelected()
        compose.onNodeWithTag("pildora-camino").assertIsNotSelected()
    }

    /** Antes: la pestaña Ajustes de la barra y su resumen de avisos; ahora, la rueda de la cabecera. */
    @Test
    fun `the gear opens settings and the balance opens your bitos`() {
        val backOwner = FakeBackDispatcherOwner()
        setContentWithBack(backOwner)

        compose.onNodeWithTag("cabecera-ajustes", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // The notifications card only renders once SettingsViewModel's DataStore-backed flow has
        // emitted (real dispatcher, not the test's) — waitForIdle alone doesn't pump that. With a
        // fresh store no hours exist, so the Notices stratum summary says so.
        val reminderCopy = "No reminders"
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(reminderCopy, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        screenTitleNode("Reminders and breathing").assertExists()
        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertDoesNotExist()

        // Ajustes no tiene flecha: se vuelve con atrás, y Hoy está donde estaba.
        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hoja-saldo", useUnmergedTree = true).assertExists()
    }

    /** Antes: la pestaña Habi de la barra; ahora, el botón de la tienda de la hoja del saldo. */
    @Test
    fun `the store button of the balance sheet navigates to the habi screen`() {
        setContent()
        // HabiAvatar's infinite bob/blink transitions never settle on their own — freeze the
        // clock BEFORE navigating there, so the framework's post-click idle-sync doesn't spin
        // forever trying to reach a steady state that never comes (T9 note).
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        tapText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.store_title))
        // Not waitForIdle(): with autoAdvance false it pumps no frames at all, so the nav
        // recomposition from the click above would never actually run.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // QA 2026-08-23: HabiScreen gates its first frame on `loading` (calm paper, tag
        // "habi-loading") until the VM's combine emits, so arrival at the route is either tag.
        compose
            .onNode(hasTestTag("habi-loading") or hasTestTag("habi-screen"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `the stats routes no longer exist`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }

        assertThrows(IllegalArgumentException::class.java) { compose.runOnUiThread { nav.navigate("stats") } }
        NavRequests.open("stats")
        compose.waitForIdle()
        assertNull(NavRequests.pending.value)
        assertEquals("today", compose.runOnUiThread { nav.currentDestination?.route })
    }

    @Test
    fun `deep links to review, tasks, focus and breathing still work`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        val esperadas =
            mapOf(
                "review" to "review",
                "tasks" to "tasks",
                "focus" to "focus?taskId={taskId}",
                "breathing" to "breathing",
            )
        esperadas.forEach { (peticion, ruta) ->
            NavRequests.open(peticion)
            compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == ruta }
            assertNull(NavRequests.pending.value)
            compose.runOnUiThread { nav.popBackStack() }
            compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == "today" }
        }
    }

    @Test
    fun `archived opens the habit sheet read only`() {
        lateinit var nav: NavHostController
        setContentWithNav(
            seed = {
                habits.create(
                    habitEntity(id = "a1", name = "Hábito viejo", status = HabitStatus.ARCHIVED, archivedOnDay = 1, archivedAtMillis = 1L),
                )
            },
        ) { nav = it }

        compose.runOnUiThread { nav.navigate("archived") }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("archived-a1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // El gesto de la fila (como el de los menús de abajo) no llega por toque sintetizado: se invoca su acción.
        compose.onNodeWithTag("archived-a1", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == RUTA_FICHA }

        assertEquals(true, compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getBoolean("lectura") })
        assertEquals("a1", compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getString("habitId") })
    }

    @Test
    fun `creating a habit leaves its id for Today`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        val app = ApplicationProvider.getApplicationContext<Application>()

        compose.onNodeWithContentDescription(app.getString(R.string.nav_new_habit), useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-choice-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tapText("Habit")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name-field"))).performTextInput("Agua")
        compose.onNodeWithText(app.getString(R.string.preset_quantity)).performScrollTo().performClick()
        compose.waitForIdle()
        // Hoy consume `creado` en cuanto lo ve (alCreadoVisto): se observa cada valor que recibe, no el final.
        val vistos = mutableListOf<String?>()
        compose.runOnUiThread {
            nav.getBackStackEntry("today").savedStateHandle.getLiveData<String?>("creado").observeForever { vistos += it }
        }
        compose.onNodeWithTag("save").performScrollTo()
        tapTag("save")
        esperarRuta(nav, "today")

        val id = runBlocking { container.database.habitDao().all().single { it.name == "Agua" }.id }
        assertTrue("Hoy recibió el id recién creado: $vistos", id in vistos)
    }

    @Test
    fun `a task created from all tasks leaves its id on Today, not on tasks`() {
        lateinit var nav: NavHostController
        NavRequests.open("tasks")
        setContentWithNav { nav = it }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == "tasks" }

        compose.runOnUiThread { nav.navigate("task") }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-form-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(
            hasSetTextAction() and (hasTestTag("task-title-field") or hasAnyAncestor(hasTestTag("task-title-field"))),
        ).performTextInput("Llamar al banco")
        tapTag("task-form-save")
        esperarRuta(nav, "tasks")

        // La escritura de la tarea corre en el ViewModel de la actividad: se deja avanzar hasta que aparece.
        val limite = System.currentTimeMillis() + 10_000
        while (runBlocking { container.database.taskDao().all().none { it.title == "Llamar al banco" } }) {
            check(System.currentTimeMillis() < limite) { "La tarea no llegó a la base de datos" }
            compose.waitForIdle()
            Thread.sleep(50)
        }
        val id = runBlocking { container.database.taskDao().all().single { it.title == "Llamar al banco" }.id }
        assertEquals(id, compose.runOnUiThread { nav.getBackStackEntry("today").savedStateHandle.get<String>("creado") })
        assertNull(compose.runOnUiThread { nav.getBackStackEntry("tasks").savedStateHandle.get<String>("creado") })
    }

    /**
     * T14: the "+" asks first (create-choice-sheet) instead of navigating straight to the habit
     * form; the form is a full route, so Today's own tag leaves the screen while it is open.
     */
    @Test
    fun `the habit form takes over from Today and Back returns to it`() {
        setContent()
        val addLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.nav_new_habit)

        compose.onNodeWithContentDescription(addLabel, useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-choice-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tapText("Habit")
        compose.waitForIdle()
        compose.onNodeWithTag("pildora", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pildora-hoy", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping create opens the choice sheet with a habit and a task`() {
        setContent()
        val addLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.nav_new_habit)

        compose.onNodeWithContentDescription(addLabel, useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-choice-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("create-choice-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Habit", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Task", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `choosing a habit from the choice sheet reaches the usual habit form`() {
        setContent()
        val addLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.nav_new_habit)

        compose.onNodeWithContentDescription(addLabel, useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-choice-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        tapText("Habit")
        compose.waitForIdle()

        compose.onNodeWithTag("create-choice-sheet", useUnmergedTree = true).assertDoesNotExist()
        // The habit form's own back chevron — reusing "the bottom bar hides on the habit form"
        // above's own proof that this is genuinely the same route, not a new one.
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).assertExists()
    }

    /**
     * D12 revoked: choosing "A task" now navigates to [com.alvarotc.bito.ui.tasks.TaskFormScreen]
     * (a route, "task-form-screen" tag) instead of opening the old TaskFormSheet as a global sheet
     * — same reason "the bottom bar hides on the habit form but survives on stats" above already
     * proves for "habit": a route change hides the bar, so this also re-proves that hasn't
     * regressed for tasks. Recalibrated from the old "task-form-sheet" tag (the sheet itself is
     * gone now — retired once the tasks list's own edit flow moved to this same screen too).
     * Beyond the brief's own two: the model test covers [com.alvarotc.bito.ui.tasks.resolvedDueDay]
     * but nothing else ever composes [com.alvarotc.bito.ui.tasks.TaskFormScreen] itself in this
     * create path — this is that one compile-and-render safety net, deliberately stopping short of
     * driving the DatePickerDialog (task-14-brief's own call, still true: that dialog has no logic
     * worth a Robolectric test).
     */
    @Test
    fun `choosing a task from the choice sheet opens the task form with saving disabled on a blank title`() {
        setContent()
        val addLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.nav_new_habit)

        compose.onNodeWithContentDescription(addLabel, useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("create-choice-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        tapText("Task")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-form-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-form-screen", useUnmergedTree = true).assertExists()
        // El Guardar apagado se pulsa para decir qué falta: se marca con su descripción de estado.
        compose.onNodeWithTag("task-form-save", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `a pending review request opens the review flow and hides the bar`() {
        // Set BEFORE setContent: BitoNavHost's LaunchedEffect must observe the already-pending
        // route on its very first composition, the same way a notification tap's Intent extra
        // would already be waiting when onCreate() first builds the NavHost.
        NavRequests.open("review")

        setContent()

        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertExists()
        assertNull(NavRequests.pending.value)
    }

    @Test
    fun `a fresh install opens on the onboarding route, not today`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `onboardingDone true opens straight on today`() {
        setContent() // seeds onboardingDone = true

        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The blocker this route-level prompt exists to close: reminder hours are seeded at first
     * launch, alarms fire on time, and on API 33+ every one of them was dropped in silence by
     * Notifier's `areNotificationsEnabled()` guard, because nothing in the app ever requested
     * POST_NOTIFICATIONS. Asked here, on arrival, so that ONE ask covers finishing onboarding,
     * restoring a backup (which skips onboarding) and updating an install created before the
     * prompt existed — this test's `onboardingDone = true` container is exactly that last case.
     */
    @Test
    fun `landing on a real route asks for the notification permission once`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val container = AppContainer(app)
        runBlocking { completeOnboarding(container) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()

        // The claim is the install's single unprompted ask being spent — read non-destructively,
        // so this asserts the composable took it rather than taking it itself.
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { container.settings.notificationPromptClaimed.first() }
        }
    }

    @Test
    fun `the onboarding flow is never interrupted by the notification prompt`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val container = AppContainer(app) // onboardingDone defaults to false: opens on onboarding
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()

        // A system dialog over the story beats would be asking for access before the app has shown
        // what it's for; the ask waits — unspent — for the user to actually land in the app.
        assertFalse(runBlocking { container.settings.notificationPromptClaimed.first() })
    }

    /**
     * M9.5 final-review Important #2: [com.alvarotc.bito.ui.onboarding.OnboardingReconciler.reconcile]
     * now runs INSIDE this gate, before `startDestination` is decided (see [BitoNavHost]'s own
     * comment on that `produceState` block) — so a v1/v2 restore's habits-but-`onboardingDone =
     * false` state resolves deterministically to "today," never a race the reconciler could lose
     * against `remember`.
     */
    @Test
    fun `a restorer with habits and onboarding not done lands on today, not onboarding`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        runBlocking { container.database.habitDao().upsert(habitEntity(id = "restored-habit")) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()

        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * M9.5 T6 (fix round 1): [NavRequests] must not navigate OVER onboarding — same philosophy as
     * [com.alvarotc.bito.ui.BitoNavHost]'s celebration guard (a mid-flow user shouldn't get
     * sandwiched into e.g. "review"), and now genuinely the same MECHANISM: `BitoNavHost` simply
     * does not call [NavRequests.consume] while `onboarding` is the current route, so
     * [NavRequests.pending] itself — a process-wide, rotation-surviving `MutableStateFlow` — stays
     * the one source of truth for "review is still waiting," same as [NavRequests] is asserted
     * elsewhere. Deliberately NOT a bare absence assertion followed by nothing else: proving the
     * route STAYS pending here is necessary but not sufficient (a build that dropped the request
     * entirely would look identical at this checkpoint too) — only watching it actually fire once
     * onboarding hands off to "today," and get consumed there, proves it survived intact. Same
     * reasoning "a pending perfect day stays hidden behind onboarding and shows once today loads"
     * already uses for the celebration sheet.
     */
    @Test
    fun `a pending review request stays held behind onboarding and opens once onboarding hands off`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        NavRequests.open("review")
        compose.waitForIdle()

        // Held, not navigated: onboarding is still the one showing and review never opened over it.
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertDoesNotExist()
        // Genuinely still pending (not consumed into any ephemeral state): this is what makes the
        // hold rotation-safe -- NavRequests.pending is the one and only source of truth throughout.
        assertEquals("review", NavRequests.pending.value)

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("review-seal", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertExists()
        assertNull(NavRequests.pending.value) // fired through the normal branch and consumed there
    }

    /**
     * The full first-run journey through the REAL [BitoNavHost] — the only place [done]'s own
     * `navigate("today") { popUpTo("onboarding") { inclusive = true } }` (wired in the "onboarding"
     * composable above) can actually be observed landing. `OnboardingScreenTest`'s own
     * "the first habit step creates the habit and completes onboarding" proves the write path and
     * [OnboardingUiState.done] in isolation, without a NavHost to navigate anywhere.
     */
    @Test
    fun `the first habit step creates and lands on today`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertExists()

        val created = runBlocking { container.database.habitDao().all().single { it.name == "Beber agua" } }
        assertEquals(Metric.CHECK, created.metric)
    }

    /**
     * T19: the permanent notification's own path — "focus" bare, no id — must resolve against
     * whatever session happens to be live, same guard shape as "a pending review request opens
     * the review flow and hides the bar" above. Seeding the session BEFORE the FocusViewModel is
     * ever built is what proves the bare route resolves against it, rather than just proving the
     * screen doesn't crash on an absent one.
     */
    @Test
    fun `a pending focus request opens the focus screen against the live session`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
            val now = System.currentTimeMillis()
            val elapsed = android.os.SystemClock.elapsedRealtime()
            container.focus.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = now,
                    endsAtMillis = now + 10 * 60_000L,
                    endsAtElapsed = elapsed + 10 * 60_000L,
                    bootMillis = FocusClock.bootSignatureOf(now, elapsed),
                ),
            )
        }
        // Set BEFORE setContent, same reasoning as the review test above: the request must
        // already be waiting the very first time BitoNavHost composes.
        NavRequests.open("focus")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()

        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertExists()
        assertNull(NavRequests.pending.value)
    }

    /** M11: la notificacion del recordatorio abre el ejercicio EN REPOSO, sin barra inferior. */
    @Test
    fun `a pending breathing request opens the exercise at rest`() {
        NavRequests.open("breathing")

        setContent()

        compose.onNodeWithTag("breathing-start", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("breathing-phase", useUnmergedTree = true).assertDoesNotExist()
        assertNull(NavRequests.pending.value)
    }

    /** D2: antes el icono de Hoy; ahora la opción «Respirar» del «+». */
    @Test
    fun `the breathing option of the create sheet opens the exercise`() {
        setContent()
        elegirSesion("create-breathing")
        compose.waitForIdle()

        compose.onNodeWithTag("breathing-screen", useUnmergedTree = true).assertExists()
    }

    /**
     * T19: antes comprobaba el «Empezar» de la fila de Hoy; esa fila la reescribe la Ola 2 (c2) y
     * aquí no se prueba por sus textos. Lo que queda es el cableado de Inicio: `abrirRuta("focus")`
     * desde la opción «Concentrarse» del «+» llega a la ruta de la sesión.
     */
    @Test
    fun `the focus option of the create sheet reaches the focus route`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        elegirSesion("create-focus")
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == "focus?taskId={taskId}" }
    }

    /** T19 (M10 review round 1, minor #2): the same wiring proven from Today above, but through
     * [com.alvarotc.bito.ui.tasks.TasksScreen]'s own "Empezar" — its `onStartFocus` reaching the
     * same route with the row's own id, not just [com.alvarotc.bito.ui.tasks.TasksScreen]'s own
     * callback in isolation ([com.alvarotc.bito.ui.tasks.TasksScreenTest]'s own coverage). */
    @Test
    fun `starting a task from the tasks list opens the focus screen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
        }
        NavRequests.open("tasks")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-menu-t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Start", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("focus-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("focus-screen", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Llamar al banco", useUnmergedTree = true).assertExists()
    }

    /**
     * D12 revoked, editing half: [com.alvarotc.bito.ui.tasks.TasksScreen]'s own "editar" now
     * navigates to "task?id={id}" instead of opening a sheet inline
     * ([com.alvarotc.bito.ui.tasks.TasksScreenTest]'s own "the row menu edit calls onEditTask with
     * the row id" proves the callback fires; this proves the route it reaches actually renders the
     * preloaded fields — the id has to survive a real Room read, not just an in-memory row, since
     * the new route loads it itself). Same "task-form-screen" tag the create path already proves in
     * "choosing a task from the choice sheet opens the task form with saving disabled on a blank
     * title" above.
     */
    @Test
    fun `editing a task from the tasks list reaches the full-screen form with the task preloaded`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Titulo original", firstStep = "Paso original", createdOnDay = today))
        }
        NavRequests.open("tasks")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-menu-t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-form-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Edit task", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Titulo original", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Paso original", useUnmergedTree = true).assertExists()
    }

    /**
     * D12 revoked, delete half: the red delete button this ola adds to TaskFormScreen's editing
     * route, wired all the way through its confirm sheet to [com.alvarotc.bito.ui.tasks.TasksViewModel.delete]
     * and back to the tasks list. Not covered by [com.alvarotc.bito.ui.tasks.TaskFormScreenTest]
     * (which fakes [onDelete] and never touches Room), so this is the one place proving the whole
     * chain — button, confirm sheet, actual delete, actual pop — really is connected.
     */
    @Test
    fun `deleting a task from its own edit screen removes it and returns to the tasks list`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Tarea a borrar", createdOnDay = today))
        }
        NavRequests.open("tasks")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-menu-t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-form-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // The screen's own button, not inside a sheet: performClick() works here.
        compose.onNodeWithTag("task-delete", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete this task?", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("tasks-list", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("task-form-screen", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Tarea a borrar", useUnmergedTree = true).assertDoesNotExist()
        assertNull(runBlocking { container.tasks.task("t1") })
    }

    /**
     * Ola 4 review (Minor #6): the task can disappear between opening the row menu and this
     * route actually loading it (borrado desde otra pantalla, reinicio a media edicion) — the
     * route used to fall back to a blank `TaskFormState(editingId = id)`, so "Guardar" looked
     * live but `TasksRepository.update` silently did nothing (its own `?: return@withTransaction`
     * on a missing id). Deleting the task right after opening the menu, before the edit action
     * actually fires, reproduces that exact window.
     */
    @Test
    fun `editing a task that vanished before the route loads it just goes back`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Tarea fantasma", createdOnDay = today))
        }
        NavRequests.open("tasks")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-menu-t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-menu-t1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        runBlocking { container.tasks.delete("t1") }
        compose.onNodeWithText("Edit", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("tasks-list", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("task-form-screen", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("task-form-loading", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * M10 review round 1 (Important #1): without `popUpTo`, "seguir con la otra" pushed a SECOND
     * "focus" entry on top of the conflict one instead of replacing it — system back from the
     * live session then landed back on the conflict screen (`busyWith` still non-null), reopening
     * the very same sheet with no way out while the other session stayed alive. Walks the exact
     * reported path: tasks list -> `focus?taskId=t2` (conflict, t1's session already live) ->
     * "seguir con la otra" -> `focus` (bare, now watching t1) -> ATRAS -> must land back on the
     * tasks list, never on the conflict sheet again.
     */
    @Test
    fun `keeping the other session during a conflict does not trap back navigation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Tarea uno", createdOnDay = today))
            container.tasks.create(taskEntity(id = "t2", title = "Tarea dos", createdOnDay = today))
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            container.focus.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = now,
                    endsAtMillis = now + 10 * 60_000L,
                    endsAtElapsed = elapsed + 10 * 60_000L,
                    bootMillis = FocusClock.bootSignatureOf(now, elapsed),
                ),
            )
        }
        NavRequests.open("tasks")
        val backOwner = FakeBackDispatcherOwner()

        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                    BitoNavHost(container)
                }
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("task-menu-t2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("task-menu-t2", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Start", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("focus-busy-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus-busy-sheet", useUnmergedTree = true).assertExists()

        // Not performClick(): a button inside a ModalBottomSheet does not receive synthesized
        // touch gestures under this Robolectric harness — invoking the node's own OnClick
        // semantics action directly is what actually proves the tap wires through.
        compose.onNodeWithTag("focus-busy-keep")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
        compose.waitForIdle()

        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertExists() // now watching t1's live session
        compose.onNodeWithTag("focus-busy-sheet", useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        // Not trapped on the conflict: back from the live session lands on the tasks list, and
        // the busy sheet — which a stacked (not replaced) conflict entry would have reopened —
        // never comes back.
        compose.onNodeWithTag("focus-busy-sheet", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("focus-clock", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("task-menu-t2", useUnmergedTree = true).assertExists()
    }
}
