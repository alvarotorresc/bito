package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
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
import kotlin.math.abs

/** La caída del objeto que se prueba y el meceo de lo que ya es tuyo (tienda-jardin.md «Probar un objeto»), con el reloj de Compose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class CaidaObjetoTest {
    @get:Rule
    val compose = createComposeRule()

    /** Cambia el estado en el hilo de UI y lo aplica ya: con el reloj parado nadie más lo haría. */
    private fun poner(cambio: () -> Unit) =
        compose.runOnUiThread {
            cambio()
            Snapshot.sendApplyNotifications()
        }

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val estado = EscenaJardinEstado()
    private var prueba by mutableStateOf<PruebaTienda?>(null)
    private val dp get() = compose.density.density

    private fun montar(
        jardin: GardenState = GardenState.INITIAL,
        reducido: Boolean = false,
    ) {
        compose.setContent {
            ConSistema(sonido = sonido, tacto = tacto, reducido = reducido) {
                EscenaJardin(jardin, EquippedSet(), Personality.NEUTRA, prueba, { 0f }, estado, Modifier.size(412.dp, 300.dp))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** Pide la prueba y devuelve, por fotograma, lo que se pinta de [clave] durante [ms]. */
    private fun probar(
        p: PruebaTienda,
        clave: String,
        ms: Int,
    ): List<Pair<Long, com.alvarotc.bito.ui.garden.PoseObjeto?>> {
        val t0 = compose.mainClock.currentTime
        poner { prueba = p }
        val poses = mutableListOf<Pair<Long, com.alvarotc.bito.ui.garden.PoseObjeto?>>()
        while (compose.mainClock.currentTime - t0 < ms) {
            compose.mainClock.advanceTimeByFrame()
            poses += (compose.mainClock.currentTime - t0) to estado.caida.pose(clave)
        }
        return poses
    }

    @Test
    fun `it falls 150 dp in 220 ms with t squared`() {
        montar()
        val poses = probar(PruebaTienda.Objeto("garden-banco"), "banco", 320)
        val dys = poses.mapNotNull { it.second?.dy?.div(dp) }
        // Empieza 150 dp arriba.
        assertTrue("arranca arriba: $dys", dys.first() < -135f)
        // Con t²: a mitad de los 220 ms le quedan tres cuartos (-112,5 dp); lineal serían -75.
        val inicio = poses.first { it.second != null }.first
        val mitad = poses.minBy { abs(it.first - inicio - 110) }.second!!.dy / dp
        assertEquals("a mitad: $dys", -112.5f, mitad, 14f)
        // Toca tierra a los 220 ms.
        val tierra = poses.first { (it.second?.dy ?: -1f) == 0f }.first - inicio
        assertTrue("toca tierra a los $tierra ms", tierra in 200L..250L)
        dys.zipWithNext().forEach { (a, b) -> assertTrue("solo baja", b >= a) }
    }

    @Test
    fun `the shadow appears at once`() {
        montar()
        val poses = probar(PruebaTienda.Objeto("garden-banco"), "banco", 260).mapNotNull { it.second }
        // La sombra sigue a la caída: con el objeto arriba no está y en tierra está entera.
        assertTrue(poses.first().sombra < 0.15f)
        poses.forEach { assertEquals("sombra con la caída", 1f + it.dy / (150f * dp), it.sombra, 0.02f) }
        assertEquals(1f, poses.last().sombra, 0.001f)
    }

    @Test
    fun `it squashes 6 percent from its base and returns with the caida spring`() {
        montar()
        val poses = probar(PruebaTienda.Objeto("garden-banco"), "banco", 900).mapNotNull { it.second }
        val aplastes = poses.map { 1f - it.escalaY }
        assertEquals("aplasta un 6 %", 0.06f, aplastes.max(), 0.002f)
        // Ensancha un 60 % de lo que aplasta, y vuelve.
        val pico = poses.maxBy { 1f - it.escalaY }
        assertEquals(1f + 0.6f * (1f - pico.escalaY), pico.escalaX, 0.001f)
        assertEquals(1f, poses.last().escalaY, 0.003f)
        // Vuelve con el muelle (no de golpe): tres fotogramas después del pico aún está aplastado y, al pasarse un poco, se ensancha
        // hacia arriba (0,78 de amortiguamiento) antes de quedarse quieto.
        val i = poses.indexOf(pico)
        assertTrue(1f - poses[i + 3].escalaY in 0.01f..0.059f)
        assertTrue("se pasa un poco", aplastes.min() < -0.0005f)
    }

    @Test
    fun `9 grains and at most 10 particles`() {
        montar()
        poner { prueba = PruebaTienda.Objeto("garden-banco") }
        var maximo = 0
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            maximo = maxOf(maximo, estado.caida.granosVisibles())
        }
        assertEquals(9, estado.caida.granosLanzados)
        assertTrue("se ven granos", maximo > 0)
        assertTrue("como mucho 10: $maximo", maximo <= 10)
        assertEquals("se apagan", 0, estado.caida.granosVisibles())
    }

    @Test
    fun `POSAR and LOW at landing`() {
        montar()
        poner { prueba = PruebaTienda.Objeto("garden-banco") }
        compose.mainClock.advanceTimeBy(180)
        assertFalse(Snd.POSAR in sonido.snds())
        assertFalse(Haptica.LOW in tacto.hapticas)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, sonido.snds().count { it == Snd.POSAR })
        assertEquals(1, tacto.hapticas.count { it == Haptica.LOW })
    }

    @Test
    fun `removing the trial removes the object with no animation`() {
        val sonda = SondaEscenaJardin()
        estado.sonda = sonda
        montar()
        probar(PruebaTienda.Objeto("garden-banco"), "banco", 600)
        assertTrue(sonda.pintados.contains("banco"))
        poner { prueba = null }
        compose.mainClock.advanceTimeByFrame()
        assertNull(estado.caida.pose("banco"))
        assertFalse(sonda.pintados.contains("banco"))
    }

    @Test
    fun `touching an owned object moves the camera and rocks it at 260 ms with PLANTA and TICK`() {
        montar(GardenState.INITIAL.copy(objects = setOf("banco")))
        val e0 = estado.camara.actual
        poner { estado.tocarTuyo("banco") }
        compose.mainClock.advanceTimeBy(200)
        assertTrue("la cámara va", estado.camara.actual.escala > e0.escala + 0.02f)
        assertEquals(0f, estado.caida.pose("banco")?.giro ?: 0f, 0f)
        assertFalse(Snd.PLANTA in sonido.snds())
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, sonido.snds().count { it == Snd.PLANTA })
        assertEquals(1, tacto.hapticas.count { it == Haptica.TICK })
        assertTrue("se mece", abs(estado.caida.pose("banco")?.giro ?: 0f) > 0.5f)
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(1.12f, estado.camara.actual.escala, 0.001f)
        assertEquals(0f, estado.caida.pose("banco")?.giro ?: 0f, 0.05f)
    }

    @Test
    fun `a special can be tried and falls into place`() {
        val sonda = SondaEscenaJardin()
        estado.sonda = sonda
        montar()
        val poses = probar(PruebaTienda.Especial("resurrection"), "setas", 600).mapNotNull { it.second }
        assertTrue(sonda.pintados.contains("setas"))
        assertTrue("cae desde arriba", poses.first().dy < -135f * dp)
        assertEquals(0f, poses.last().dy, 0f)
        // Las setas no caben a 1,12: la cámara se queda en el jardín entero.
        assertEquals(1f, estado.camara.actual.escala, 0.001f)
        assertEquals(-7.72f * dp, estado.camara.actual.ty, 0.5f)
    }

    @Test
    fun `reduced fades 150 ms with no grains`() {
        montar(reducido = true)
        val poses = probar(PruebaTienda.Objeto("garden-banco"), "banco", 260).mapNotNull { it.second }
        poses.forEach {
            assertEquals(0f, it.dy, 0f)
            assertEquals(1f, it.escalaY, 0f)
        }
        assertTrue("empieza transparente", poses.first().alpha < 0.25f)
        assertEquals(1f, poses.last().alpha, 0.001f)
        assertEquals(0, estado.caida.granosLanzados)
        // Sonido y háptica no cambian.
        assertTrue(Snd.POSAR in sonido.snds())
        assertTrue(Haptica.LOW in tacto.hapticas)
    }

    @Test
    fun `changing object mid-fall restarts from the current camera`() {
        montar()
        probar(PruebaTienda.Objeto("garden-banco"), "banco", 100)
        val antes = estado.camara.actual
        assertTrue("la cámara iba de camino", antes.escala > 1.01f && antes.escala < 1.11f)
        val poses = probar(PruebaTienda.Especial("streak-7"), "farolillo", 16)
        val despues = estado.camara.actual
        assertTrue("sin salto: $antes -> $despues", abs(despues.escala - antes.escala) < 0.03f)
        assertTrue("sin salto: $antes -> $despues", abs(despues.tx - antes.tx) < 6f * dp)
        assertNull("el banco se va", estado.caida.pose("banco"))
        assertTrue("el farolillo empieza su caída", poses.last().second!!.dy < -130f * dp)
    }
}
