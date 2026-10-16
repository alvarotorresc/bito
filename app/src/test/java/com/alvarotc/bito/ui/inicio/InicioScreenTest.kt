package com.alvarotc.bito.ui.inicio

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.EventoGota
import com.alvarotc.bito.ui.premios.Momento
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class InicioScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val contexto = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun montar(
        reducido: Boolean = false,
        habitos: Int = 0,
        creado: String? = null,
    ) {
        banco.preparar(habitos)
        rule.setContent { ConSistema(reducido = reducido) { banco.Inicio(creado = creado) } }
        rule.waitForIdle()
    }

    private fun esperarAsentado() = rule.waitForIdle()

    private fun volverAHoy() {
        rule.onNodeWithTag("pildora-hoy").performClick()
        esperarAsentado()
    }

    @Test
    fun `starts on Today with the pill on Today`() {
        montar()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
    }

    @Test
    fun `the pill opens Camino and goes back to Today`() {
        montar()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.onNodeWithTag("inicio-hoy").assertDoesNotExist()
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
        volverAHoy()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
    }

    @Test
    fun `your path link calls what it is given and carries its tag`() {
        var pulsado = 0
        rule.setContent { ConSistema { EnlaceTuCamino({ pulsado++ }) } }
        rule.onNodeWithTag("tu-camino").performClick()
        assertEquals(1, pulsado)
    }

    @Test
    fun `back on Camino returns to Today`() {
        montar()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        esperarAsentado()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
    }

    @Test
    fun `with reduced motion the change is instant after 150 ms`() {
        montar(reducido = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(160)
        rule.onNodeWithTag("inicio-camino").assertExists()
    }

    private fun alturaJardin() = rule.onNodeWithTag("jardin-alto").getBoundsInRoot().height.value

    @Test
    fun `tapping the pill mid transition reverses without a jump`() {
        montar()
        rule.mainClock.autoAdvance = false
        val inicio = alturaJardin()
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(120)
        val enMedio = alturaJardin()
        assertTrue("va creciendo hacia Camino", enMedio > inicio + 10f)
        rule.onNodeWithTag("pildora-hoy").performClick()
        rule.mainClock.advanceTimeByFrame()
        val tras = alturaJardin()
        // Continúa desde donde estaba: un fotograma mueve unos pocos dp, no vuelve a un extremo.
        assertTrue("sin salto: $enMedio -> $tras", kotlin.math.abs(tras - enMedio) < 40f)
        assertTrue("no ha vuelto de golpe a Hoy", tras > inicio + 5f)
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
        assertEquals(inicio, alturaJardin(), 1f)
    }

    @Test
    fun `with reduced motion the list does not slide, it only fades`() {
        montar(reducido = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(40)
        val lista = rule.onNodeWithTag("inicio-hoy").getBoundsInRoot()
        assertEquals("la lista no se desplaza", alturaJardin(), lista.top.value, 1f)
    }

    @Test
    fun `with full motion the list slides down while it leaves`() {
        montar()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(100)
        val lista = rule.onNodeWithTag("inicio-hoy").getBoundsInRoot()
        assertTrue("la lista baja", lista.top.value > alturaJardin() + 20f)
    }

    @Test
    fun `Camino survives going to another route and coming back`() {
        banco.preparar()
        var pantalla by mutableStateOf("inicio")
        rule.setContent {
            ConSistema {
                val holder = rememberSaveableStateHolder()
                if (pantalla == "inicio") {
                    holder.SaveableStateProvider("inicio") { banco.Inicio() }
                } else {
                    holder.SaveableStateProvider("otra") { Box(Modifier.testTag("otra")) }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        pantalla = "otra"
        esperarAsentado()
        rule.onNodeWithTag("otra").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            pantalla = "inicio"
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        // Sin animar: en el primer fotograma ya es Camino y la lista no está.
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.onNodeWithTag("inicio-hoy").assertDoesNotExist()
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
    }

    @Test
    fun `gear goes to settings, balance opens your bitos, plus opens the create sheet`() {
        montar()
        rule.onNodeWithTag("cabecera-ajustes").performClick()
        assertTrue(banco.llamadas.contains("ajustes"))
        rule.onNodeWithTag("cabecera-saldo").performClick()
        esperarAsentado()
        rule.onNodeWithTag("hoja-saldo").assertExists()
    }

    @Test
    fun `plus opens the create sheet and a habit navigates once the sheet is gone`() {
        montar()
        rule.onNodeWithTag("boton-crear").performClick()
        esperarAsentado()
        rule.onNodeWithTag("create-choice-sheet").assertExists()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(contexto.getString(R.string.tasks2_crear_habito)).performClick()
        rule.mainClock.advanceTimeBy(40)
        assertTrue("no navega con la hoja aún cerrándose", banco.llamadas.none { it == "crearHabito" })
        rule.mainClock.advanceTimeBy(800)
        assertEquals(1, banco.llamadas.count { it == "crearHabito" })
    }

    @Test
    fun `the plus moves up while a notice shows`() {
        val avisos = AvisoEstado()
        banco.preparar()
        rule.setContent {
            ConSistema { CompositionLocalProvider(LocalAvisosInicio provides avisos) { banco.Inicio() } }
        }
        rule.waitForIdle()
        val antes = rule.onNodeWithTag("boton-crear").getBoundsInRoot().top
        val ambito = CoroutineScope(banco.dispatcher)
        ambito.launch { avisos.mostrar("Hecho") }
        rule.waitForIdle()
        val durante = rule.onNodeWithTag("boton-crear").getBoundsInRoot().top
        assertTrue("el + sube mientras hay aviso", durante < antes)
        banco.dispatcher.scheduler.advanceTimeBy(Duraciones.AVISO + 200L)
        rule.waitForIdle()
        assertEquals(antes.value, rule.onNodeWithTag("boton-crear").getBoundsInRoot().top.value, 0.5f)
    }

    private fun frase(): String {
        val spec = banco.hoy.uiState.value.spec
        return contexto.getString(HabiVoice.bubbleRes(spec.mood, spec.personality))
    }

    @Test
    fun `coming back from creating a habit shows Habi's line even after the signal is cleared`() {
        banco.preparar()
        banco.sembrar("make-bed", "Hacer la cama")
        var creado by mutableStateOf<String?>("make-bed")
        rule.setContent { ConSistema { banco.Inicio(creado = creado, alCreadoVisto = { creado = null }) } }
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
        creado = null
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(Duraciones.GLOBITO + 500L)
        rule.onNodeWithText(frase()).assertDoesNotExist()
    }

    @Test
    fun `a created id of a task that does not touch today says nothing`() {
        banco.preparar()
        banco.sembrar("make-bed", "Hacer la cama")
        rule.setContent { ConSistema { banco.Inicio(creado = "tarea-de-otro-dia") } }
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertDoesNotExist()
    }

    @Test
    fun `a created id that arrives while Today is loading still makes Habi talk`() {
        val pausado = StandardTestDispatcher(TestScope().testScheduler)
        banco.preparar(dispatcherVM = pausado)
        rule.setContent { ConSistema { banco.Inicio(creado = "make-bed") } }
        rule.waitForIdle()
        banco.sembrar("make-bed", "Hacer la cama")
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
    }

    @Test
    fun `the progress of Camino fades the list before half and the stats after`() {
        assertEquals(1f, progresoCamino(0f).alfaLista, 0f)
        assertEquals(0f, progresoCamino(0.5f).alfaLista, 0f)
        assertEquals(0f, progresoCamino(0.5f).alfaEstadisticas, 0f)
        assertEquals(1f, progresoCamino(1f).alfaEstadisticas, 0f)
        assertEquals(0f, progresoCamino(0.55f).alfaTitulo, 0f)
        assertEquals(1f, progresoCamino(1f).alfaTitulo, 0f)
        assertEquals(0f, progresoCamino(0.25f).alfaCabecera, 0f)
    }

    @Test
    fun `the garden edge goes from 40 percent to 18 percent and then to the Camino height`() {
        val alto = 1000f
        val escena = 300f
        val extra = 150f
        assertEquals(400f, bordeJardin(0f, 0f, alto, escena, extra, 0f), 0.01f)
        assertEquals(180f, bordeJardin(0f, 1f, alto, escena, extra, 0f), 0.01f)
        assertEquals(600f, bordeJardin(1f, 0f, alto, escena, extra, 0f), 0.01f)
        // Con poco alto, el escenario más 150 dp manda sobre el 60 %.
        assertEquals(450f, bordeJardin(1f, 0f, 600f, escena, extra, 0f), 0.01f)
        assertEquals(1f, escalaJardin(1f, 0f, alto, escena), 0.001f)
        assertEquals(0.88f * (400f / 300f), escalaJardin(0f, 1f, alto, escena), 0.001f)
    }

    // ---- Extremo a extremo: lo que ningún carril podía probar solo (Tarea 27) ----

    /** El Inicio de verdad en primer plano, con la marca del libro a cero para que lo que entre se celebre. */
    private fun montarVivo(habitos: Int = 0) {
        banco.preparar(habitos, marcaCero = true)
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
    }

    private fun libro(vararg filas: PointsLedgerEntity) = runBlocking { banco.db.pointsLedgerDao().insertAll(filas.toList()) }

    private fun fila(
        id: String,
        t: Long,
        motivo: PointsReason,
        ref: String,
        delta: Int,
    ) = pointsLedgerEntity(id = id, delta = delta, reason = motivo, refId = ref, logicalDay = banco.hoyLogico, createdAtMillis = t)

    private fun esperarTag(tag: String) =
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Mueve a la vez el reloj de Compose y el de la cola de celebraciones (su respiro de 360 ms es un `delay` del
     * planificador del banco) de 16 en 16 ms hasta que salga [tag]: `waitForIdle` correría la cola entera de golpe.
     */
    private fun avanzarHasta(
        tag: String,
        maximoMs: Long = 8_000,
    ) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (!hay(tag) && t < maximoMs) {
            banco.dispatcher.scheduler.advanceTimeBy(16)
            banco.dispatcher.scheduler.runCurrent()
            rule.mainClock.advanceTimeBy(16)
            t += 16
        }
        assertTrue("$tag no salió en $maximoMs ms; en la cola: ${banco.celebraciones.actual.value}", hay(tag))
    }

    private fun avanzar(ms: Long) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            banco.dispatcher.scheduler.advanceTimeBy(16)
            banco.dispatcher.scheduler.runCurrent()
            rule.mainClock.advanceTimeBy(16)
            t += 16
        }
    }

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `tapping the garden sky opens Camino and dragging over it does not`() {
        montar()
        rule.onNodeWithTag("garden-scene").performTouchInput { swipeLeft() }
        esperarAsentado()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
        rule.onNodeWithTag("garden-scene").performTouchInput { click(Offset(20f, 20f)) }
        esperarAsentado()
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
        rule.onNodeWithTag("inicio-camino").assertExists()
    }

    @Test
    fun `the balance opens the real sheet with five movements and the store button navigates after it closes`() {
        montar()
        libro(*(1..6).map { fila("m$it", it.toLong(), PointsReason.PERFECT_DAY, "week:${banco.hoyLogico - it}", 10) }.toTypedArray())
        rule.waitForIdle()
        rule.onNodeWithTag("cabecera-saldo").performClick()
        esperarAsentado()
        rule.onNodeWithTag("hoja-saldo").assertExists()
        rule.onAllNodesWithText(contexto.getString(R.string.base_mov_semana)).assertCountEquals(5)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("saldo-tienda").performClick()
        rule.mainClock.advanceTimeBy(40)
        assertTrue("no navega con la hoja aún cerrándose", banco.llamadas.none { it == "tienda" })
        rule.mainClock.advanceTimeBy(800)
        assertEquals(1, banco.llamadas.count { it == "tienda" })
    }

    @Test
    fun `a new badge opens its sheet as new and Continue lets the next vein through`() {
        montarVivo()
        runBlocking { banco.db.badgeDao().insert(BadgeEntity("first-habit", 500)) }
        avanzarHasta("hoja-logro")
        rule.onNodeWithTag("logro-estado", useUnmergedTree = true).assertDoesNotExist()
        libro(fila("d", 10, PointsReason.PERFECT_DAY, "day:${banco.hoyLogico}", 3))
        rule.mainClock.advanceTimeBy(3_000)
        assertTrue("la cadena espera a que se lea el logro", !hay("veta-DIA"))
        rule.onNodeWithText(contexto.getString(R.string.camino_seguir)).performClick()
        avanzarHasta("veta-DIA")
        assertTrue(!hay("hoja-logro"))
    }

    @Test
    fun `see it in the garden from a new badge closes the sheet and takes you to Camino highlighting the special`() {
        montarVivo()
        runBlocking { banco.db.badgeDao().insert(BadgeEntity(GardenSpecial.LANTERN.badgeId, 500)) }
        avanzarHasta("hoja-logro")
        rule.onNodeWithText(contexto.getString(R.string.camino_verlo)).performClick()
        avanzar(40)
        assertTrue("la hoja aún se está cerrando", hay("hoja-logro"))
        avanzar(Duraciones.CERRAR_HOJA * 2L + 800L)
        assertTrue("en la cola: ${banco.celebraciones.actual.value}", !hay("hoja-logro"))
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
        assertEquals(listOf(GardenSpecial.LANTERN.key), banco.sonda.destacados)
    }

    @Test
    fun `see it in the garden from Camino closes the logro sheet in 180 ms and then highlights the special`() {
        montar()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        runBlocking { banco.db.badgeDao().insert(BadgeEntity(GardenSpecial.LANTERN.badgeId, 500)) }
        rule.waitForIdle()
        rule.onNodeWithTag("logro-${GardenSpecial.LANTERN.badgeId}").performScrollTo().performClick()
        esperarAsentado()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(contexto.getString(R.string.camino_verlo)).performClick()
        rule.mainClock.advanceTimeBy(Duraciones.CERRAR_HOJA - 80L)
        assertTrue("la hoja aún sale", banco.sonda.destacados.isEmpty())
        rule.mainClock.advanceTimeBy(400)
        assertEquals(listOf(GardenSpecial.LANTERN.key), banco.sonda.destacados)
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
    }

    @Test
    fun `marking a habit launches the drop towards the plot the garden registers`() {
        montarVivo(habitos = 1)
        val eventos = mutableListOf<EventoGota>()
        CoroutineScope(banco.dispatcher).launch { banco.registro.gotas.collect { eventos += it } }
        assertTrue("el jardín registra su parcela", ClavesAncla.PARCELA in banco.registro.posiciones)
        rule.onNodeWithTag("circulo-h0").performClick()
        rule.waitForIdle()
        assertEquals(listOf<EventoGota>(EventoGota.Sale("fila:h0")), eventos)
        assertTrue("la fila registra su círculo", "fila:h0" in banco.registro.posiciones)
        rule.mainClock.advanceTimeBy(Duraciones.GOTA_RETRASO + Duraciones.GOTA + 200L)
        assertEquals("la capa única recoge la llegada", 1, banco.sonido.snds().count { it == Snd.LLEGADA })
    }

    @Test
    fun `finishing a task drops grains from its circle on the same layer`() {
        banco.preparar(marcaCero = true)
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoyLogico)) }
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
        val eventos = mutableListOf<EventoGota>()
        CoroutineScope(banco.dispatcher).launch { banco.registro.gotas.collect { eventos += it } }
        rule.onNodeWithTag("circulo-tarea-t1").performClick()
        rule.waitForIdle()
        assertEquals(listOf<EventoGota>(EventoGota.Granos("tarea:t1")), eventos)
        assertNotNull("el círculo de la tarea está anclado", banco.registro.posicionO("tarea:t1"))
    }

    @Test
    fun `a habit marked on Today pays its coins to the balance and then the next moment of the queue comes`() {
        banco.preparar(habitos = 1, marcaCero = true)
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
        // Crear el hábito ya abrió el logro first-habit: se lee y se sigue antes de marcar.
        avanzarHasta("hoja-logro")
        rule.onNodeWithText(contexto.getString(R.string.camino_seguir)).performClick()
        avanzar(Duraciones.CERRAR_HOJA * 2L + 800L)
        var tics = 0
        CoroutineScope(banco.dispatcher).launch { banco.registro.llegadasDeMoneda.collect { tics++ } }
        val orden = mutableListOf<Momento>()
        CoroutineScope(banco.dispatcher).launch { banco.celebraciones.actual.collect { m -> m?.let { orden += it } } }
        rule.onNodeWithTag("circulo-h0").performClick()
        avanzarHasta("veta-DIA")
        assertEquals("una moneda, un tic en el saldo", 1, tics)
        assertTrue("la moneda sonó", Snd.MONEDA in banco.sonido.snds() || Snd.CAMPANILLA in banco.sonido.snds())
        // G14: las monedas primero y la veta después; el logro que se escribe aparte sale detrás, nunca delante.
        assertEquals(listOf(1, 2), orden.map { it.orden }.take(2))
    }

    @Test
    fun `coming back from creating a habit makes Habi jump and speak while the list scrolls to the new row`() {
        banco.preparar(marcaCero = true)
        banco.sembrar("make-bed", "Hacer la cama")
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(creado = "make-bed", enPrimerPlano = true) } }
        rule.waitForIdle()
        assertEquals("Habi salta una vez", 1, banco.sonda.saltosHabi())
        rule.onNodeWithText(frase()).assertExists()
        rule.onNodeWithTag("fila-make-bed").assertExists()
        assertTrue("la frase sale sobre Habi", ClavesAncla.HABI in banco.registro.posiciones)
    }

    @Test
    fun `the week vein speaks with Habi's real personality and the user's name`() {
        banco.preparar(marcaCero = true)
        runBlocking { banco.ajustes.update { it.copy(personality = Personality.SARGENTO, userName = "Marta") } }
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
        libro(fila("w", 10, PointsReason.PERFECT_DAY, "week:${banco.hoyLogico}", 10))
        esperarTag("veta-SEMANA")
        rule.onNodeWithText(contexto.getString(HabiVoice.perfectDayRes(Personality.SARGENTO), "Marta")).assertExists()
    }

    @Test
    fun `nothing is celebrated while Camino is open and it plays when back on Today`() {
        montarVivo()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        libro(fila("w", 10, PointsReason.PERFECT_DAY, "week:${banco.hoyLogico}", 10))
        rule.mainClock.advanceTimeBy(3_000)
        assertTrue("en Camino la veta espera", !hay("veta-SEMANA"))
        volverAHoy()
        esperarTag("veta-SEMANA")
    }

    @Test
    fun `leaving Today for Camino ends the vein that was playing`() {
        montarVivo()
        libro(fila("w", 10, PointsReason.PERFECT_DAY, "week:${banco.hoyLogico}", 10))
        esperarTag("veta-SEMANA")
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        assertTrue("lo que sonaba termina al salir de Hoy", !hay("veta-SEMANA"))
    }

    @Test
    fun `a cold start with a grown garden celebrates nothing from it`() {
        // Hoy arranca en carga con el jardín inicial; si eso contara como jardín anterior, el real celebraría todo otra vez.
        val pausado = StandardTestDispatcher(TestScope().testScheduler)
        banco.preparar(dispatcherVM = pausado, marcaCero = true)
        val inicial = GardenState.INITIAL
        banco.hoy.forzarJardin(
            inicial.copy(
                plots =
                    listOf(
                        GardenPlot(inicial.plots[0].species, GrowthStage.TREE),
                        GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SEED),
                    ),
                specials = setOf(GardenSpecial.LANTERN),
            ),
        )
        val vistos = mutableListOf<Momento>()
        val escucha = Job()
        CoroutineScope(banco.dispatcher + escucha).launch { banco.celebraciones.actual.collect { it?.let(vistos::add) } }
        rule.setContent { ConSistema { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        escucha.cancel()
        assertTrue("el arranque no celebra el jardín: $vistos", vistos.none { it is Momento.Especial || it is Momento.Etapa })
    }

    @Test
    fun `coming back from creating a habit with a stale Today still makes Habi talk`() {
        // Así se ve volver tras más de 5 s en el formulario: Hoy conserva un estado cargado sin el hábito nuevo.
        val pausado = StandardTestDispatcher(TestScope().testScheduler)
        banco.preparar(dispatcherVM = pausado)
        banco.sembrar("make-bed", "Hacer la cama")
        var creado by mutableStateOf<String?>(null)
        var vistos = 0
        rule.setContent { ConSistema { banco.Inicio(creado = creado, alCreadoVisto = { vistos++ }) } }
        rule.waitForIdle()
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        banco.sembrar("read", "Leer", 1)
        creado = "read"
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
    }

    @Test
    fun `coming back from creating a task written after the signal still makes Habi talk`() {
        banco.preparar()
        banco.sembrar("make-bed", "Hacer la cama")
        var creado by mutableStateOf<String?>(null)
        rule.setContent { ConSistema { banco.Inicio(creado = creado) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        creado = "t1" // el id llega antes de que la tarea se escriba
        rule.mainClock.advanceTimeBy(200)
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoyLogico)) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText(frase()).assertExists()
    }

    @Test
    fun `the garden of Today is cut with a straight bottom edge`() {
        assertEquals(RectangleShape, FormaJardinHoy)
    }

    @Test
    fun `closing the day hides the pill and the plus and back returns to Today`() {
        banco.preparar()
        var vistos = 0
        rule.setContent { ConSistema { banco.Inicio(cierreAlEntrar = true, alCierreVisto = { vistos++ }) } }
        rule.waitUntil(5_000) { hay("cierre") }

        assertEquals(1, vistos)
        rule.onNodeWithTag("pildora").assertDoesNotExist()
        rule.onNodeWithTag("boton-crear").assertDoesNotExist()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { !hay("cierre") }
        rule.onNodeWithTag("pildora").assertExists()
        rule.onNodeWithTag("boton-crear").assertExists()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
    }

    @Test
    fun `the close day button opens the close inside Inicio instead of leaving`() {
        montar(habitos = 1)
        rule.onNodeWithTag("cerrar-dia").performScrollTo().performClick()
        rule.waitUntil(5_000) { hay("cierre") }

        assertTrue("no sale a otra ruta: ${banco.llamadas}", "repaso" !in banco.llamadas)
        rule.onNodeWithTag("pildora").assertDoesNotExist()
    }

    @Test
    fun `the close mounts its own content over Inicio`() {
        banco.preparar(habitos = 1)
        rule.setContent { ConSistema { banco.Inicio(cierreAlEntrar = true) } }
        rule.waitUntil(5_000) { hay("cierre-buenas-noches") }
        rule.onNodeWithTag("inicio").assertExists()
    }

    @Test
    fun `the big thing flag reaches the list and is marked seen`() {
        banco.preparar()
        var vistos = 0
        rule.setContent { ConSistema { banco.Inicio(gordo = true, alGordoVisto = { vistos++ }) } }
        rule.waitUntil(5_000) { vistos == 1 }
        rule.onNodeWithTag("inicio-hoy").assertExists()
    }

    @Test
    fun `without the flags nothing is marked seen`() {
        banco.preparar()
        var vistos = 0
        rule.setContent { ConSistema { banco.Inicio(alCierreVisto = { vistos++ }, alGordoVisto = { vistos++ }) } }
        rule.waitForIdle()
        assertEquals(0, vistos)
        rule.onNodeWithTag("cierre").assertDoesNotExist()
        rule.onNodeWithTag("pildora").assertExists()
    }

    @Test
    fun `with the close open the list of Today leaves the accessibility tree`() {
        banco.preparar()
        rule.setContent { ConSistema { banco.Inicio(cierreAlEntrar = true) } }
        rule.waitUntil(5_000) { hay("cierre") }
        rule.onNodeWithTag("tu-camino").assertDoesNotExist()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { !hay("cierre") }
        rule.onNodeWithTag("tu-camino").assertExists()
    }

    @Test
    fun `reopening the close starts clean and the dismissed row comes back`() {
        banco.preparar(habitos = 1)
        rule.setContent { ConSistema { banco.Inicio(cierreAlEntrar = true) } }
        rule.waitUntil(5_000) { hay("cierre-no-h0") }
        rule.onNodeWithTag("cierre-no-h0").performClick()
        rule.waitUntil(5_000) { hay("cierre-atenuada-h0") }

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { !hay("cierre") }
        rule.onNodeWithTag("cerrar-dia").performScrollTo().performClick()
        rule.waitUntil(5_000) { hay("cierre") }

        rule.waitUntil(5_000) { hay("cierre-no-h0") }
        assertFalse("lo decidido no pasa de una apertura a otra", hay("cierre-atenuada-h0"))
    }
}
