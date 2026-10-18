package com.alvarotc.bito.ui

import android.app.Application
import android.app.NotificationManager
import android.os.Looper
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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ContenedoresDeSesion
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.ui.inicio.EntradaSesion
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
import java.time.Duration
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
    val sesiones = ContenedoresDeSesion()

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

    /** Hábito y Tarea del «+» navegan 90 ms después de cerrarse la hoja: se deja correr ese tiempo. */
    private fun esperarCierreDeLaHoja() {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    /**
     * Espera a que salga [tag]. Las pantallas de ruta (repaso, respirar) pintan su contenido solo cuando su
     * ViewModel termina de cargar por los hilos reales de Room y DataStore, que `waitForIdle` no espera: afirmar
     * con `assertExists` nada más montar es una carrera contra esos hilos.
     */
    private fun esperarEtiqueta(tag: String) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
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
        container = sesiones.registrar(AppContainer(ApplicationProvider.getApplicationContext<Application>()))
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
        container = sesiones.registrar(AppContainer(ApplicationProvider.getApplicationContext<Application>()))
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
        container = sesiones.registrar(AppContainer(app))
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
        val container = sesiones.registrar(AppContainer(app))
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
     * Same limitation the old review screen test documented:
     * `createComposeRule()` has no exposed `.activity` to read a working
     * [OnBackPressedDispatcherOwner] back off of, so the only deterministic way to drive a system
     * back press under this harness is to provide the composition OUR OWN dispatcher instance and
     * invoke it ourselves from outside. Compose Navigation's own `NavHost` registers its back
     * handling against whatever [LocalOnBackPressedDispatcherOwner] it finds, so wrapping the
     * whole [BitoNavHost] in this owner (rather than a single screen)
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

    private fun actividad() = (compose as AndroidComposeTestRule<*, *>).activity as android.app.Activity

    /** Simula las barras del sistema: [estado] px arriba y [navegacion] px abajo, como las daría un móvil con gestos. */
    private fun ponerBarras(
        estado: Int,
        navegacion: Int,
    ) {
        compose.runOnUiThread {
            val vista = actividad().window.decorView
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, estado, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navegacion))
                    .build()
            ViewCompat.dispatchApplyWindowInsets(vista, insets)
        }
        compose.waitForIdle()
    }

    @Test
    fun `the bars are applied once and Inicio places its own header below the status bar`() {
        WindowCompat.setDecorFitsSystemWindows(actividad().window, false)
        setContent()
        ponerBarras(estado = 66, navegacion = 66)
        val densidad = actividad().resources.displayMetrics.density
        val bordeEstado = 66f / densidad
        // Las barras se aplican una sola vez: Inicio arranca bajo la de estado (no a 132 px) y la cabecera cae debajo.
        // Que ese hueco lo pinte la escena y no el papel del Scaffold lo fija `escalaJardin` (InicioScreenTest).
        val inicio = compose.onNodeWithTag("inicio").getBoundsInRoot()
        assertEquals(bordeEstado, inicio.top.value, 1f)
        val saldo = compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("el saldo queda bajo la barra de estado", saldo.top.value >= bordeEstado - 1f)
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
    fun `the habi route shows the store`() {
        setContent()
        // El armario de la tienda lleva el HabiAvatar, cuyas transiciones infinitas nunca se asientan: se
        // congela el reloj ANTES de navegar, para que la espera de reposo del clic no gire sin fin (nota de T9).
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        tapText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.premios_ir_tienda))
        compose.mainClock.advanceTimeBy(200) // la hoja navega a los 160 ms de cerrarse
        // Not waitForIdle(): with autoAdvance false it pumps no frames at all, so the nav
        // recomposition from the click above would never actually run.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // La tienda espera a su primer estado real (papel, tag «tienda-cargando») antes de pintar el contenido.
        compose
            .onNode(hasTestTag("tienda-cargando") or hasTestTag("ttab-habi"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `entering the store dims Today and leaving it clears the veil`() {
        val backOwner = FakeBackDispatcherOwner()
        var ultimo: Float? = null
        sondaVeloHoy = { ultimo = it }

        fun velo() = ultimo

        try {
            setContentWithBack(backOwner)
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(100)
            assertEquals(0f, velo()!!, 0.001f)

            compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            tapText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.premios_ir_tienda))
            compose.mainClock.advanceTimeBy(200)
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(120)
            val mitad = velo()
            assertTrue("Hoy se está oscureciendo ($mitad)", mitad != null && mitad > 0f && mitad <= VELO_HOY + 0.001f)
            assertEquals(0.35f, VELO_HOY, 0.0001f)

            compose.mainClock.advanceTimeBy(1_500)
            compose.runOnUiThread { backOwner.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(1_500)
            assertEquals("al hundirse la tienda el velo se va", 0f, velo()!!, 0.01f)
        } finally {
            sondaVeloHoy = null
        }
    }

    /** Lo alto (sin recortar por la pantalla) de la cabecera de Hoy, la que sube con la tienda encima; nulo si Hoy ya no está compuesto. */
    private fun altoDeHoy(): Float? =
        compose
            .onAllNodesWithTag("cabecera-saldo", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.positionInRoot
            ?.y

    @Test
    fun `Today rises 40 dp under the store and the veil settles at 35 percent, which is 65 percent of brightness`() {
        val backOwner = FakeBackDispatcherOwner()
        var velo = 0f
        sondaVeloHoy = { velo = it }
        try {
            setContentWithBack(backOwner)
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(300)
            val inicial = altoDeHoy()!!

            compose.onNodeWithTag("cabecera-saldo", useUnmergedTree = true).performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            tapText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.premios_ir_tienda))
            var ultimo = inicial
            repeat(160) {
                compose.mainClock.advanceTimeBy(16)
                altoDeHoy()?.let {
                    ultimo = it
                }
            }
            val sube = inicial - ultimo
            val cuarenta = with(compose.density) { 40.dp.toPx() }
            assertEquals("Hoy sube 40 dp", cuarenta, sube, 2f)
            assertEquals("el velo se asienta en el 35 %: Hoy queda al 65 %", 0.35f, velo, 0.01f)

            compose.runOnUiThread { backOwner.onBackPressedDispatcher.onBackPressed() }
            repeat(160) { compose.mainClock.advanceTimeBy(16) }
            assertEquals("Hoy vuelve a su sitio", inicial, altoDeHoy()!!, 1f)
            assertEquals(0f, velo, 0.01f)
        } finally {
            sondaVeloHoy = null
        }
    }

    @Test
    fun `opening the store while Camino is on screen lands on Habi with no trial`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        compose.mainClock.autoAdvance = false
        tapTag("pildora-camino")
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("pildora-camino", useUnmergedTree = true).assertIsSelected()

        // Es lo que hace `abrirTienda` del Inicio, venga de la hoja del saldo o de donde venga.
        compose.runOnUiThread { nav.navigate(RUTA_TIENDA) { launchSingleTop = true } }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(RUTA_TIENDA, compose.runOnUiThread { nav.currentDestination?.route })
        // La tienda espera a su primer estado real (hilos reales de Room y DataStore) antes de pintar las pestañas.
        val limite = System.currentTimeMillis() + 10_000
        while (compose.onAllNodesWithTag("ttab-habi", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < limite) { "La tienda no salió del estado de carga" }
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
        compose.onNodeWithTag("ttab-habi", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("ttab-jardin", useUnmergedTree = true).assertIsNotSelected()
        compose.onNodeWithTag("ttab-congeladores", useUnmergedTree = true).assertIsNotSelected()
        compose.onNodeWithTag("barra", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the stats routes no longer exist`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }

        assertThrows(IllegalArgumentException::class.java) { compose.runOnUiThread { nav.navigate("stats") } }
        NavRequests.open("stats")
        compose.waitForIdle()
        assertNull(NavRequests.pending.value)
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
    }

    /** Antes iba también «review» → "review"; ahora abre el cierre dentro del Inicio (casos del cierre, más abajo). */
    @Test
    fun `the deep link to tasks still works`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        val esperadas =
            mapOf("tasks" to "tasks")
        esperadas.forEach { (peticion, ruta) ->
            NavRequests.open(peticion)
            compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == ruta }
            assertNull(NavRequests.pending.value)
            compose.runOnUiThread { nav.popBackStack() }
            compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == RUTA_HOY }
        }
    }

    @Test
    fun `there is no breathing route anymore`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }

        val rutas = compose.runOnUiThread { nav.graph.map { it.route } }
        assertTrue("sin ruta de respirar: $rutas", rutas.none { it == "breathing" })
        assertThrows(IllegalArgumentException::class.java) { compose.runOnUiThread { nav.navigate("breathing") } }
    }

    @Test
    fun `there is no focus route anymore`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }

        val rutas = compose.runOnUiThread { nav.graph.map { it.route } }
        assertTrue("sin ruta de foco: $rutas", rutas.none { it?.startsWith("focus") == true })
        assertThrows(IllegalArgumentException::class.java) { compose.runOnUiThread { nav.navigate("focus?taskId=t1") } }
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

    /** Empuja [ruta] sobre Hoy, comprueba que hay una flecha de volver y que, al pulsarla, la pila vuelve a Hoy. */
    private fun volverDesde(
        ruta: String,
        rutaNav: String,
    ) {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        compose.runOnUiThread { nav.navigate(ruta) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == rutaNav }
        compose.waitUntil(timeoutMillis = 5_000) { hayTag("volver") }
        compose.onNodeWithTag("volver", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        esperarRuta(nav, RUTA_HOY)
    }

    @Test
    fun `settings has a back arrow that returns to Today`() = volverDesde("settings", "settings")

    @Test
    fun `archived has a back arrow that returns to Today`() = volverDesde("archived", "archived")

    @Test
    fun `tasks has a back arrow that returns to Today`() = volverDesde("tasks", "tasks")

    @Test
    fun `the new habit form has a back arrow that returns to Today`() = volverDesde("habit", "habit?id={id}&objetivo={objetivo}")

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
        esperarCierreDeLaHoja()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name-field"))).performTextInput("Agua")
        compose.onNodeWithText(app.getString(R.string.preset_quantity)).performScrollTo().performClick()
        compose.waitForIdle()
        // Hoy consume `creado` en cuanto lo ve (alCreadoVisto): se observa cada valor que recibe, no el final.
        val vistos = mutableListOf<String?>()
        val observador = androidx.lifecycle.Observer<String?> { vistos += it }
        compose.runOnUiThread {
            nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<String?>("creado").observeForever(observador)
        }
        compose.onNodeWithTag("save").performScrollTo()
        tapTag("save")
        esperarRuta(nav, RUTA_HOY)

        val id = runBlocking { container.database.habitDao().all().single { it.name == "Agua" }.id }
        compose.runOnUiThread {
            nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<String?>("creado").removeObserver(observador)
        }
        assertTrue("Hoy recibió el id recién creado: $vistos", id in vistos)
    }

    /** P19: tras una petición que sustituye la entrada de Hoy (con argumentos), lo recién creado sigue llegando a Hoy. */
    @Test
    fun `a habit created from Inicio is still highlighted`() {
        lateinit var nav: NavHostController
        NavRequests.open("review")
        setContentWithNav { nav = it }
        esperarEtiqueta("cierre")
        val app = ApplicationProvider.getApplicationContext<Application>()

        compose.runOnUiThread { nav.navigate("habit") }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("name-field"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name-field"))).performTextInput("Agua")
        compose.onNodeWithText(app.getString(R.string.preset_quantity)).performScrollTo().performClick()
        compose.waitForIdle()
        val vistos = mutableListOf<String?>()
        val observador = androidx.lifecycle.Observer<String?> { vistos += it }
        compose.runOnUiThread {
            nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<String?>("creado").observeForever(observador)
        }
        compose.onNodeWithTag("save").performScrollTo()
        tapTag("save")
        esperarRuta(nav, RUTA_HOY)

        val id = runBlocking { container.database.habitDao().all().single { it.name == "Agua" }.id }
        compose.runOnUiThread {
            nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<String?>("creado").removeObserver(observador)
        }
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
        assertEquals(id, compose.runOnUiThread { nav.getBackStackEntry(RUTA_HOY).savedStateHandle.get<String>("creado") })
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
        esperarCierreDeLaHoja()
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
        esperarCierreDeLaHoja()

        compose.onNodeWithTag("create-choice-sheet", useUnmergedTree = true).assertDoesNotExist()
        // The habit form's own back chevron: proof that this is the habit form route, not a new one.
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).assertExists()
    }

    /**
     * D12 revoked: choosing "A task" now navigates to [com.alvarotc.bito.ui.tasks.TaskFormScreen]
     * (a route, "task-form-screen" tag) instead of opening the old TaskFormSheet as a global sheet
     * — a route of its own, like "habit". Recalibrated from the old "task-form-sheet" tag (the sheet itself is
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

    /** Antes: la ruta "review". Ahora la misma petición pendiente abre el cierre dentro del Inicio, sin píldora ni +. */
    @Test
    fun `a pending review request opens the day close inside Inicio and hides the pill`() {
        // Set BEFORE setContent: BitoNavHost's LaunchedEffect must observe the already-pending
        // route on its very first composition, the same way a notification tap's Intent extra
        // would already be waiting when onCreate() first builds the NavHost.
        NavRequests.open("review")
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }

        // El cierre cuelga del Inicio, que espera a su carga (Room y DataStore en hilos reales): se espera a su etiqueta.
        esperarEtiqueta("cierre")
        assertNull(NavRequests.pending.value)
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
        compose.onNodeWithTag("pildora", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the review request opens the day close inside Inicio`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        esperarEtiqueta("inicio-hoy")

        compose.runOnIdle { NavRequests.open("review") }

        esperarEtiqueta("cierre")
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
        assertNull(NavRequests.pending.value)
        // Una sola entrada de Hoy: la petición la sustituye en vez de apilar otra.
        assertEquals(1, compose.runOnUiThread { nav.currentBackStack.value.count { it.destination.route == RUTA_HOY } })
    }

    @Test
    fun `a review request from all tasks leaves only Today with the close in the stack`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        esperarEtiqueta("inicio-hoy")
        compose.runOnUiThread { nav.navigate("tasks") }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == "tasks" }

        compose.runOnIdle { NavRequests.open("review") }

        esperarEtiqueta("cierre")
        val pila = compose.runOnUiThread { nav.currentBackStack.value.mapNotNull { it.destination.route } }
        assertEquals(listOf(RUTA_HOY), pila)
        assertEquals(true, compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getBoolean("cierre") })
    }

    @Test
    fun `there is no review route any more`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        assertNull(compose.runOnUiThread { nav.graph.findNode("review") })
        assertThrows(IllegalArgumentException::class.java) { compose.runOnUiThread { nav.navigate("review") } }
    }

    @Test
    fun `back from the day close returns to Today on the same route`() {
        val backOwner = FakeBackDispatcherOwner()
        NavRequests.open("review")
        setContentWithBack(backOwner)
        esperarEtiqueta("cierre")

        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(timeoutMillis = 5_000) { !hayTag("cierre") }
        compose.onNodeWithTag("pildora-hoy", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("inicio", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the gordo request opens Today with the big thing flag`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        esperarEtiqueta("inicio-hoy")
        val vistos = mutableListOf<Boolean?>()
        val observador = androidx.lifecycle.Observer<Boolean?> { vistos += it }
        val vista = "gordo-visto"

        compose.runOnIdle { NavRequests.open("gordo") }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getBoolean("gordo") } == true
        }
        compose.runOnUiThread { nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<Boolean?>(vista).observeForever(observador) }

        // HoyLista recibe abrirGordo y avisa de que lo ha visto: la entrada lo apunta y no vuelve a pedirlo.
        compose.waitUntil(timeoutMillis = 5_000) { vistos.lastOrNull() == true }
        compose.runOnUiThread { nav.getBackStackEntry(RUTA_HOY).savedStateHandle.getLiveData<Boolean?>(vista).removeObserver(observador) }
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
        assertNull(NavRequests.pending.value)
        assertEquals(1, compose.runOnUiThread { nav.currentBackStack.value.count { it.destination.route == RUTA_HOY } })
    }

    @Test
    fun `the today request goes back to the Today already open`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        esperarEtiqueta("inicio-hoy")
        compose.runOnUiThread { nav.navigate("tasks") }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == "tasks" }

        compose.runOnIdle { NavRequests.open("today") }

        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == RUTA_HOY }
        assertEquals(1, compose.runOnUiThread { nav.currentBackStack.value.count { it.destination.route == RUTA_HOY } })
        assertFalse(compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getBoolean("cierre") } ?: true)
    }

    @Test
    fun `the habit form route takes an optional suggested target`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        esperarEtiqueta("inicio-hoy")

        compose.runOnUiThread { nav.navigate("habit") }
        compose.waitUntil(timeoutMillis = 5_000) { compose.runOnUiThread { nav.currentDestination?.route } == RUTA_FORM_HABITO }
        assertEquals(-1, compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getInt("objetivo") })

        compose.runOnUiThread { nav.navigate("habit?objetivo=12") }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnUiThread { nav.currentBackStackEntry?.arguments?.getInt("objetivo") } == 12
        }
        assertEquals(RUTA_FORM_HABITO, compose.runOnUiThread { nav.currentDestination?.route })
    }

    @Test
    fun `a fresh install opens on the onboarding route, not today`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = sesiones.registrar(AppContainer(app)) // onboardingDone defaults to false: nothing seeded here
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
        val container = sesiones.registrar(AppContainer(app))
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
        val container = sesiones.registrar(AppContainer(app)) // onboardingDone defaults to false: opens on onboarding
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
        val container = sesiones.registrar(AppContainer(app)) // onboardingDone defaults to false: nothing seeded here
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
        val container = sesiones.registrar(AppContainer(app)) // onboardingDone defaults to false: nothing seeded here
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
        compose.onNodeWithTag("cierre", useUnmergedTree = true).assertDoesNotExist()
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
            compose.onAllNodesWithTag("cierre", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cierre", useUnmergedTree = true).assertExists()
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
        val container = sesiones.registrar(AppContainer(app)) // onboardingDone defaults to false: nothing seeded here
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

    /** Monta la app con la marca del libro a cero (lo que entre se celebra) y un despachador de «atrás» propio. */
    private fun setContentCelebrando(
        backOwner: FakeBackDispatcherOwner,
        onboardingHecho: Boolean = true,
        alNav: (NavHostController) -> Unit = {},
    ) {
        container = sesiones.registrar(AppContainer(ApplicationProvider.getApplicationContext<Application>()))
        runBlocking {
            if (onboardingHecho) completeOnboarding(container)
            container.settings.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
        }
        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                    val nav = rememberNavController()
                    alNav(nav)
                    BitoNavHost(container, nav)
                }
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    private fun hayTag(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Un logro nuevo espera [VENTANA_LOGRO_MS] en el reloj del hilo principal, que Robolectric solo mueve si se le pide. */
    private fun esperarAvanzando(condicion: () -> Boolean) =
        compose.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            condicion()
        }

    /** Da tiempo real a que Room y DataStore entreguen lo suyo antes de afirmar que algo NO se pinta. */
    private fun dejarPasar() =
        repeat(10) {
            compose.waitForIdle()
            Thread.sleep(50)
        }

    /** Un logro que llega con Ajustes abierto espera: Ajustes no es Hoy. Al volver a Hoy, sale. */
    @Test
    fun `a new badge does not show while settings is open`() {
        val backOwner = FakeBackDispatcherOwner()
        setContentCelebrando(backOwner)
        compose.onNodeWithTag("cabecera-ajustes", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { hayTag("inicio").not() }
        screenTitleNode("Reminders and breathing").assertExists()

        runBlocking { container.database.badgeDao().insert(BadgeEntity("first-habit", 500)) }
        dejarPasar()
        assertTrue("el logro no se pinta con Ajustes abierto", !hayTag("hoja-logro"))

        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        esperarAvanzando { hayTag("hoja-logro") }
    }

    /**
     * El repaso que el onboarding retiene y el día perfecto que ya estaba en el libro: ni el onboarding ni el repaso
     * (que abre en cuanto el onboarding entrega) pintan la veta; sale al volver a Hoy.
     */
    @Test
    fun `a pending perfect day with the review held behind onboarding is not painted until back on Today`() {
        val backOwner = FakeBackDispatcherOwner()
        lateinit var nav: NavHostController
        setContentCelebrando(backOwner, onboardingHecho = false) { nav = it }
        val hoy = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            container.database
                .pointsLedgerDao()
                .insertAll(
                    listOf(
                        pointsLedgerEntity(
                            id = "d",
                            delta = 3,
                            reason = PointsReason.PERFECT_DAY,
                            refId = "day:$hoy",
                            logicalDay = hoy,
                            createdAtMillis = 10,
                        ),
                    ),
                )
        }
        NavRequests.open("review")
        compose.waitUntil(timeoutMillis = 5_000) { hayTag("onboarding-screen") }
        dejarPasar()

        assertTrue("el onboarding no pinta la veta", !hayTag("veta-DIA"))
        assertTrue("el onboarding no pinta ningún logro", !hayTag("hoja-logro"))
        assertEquals("review", NavRequests.pending.value)

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { hayTag("cierre") }
        dejarPasar()
        assertTrue("el repaso no pinta la veta del día", !hayTag("veta-DIA"))
        assertTrue("el repaso no pinta ningún logro", !hayTag("hoja-logro"))

        compose.runOnIdle { backOwner.onBackPressedDispatcher.onBackPressed() }
        // La veta ya no sale aquí: la Hoy que asoma un instante bajo el onboarding la celebra a ciegas antes de que el
        // repaso tome la pantalla (ver el informe). Lo que sí sale, y solo al volver, es el logro del primer hábito.
        esperarAvanzando { hayTag("hoja-logro") }
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
    }

    /**
     * La notificación de la sesión pide `focus`: abre Hoy en modo sesión, en el estado de la sesión que esté viva.
     * La sesión se siembra antes de componer, así que la petición ya espera cuando el NavHost aparece.
     */
    @Test
    fun `the focus request opens the live session`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = sesiones.registrar(AppContainer(app))
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        runBlocking {
            completeOnboarding(container)
            container.tasks.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
            container.settings.update { it.copy(sessionEntry = Entrada.DIRECTO) }
            container.motorSesion.preparar(Destino.Tarea("t1"))
            container.motorSesion.empezar()
        }
        NavRequests.open("focus")

        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()

        esperarEtiqueta("ses-b-foco")
        assertNull(NavRequests.pending.value)
        assertNull(EntradaSesion.pendiente.value)
        runBlocking { container.motorSesion.loDejo() }
    }

    /** El aviso diario de respirar abre la respiración suelta en Hoy, en reposo (elegir), con el último modo. */
    @Test
    fun `a pending breathing request opens the loose breathing at rest in today`() {
        lateinit var nav: NavHostController
        NavRequests.open("breathing")

        setContentWithNav { nav = it }

        esperarEtiqueta("r-empezar")
        compose.onNodeWithTag("e-fase", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
        assertNull(NavRequests.pending.value)
        assertNull(EntradaSesion.pendiente.value)
    }

    /** D2: antes el icono de Hoy; ahora la opción «Respirar» del «+». */
    @Test
    fun `the breathing option of the create sheet opens the loose breathing`() {
        setContent()
        elegirSesion("create-breathing")

        esperarEtiqueta("r-empezar")
    }

    /** El «+» → Sesión ahora → Foco abre Preparar dentro del Inicio, sin cambiar de ruta. */
    @Test
    fun `the focus option of the create sheet opens prepare in today`() {
        lateinit var nav: NavHostController
        setContentWithNav { nav = it }
        elegirSesion("create-focus")

        esperarEtiqueta("ses-b-prep")
        assertEquals(RUTA_HOY, compose.runOnUiThread { nav.currentDestination?.route })
    }

    /** Lo mismo desde el «Empezar» de Tareas: vuelve a Hoy y abre Preparar con esa tarea. */
    @Test
    fun `starting a task from the tasks list opens prepare with that task`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = sesiones.registrar(AppContainer(app))
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

        esperarEtiqueta("ses-b-prep")
        assertEquals(Objetivo.Tarea("t1"), container.motorSesion.borrador.value?.objetivo)
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
        val container = sesiones.registrar(AppContainer(app))
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
        val container = sesiones.registrar(AppContainer(app))
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
        val container = sesiones.registrar(AppContainer(app))
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
}
