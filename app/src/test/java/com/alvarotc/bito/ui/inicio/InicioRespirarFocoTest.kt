package com.alvarotc.bito.ui.inicio

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AireGrabado
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.ResultadoAccion
import com.alvarotc.bito.ui.sesion.MotorSesionFalso
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sistema.ConSistema
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** Respirar para entrar y Foco con sus bloques de verdad, dentro del Inicio y con el motor falso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class InicioRespirarFocoTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val motor = MotorSesionFalso()
    private val aire = AireGrabado()

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() {
        EntradaSesion.consumir()
        banco.cerrar()
    }

    private fun existe(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Respirar pide un fotograma tras otro mientras dura: con el reloj a mano se avanza hasta que pasa lo esperado. */
    private fun esperar(condicion: () -> Boolean) {
        repeat(200) {
            if (condicion()) return
            rule.mainClock.advanceTimeBy(50)
        }
        assertTrue("no llegó a tiempo", condicion())
    }

    private fun montar() {
        banco.preparar()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto) {
                banco.Inicio(sesion = SesionInicio(motor, aire, banco.ajustes), enPrimerPlano = true)
            }
        }
        rule.mainClock.advanceTimeBy(100)
        EntradaSesion.pedir(null)
        esperar { existe("ses-tierra") }
    }

    @Test
    fun `no balance in entry breathing but the music button is there`() {
        motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0L)
        montar()
        esperar { existe("e-fase") }

        assertFalse(existe("ses-saldo"))
        assertTrue(existe("ses-musica"))
        assertTrue(existe("e-saltar"))
    }

    @Test
    fun `back from breathing says skip and back to prepare`() {
        motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0L)
        montar()

        val etiqueta = rule.onNodeWithTag("ses-atras").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("Saltar y volver a preparar", etiqueta)
    }

    @Test
    fun `music button toggles the air and saves the setting`() {
        motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0L)
        montar()
        val antes = runBlocking { banco.ajustes.settings.first().breathingMusicEnabled }

        rule.onNodeWithTag("ses-musica").performClick()
        esperar { runBlocking { banco.ajustes.settings.first().breathingMusicEnabled } != antes }

        assertTrue("musica:${!antes}" in aire.llamadas)
        assertEquals(!antes, runBlocking { banco.ajustes.settings.first().breathingMusicEnabled })
    }

    @Test
    fun `skip in the real block asks the motor to skip`() {
        motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0L)
        montar()
        esperar { existe("e-saltar") }

        rule.onNodeWithTag("e-saltar").performClick()
        esperar { "saltar" in motor.llamadas }

        assertTrue("saltar" in motor.llamadas)
    }

    @Test
    fun `focus shows its clock and buttons and no background tag when Bito is quiet`() {
        motor.viva(planSesion(entrada = Entrada.DIRECTO), 25 * 60_000L - (18 * 60_000L + 42_000L))
        montar()
        esperar { existe("f-t") }

        rule.onNodeWithTag("f-t").assertTextEquals("18:42")
        rule.onNodeWithTag("f-hecho").assertTextEquals("He terminado")
        assertTrue(existe("ses-saldo"))
        assertFalse("con Bito callado no hay etiqueta de fondo", existe("f-et-fondo"))
        assertFalse(existe("f-et-dnd"))
    }

    @Test
    fun `he terminado in the real block holds Hecha and goes back to today with its notice`() {
        motor.respuesta = ResultadoAccion(3, false, AvisoSesion.Hecho("Informe"))
        motor.viva(planSesion(entrada = Entrada.DIRECTO), 60_000L)
        montar()
        esperar { existe("f-hecho") }

        rule.onNodeWithTag("f-hecho").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        assertTrue("sigue la sesión en pantalla a 1,5 s", existe("ses-tierra"))

        rule.mainClock.advanceTimeBy(1_500)
        esperar { existe("pildora-hoy") }
        rule.onNodeWithText("Informe, hecho.").assertExists()
    }

    @Test
    fun `Habi is radiant in the finished block and plain in focus`() {
        motor.viva(planSesion(bloques = 1, entrada = Entrada.DIRECTO), 60_000L)
        montar()
        esperar { existe("f-hecho") }
        assertEquals(Mood.NORMAL, banco.sonda.moodHabi())

        motor.viva(planSesion(bloques = 1, entrada = Entrada.DIRECTO), 25 * 60_000L + 1_000L)
        esperar { existe("t-preg") }
        esperar { banco.sonda.moodHabi() == Mood.RADIANT }

        assertEquals(Mood.RADIANT, banco.sonda.moodHabi())
    }
}
