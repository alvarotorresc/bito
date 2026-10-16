package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.sesion.Encuadre
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class RespirarSueltaAnimoTest {
    @get:Rule
    val compose = nuevaRegla()

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var m: Montaje

    @Before
    fun preparar() {
        m = Montaje(carpeta)
    }

    @After
    fun cerrar() {
        m.cerrar()
    }

    /** El reloj de Compose solo avanza cuando el test lo dice (en pasos de 16 ms mientras espera algo que viene de Room). */
    private fun hasta(condicion: () -> Boolean) {
        var vueltas = 0
        while (!condicion()) {
            compose.mainClock.advanceTimeBy(16)
            Thread.sleep(2)
            if (++vueltas >= 300) {
                error("no llega: " + compose.onRoot().printToString())
            }
        }
    }

    private fun esperar(tag: String) = hasta { existe(tag) }

    private fun existe(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun recuerdos() = runBlocking { m.db.hablaDao().eventosDesde(0).first() }

    private fun sesiones() = runBlocking { m.estado.first().breathingSessions }

    /** Monta, respira Calmarme entera y se queda en «¿Cómo estás ahora?» con la llegada ya hecha. */
    private fun hastaLaPregunta(
        esperarBd: Boolean = true,
        siembra: () -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        siembra()
        m.montar(compose)
        esperar("r-empezar")
        pulsar("r-empezar")
        esperar("s-parar")
        m.avanzar(compose, 119_800)
        // El último tramo, fotograma a fotograma, para saber cuándo llega la pregunta.
        var pasos = 0
        while (!existe("a-saltar")) {
            m.avanzar(compose, 16)
            check(++pasos < 100) { "no llega la pregunta" }
        }
        if (esperarBd) hasta { sesiones().any { it.durationSeconds == 120 } }
    }

    private fun elegir(etiqueta: String) = pulsar(etiqueta)

    /** Pulsa y deja pasar un fotograma para que el cambio llegue a la pantalla. */
    private fun pulsar(etiqueta: String) {
        // Hasta que el bloque que se va termina de irse (160 ms) y el nuevo de entrar, el viejo aún está encima.
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(etiqueta).performClick()
        compose.mainClock.advanceTimeByFrame()
    }

    /** Cuántos ms de reloj pasan hasta que [condicion] se cumple, en pasos de 16 ms. */
    private fun cuantoTarda(condicion: () -> Boolean): Long {
        var t = 0L
        while (!condicion()) {
            compose.mainClock.advanceTimeBy(16)
            t += 16
            check(t < 10_000) { "no llega" }
        }
        return t
    }

    @Test
    fun `arrival rings the bell, then Habi cheers for 1_2 s and the bubble says it for 2_2 s`() {
        hastaLaPregunta(esperarBd = false)
        assertEquals(Encuadre.COMO_ESTAS, m.camara.destino)
        assertTrue(Snd.CAMPANILLA in m.sonido.snds())
        assertTrue(Haptica.CLICK in m.tacto.hapticas)
        assertFalse(m.gestos.alegre)
        val llegada = cuantoTarda { m.gestos.alegre }
        assertTrue("a los 560 ms, fue a los $llegada", llegada in 480L..560L)
        assertTrue(existe("globito"))
        val alegria = cuantoTarda { !m.gestos.alegre }
        assertTrue("1,2 s, fue $alegria", alegria in 1_184L..1_232L)
        assertTrue(existe("globito"))
        // El globito dura 2,2 s en total desde que sale, no los 2,4 s del sistema.
        val resto = cuantoTarda { !existe("globito") }
        assertTrue("el globito se fue a los ${alegria + resto} ms", alegria + resto in 2_200L..2_700L)
    }

    @Test
    fun `the three options have the exact texts and no balance`() {
        hastaLaPregunta()
        m.avanzar(compose, 500)
        compose.onNodeWithTag("como-mejor").assertIsDisplayed()
        compose.onNodeWithTag("como-igual").assertIsDisplayed()
        compose.onNodeWithTag("como-cuesta").assertIsDisplayed()
        compose.onNodeWithTag("a-saltar").assertIsDisplayed()
        assertFalse(existe("ses-saldo"))
    }

    @Test
    fun `choosing writes COMO_ESTAS with its mode and skip writes nothing`() {
        hastaLaPregunta()
        elegir("como-igual")
        hasta { recuerdos().isNotEmpty() }
        val e = recuerdos().single()
        assertEquals(TipoMemoria.COMO_ESTAS.name, e.tipo)
        assertEquals(1, e.valor)
        assertEquals(BreathingMode.CALM.name, e.ref)
        assertTrue(Snd.GORGOTEO in m.sonido.snds())
        assertTrue(Haptica.TICKS in m.tacto.hapticas)
    }

    @Test
    fun `values are 0 better, 1 same, 2 still hard`() {
        assertEquals(listOf(0, 1, 2), AnimoSuelta.entries.map { it.valor })
        assertEquals(listOf("MEJOR", "IGUAL", "CUESTA"), AnimoSuelta.entries.map { it.name })
    }

    @Test
    fun `skip writes nothing and the end says the plain line`() {
        hastaLaPregunta()
        pulsar("a-saltar")
        esperar("sf-txt")
        assertTrue(recuerdos().isEmpty())
        compose.onNodeWithTag("sf-txt").assertTextEquals(YA_ESTA)
        assertEquals(Encuadre.SUELTA_FIN, m.camara.destino)
        assertFalse(Snd.GORGOTEO in m.sonido.snds())
    }

    @Test
    fun `skipping the moment it can be clicked still says the plain line and Habi still cheers`() {
        hastaLaPregunta()
        // Sin dejar pasar los 560 ms de la llegada (el bloque viejo ya se fue): lo que pulsa el que tiene prisa.
        m.avanzar(compose, 200)
        assertFalse(m.gestos.alegre)
        compose.onNodeWithTag("a-saltar").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperar("sf-txt")
        assertFalse(m.gestos.alegre)
        assertEquals(YA_ESTA, textoFin())
        assertTrue(recuerdos().isEmpty())
        // El mock lanza la alegría con su `setTimeout` igualmente, también sobre la tarjeta del fin.
        val llegada = cuantoTarda { m.gestos.alegre }
        assertTrue("alegría a los $llegada ms", llegada in 0L..560L)
        assertEquals(YA_ESTA, textoFin())
    }

    @Test
    fun `starting again before the cheer arrives cancels it`() {
        hastaLaPregunta()
        m.avanzar(compose, 200)
        compose.onNodeWithTag("a-saltar").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperar("sf-otra")
        pulsar("sf-otra")
        m.avanzar(compose, 2_000)
        assertFalse(m.gestos.alegre)
    }

    private companion object {
        const val YA_ESTA = "Ya está."
    }

    private fun textoFin(): String =
        compose.onNodeWithTag("sf-txt").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun `better jumps and still hard gets the normal face`() {
        hastaLaPregunta()
        m.avanzar(compose, 700)
        assertTrue(m.gestos.alegre)
        elegir("como-mejor")
        assertEquals(1, m.gestos.saltos)
        assertEquals(0, m.gestos.caras)
        hasta { recuerdos().isNotEmpty() }
    }

    @Test
    fun `still hard puts the normal face`() {
        hastaLaPregunta()
        m.avanzar(compose, 700)
        assertTrue(m.gestos.alegre)
        elegir("como-cuesta")
        assertFalse(m.gestos.alegre)
        assertEquals(1, m.gestos.caras)
        assertEquals(0, m.gestos.saltos)
        hasta { recuerdos().isNotEmpty() }
    }

    @Test
    fun `the answer passes to the end after 900 ms`() {
        hastaLaPregunta()
        elegir("como-igual")
        m.avanzar(compose, 800)
        assertFalse(existe("sf-txt"))
        m.avanzar(compose, 150)
        esperar("sf-txt")
        hasta { recuerdos().isNotEmpty() }
        assertEquals(1, recuerdos().size)
    }

    @Test
    fun `the end shows week and total counters`() {
        hastaLaPregunta(siembra = {
            runBlocking {
                // Tres de esta semana (una hora antes) y una de enero, que solo cuenta en el total.
                repeat(3) { m.breathing.save(BreathingMode.CALM, PARED_BASE - 3_600_000L - it, 60, true) }
                m.breathing.save(BreathingMode.SLEEP, PARED_BASE - 200L * 86_400_000L, 600, true)
            }
        })
        pulsar("a-saltar")
        esperar("sf-total")
        compose.onNodeWithTag("sf-semana").assertTextEquals("Esta semana: 4 sesiones · 5 min")
        compose.onNodeWithTag("sf-total").assertTextEquals("Desde el principio: 5 sesiones · 15 min")
        assertTrue(existe("sf-otra"))
        assertTrue(existe("sf-listo"))
        assertFalse(existe("ses-saldo"))
    }

    @Test
    fun `listo goes home`() {
        hastaLaPregunta()
        pulsar("a-saltar")
        esperar("sf-listo")
        pulsar("sf-listo")
        assertEquals(1, m.volvioAHoy)
        assertEquals(Encuadre.HOY, m.camara.destino)
    }

    @Test
    fun `reduced makes it 100 ms for the bubble and 500 ms for the end`() {
        m.cerrar()
        m = Montaje(carpeta, reducido = true)
        hastaLaPregunta(esperarBd = false)
        val llegada = cuantoTarda { m.gestos.alegre }
        assertTrue("a los 100 ms, fue a los $llegada", llegada in 48L..112L)
        elegir("como-igual")
        val fin = cuantoTarda { existe("sf-txt") }
        assertTrue("al fin a los 500 ms, fue a los $fin", fin in 400L..640L)
    }
}
