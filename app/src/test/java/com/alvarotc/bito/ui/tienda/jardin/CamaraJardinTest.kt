package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.garden.SceneItem
import com.alvarotc.bito.ui.garden.SceneRect
import com.alvarotc.bito.ui.garden.sceneItems
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.PruebaTienda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** La cámara de la tienda (tienda-jardin.md «Cámara (3.2)»). Los valores esperados están hechos a mano para 412 x 300 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class CamaraJardinTest {
    @get:Rule
    val compose = createComposeRule()

    /** Cambia el estado en el hilo de UI y lo aplica ya: con el reloj parado nadie más lo haría. */
    private fun poner(cambio: () -> Unit) =
        compose.runOnUiThread {
            cambio()
            Snapshot.sendApplyNotifications()
        }

    // La escena de la tienda en dp: 412 de ancho y su alto por el aspecto del arte; la vista es de 412 x 300.
    private val escenaW = 412f
    private val escenaH = 412f / GardenArt.SCENE_ASPECT
    private val vistaW = 412f
    private val vistaH = 300f

    private val lleno =
        GardenState.INITIAL.copy(specials = GardenSpecial.entries.toSet(), objects = setOf("banco", "regadera"))

    private fun rect(clave: String): SceneRect =
        sceneItems(lleno, escenaW, escenaH).first {
            (it is SceneItem.Objeto && it.clave == clave) || (it is SceneItem.Special && it.special.key == clave)
        }.rect

    private fun encuadre(clave: String) = encuadreDe(cajaConSombra(rect(clave)), escenaW, escenaH, vistaW, vistaH, 8f, 66f)

    private fun assertEncuadre(
        esperado: Encuadre,
        real: Encuadre,
        tol: Float = 0.05f,
        que: String = "",
    ) {
        assertEquals("$que escala", esperado.escala, real.escala, 0.001f)
        assertEquals("$que tx", esperado.tx, real.tx, tol)
        assertEquals("$que ty", esperado.ty, real.ty, tol)
    }

    // 300 - 307,72 = -7,72: el jardín entero, apoyado abajo.
    private val entero = Encuadre(1f, 0f, -7.72f)

    @Test
    fun `scale 1_12 when the object and its shadow fit`() {
        // 412 - 412 x 1,12 = -49,44 y 300 - 307,72 x 1,12 = -44,64: la cámara llega al tope de la escena.
        assertEncuadre(Encuadre(1.12f, -49.44f, -44.64f), encuadre("banco"), que = "banco")
        assertEncuadre(Encuadre(1.12f, 0f, -1.89f), encuadre("farolillo"), que = "farolillo")
        assertEncuadre(Encuadre(1.12f, -49.44f, -44.64f), encuadre("estanque"), que = "estanque")
        assertEncuadre(Encuadre(1.12f, -49.44f, 0f), encuadre("cerezo-grande"), que = "cerezo grande")
        assertEncuadre(Encuadre(1.12f, 0f, 0f), encuadre("arbol-dorado"), que = "arbol dorado")
    }

    @Test
    fun `scale 1 for the mushrooms, the path and the watering can when they do not fit`() {
        for (clave in listOf("setas", "caminito", "regadera")) assertEncuadre(entero, encuadre(clave), que = clave)
    }

    @Test
    fun `8 dp margins on both sides and at the bottom`() {
        val cerca = listOf("banco", "farolillo", "estanque", "cerezo-grande", "arbol-dorado")
        for (clave in cerca) {
            val e = encuadre(clave)
            val c = cajaConSombra(rect(clave))
            assertTrue("$clave izquierda", c.left * e.escala + e.tx >= 8f - 0.01f)
            assertTrue("$clave derecha", (c.left + c.width) * e.escala + e.tx <= vistaW - 8f + 0.01f)
            assertTrue("$clave abajo", (c.top + c.height) * e.escala + e.ty <= vistaH - 8f + 0.01f)
        }
    }

    @Test
    fun `66 dp free under the title`() {
        for (clave in listOf("banco", "farolillo", "estanque", "cerezo-grande", "arbol-dorado")) {
            val e = encuadre(clave)
            val c = cajaConSombra(rect(clave))
            assertTrue("$clave arriba ${c.top * e.escala + e.ty}", c.top * e.escala + e.ty >= 66f - 0.01f)
        }
    }

    @Test
    fun `each object is whole in the frame`() {
        // Como la prueba del mock: entero a lo ancho con 8 (medio punto de holgura), arriba dentro y, lo que está en pie, a 2 del borde de abajo.
        val planos = setOf("caminito", "estanque")
        for (clave in GardenArt.objectSpots.keys + GardenArt.specialSpots.keys) {
            val e = encuadre(clave)
            val r = rect(clave)
            val l = r.left * e.escala + e.tx
            val d = (r.left + r.width) * e.escala + e.tx
            val t = r.top * e.escala + e.ty
            val b = (r.top + r.height) * e.escala + e.ty
            assertTrue("$clave izquierda $l", l >= 7.5f)
            assertTrue("$clave derecha $d", d <= vistaW - 7.5f)
            assertTrue("$clave arriba $t", t >= 0f)
            if (clave !in planos) assertTrue("$clave abajo $b", b <= vistaH - 2f)
        }
    }

    @Test
    fun `the whole garden rests on the bottom edge`() {
        assertEncuadre(entero, encuadreEntero(escenaW, escenaH, vistaW, vistaH))
    }

    @Test
    fun `the camera spring has no bounce and is interruptible`() {
        val camara = CamaraJardin()
        var scope: CoroutineScope? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { camara.fijar(entero) }
        }
        compose.mainClock.advanceTimeByFrame()
        val destino = Encuadre(1.12f, -49.44f, -44.64f)
        scope!!.launch { camara.ir(destino) }
        val escalas = mutableListOf<Float>()
        val tys = mutableListOf<Float>()
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            escalas += camara.actual.escala
            tys += camara.actual.ty
        }
        // Sube sin pasarse nunca (amortiguamiento 1,0): monótona y por debajo del destino.
        assertTrue("se mueve: $escalas", escalas.last() > 1.01f)
        escalas.zipWithNext().forEach { (a, b) -> assertTrue("monótona $escalas", b >= a) }
        assertTrue(escalas.all { it <= 1.12f + 1e-4f })
        assertTrue(tys.all { it >= -44.64f - 1e-3f })
        // A mitad, otro destino: parte de donde está, sin saltar.
        val antes = camara.actual
        scope!!.launch { camara.ir(entero) }
        compose.mainClock.advanceTimeByFrame()
        val despues = camara.actual
        assertTrue("sin salto: $antes -> $despues", abs(despues.escala - antes.escala) < 0.03f)
        assertTrue("sin salto: $antes -> $despues", abs(despues.ty - antes.ty) < 5f)
        compose.mainClock.advanceTimeBy(2000)
        assertEncuadre(entero, camara.actual, 0.01f)
    }

    @Test
    fun `leaving the trial returns the camera to the whole garden`() {
        val estado = EscenaJardinEstado()
        var prueba by mutableStateOf<PruebaTienda?>(PruebaTienda.Objeto("garden-banco"))
        compose.setContent {
            ConSistema {
                EscenaJardin(GardenState.INITIAL, EquippedSet(), Personality.NEUTRA, prueba, { 0f }, estado, Modifier.size(412.dp, 300.dp))
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        val d = compose.density.density
        assertEncuadre(Encuadre(1.12f, -49.44f * d, -44.64f * d), estado.camara.actual, 0.5f, "en el banco")
        poner { prueba = null }
        compose.mainClock.advanceTimeBy(2000)
        assertEncuadre(Encuadre(1f, 0f, -7.72f * d), estado.camara.actual, 0.5f, "de vuelta")
    }

    @Test
    fun `moving the camera never recomposes the scene each frame`() {
        val estado = EscenaJardinEstado()
        val sonda = SondaEscenaJardin()
        estado.sonda = sonda
        var prueba by mutableStateOf<PruebaTienda?>(null)
        compose.setContent {
            ConSistema {
                EscenaJardin(GardenState.INITIAL, EquippedSet(), Personality.NEUTRA, prueba, { 0f }, estado, Modifier.size(412.dp, 300.dp))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        poner { prueba = PruebaTienda.Objeto("garden-banco") }
        compose.mainClock.advanceTimeByFrame()
        val desde = sonda.recomposiciones
        val e0 = estado.camara.actual
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("la cámara se ha movido", estado.camara.actual.escala > e0.escala + 0.05f)
        assertTrue("recomposiciones: ${sonda.recomposiciones - desde}", sonda.recomposiciones - desde <= 2)
    }
}
