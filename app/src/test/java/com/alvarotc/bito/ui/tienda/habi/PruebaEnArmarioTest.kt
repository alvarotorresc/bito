package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import com.alvarotc.bito.ui.tienda.PruebaTienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FOTOGRAMA = 16

/** Monta [ArmarioHabi] con un reloj de Compose a mano y apunta lo que se ve fotograma a fotograma. */
internal class BancoArmario(
    private val rule: ComposeContentTestRule,
    reducido: Boolean = false,
    inicial: EquippedSet = EquippedSet(),
) {
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val sonda = SondaSalto()
    val look = mutableStateOf(inicial)
    val prueba = mutableStateOf<PruebaTienda?>(null)
    lateinit var armario: EscenaArmarioEstado
    lateinit var anim: AnimacionPrueba

    init {
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                armario = remember { EscenaArmarioEstado() }.also { it.sonda = sonda }
                anim = remember { AnimacionPrueba() }
                Box(Modifier.width(412.dp)) {
                    ArmarioHabi(
                        look = look.value,
                        prueba = prueba.value,
                        armario = armario,
                        luz = { 0f },
                        sonido = sonido,
                        tacto = tacto,
                        animacion = anim,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    /** Cambia lo que se ve y deja pasar un fotograma para que se componga. */
    fun poner(
        nuevo: EquippedSet,
        probando: PruebaTienda? = null,
    ) {
        rule.runOnIdle {
            look.value = nuevo
            prueba.value = probando
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
    }

    /** Avanza [ms] de fotograma en fotograma y devuelve lo que valía [lectura] en cada uno. */
    fun avanzar(
        ms: Int,
        lectura: () -> Float,
    ): List<Float> {
        val vistos = mutableListOf<Float>()
        var t = 0
        while (t < ms) {
            val paso = minOf(FOTOGRAMA, ms - t)
            rule.mainClock.advanceTimeBy(paso.toLong())
            vistos += lectura()
            t += paso
        }
        return vistos
    }

    fun avanzar(ms: Int) = rule.mainClock.advanceTimeBy(ms.toLong())
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PruebaEnArmarioTest {
    @get:Rule val rule = createComposeRule()

    private val gorro = "head-gorro-lana"
    private val botas = "feet-botas-agua"

    @Test
    fun `a hat falls 210 ms and then rebounds with the caida spring`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(head = gorro), PruebaTienda.Pieza(gorro))
        assertEquals(380f, b.anim.caidaDp, 12f)
        assertEquals(0.2f, b.anim.alfaPieza, 0.1f)
        // t² a mitad de tiempo: un cuarto del recorrido.
        b.avanzar(100)
        assertEquals(380f * (1f - 0.23f), b.anim.caidaDp, 45f)
        assertTrue(b.anim.alfaPieza in 0.3f..0.9f)
        b.avanzar(130)
        assertEquals(0f, b.anim.caidaDp, 0.01f)
        val rebote = b.avanzar(1500) { b.anim.reboteDp }
        assertEquals(3f, rebote.maxOf { kotlin.math.abs(it) }, 0.6f)
        assertEquals(0f, b.anim.reboteDp, 0.05f)
        assertNull(b.anim.gesto)
    }

    @Test
    fun `Habi squashes 7 percent in 80 and 240 ms`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(head = gorro), PruebaTienda.Pieza(gorro))
        val antes = b.avanzar(190) { b.armario.escalaY }
        assertTrue("no se aplasta antes de llegar", antes.all { it == 1f })
        b.avanzar(30)
        val escala = b.avanzar(500) { b.armario.escalaY }
        assertEquals(0.93f, escala.min(), 0.01f)
        assertEquals(1f, b.armario.escalaY, 0.001f)
        // la ida dura 80 ms: a los 96 ms del aterrizaje ya está en el fondo
        assertTrue(escala.indexOfFirst { it <= 0.935f } in 0..6)
    }

    @Test
    fun `a hat plays POSAR and TICK then LOW`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(head = gorro), PruebaTienda.Pieza(gorro))
        b.avanzar(100)
        assertEquals(listOf(Snd.POSAR), b.sonido.snds())
        assertEquals(listOf(Haptica.TICK), b.tacto.hapticas.toList())
        b.avanzar(200)
        assertEquals(listOf(Haptica.TICK, Haptica.LOW), b.tacto.hapticas.toList())
        assertEquals(listOf(Snd.POSAR), b.sonido.snds())
    }

    @Test
    fun `boots sprout with 260 dp clipped and 10 grains in 420 ms`() {
        val b = BancoArmario(rule)
        val escalaBrote = mutableListOf<Float>()
        b.poner(EquippedSet(feet = botas), PruebaTienda.Pieza(botas))
        assertEquals(GestoPrueba.BROTA, b.anim.gesto)
        assertEquals(260f, b.anim.brotarDp, 6f)
        assertEquals(10, GRANOS_BROTE)
        assertEquals(10, b.anim.granos)
        val granos =
            b.avanzar(380) {
                escalaBrote += b.armario.escalaY
                b.anim.progresoGranos
            }
        assertTrue("los granos aún vuelan a los 400 ms", granos.last() < 1f)
        b.avanzar(60)
        assertEquals(1f, b.anim.progresoGranos, 0.001f)
        assertEquals(0.96f, escalaBrote.min(), 0.01f)
        b.avanzar(1500)
        assertEquals(0f, b.anim.brotarDp, 0.5f)
        assertEquals(listOf(Snd.BROTE, Snd.POSAR), b.sonido.snds())
        assertEquals(listOf(Haptica.TICK, Haptica.LOW), b.tacto.hapticas.toList())
    }

    @Test
    fun `a color fades 180 ms and squashes 6 percent and plays BROTE`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(bodyColor = "body-rosa"), PruebaTienda.Pieza("body-rosa"))
        assertEquals(GestoPrueba.FUNDE, b.anim.gesto)
        val escala = mutableListOf<Float>()
        val medio =
            b.avanzar(30) {
                escala += b.armario.escalaY
                b.anim.fundido
            }
        assertTrue(medio.last() in 0.1f..0.99f)
        b.avanzar(110) {
            escala += b.armario.escalaY
            0f
        }
        assertEquals("a los 150 ms sigue la capa", GestoPrueba.FUNDE, b.anim.gesto)
        b.avanzar(100)
        assertNull("a los 250 ms ya no hay capa", b.anim.gesto)
        escala += b.avanzar(400) { b.armario.escalaY }
        assertEquals(0.94f, escala.min(), 0.01f)
        assertEquals(listOf(Snd.BROTE), b.sonido.snds())
        assertEquals(listOf(Haptica.TICK), b.tacto.hapticas.toList())
        assertEquals(0f, b.anim.caidaDp, 0f)
    }

    @Test
    fun `eyes do not make a happy face`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(eyeColor = "eyes-azul"), PruebaTienda.Pieza("eyes-azul"))
        b.avanzar(1500)
        assertEquals("ni salta ni se alegra", 0f, b.sonda.picoFraccion, 0f)
        assertEquals(listOf(Snd.BROTE), b.sonido.snds())
    }

    @Test
    fun `taking the trial off fades 180 ms and plays HUNDIR and LOW`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(head = gorro), PruebaTienda.Pieza(gorro))
        b.avanzar(2000)
        b.sonido.sonados.clear()
        b.tacto.hapticas.clear()
        b.poner(EquippedSet(), null)
        assertEquals(GestoPrueba.FUNDE, b.anim.gesto)
        val sale = b.avanzar(30) { b.anim.fundido }
        assertTrue("sale: del 1 hacia el 0", sale.last() in 0.02f..0.95f)
        b.avanzar(210)
        assertNull(b.anim.gesto)
        b.avanzar(400)
        assertEquals(listOf(Snd.HUNDIR), b.sonido.snds())
        assertEquals(listOf(Haptica.LOW), b.tacto.hapticas.toList())
    }

    @Test
    fun `changing the trial mid-fall restarts from the current state`() {
        val b = BancoArmario(rule)
        b.poner(EquippedSet(feet = botas), PruebaTienda.Pieza(botas))
        b.avanzar(40)
        val aplastada = b.armario.escalaY
        assertTrue("las botas ya la aplastan", aplastada < 1f)
        b.poner(EquippedSet(feet = botas, head = gorro), PruebaTienda.Pieza(gorro))
        assertEquals("la nueva es el gorro, que cae", GestoPrueba.CAE, b.anim.gesto)
        assertEquals(gorro, b.anim.itemId)
        assertEquals(380f, b.anim.caidaDp, 12f)
        assertTrue("el aplastamiento sigue donde estaba, no vuelve a cero", b.armario.escalaY <= aplastada + 0.02f)
        val escala = b.avanzar(900) { b.armario.escalaY }
        assertEquals("y acaba suelta", 1f, escala.last(), 0.001f)
        assertEquals(listOf(Snd.BROTE, Snd.POSAR), b.sonido.snds())
        assertEquals("solo el aterrizaje del gorro", 1, b.tacto.hapticas.count { it == Haptica.LOW })
    }

    @Test
    fun `reduced fades 150 ms with no grains and no squash`() {
        val b = BancoArmario(rule, reducido = true)
        b.poner(EquippedSet(feet = botas), PruebaTienda.Pieza(botas))
        assertEquals(GestoPrueba.FUNDE, b.anim.gesto)
        val cae = b.avanzar(40) { b.anim.caidaDp + b.anim.brotarDp }
        assertTrue("sin caída ni brote", cae.all { it == 0f })
        assertEquals(0, b.anim.granos)
        assertTrue(b.anim.fundido in 0.1f..0.97f)
        b.avanzar(120)
        assertNull(b.anim.gesto)
        val escala = b.avanzar(400) { b.armario.escalaY }
        assertTrue("sin aplastar", escala.all { it == 1f })
        assertFalse(b.sonido.snds().isEmpty())
        assertEquals("el sonido y la háptica no cambian", listOf(Haptica.TICK, Haptica.LOW), b.tacto.hapticas.toList())
    }
}
