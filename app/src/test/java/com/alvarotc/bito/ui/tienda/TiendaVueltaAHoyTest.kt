package com.alvarotc.bito.ui.tienda

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.sesion.SonidoGrabado
import com.alvarotc.bito.sesion.TactoGrabado
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.NavRequests
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

private const val ESPERA_MAXIMA = 1_500
private const val ZONA = "Europe/Madrid"
private const val MARGEN_LOGROS = 1_000L
private const val ASENTAR_MS = 1_500L

/** J31: al volver de la tienda a Hoy, lo recién comprado se mece una vez a los 420 ms (spec §3.3.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class TiendaVueltaAHoyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<BitoApp>()
    private lateinit var container: AppContainer
    private val sonda = SondaJardin()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    /** El instante fijado a mano (16 de octubre de 2026 a mediodía) y lo que han avanzado los fotogramas desde entonces. */
    private var ultimaNota = ""

    @Volatile private var fijo = instante(2026, 10, 16)
    private val transcurrido = AtomicLong(0)

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        AppVisibility.visible = true
        container = app.container
        container.relojTienda = RelojTienda(ahora = { fijo + transcurrido.get() }, zona = { ZoneId.of(ZONA) })
        NotificationChannels.ensure(app)
        runBlocking { container.settings.update { it.copy(onboardingDone = true, personality = Personality.NEUTRA) } }
    }

    @After
    fun tearDown() {
        container.cerrarSesion()
        AppVisibility.visible = false
        NavRequests.consume()
        AppStartup.quiesceForTests()
    }

    @Test
    fun `coming back from the store rocks the bought bench once at 420 ms and not on the second return`() {
        sembrar(140)
        montar()
        entrarEnLaTienda()
        // Una visita larga: antes de comprar pasan más de los 5 s de WhileSubscribed y Hoy deja de recoger, con el estado de
        // antes de comprar. El reloj de Compose no mueve ese temporizador: se avanza también el del looper.
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            fotogramas(1_000)
        }
        pulsar("ttab-jardin")
        hasta { hay("item-garden-banco") }
        pulsar("item-garden-banco")
        hasta { hay("barra") }
        pulsar("barra-comprar")
        hasta { saldoDe() == 60 }
        hasta { desc("item-garden-banco").contains("En tu jardín") }
        assertEquals("con la tienda abierta no se mece nada", 0, plantas())

        // Atrás: Hoy vuelve; el mece llega a los 420 ms de cerrarse la tienda, una sola vez.
        val m0 = sonido.sonidos.size
        val t0 = compose.mainClock.currentTime
        pulsar("tienda-atras")
        var cuando = -1L
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cuando < 0 && plantas(m0) > 0) cuando = compose.mainClock.currentTime - t0
            if (cuando < 0) fotogramas(16)
        }
        assertTrue("el banco se mece al volver, a los ~420 ms (pasaron $cuando)", cuando in 380..700)
        hasta { hay("garden-scene") && !hay("tienda") }
        fotogramas(3000)
        assertEquals("una sola vez", 1, plantas(m0))

        // Segunda vez sin comprar nada: no se mece.
        entrarEnLaTienda()
        val m1 = sonido.sonidos.size
        pulsar("tienda-atras")
        hasta { hay("garden-scene") && !hay("tienda") }
        fotogramas(3000)
        assertEquals("sin compras nuevas no se mece", 0, plantas(m1))
    }

    private fun plantas(desde: Int = 0) = sonido.sonidos.toList().drop(desde).count { it == Snd.PLANTA }

    // ───────────────────────── ayudantes ─────────────────────────

    private fun instante(
        anio: Int,
        mes: Int,
        dia: Int,
    ): Long = ZonedDateTime.of(anio, mes, dia, 12, 0, 0, 0, ZoneId.of(ZONA)).toInstant().toEpochMilli()

    private fun diaFijo(): Int = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(fijo), ZoneId.of(ZONA)).toEpochDay().toInt()

    /**
     * Saldo [saldo] con dos congeladores comprados, un hábito con 31 días seguidos (mejor racha de 31), y la flor y el
     * pañuelo brasa ya ganados. El libro lleva un único asiento positivo para que el saldo sea exacto.
     */
    private fun sembrar(saldo: Int) {
        runBlocking {
            val dia = diaFijo()
            container.habits.create(
                habitEntity(id = "h1", name = "Estirar", metric = Metric.CHECK, target = 1, unit = null, createdOnDay = dia - 40),
            )
            for (i in 0..30) {
                container.database.entryDao().insert(
                    entryEntity(id = "e$i", habitId = "h1", logicalDay = dia - i, value = 1, createdAtMillis = fijo),
                )
            }
            // Hoy abre el Inicio y reconcilia con el reloj de verdad: se hace aquí antes, para que luego no llegue nada nuevo
            // (puntos, piezas de racha ni logros por celebrar) y el saldo sea el que se pide.
            val ahora = System.currentTimeMillis()
            container.settings.update { it.copy(badgesSeenUntilMillis = ahora + MARGEN_LOGROS) }
            container.reconciler.reconcile(LogicalDays.logicalDayOf(ahora, 0, ZoneId.systemDefault()), ahora)
            container.rewards.grantItems(setOf("head-flor", "neck-panuelo-brasa"), fijo)
            val actual = container.database.pointsLedgerDao().all().sumOf { it.delta }
            container.rewards.spend(saldo + 200 - actual, PointsReason.TASK_DONE, "task:ajuste", dia, fijo)
            container.rewards.spend(-100, PointsReason.BUY_FREEZER, "f1", dia, fijo)
            container.rewards.spend(-100, PointsReason.BUY_FREEZER, "f2", dia, fijo)
        }
    }

    @androidx.compose.runtime.Composable
    private fun contenido() {
        BitoTheme(sonido = sonido, tacto = tacto) {
            CompositionLocalProvider(LocalSondaJardin provides sonda) { BitoNavHost(container) }
        }
    }

    private fun montar() {
        compose.setContent { contenido() }
        // Las escenas piden un fotograma tras otro: con el reloj solo, la prueba no llegaría nunca a estar quieta.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
    }

    private fun abrirTienda() {
        montar()
        entrarEnLaTienda()
    }

    private fun entrarEnLaTienda() {
        hasta { hay("cabecera-saldo") }
        pulsar("cabecera-saldo")
        hasta { hay("saldo-tienda") }
        pulsar("saldo-tienda")
        esperarTienda()
        // La tienda brota con un muelle: hasta que asienta no es la pantalla de arriba y atrás no la cierra.
        fotogramas(ASENTAR_MS)
    }

    private fun esperarTienda() {
        hasta { hay("tienda") && !hay("tienda-cargando") && hay("tluz") }
    }

    private fun fotogramas(ms: Long = 50) {
        compose.mainClock.advanceTimeBy(ms)
        transcurrido.addAndGet(ms)
    }

    /** Espera a que se cumpla [cond] dejando correr fotogramas: Room y DataStore van por hilos reales. */
    private fun hasta(
        maximo: Int = ESPERA_MAXIMA,
        cond: () -> Boolean,
    ) {
        repeat(maximo) {
            // Lo que lanzan los ViewModel en el principal solo corre cuando el bucle se vacía; las condiciones que no miran el árbol no lo hacen.
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            fotogramas(16)
            Thread.sleep(1)
        }
        assertTrue("no llegó a tiempo; en pantalla: ${etiquetas()}", cond())
    }

    private fun etiquetas(): List<String> =
        compose.onAllNodes(SemanticsMatcher("con etiqueta") { SemanticsProperties.TestTag in it.config }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }

    private fun nodos(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(tag: String) = nodos(tag).isNotEmpty()

    private fun nodo(tag: String): SemanticsNode = nodos(tag).firstOrNull() ?: error("no hay «$tag»; en pantalla: ${etiquetas()}")

    private fun hayTexto(texto: String) = compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTextoQueContiene(trozo: String) =
        compose.onAllNodes(
            SemanticsMatcher(
                "texto con «$trozo»",
            ) { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(trozo) } == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun hayDescripcion(texto: String) =
        compose.onAllNodes(
            SemanticsMatcher(
                "descripción «$texto»",
            ) { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == texto } == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    /** Todo el texto de un nodo y de sus descendientes. */
    private fun textos(n: SemanticsNode): String =
        buildString {
            n.config.getOrNull(SemanticsProperties.Text)?.let { lista -> append(lista.joinToString("") { it.text }) }
            n.children.forEach { append(textos(it)) }
        }

    private fun texto(tag: String): String = textos(nodo(tag))

    private fun desc(tag: String): String =
        nodos(tag).firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()

    private fun seleccionada(tag: String): Boolean = nodos(tag).firstOrNull()?.config?.getOrNull(SemanticsProperties.Selected) == true

    private fun luzActual(): String? =
        compose.onAllNodes(
            SemanticsMatcher("astro de la luz") { n ->
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                    it.startsWith("Luz: ")
                } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.ContentDescription)?.first()

    private fun saldoDe(): Int =
        nodos("saldo").mapNotNull { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .firstNotNullOfOrNull { Regex("""(\d+) bito""").find(it)?.groupValues?.get(1)?.toInt() }
            ?: -1

    /** Un clic de verdad: sube por el árbol hasta el primer nodo con acción de clic. */
    private fun clic(inicio: SemanticsNode) {
        var n: SemanticsNode? = inicio
        while (n != null) {
            val accion = n.config.getOrNull(SemanticsActions.OnClick)?.action
            if (accion != null) {
                accion()
                fotogramas()
                return
            }
            n = n.parent
        }
        error("nada pulsable en ${textos(inicio)}")
    }

    private fun pulsar(tag: String) = clic(nodo(tag))

    private fun pulsarTexto(texto: String) =
        clic(
            compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
                ?: error("no hay el texto «$texto»; en pantalla: ${etiquetas()}"),
        )

    /** Trae la pieza a la tira de Habi: salta a su ranura con la raíz y espera a que esté compuesta. */
    private fun verItem(id: String) {
        val ranura =
            when (id.substringBefore('-')) {
                "head" -> "cabeza"
                "face" -> "cara"
                "neck" -> "cuello"
                "feet" -> "pies"
                "body" -> "colores"
                "eyes" -> "ojos"
                "costume" -> "disfraces"
                else -> error("ranura desconocida para $id")
            }
        hasta { hay("raiz-$ranura") || hay("item-$id") }
        if (!hay("item-$id")) pulsar("raiz-$ranura")
        hasta { hay("item-$id") }
    }

    private fun atrasDelSistema() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        fotogramas()
    }
}
