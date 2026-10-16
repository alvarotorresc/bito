package com.alvarotc.bito.ui.inicio

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AireMudo
import com.alvarotc.bito.ui.sesion.BloqueSesion
import com.alvarotc.bito.ui.sesion.LocalBloqueRetenido
import com.alvarotc.bito.ui.sesion.LocalSalidaSesion
import com.alvarotc.bito.ui.sesion.LocalSaltoHabi
import com.alvarotc.bito.ui.sesion.LocalSesionInicio
import com.alvarotc.bito.ui.sesion.MotorSesionFalso
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.launch
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

    private fun montar(
        reducido: Boolean = false,
        // Estas pruebas son del modo sesión del Inicio: los bloques de verdad tienen las suyas (`InicioRespirarFocoTest`).
        contenido: @Composable (BloqueSesion) -> Unit = {},
    ) {
        banco.preparar()
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto, reducido) {
                banco.Inicio(
                    sesion = SesionInicio(motor, AireMudo, banco.ajustes),
                    enPrimerPlano = true,
                    contenidoSesion = contenido,
                )
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

    private fun atrasDelSistema() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        asentar()
        rule.waitForIdle()
    }

    private fun avisosSigueEnMarcha() =
        rule.onAllNodes(androidx.compose.ui.test.hasText("Sigue en marcha", substring = true)).fetchSemanticsNodes().size

    @Test
    fun `in Today with a live focus back is not the session's and Camino closes first`() {
        motor.viva(directo)
        montar()
        pedir(null)
        atras()
        assertEquals(1, avisosSigueEnMarcha())

        assertFalse("en Hoy nadie atrapa Atrás", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        rule.onNodeWithTag("pildora-camino").performClick()
        asentar()
        rule.waitForIdle()
        rule.onNodeWithTag("inicio-camino").assertExists()
        atrasDelSistema()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
    }

    @Test
    fun `back from prepare leaves no draft trapping back in Today`() {
        montar()
        pedir(Destino.Tarea("t1"))
        atras()

        assertFalse(existe("ses-tierra"))
        assertFalse("el borrador no atrapa Atrás en Hoy", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `back from finished leaves nothing trapping back in Today`() {
        motor.viva(planSesion(bloques = 1, entrada = Entrada.DIRECTO), 60 * MINUTO)
        montar()
        pedir(null)
        atras()

        assertFalse(existe("ses-tierra"))
        assertFalse("Terminada esperando no atrapa Atrás en Hoy", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `a session mode restored after the process died with nothing to show leaves for Today`() {
        banco.preparar()
        val tester = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        var montajes = 0
        tester.setContent {
            // El segundo montaje es el de después de morir el proceso: el borrador (en memoria) ya no está.
            if (remember { ++montajes } == 2) motor.borrador.value = null
            ConSistema(banco.sonido, banco.tacto) {
                banco.Inicio(sesion = SesionInicio(motor, AireMudo, banco.ajustes), enPrimerPlano = true)
            }
        }
        rule.waitForIdle()
        pedir(Destino.Tarea("t1"))
        assertTrue(existe("ses-b-prep"))

        // El proceso muere: el modo sesión se guarda con la instancia, pero el borrador y la sesión viven en memoria.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        asentar()
        rule.waitForIdle()

        assertFalse("no se queda una tierra vacía", existe("ses-tierra"))
        assertTrue("Hoy, con su píldora", existe("pildora-hoy"))
        assertTrue("y con su +", existe("boton-crear"))
    }

    private fun tictacSinRecomponer(): Pair<Int, Int> {
        val registro = motor.registro.value!!
        rule.waitForIdle()
        val antes = banco.sonda.recomposiciones

        // El tic del motor real: un instante nuevo cada 250 ms del mismo tramo y con la sesión (el registro) sin cambiar.
        repeat(20) { i ->
            val ms = 1_000L + 250L * i
            rule.runOnUiThread {
                motor.instante.value = RelojSesion.en(registro, Ahora(INICIO_PARED + ms, ANCLA_ELAPSED + ms))
            }
            rule.waitForIdle()
        }
        return antes to banco.sonda.recomposiciones
    }

    @Test
    fun `the ticks of a live session do not recompose Today`() {
        motor.viva(directo)
        montar()

        val (antes, despues) = tictacSinRecomponer()

        assertEquals("Hoy no se recompone con los tics", antes, despues)
    }

    @Test
    fun `the ticks of a live session do not recompose the Inicio inside the session either`() {
        motor.viva(directo)
        montar()
        pedir(null)

        val (antes, despues) = tictacSinRecomponer()

        assertEquals("el Inicio no se recompone con los tics", antes, despues)
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

    /** Un bloque de Foco que, como «He terminado», cierra la sesión y se queda 2200 ms con su aviso. */
    private val bloqueQueTermina: @Composable (BloqueSesion) -> Unit = { b ->
        if (b == BloqueSesion.FOCO) {
            val retenido = LocalBloqueRetenido.current
            val salida = LocalSalidaSesion.current
            val scope = rememberCoroutineScope()
            Text(
                "terminar",
                Modifier.testTag("t-terminar").clickable {
                    scope.launch {
                        launch {
                            retenido.conservar(BloqueSesion.FOCO, 2_200)
                            salida.avisar("Informe, hecho.")
                            salida.volverAHoy()
                        }
                        motor.heTerminado()
                    }
                },
            )
        }
    }

    @Test
    fun `a block stays mounted after the session closes and then goes back to today with its notice`() {
        motor.viva(directo)
        montar(contenido = bloqueQueTermina)
        pedir(null)
        assertTrue(existe("t-terminar"))
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag("t-terminar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("el motor ya cerró como el real: ${motor.llamadas}", null, motor.instante.value)
        assertTrue("sigue montado a 1000 ms", existe("t-terminar"))
        assertTrue(existe("ses-tierra"))

        rule.mainClock.advanceTimeBy(1_100)
        assertTrue("sigue montado a 2100 ms", existe("t-terminar"))

        rule.mainClock.advanceTimeBy(1_500)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertFalse(existe("ses-tierra"))
        assertTrue(existe("pildora-hoy"))
        rule.onNodeWithText("Informe, hecho.").assertExists()
    }

    @Test
    fun `the blocks can make Habi hop and read the session data`() {
        var datos: com.alvarotc.bito.ui.sesion.SesionInicio? = null
        var saltar: ((Float) -> Unit)? = null
        motor.viva(directo)
        montar(contenido = { b ->
            if (b == BloqueSesion.FOCO) {
                datos = LocalSesionInicio.current
                saltar = LocalSaltoHabi.current
            }
        })
        pedir(null)
        rule.waitForIdle()
        val antes = banco.sonda.saltosHabi()

        rule.runOnUiThread { saltar!!(0.11f) }
        rule.waitUntil(5_000) { banco.sonda.saltosHabi() > antes }

        assertEquals(antes + 1, banco.sonda.saltosHabi())
        assertTrue(datos != null)
        assertFalse(datos!!.noMolestarActivo(motor.registro.value))
    }

    @Test
    fun `celebrations wait until Today and only the coins show inside the session`() {
        banco.preparar(marcaCero = true)
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto) {
                banco.Inicio(sesion = SesionInicio(motor, AireMudo, banco.ajustes), enPrimerPlano = true)
            }
        }
        rule.waitForIdle()
        pedir(Destino.Tarea("t1"))

        runBlocking {
            banco.db.pointsLedgerDao().insertAll(
                listOf(
                    pointsLedgerEntity(
                        id = "w",
                        delta = 10,
                        reason = PointsReason.PERFECT_DAY,
                        refId = "week:${banco.hoyLogico}",
                        logicalDay = banco.hoyLogico,
                        createdAtMillis = 10,
                    ),
                ),
            )
        }
        rule.waitForIdle()
        assertFalse("dentro de la sesión la veta espera", existe("veta-SEMANA"))
        assertEquals(null, banco.celebraciones.actual.value)

        atras()
        rule.waitUntil(5_000) { existe("veta-SEMANA") }
        assertTrue("al volver a Hoy sale la veta", existe("veta-SEMANA"))
    }
}
