package com.alvarotc.bito.ui.premios

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapaParticulasTest {
    @get:Rule val rule = createComposeRule()

    private val fila = Rect(0f, 400f, 40f, 440f)
    private val saldo = Rect(300f, 20f, 360f, 50f)
    private val parcela = Rect(100f, 100f, 200f, 220f)
    private val tarea = Rect(10f, 600f, 50f, 640f)

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val estado = EstadoParticulas()
    private var llegadas = 0
    private var fin = 0

    private fun registroCompleto(ahora: () -> Long = System::currentTimeMillis) =
        RegistroAnclas(ahora).apply {
            poner("fila:h1", fila)
            poner("fila:h2", fila)
            poner(ClavesAncla.SALDO, saldo)
            poner(ClavesAncla.PARCELA, parcela)
            poner(ClavesAncla.tarea("t1"), tarea)
        }

    /** El reloj queda parado: cada test lo mueve a mano. Un fotograma deja a las corrutinas de la capa recogiendo. */
    private fun montar(
        registro: RegistroAnclas,
        monedas: MutableState<Momento.Monedas?> = mutableStateOf(null),
        reducido: Boolean = false,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                CapaParticulasConEstado(registro, monedas.value, { llegadas++ }, { fin++ }, Modifier, estado)
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun avanzar(ms: Long) = rule.mainClock.advanceTimeBy(ms)

    private fun poner(
        estadoMonedas: MutableState<Momento.Monedas?>,
        m: Momento.Monedas?,
    ) = rule.runOnUiThread {
        estadoMonedas.value = m
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `the trajectory starts at the row and ends at the plot base`() {
        val origen = Rect(0f, 500f, 40f, 540f)
        val destino = Rect(300f, 100f, 360f, 160f)
        assertEquals(origen.center, trayectoria(origen, destino, 0f))
        assertEquals(Offset(destino.center.x, destino.bottom), trayectoria(origen, destino, 1f))
    }

    @Test
    fun `the trajectory arcs above both ends`() {
        val origen = Rect(0f, 500f, 40f, 540f)
        val destino = Rect(300f, 100f, 360f, 160f)
        val mitad = trayectoria(origen, destino, 0.5f)
        val recta = (origen.center.y + destino.bottom) / 2f
        assertTrue("la mitad del arco queda por encima de la recta", mitad.y < recta - 50f)
    }

    @Test
    fun `coins arrive one by one and finish once`() {
        val registro = registroCompleto()
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)))
        avanzar(Duraciones.MONEDA + 2L * Duraciones.MONEDA_ESCALON + 100L)
        assertEquals(3, llegadas)
        assertEquals(1, fin)
        assertEquals("sin monedas en el aire al final", 0, estado.monedas.size)
        assertEquals("con varias, la primera es la campanilla", 1, sonido.snds().count { it == Snd.CAMPANILLA })
        assertEquals(2, sonido.snds().count { it == Snd.MONEDA })
        assertEquals(3, tacto.hapticas.count { it == Haptica.TICK })
    }

    @Test
    fun `the coins land staggered and the last one ends the batch`() {
        val registro = registroCompleto()
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)))
        avanzar(Duraciones.MONEDA + 40L)
        assertEquals(1, llegadas)
        assertEquals(0, fin)
        avanzar(2L * Duraciones.MONEDA_ESCALON)
        assertEquals(3, llegadas)
        assertEquals(1, fin)
    }

    @Test
    fun `the particle layer never draws more than its caps`() {
        val registro = registroCompleto()
        val monedas = mutableStateOf<Momento.Monedas?>(null)
        montar(registro, monedas)
        var maxGotas = 0
        repeat(10) {
            registro.gota("fila:h1")
            avanzar(16)
            maxGotas = maxOf(maxGotas, estado.gotas.size)
        }
        assertEquals(TopesParticulas.GOTAS, maxGotas)
        assertEquals("salir es de la fila: la capa no suena al salir", 0, sonido.snds().count { it == Snd.GORGOTEO })

        var maxGranos = 0
        repeat(3) {
            registro.granos(ClavesAncla.tarea("t1"))
            avanzar(16)
            maxGranos = maxOf(maxGranos, estado.granos.size)
        }
        assertEquals(TopesParticulas.GRANOS, maxGranos)

        poner(monedas, Momento.Monedas("fila:h1", 8, 8, 1))
        avanzar(16)
        assertEquals(TopesParticulas.MONEDAS, estado.monedas.size)
        avanzar(Duraciones.MONEDA + 5L * Duraciones.MONEDA_ESCALON + 100L)
        assertEquals(5, llegadas)
        assertEquals(1, fin)
    }

    @Test
    fun `grains fall from the task circle without sound`() {
        val registro = registroCompleto()
        montar(registro)
        registro.granos(ClavesAncla.tarea("t1"))
        avanzar(16)
        assertEquals(TopesParticulas.GRANOS, estado.granos.size)
        avanzar(Duraciones.GRANOS + 13L * Duraciones.GRANOS_ESCALON + 100L)
        assertEquals(0, estado.granos.size)
        assertTrue("el sonido es de la fila", sonido.sonados.isEmpty())
        assertTrue("la háptica es de la fila", tacto.hapticas.isEmpty())
        assertEquals("los granos no terminan monedas ajenas", 0, fin)
    }

    @Test
    fun `grains still fall after their row has left the screen`() {
        val registro = registroCompleto()
        montar(registro)
        registro.granos(ClavesAncla.tarea("t1"))
        avanzar(16)
        registro.quitar(ClavesAncla.tarea("t1")) // la fila sale a los 340 ms
        avanzar(200)
        assertEquals("los granos siguen cayendo con el origen que ya copiaron", TopesParticulas.GRANOS, estado.granos.size)
    }

    @Test
    fun `grains without an anchor draw nothing and do not throw`() {
        val registro = RegistroAnclas()
        montar(registro)
        registro.granos(ClavesAncla.tarea("t9"))
        avanzar(100)
        assertEquals(0, estado.granos.size)
        assertEquals(0, fin)
    }

    @Test
    fun `a drop flies while monedas is null`() {
        val registro = registroCompleto()
        montar(registro)
        registro.gota("fila:h1")
        avanzar(100)
        assertEquals(1, estado.gotas.size)
        assertTrue("al salir no suena nada (es de la fila)", sonido.sonados.isEmpty())
        avanzar(Duraciones.GOTA_RETRASO + Duraciones.GOTA + 40L)
        assertEquals(0, estado.gotas.size)
        assertEquals("solo suena al llegar", listOf(Snd.LLEGADA), sonido.snds())
        assertEquals(0, fin)
    }

    @Test
    fun `a drop in flight is not restarted when the coins change`() {
        val registro = registroCompleto()
        val monedas = mutableStateOf<Momento.Monedas?>(null)
        montar(registro, monedas)
        registro.gota("fila:h1")
        avanzar(200)
        poner(monedas, Momento.Monedas("fila:h1", 1, 1, 1))
        avanzar(16)
        assertEquals("la gota sigue volando", 1, estado.gotas.size)
        avanzar(Duraciones.GOTA + 100L)
        assertEquals("una sola llegada de gota", 1, sonido.snds().count { it == Snd.LLEGADA })
        assertEquals("la capa nunca pone el gorgoteo", 0, sonido.snds().count { it == Snd.GORGOTEO })
    }

    @Test
    fun `putting a drop out removes it without a landing`() {
        val registro = registroCompleto()
        montar(registro)
        registro.gota("fila:h1")
        avanzar(200)
        registro.apagarGota("fila:h1")
        avanzar(16)
        avanzar(Duraciones.GOTA + 200L)
        assertEquals(0, estado.gotas.size)
        assertTrue("una gota apagada no llega", Snd.LLEGADA !in sonido.snds())
    }

    @Test
    fun `a missing anchor skips the flight without crashing`() {
        val registro = RegistroAnclas()
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)))
        avanzar(100)
        assertEquals(1, fin)
        assertEquals(0, estado.monedas.size)
        assertTrue("suena igual", Snd.CAMPANILLA in sonido.snds())
    }

    @Test
    fun `coins without an anchor still tell the balance once per coin`() {
        val registro = RegistroAnclas()
        var avisos = 0
        val tarea =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
                registro.llegadasDeMoneda.collect { avisos++ }
            }
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)))
        avanzar(100)
        assertEquals("el saldo sube con su tic aunque no haya vuelo", 3, avisos)
        tarea.cancel()
    }

    @Test
    fun `a drop or grains without an anchor never end somebody else's coins`() {
        val registro = RegistroAnclas()
        montar(registro)
        registro.gota("fila:nada")
        registro.granos(ClavesAncla.tarea("nada"))
        avanzar(1_000)
        assertEquals(0, fin)
        assertEquals(0, estado.gotas.size)
        assertTrue("la llegada suena igual", Snd.LLEGADA in sonido.snds())
    }

    @Test
    fun `remarking flies a drop but no coins`() {
        val registro = registroCompleto()
        montar(registro)
        registro.gota("fila:h1")
        avanzar(16)
        registro.apagarGota("fila:h1")
        avanzar(16)
        registro.gota("fila:h1")
        avanzar(16)
        assertEquals(0, sonido.snds().count { it == Snd.GORGOTEO })
        assertEquals(0, llegadas)
        assertEquals(1, estado.gotas.count { it.apagado.value == 1f })
    }

    @Test
    fun `coins use the last known position of an anchor that just left`() {
        var reloj = 0L
        val registro = registroCompleto { reloj }
        registro.quitar(ClavesAncla.tarea("t1")) // la fila de la tarea ya se fue cuando llega la entrada del libro
        reloj = 1_500
        montar(registro, mutableStateOf(Momento.Monedas(ClavesAncla.tarea("t1"), 3, 3, 1)))
        avanzar(16)
        assertEquals("vuelan desde donde estuvo la fila", 3, estado.monedas.size)
        avanzar(Duraciones.MONEDA + 2L * Duraciones.MONEDA_ESCALON + 100L)
        assertEquals(3, llegadas)
        assertEquals(1, fin)
    }

    @Test
    fun `a retired anchor is forgotten after two seconds`() {
        var reloj = 0L
        val registro = registroCompleto { reloj }
        registro.quitar(ClavesAncla.tarea("t1"))
        reloj = 2_500
        montar(registro, mutableStateOf(Momento.Monedas(ClavesAncla.tarea("t1"), 3, 3, 1)))
        avanzar(100)
        assertEquals("sin origen, sin vuelo", 0, estado.monedas.size)
        assertEquals(1, fin)
        assertEquals(0, llegadas)
    }

    @Test
    fun `with reduced motion nothing flies and it still sounds`() {
        val registro = registroCompleto()
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)), reducido = true)
        avanzar(100)
        assertEquals(0, estado.monedas.size)
        assertEquals(0, fin)
        avanzar(Duraciones.REDUCIDO.toLong())
        assertEquals(1, fin)
        assertTrue(Snd.CAMPANILLA in sonido.snds())
        assertEquals(3, llegadas)
    }

    @Test
    fun `the balance is told once per coin that lands`() {
        val registro = registroCompleto()
        var avisos = 0
        montar(registro, mutableStateOf(Momento.Monedas("fila:h1", 3, 3, 1)))
        val tarea =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
                registro.llegadasDeMoneda.collect { avisos++ }
            }
        avanzar(Duraciones.MONEDA + 40L)
        assertEquals("el saldo no hace tic antes de que llegue la primera", 1, avisos)
        avanzar(2L * Duraciones.MONEDA_ESCALON + 100L)
        assertEquals(3, avisos)
        tarea.cancel()
    }

    @Test
    fun `retired anchors are pruned after two seconds`() {
        var reloj = 0L
        val registro = RegistroAnclas { reloj }
        registro.poner("a", fila)
        registro.quitar("a")
        assertEquals(1, registro.retiradasRecordadas)
        reloj = 2_500
        registro.poner("b", fila)
        registro.quitar("b")
        assertEquals("la caducada se poda al retirar otra", 1, registro.retiradasRecordadas)
    }
}
