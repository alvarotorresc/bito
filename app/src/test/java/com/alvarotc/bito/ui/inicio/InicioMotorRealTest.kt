package com.alvarotc.bito.ui.inicio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.sesion.AireMudo
import com.alvarotc.bito.sesion.BancoSesion
import com.alvarotc.bito.sesion.Grabadores
import com.alvarotc.bito.sesion.MINUTO
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sistema.ConSistema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El Inicio con el motor de verdad (sobre su banco) y no con el falso: lo que el falso tapa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class InicioMotorRealTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private lateinit var sesiones: BancoSesion
    private lateinit var g: Grabadores
    private lateinit var motor: MotorSesionAndroid

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
        sesiones = BancoSesion(tmp.newFolder("sesion"))
        g = Grabadores()
        motor = sesiones.motor(g)
    }

    @After
    fun tearDown() {
        EntradaSesion.consumir()
        sesiones.cerrar()
        banco.cerrar()
    }

    private fun existe(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun hayTexto(texto: String) = rule.onAllNodes(hasText(texto)).fetchSemanticsNodes().isNotEmpty()

    /** Si en algún momento se vio Hoy (su píldora), la pantalla salió de la sesión. */
    private var viHoy = false

    /** El motor trabaja en sus hilos y la pantalla con el reloj a mano: se avanza el reloj hasta que pasa lo esperado. */
    private fun esperar(condicion: () -> Boolean) {
        repeat(500) {
            if (existe("pildora-hoy")) viHoy = true
            if (condicion()) return
            rule.mainClock.advanceTimeBy(50)
            Thread.sleep(5)
        }
        assertTrue("no llegó a tiempo", condicion())
    }

    private fun avanzar(ms: Int) {
        repeat(ms / 50) {
            rule.mainClock.advanceTimeBy(50)
            if (existe("pildora-hoy")) viHoy = true
        }
    }

    /** Una sesión de la tarea «t1» ya en marcha, directa a Foco, con [bloques] bloques y [minutos] de reloj corridos. */
    private fun conSesion(
        bloques: Int,
        minutos: Long,
    ) = runBlocking {
        sesiones.settings.update { it.copy(sessionEntry = Entrada.DIRECTO) }
        sesiones.tasks.create(taskEntity(id = "t1"))
        motor.preparar(Destino.Tarea("t1"))
        motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = bloques))
        motor.empezar()
        sesiones.reloj.avanzar(minutos * MINUTO)
        motor.tic()
    }

    private fun montar() {
        banco.preparar()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto) {
                banco.Inicio(sesion = SesionInicio(motor, AireMudo, banco.ajustes), enPrimerPlano = true)
            }
        }
        rule.mainClock.advanceTimeBy(100)
        EntradaSesion.pedir(null)
        esperar { existe("ses-tierra") }
    }

    /** Con fuera parado en el cierre, pasa un buen rato de pantalla y solo entonces fuera acaba. */
    private fun cerrarFueraTrasUnRato() {
        viHoy = false
        esperar { g.fuera.esperandoLaPuerta }
        avanzar(1_500)
        val salio = viHoy
        g.fuera.puerta!!.complete(Unit)
        assertFalse("mientras fuera acaba, la pantalla sigue en la sesión", salio)
    }

    @Test
    fun `leaving focus shows its notice even when telling the outside takes a while`() {
        conSesion(bloques = 4, minutos = 10)
        g.fuera.puerta = CompletableDeferred()
        montar()
        esperar { existe("f-dejar") }

        rule.onNodeWithTag("f-dejar").performClick()
        cerrarFueraTrasUnRato()

        esperar { hayTexto("Lo dejamos aquí. Tus 10 min cuentan.") }
        esperar { existe("pildora-hoy") }
    }

    @Test
    fun `later in finished shows its notice even when telling the outside takes a while`() {
        conSesion(bloques = 1, minutos = 26)
        g.fuera.puerta = CompletableDeferred()
        montar()
        esperar { existe("t-luego") }

        rule.onNodeWithTag("t-luego").performClick()
        cerrarFueraTrasUnRato()

        esperar { hayTexto("Queda en tu día para luego.") }
        esperar { existe("pildora-hoy") }
    }

    @Test
    fun `another five in finished goes on to the new focus without passing through Today`() {
        conSesion(bloques = 1, minutos = 26)
        // Si el cierre de la vieja avisara fuera (y suspendiera) antes de arrancar la nueva, la pantalla saldría a Hoy.
        g.fuera.puerta = CompletableDeferred()
        montar()
        esperar { existe("t-otra") }
        viHoy = false

        rule.onNodeWithTag("t-otra").performClick()
        avanzar(1_500)
        g.fuera.puerta!!.complete(Unit)
        esperar { existe("ses-b-foco") }

        assertFalse("la pantalla no pasó por Hoy", viHoy)
    }

    @Test
    fun `back from breathing returns to prepare without passing through Today`() {
        runBlocking {
            sesiones.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            motor.empezar()
        }
        g.fuera.puerta = CompletableDeferred()
        montar()
        esperar { existe("ses-b-entrar") }
        viHoy = false

        rule.onNodeWithTag("ses-atras").performClick()
        cerrarFueraTrasUnRato()
        esperar { existe("ses-b-prep") }

        assertFalse("la pantalla no pasó por Hoy", viHoy)
    }
}
