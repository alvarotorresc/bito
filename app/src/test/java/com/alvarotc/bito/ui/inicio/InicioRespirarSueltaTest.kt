package com.alvarotc.bito.ui.inicio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AireGrabado
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.ui.sesion.MotorSesionFalso
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sistema.ConSistema
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

/** La respiración suelta dentro del Inicio: se abre desde todas partes, con el último modo y con H11. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class InicioRespirarSueltaTest {
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

    private fun hayTexto(texto: String) = rule.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(condicion: () -> Boolean) {
        repeat(300) {
            if (condicion()) return
            rule.mainClock.advanceTimeBy(50)
            Thread.sleep(2)
        }
        assertTrue("no llegó a tiempo", condicion())
    }

    private fun montar(modo: BreathingMode = BreathingMode.CALM) {
        banco.preparar()
        runBlocking { banco.ajustes.update { it.copy(breathingLastMode = modo, userName = "Álvaro") } }
        rule.mainClock.autoAdvance = false
        val contexto = rule.activity
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto) {
                banco.Inicio(
                    sesion =
                        SesionInicio(
                            motor,
                            aire,
                            banco.ajustes,
                            breathing = BreathingRepository(banco.db),
                            memoria = MemoriaRepository(banco.db),
                            estadoDominio = DomainStateRepository(banco.db).observe(),
                            catalogo = CatalogoRecursos { contexto.resources },
                        ),
                    enPrimerPlano = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun `the plus and the card Respirar open the loose breathing in Today with the last mode`() {
        montar(BreathingMode.SLEEP)
        assertTrue(existe("boton-crear"))

        EntradaSesion.pedir(Destino.Respirar)
        esperar { existe("r-empezar") }

        assertFalse("la píldora y el + se van", existe("boton-crear"))
        assertFalse("la tierra de la sesión no se monta", existe("ses-tierra"))
        assertEquals(listOf("preparar:Respirar"), motor.llamadas.filter { it.startsWith("preparar") })
        rule.onNode(hasText("Dormir") and isSelected()).assertExists()
        rule.onNode(hasText("Calmarme") and isSelected()).assertDoesNotExist()
    }

    @Test
    fun `with a live session it asks first and leaving it starts the loose breathing`() {
        motor.viva(planSesion(entrada = Entrada.DIRECTO), 0L)
        motor.avisoAlDejarla = AvisoSesion.ComoIntento("Llamar al banco")
        montar()

        EntradaSesion.pedir(Destino.Respirar)
        esperar { hayTexto("Ya tienes algo en marcha") }
        assertFalse(existe("r-empezar"))

        rule.onNodeWithText("Dejarla y empezar esta").performClick()
        // La respiración se abre en cuanto se deja la sesión, sin esperar a que el aviso (4 s) se vaya.
        esperar { "dejarlaYEmpezar" in motor.llamadas }
        rule.mainClock.advanceTimeBy(600)

        assertTrue("la suelta ya está abierta", existe("r-empezar"))
        assertTrue("y el aviso sigue a la vista", hayTexto("Llamar al banco: queda como intento."))
    }

    @Test
    fun `with a live session following it goes back to the session`() {
        motor.viva(planSesion(entrada = Entrada.DIRECTO), 0L)
        montar()

        EntradaSesion.pedir(Destino.Respirar)
        esperar { hayTexto("Ya tienes algo en marcha") }
        rule.onNode(hasText("Seguir con", substring = true)).performClick()
        esperar { existe("ses-tierra") }

        assertTrue("seguirConLaViva" in motor.llamadas)
        assertFalse(existe("r-empezar"))
    }

    @Test
    fun `back from choosing returns to Today with its plus`() {
        montar()
        EntradaSesion.pedir(Destino.Respirar)
        esperar { existe("r-empezar") }

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        esperar { existe("boton-crear") }

        assertFalse(existe("r-empezar"))
    }
}
