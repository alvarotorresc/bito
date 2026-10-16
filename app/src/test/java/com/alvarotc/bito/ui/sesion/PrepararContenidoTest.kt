package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.Conflicto
import com.alvarotc.bito.sesion.FondoGrabado
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class PrepararContenidoTest {
    @get:Rule
    val compose = createComposeRule()

    private val motor = MotorSesionFalso()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val fondo = FondoGrabado()
    private val permiso = BancoPermiso()
    private val avisos = mutableListOf<String>()
    private val hojas = RegistroHoja()

    private fun montar(
        plan: PlanSesion = planSesion(objetivo = Objetivo.Tarea("t0")),
        personalidad: Personality = Personality.NEUTRA,
    ) {
        motor.borrador.value = plan
        compose.setContent {
            ConSistema(sonido, tacto) {
                permiso.Con {
                    CompositionLocalProvider(LocalRegistroHoja provides hojas) {
                        val datos = datosMock(fondo, permiso.noMolestar, personalidad, avisos)
                        val actual by motor.borrador.collectAsState()
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            actual?.let { PrepararContenido(it, motor, datos, permiso.noMolestar) }
                        }
                    }
                }
            }
        }
    }

    private fun cambiaPlan(plan: PlanSesion) = compose.runOnIdle { motor.borrador.value = plan }

    private fun plan() = motor.borrador.value!!

    @Test
    fun `Preparar shows the exact texts of the mock`() {
        montar()
        listOf(
            "¿En qué te concentras?",
            "Informe trimestral",
            "Primer paso: abrir el documento",
            "Cambiar",
            "Tiempo",
            "15 min",
            "25 min",
            "45 min",
            "Otro",
            "Para entrar",
            "Respirar 1 min",
            "Directo",
            "Bloques",
            "4 de 25 min: 5 de descanso entre ellos y 15 al cerrar cada 4",
            "Sonido de fondo",
            "Lluvia",
            "No molestar",
            "Silencia el móvil mientras dura",
            "Empezar",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        listOf("p-nom", "p-paso", "p-min", "p-entrar", "p-blq", "p-fondo", "p-dnd", "p-empezar").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.onNodeWithText("25 min").assertIsSelected()
        compose.onNodeWithText("Respirar 1 min").assertIsSelected()
    }

    @Test
    fun `a task without first step says so`() {
        montar(planSesion(objetivo = Objetivo.Tarea("t1")))
        compose.onNodeWithText("Pagar el alquiler").assertIsDisplayed()
        compose.onNodeWithText("Sin primer paso apuntado").assertIsDisplayed()
    }

    @Test
    fun `free focus shows Solo foco and no subtitle`() {
        montar(planSesion(objetivo = Objetivo.Libre))
        compose.onNodeWithTag("p-nom").assertIsDisplayed()
        compose.onNodeWithText("Solo foco").assertIsDisplayed()
        compose.onNodeWithTag("p-paso").assertDoesNotExist()
    }

    @Test
    fun `the fourth tab says n min when pomo focus is not 15 25 or 45`() {
        montar(planSesion(objetivo = Objetivo.Tarea("t0"), minutosFoco = 30))
        compose.onNodeWithText("Otro").assertDoesNotExist()
        compose.onNodeWithText("30 min").assertIsSelected()
        compose.onNodeWithText("25 min").assertIsNotSelected()
    }

    @Test
    fun `a preset tab sets the minutes in the plan`() {
        montar()
        compose.onNodeWithText("45 min").performClick()
        assertEquals(45, plan().minutosFoco)
        compose.onNodeWithText("15 min").performClick()
        assertEquals(15, plan().minutosFoco)
    }

    @Test
    fun `Otro opens the minutes sheet and Listo puts the tab to n min`() {
        montar()
        compose.onNodeWithText("Otro").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("¿Cuántos minutos?").assertIsDisplayed()
        compose.onNodeWithText("Listo").performClick()
        compose.waitForIdle()
        assertEquals(30, plan().minutosFoco)
        compose.onNodeWithText("30 min").assertIsSelected()
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
    }

    @Test
    fun `tapping the selected fourth tab opens the minutes sheet again`() {
        montar(planSesion(objetivo = Objetivo.Tarea("t0"), minutosFoco = 30))
        compose.onNodeWithText("30 min").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("¿Cuántos minutos?").assertIsDisplayed()
    }

    @Test
    fun `entering directly changes the plan`() {
        montar()
        compose.onNodeWithText("Directo").performClick()
        assertEquals(Entrada.DIRECTO, plan().entrada)
    }

    @Test
    fun `blocks stepper stops at 1 and 8 with its labels`() {
        montar(planSesion(objetivo = Objetivo.Tarea("t0"), bloques = 1))
        compose.onNodeWithContentDescription("Un bloque menos").performClick()
        assertEquals(1, plan().bloques)
        compose.onNodeWithContentDescription("Un bloque más").performClick()
        assertEquals(2, plan().bloques)
        cambiaPlan(plan().copy(bloques = 8))
        compose.onNodeWithContentDescription("Un bloque más").performClick()
        assertEquals(8, plan().bloques)
        compose.onNodeWithContentDescription("Un bloque menos").performClick()
        assertEquals(7, plan().bloques)
    }

    @Test
    fun `the blocks subtitle follows the count`() {
        montar(planSesion(objetivo = Objetivo.Tarea("t0"), bloques = 1))
        compose.onNodeWithText("Un bloque, sin descanso").assertIsDisplayed()
        cambiaPlan(plan().copy(bloques = 3))
        compose.onNodeWithText("3 de 25 min, 5 de descanso entre ellos").assertIsDisplayed()
        cambiaPlan(plan().copy(bloques = 4))
        compose.onNodeWithText("4 de 25 min: 5 de descanso entre ellos y 15 al cerrar cada 4").assertIsDisplayed()
    }

    @Test
    fun `the background row opens the sheet and a choice shows the sample and goes to the plan`() {
        montar()
        compose.onNodeWithTag("p-fondo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Murmullo de gente, lejos").assertIsDisplayed()
        compose.onNodeWithText("Cafetería").performClick()
        compose.waitForIdle()
        assertEquals(SessionBackground.CAFE, plan().fondo)
        assertEquals(listOf("muestra:CAFE"), fondo.llamadas)
    }

    @Test
    fun `the task row opens the task sheet and choosing a task changes the plan`() {
        montar()
        compose.onNodeWithText("Cambiar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pagar el alquiler").performClick()
        compose.waitForIdle()
        assertEquals(Objetivo.Tarea("t1"), plan().objetivo)
        assertTrue(Snd.TIC in sonido.snds())
        assertTrue(Haptica.SEG in tacto.hapticas)
        compose.onNodeWithText("Sin primer paso apuntado").assertIsDisplayed()
    }

    @Test
    fun `the dnd switch does not move without permission and opens the sheet`() {
        montar()
        compose.onNodeWithTag("p-dnd").performClick()
        compose.waitForIdle()
        assertFalse(plan().noMolestar)
        compose.onNodeWithText("No molestar durante la sesión").assertIsDisplayed()
        assertTrue(Snd.INTERRUPTOR_ON !in sonido.snds())
        assertTrue(Snd.INTERRUPTOR_OFF !in sonido.snds())
    }

    @Test
    fun `granted permission turns it on with its notice`() {
        permiso.concederAlLanzar = true
        montar()
        compose.onNodeWithTag("p-dnd").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        assertTrue(plan().noMolestar)
        assertEquals(1, permiso.lanzados.size)
        assertTrue(Snd.INTERRUPTOR_ON in sonido.snds())
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
        assertEquals(listOf("Listo: el móvil se silencia al empezar."), avisos)
    }

    @Test
    fun `with permission the switch just turns on and off`() {
        permiso.conceder(true)
        montar()
        compose.onNodeWithTag("p-dnd").performClick()
        assertTrue(plan().noMolestar)
        assertTrue(Snd.INTERRUPTOR_ON in sonido.snds())
        compose.onNodeWithTag("p-dnd").performClick()
        assertFalse(plan().noMolestar)
        assertTrue(permiso.lanzados.isEmpty())
    }

    @Test
    fun `not now leaves the subtitle`() {
        montar()
        compose.onNodeWithTag("p-dnd").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ahora no").performClick()
        compose.waitForIdle()
        assertFalse(plan().noMolestar)
        compose.onNodeWithText("Sin permiso: la sesión va igual").assertIsDisplayed()
        compose.onNodeWithText("Silencia el móvil mientras dura").assertDoesNotExist()
    }

    @Test
    fun `denied permission shows the denied sheet`() {
        montar()
        compose.onNodeWithTag("p-dnd").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        assertFalse(plan().noMolestar)
        compose.onNodeWithText("No molestar sigue apagado").assertIsDisplayed()
        compose.onNodeWithText("Es opcional. Puedes darlo cuando quieras desde Ajustes.").assertIsDisplayed()
    }

    @Test
    fun `Empezar asks the motor and plays nothing itself`() {
        montar()
        compose.onNodeWithTag("p-empezar").performClick()
        compose.waitForIdle()
        assertEquals(listOf("empezar"), motor.llamadas)
        assertTrue(Snd.BROTE !in sonido.snds())
        assertTrue(Haptica.CONFIRM !in tacto.hapticas)
    }

    @Test
    fun `a conflict opens the busy sheet and keeping the other one goes back to it`() {
        motor.conflicto = Conflicto.OtraEnMarcha(Objetivo.Tarea("t1"), 12 * 60_000L + 20_000L, 25)
        montar()
        compose.onNodeWithTag("p-empezar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ya tienes algo en marcha").assertIsDisplayed()
        compose.onNodeWithText("Pagar el alquiler: quedan 13 min de 25.").assertIsDisplayed()
        compose.onNodeWithText("Seguir con Pagar el alquiler").performClick()
        compose.waitForIdle()
        assertEquals(listOf("empezar", "seguirConLaViva"), motor.llamadas)
    }

    @Test
    fun `dropping the other one starts this one and tells it stays as an attempt`() {
        motor.conflicto = Conflicto.OtraEnMarcha(Objetivo.Tarea("t1"), 12 * 60_000L, 25)
        motor.avisoAlDejarla = AvisoSesion.ComoIntento("Pagar el alquiler")
        montar()
        compose.onNodeWithTag("p-empezar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dejarla y empezar esta").performClick()
        compose.waitForIdle()
        assertEquals(listOf("empezar", "dejarlaYEmpezar"), motor.llamadas)
        assertEquals(listOf("Pagar el alquiler: queda como intento."), avisos)
    }

    @Test
    fun `an open sheet registers so that Back closes it first`() {
        montar()
        assertFalse(hojas.abierta)
        compose.onNodeWithText("Cambiar").performClick()
        compose.waitForIdle()
        assertTrue(hojas.abierta)
        compose.onNodeWithText("Pagar el alquiler").assertIsDisplayed()
        compose.runOnIdle { assertTrue(hojas.cerrar()) }
        compose.waitForIdle()
        assertFalse(hojas.abierta)
        compose.onNodeWithText("Pagar el alquiler").assertDoesNotExist()
        assertEquals(Objetivo.Tarea("t0"), plan().objetivo)
    }

    @Test
    fun `every tappable is at least 44dp`() {
        montar()
        compose.waitForIdle()
        val tocables = compose.onAllNodes(hasClickAction() or hasContentDescription("Un bloque menos")).fetchSemanticsNodes()
        assertTrue("hay mandos", tocables.size >= 9)
        val px = compose.density.run { 44.dp.toPx() }
        tocables.forEach {
            val b = it.touchBoundsInRoot
            assertTrue("${it.config} ${b.width} x ${b.height}", b.width >= px - 0.5f && b.height >= px - 0.5f)
        }
        assertTrue(compose.onNodeWithText("Cambiar").getBoundsInRoot().height >= 44.dp)
    }

    @Test
    fun `the block emits its eight pieces as direct children of the earth`() {
        val plan = planSesion(objetivo = Objetivo.Tarea("t0"))
        val sonda = SondaBloques()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(sonido, tacto) {
                permiso.Con {
                    val datos = datosMock(fondo, permiso.noMolestar)
                    Box(Modifier.fillMaxSize()) {
                        SesionContenido(EstadoSesionUi(BloqueSesion.PREPARAR), sonda = sonda) {
                            PrepararContenido(plan, motor, datos, permiso.noMolestar)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals((0..7).toSet(), sonda.piezas.keys)
    }
}
