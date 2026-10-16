package com.alvarotc.bito.ui.sesion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.sesion.FondoGrabado
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class HojasSesionTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val fondo = FondoGrabado()

    private var abierta by mutableStateOf(true)
    private var cerrada = 0

    private fun alCerrar() {
        abierta = false
        cerrada++
    }

    @Test
    fun `the task sheet lists today's tasks and Solo foco at the end`() {
        val elegidas = mutableListOf<Objetivo>()
        compose.setContent {
            ConSistema(sonido, tacto) {
                HojaTarea(abierta, TAREAS_MOCK, Objetivo.Tarea("t0"), { elegidas += it }, ::alCerrar)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("¿En qué te concentras?").assertIsDisplayed()
        compose.onNodeWithText("abrir el documento", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sin primer paso").assertIsDisplayed()
        compose.onNodeWithText("Informe trimestral", substring = true).assertIsSelected()
        val alquiler = compose.onNodeWithText("Pagar el alquiler", substring = true).getBoundsInRoot().top
        val solo = compose.onNodeWithText("Solo foco").getBoundsInRoot().top
        assertTrue("Solo foco va al final", solo > alquiler)

        compose.onNodeWithText("Pagar el alquiler", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf<Objetivo>(Objetivo.Tarea("t1")), elegidas)
        assertTrue(Snd.TIC in sonido.snds())
        assertTrue(Haptica.SEG in tacto.hapticas)
        assertEquals(1, cerrada)
    }

    @Test
    fun `free focus can be chosen from the task sheet`() {
        val elegidas = mutableListOf<Objetivo>()
        compose.setContent {
            ConSistema(sonido, tacto) {
                HojaTarea(abierta, TAREAS_MOCK, Objetivo.Tarea("t0"), { elegidas += it }, ::alCerrar)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Solo foco").performClick()
        compose.waitForIdle()
        assertEquals(listOf<Objetivo>(Objetivo.Libre), elegidas)
    }

    @Test
    fun `the minutes sheet starts at 30 and walks from 5 to 180 in fives`() {
        compose.setContent { ConSistema(sonido, tacto) { HojaMinutos(abierta, {}, ::alCerrar) } }
        compose.waitForIdle()
        compose.onNodeWithText("¿Cuántos minutos?").assertIsDisplayed()
        compose.onNodeWithText("De 5 en 5, hasta 3 horas.").assertIsDisplayed()
        val rango = { v: Float -> ProgressBarRangeInfo(v, 5f..180f) }
        compose.onNodeWithContentDescription("¿Cuántos minutos?").assertRangeInfoEquals(rango(30f))
        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithContentDescription("¿Cuántos minutos?").assertRangeInfoEquals(rango(35f))
        repeat(7) { compose.onNodeWithContentDescription("Menos").performClick() }
        compose.onNodeWithContentDescription("¿Cuántos minutos?").assertRangeInfoEquals(rango(5f))
        compose.onNodeWithContentDescription("¿Cuántos minutos?")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(180f) }
        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithContentDescription("¿Cuántos minutos?").assertRangeInfoEquals(rango(180f))
    }

    @Test
    fun `Listo saves the minutes with a confirm and Cancelar saves nothing`() {
        val guardados = mutableListOf<Int>()
        compose.setContent { ConSistema(sonido, tacto) { HojaMinutos(abierta, { guardados += it }, ::alCerrar) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.waitForIdle()
        assertEquals(listOf(35), guardados)
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
    }

    @Test
    fun `Cancelar closes without saving and sits above the principal button`() {
        val guardados = mutableListOf<Int>()
        compose.setContent { ConSistema(sonido, tacto) { HojaMinutos(abierta, { guardados += it }, ::alCerrar) } }
        compose.waitForIdle()
        val cancelar = compose.onNodeWithText("Cancelar").getBoundsInRoot()
        val listo = compose.onNodeWithText("Listo").getBoundsInRoot()
        assertTrue(cancelar.bottom <= listo.top)
        assertTrue(listo.height >= 48.dp)
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertTrue(guardados.isEmpty())
        assertEquals(1, cerrada)
    }

    @Test
    fun `the more variant starts at 10 and says Sumar`() {
        val guardados = mutableListOf<Int>()
        compose.setContent { ConSistema(sonido, tacto) { HojaMinutos(abierta, { guardados += it }, ::alCerrar, mas = true) } }
        compose.waitForIdle()
        compose.onNodeWithText("¿Cuántos minutos más?").assertIsDisplayed()
        compose.onNodeWithText("Se suman a lo que queda.").assertIsDisplayed()
        compose.onNodeWithText("Listo").assertDoesNotExist()
        compose.onNodeWithText("Sumar").performClick()
        compose.waitForIdle()
        assertEquals(listOf(10), guardados)
    }

    @Test
    fun `the big stepper has 48 dp buttons`() {
        compose.setContent { ConSistema(sonido, tacto) { HojaMinutos(abierta, {}, ::alCerrar) } }
        compose.waitForIdle()
        val mas = compose.onNodeWithContentDescription("Más").getBoundsInRoot()
        val menos = compose.onNodeWithContentDescription("Menos").getBoundsInRoot()
        assertEquals(48.dp, mas.width)
        assertEquals(48.dp, mas.height)
        assertEquals(48.dp, menos.width)
        // 48 sp / 900 de cifra entre los dos, a 18 dp de cada uno.
        assertTrue(mas.left - menos.right >= 18.dp * 2)
    }

    @Test
    fun `the background sheet lists the four options starting with none and asks the sample`() {
        var elegido by mutableStateOf(SessionBackground.RAIN)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(sonido, tacto) { HojaFondoSesion(abierta, elegido, { elegido = it }, fondo, ::alCerrar) }
        }
        compose.mainClock.advanceTimeBy(600)
        listOf(
            "Sonido de fondo",
            "Suena mientras dura el foco y para en los descansos.",
            "Silencio",
            "Lluvia fina sobre el jardín",
            "Un rumor grave y continuo",
            "Murmullo de gente, lejos",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        val orden = listOf("Ninguno", "Lluvia", "Ruido marrón", "Cafetería").map { compose.onNodeWithText(it).getBoundsInRoot().top }
        assertEquals(orden.sorted(), orden)
        compose.onNodeWithText("Hecho").assertDoesNotExist()

        compose.onNodeWithText("Cafetería").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(SessionBackground.CAFE, elegido)
        assertEquals(listOf("muestra:CAFE"), fondo.llamadas)
        assertTrue(Snd.TIC in sonido.snds())
        assertTrue(Haptica.SEG in tacto.hapticas)
        // La muestra la da el fondo de la sesión, no una vista previa de la hoja.
        assertTrue(sonido.bucles.isEmpty())
    }

    @Test
    fun `the busy sheet names the other session and offers its two ways`() {
        var seguir = 0
        var dejar = 0
        compose.setContent {
            ConSistema(sonido, tacto) {
                HojaOcupado(abierta, "Informe trimestral", 13, 25, { seguir++ }, { dejar++ }, ::alCerrar)
            }
        }
        compose.waitForIdle()
        listOf(
            "Ya tienes algo en marcha",
            "Informe trimestral: quedan 13 min de 25.",
            "Seguir con Informe trimestral",
            "Vuelves a esa sesión tal como va",
            "Dejarla y empezar esta",
            "Lo que llevas cuenta como intento",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Dejarla y empezar esta").performClick()
        compose.waitForIdle()
        assertEquals(0, seguir)
        assertEquals(1, dejar)
    }

    @Test
    fun `every option of the sheets is a tappable of at least 44 dp`() {
        compose.setContent {
            ConSistema(sonido, tacto) {
                HojaTarea(abierta, TAREAS_MOCK, Objetivo.Tarea("t0"), {}, ::alCerrar)
            }
        }
        compose.waitForIdle()
        val px = compose.density.run { 44.dp.toPx() }
        val nodos = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodos.size >= 3)
        nodos.forEach { assertTrue("${it.config}", it.touchBoundsInRoot.height >= px - 0.5f) }
    }
}
