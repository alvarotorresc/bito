package com.alvarotc.bito.ui.inicio

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AireMudo
import com.alvarotc.bito.ui.sesion.MotorSesionFalso
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
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

private const val MINUTO = 60_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class InicioSesionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val motor = MotorSesionFalso()
    private val directo = planSesion(entrada = Entrada.DIRECTO)

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() {
        EntradaSesion.consumir()
        banco.cerrar()
    }

    private fun montar(reducido: Boolean = false) {
        banco.preparar()
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto, reducido) {
                banco.Inicio(sesion = SesionInicio(motor, AireMudo, banco.ajustes), enPrimerPlano = true)
            }
        }
        rule.waitForIdle()
    }

    private fun existe(tag: String) = rule.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun pedir(destino: Destino?) {
        EntradaSesion.pedir(destino)
        rule.waitForIdle()
        rule.waitUntil(5_000) { existe("ses-tierra") }
    }

    private fun asentar() = rule.mainClock.advanceTimeBy(2_000)

    private fun atras() {
        rule.onNodeWithTag("ses-atras").performClick()
        asentar()
        rule.waitForIdle()
    }

    @Test
    fun `the recovery runs on mount`() {
        montar()

        assertTrue("recuperar", "recuperar" in motor.llamadas)
    }

    @Test
    fun `pill and plus hide in session mode`() {
        montar()
        assertTrue(existe("pildora-hoy"))
        assertTrue(existe("boton-crear"))

        pedir(Destino.Tarea("t1"))

        assertFalse(existe("pildora-hoy"))
        assertFalse(existe("boton-crear"))
        assertTrue(existe("ses-atras"))
        assertEquals(listOf("recuperar", "preparar:Tarea(id=t1)").takeLast(2), motor.llamadas.takeLast(2))
    }

    @Test
    fun `the focus request opens the live session`() {
        motor.viva(directo)
        montar()

        pedir(null)

        assertTrue(existe("ses-b-foco"))
        assertFalse("sin preparar: ya hay una viva", motor.llamadas.any { it.startsWith("preparar") })
    }

    @Test
    fun `back from prepare returns to today with the camera sound`() {
        montar()
        pedir(Destino.Tarea("t1"))
        banco.sonido.sonados.clear()
        banco.tacto.hapticas.clear()

        atras()

        assertFalse(existe("ses-tierra"))
        assertTrue(existe("pildora-hoy"))
        assertTrue(Snd.ASCENSO in banco.sonido.snds())
        assertTrue(Haptica.LOW in banco.tacto.hapticas)
    }

    @Test
    fun `back from breathing to enter goes back to prepare`() {
        motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0)
        montar()
        pedir(null)
        assertTrue(existe("ses-b-entrar"))

        atras()

        assertTrue(motor.llamadas.contains("volverAPreparar"))
        assertTrue(existe("ses-tierra"))
        assertTrue(existe("ses-b-prep"))
    }

    @Test
    fun `back in focus keeps the session and tells it is still running`() {
        motor.viva(directo)
        montar()
        pedir(null)

        atras()

        assertFalse(existe("ses-tierra"))
        rule.onNodeWithText("Sigue en marcha: 25:00 en la notificación.").assertExists()
        assertTrue(motor.registro.value != null)
        assertEquals(emptyList<String>(), motor.llamadas.filter { it != "recuperar" })
    }

    @Test
    fun `back in a break keeps the session and tells the time left in the break`() {
        motor.viva(directo, 25 * MINUTO + 1_000)
        montar()
        pedir(null)
        assertTrue(existe("ses-b-descanso"))

        atras()

        assertFalse(existe("ses-tierra"))
        rule.onNodeWithText("Sigue en marcha: 4:59 en la notificación.").assertExists()
        assertTrue(motor.registro.value != null)
    }

    @Test
    fun `back in finished goes to today with no notice and the session keeps waiting`() {
        motor.viva(planSesion(bloques = 1, entrada = Entrada.DIRECTO), 60 * MINUTO)
        montar()
        pedir(null)
        assertTrue(existe("ses-b-fin"))

        atras()

        assertFalse(existe("ses-tierra"))
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("Sigue en marcha", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue("la sesión sigue esperando su respuesta", motor.registro.value != null)
        assertEquals(emptyList<String>(), motor.llamadas.filter { it != "recuperar" })
    }

    @Test
    fun `the session locks portrait and releases it`() {
        montar()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, rule.activity.requestedOrientation)

        pedir(Destino.Tarea("t1"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)

        atras()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, rule.activity.requestedOrientation)
    }

    @Test
    fun `the camera zooms in on entering with its sound and Habi answers a tap with voice and tick`() {
        montar()
        banco.sonido.sonados.clear()

        pedir(Destino.Tarea("t1"))
        assertTrue(Snd.ZOOM in banco.sonido.snds())
        assertTrue(Haptica.RISE in banco.tacto.hapticas)

        banco.sonido.sonados.clear()
        banco.tacto.hapticas.clear()
        rule.onNode(hasContentDescription("Habi", substring = true)).performClick()
        assertEquals(listOf(Snd.HABI), banco.sonido.snds())
        assertEquals(listOf(Haptica.TICK), banco.tacto.hapticas)
    }

    @Test
    fun `the readers hear the change of block`() {
        motor.viva(directo)
        montar()
        pedir(null)
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Bloque 1 de 4")).assertExists()

        motor.viva(directo, 25 * MINUTO + 1_000)
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Descanso de 5 minutos")).assertExists()

        motor.viva(planSesion(bloques = 1, entrada = Entrada.DIRECTO), 60 * MINUTO)
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Sesión terminada")).assertExists()
    }

    @Test
    fun `a plus tap on focus opens prepare in the session`() {
        montar()
        rule.onNodeWithTag("boton-crear").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create-session").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create-focus").performClick()
        rule.waitUntil(5_000) { existe("ses-b-prep") }

        assertTrue("preparar:null" in motor.llamadas)
    }
}
