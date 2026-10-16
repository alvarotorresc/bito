package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Gesto
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La cámara de la sesión contra el mock (`encuadre()`, `pintaCam()`, `ir()` de `M/sesion.html`). Los valores
 * esperados salen de la escena del mock (1225 × 915, pies al 88 %) en una pantalla de 412 × 915, no de la
 * fórmula de la app: escala = s·1225/412, tx = 206 − s·612,5 y ty = pie − s·805,2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class CamaraSesionTest {
    @get:Rule
    val compose = createComposeRule()

    // El jardín del Inicio a 412 de ancho, en las mismas unidades que la pantalla.
    private val escenaW = 412f
    private val escenaH = 412f / GardenArt.SCENE_ASPECT

    private fun toma(
        e: Encuadre,
        alto: Float = 915f,
        contenido: Float = 0f,
    ) = tomaDeCamara(e.s, e.pie.toFloat(), e.suelo.toFloat(), alto, escenaW, escenaH, holgura = 8f, altoContenido = contenido)

    // (escala del mock relativa a la escena de 412, tx, ty), calculado a mano con la escena del mock.
    private val esperado =
        mapOf(
            Encuadre.HOY to Triple(1.1893f, -39.0f, 0f),
            Encuadre.PREPARAR to Triple(1.7840f, -161.5f, -113.12f),
            Encuadre.RESPIRAR to Triple(2.9733f, -406.5f, -165.2f),
            Encuadre.FOCO to Triple(2.2002f, -247.25f, -155.85f),
            Encuadre.DESCANSO to Triple(1.7840f, -161.5f, -153.12f),
            Encuadre.TERMINADA to Triple(2.1408f, -235.0f, -139.74f),
            Encuadre.SUELTA_ELEGIR to Triple(2.2002f, -247.25f, -165.85f),
            Encuadre.SUELTA_MARCHA to Triple(2.9733f, -406.5f, -165.2f),
            Encuadre.COMO_ESTAS to Triple(2.2002f, -247.25f, -185.85f),
            Encuadre.SUELTA_FIN to Triple(2.3786f, -284.0f, -174.16f),
        )

    @Test
    fun `the table is the mock ENC`() {
        assertEquals(10, Encuadre.entries.size)
        assertEquals(Triple(0.40f, 322, 366), Encuadre.HOY.let { Triple(it.s, it.pie, it.suelo) })
        assertEquals(Triple(1.00f, 640, 700), Encuadre.RESPIRAR.let { Triple(it.s, it.pie, it.suelo) })
        assertEquals(Triple(0.74f, 440, 480), Encuadre.FOCO.let { Triple(it.s, it.pie, it.suelo) })
        assertEquals(Triple(0.80f, 470, 540), Encuadre.SUELTA_FIN.let { Triple(it.s, it.pie, it.suelo) })
    }

    @Test
    fun `each frame leaves the scale and translation of the mock at 412 x 915`() {
        for ((e, v) in esperado) {
            val t = toma(e)
            assertEquals("escala de $e", v.first, t.escala, 0.002f)
            assertEquals("tx de $e", v.second, t.tx, 0.1f)
            assertEquals("ty de $e", v.third, t.ty, 0.1f)
        }
    }

    @Test
    fun `the frame never shows the scene edge`() {
        for (alto in listOf(915f, 800f, 732f, 640f)) {
            for (e in Encuadre.entries) {
                val t = toma(e, alto)
                val suelo = sueloEnPantalla(e.suelo.toFloat(), alto, 0f)
                assertTrue("izquierda de $e a $alto", t.tx <= 0f)
                assertTrue("derecha de $e a $alto", t.tx + t.escala * escenaW >= escenaW - 0.01f)
                assertTrue("arriba de $e a $alto", t.ty <= 0f)
                // Por debajo del suelo la tierra lo tapa; si el borde de arriba no manda, bajan 8 más.
                assertTrue("abajo de $e a $alto", t.ty + t.escala * escenaH >= suelo - 0.01f)
                if (t.ty < 0f) assertTrue("holgura de $e a $alto", t.ty + t.escala * escenaH >= suelo + 8f - 0.01f)
            }
        }
        // En Hoy el pie dejaría asomar el borde de arriba: manda ese límite y el encuadre se pega a 0.
        assertEquals(0f, toma(Encuadre.HOY).ty, 0f)
    }

    @Test
    fun `a shorter screen scales foot and ground`() {
        // 732 = 0,8 · 915: el pie de Foco baja a 352 y el suelo a 384. Hoy no necesita agrandar el jardín para
        // cubrir su 40 % (0,4 · 732 = 292,8 < 307,7), así que la escala es la de Hoy (1) por 0,74 / 0,40 = 1,85.
        assertEquals(384f, sueloEnPantalla(480f, 732f, 0f), 0.01f)
        val t = toma(Encuadre.FOCO, alto = 732f)
        assertEquals(1.85f, t.escala, 0.001f)
        // A mano: escenaH = 412 · 915 / 1225 = 307,74 y 352 − 1,85 · 0,88 · 307,74 = −149,0.
        assertEquals(-149.0f, t.ty, 0.2f)
    }

    @Test
    fun `the ground rises when content does not fit`() {
        // El bloque de Foco cabe bajo 480: el suelo no se mueve.
        assertEquals(480f, sueloEnPantalla(480f, 915f, 400f), 0.01f)
        // Si pide 500 de alto, el suelo sube lo justo: 915 − 500 = 415.
        assertEquals(415f, sueloEnPantalla(480f, 915f, 500f), 0.01f)
        // Y el encuadre sigue sin dejar asomar el borde por encima del suelo nuevo.
        val t = toma(Encuadre.FOCO, contenido = 500f)
        assertTrue(t.ty + t.escala * escenaH >= 415f + 8f)
    }

    @Test
    fun `the modifier draws the scene at the Foco frame`() {
        lateinit var camara: EstadoCamaraSesion
        compose.setContent {
            ConSistema(SonidoGrabado(), TactoGrabado(), false) {
                camara = rememberEstadoCamaraSesion(Encuadre.FOCO)
                val densidad = LocalDensity.current
                Box(
                    Modifier
                        .size(with(densidad) { escenaW.toDp() }, with(densidad) { escenaH.toDp() })
                        .camaraSesion(camara, with(densidad) { 915f.toDp() }, escenaW, escenaH)
                        .testTag("escena"),
                )
            }
        }
        compose.waitForIdle()
        // Escala, tx y ty de Foco de la tabla del mock (a mano), en la caja ya transformada.
        val b = compose.onNodeWithTag("escena").fetchSemanticsNode().boundsInRoot
        assertEquals(-247.25f, b.left, 0.5f)
        assertEquals(-155.85f, b.top, 0.5f)
        assertEquals(2.2002f * 412f, b.width, 0.5f)
        assertEquals(2.2002f * 307.74f, b.height, 1f)
    }

    private lateinit var estado: EstadoCamaraSesion
    private lateinit var gesto: Gesto
    private lateinit var scope: CoroutineScope
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    private fun montar(
        inicial: Encuadre = Encuadre.HOY,
        reducido: Boolean = false,
    ) {
        compose.setContent {
            ConSistema(sonido, tacto, reducido) {
                estado = rememberEstadoCamaraSesion(inicial)
                gesto = rememberGesto()
                scope = rememberCoroutineScope()
            }
        }
        compose.waitForIdle()
    }

    private fun ir(a: Encuadre) {
        compose.runOnIdle { scope.launch { estado.ir(a, gesto) } }
    }

    @Test
    fun `going from Today plays zoom and rise`() {
        montar()
        ir(Encuadre.PREPARAR)
        compose.waitForIdle()

        assertEquals(listOf(Snd.ZOOM), sonido.snds())
        assertEquals(listOf(Haptica.RISE), tacto.hapticas)
        assertEquals(Encuadre.PREPARAR, estado.destino)
    }

    @Test
    fun `going back to Today plays ascenso and low`() {
        montar(Encuadre.PREPARAR)
        ir(Encuadre.HOY)
        compose.waitForIdle()

        assertEquals(listOf(Snd.ASCENSO), sonido.snds())
        assertEquals(listOf(Haptica.LOW), tacto.hapticas)
    }

    @Test
    fun `zooming in more than 0,1 plays acercar`() {
        montar(Encuadre.PREPARAR)
        ir(Encuadre.RESPIRAR)
        compose.waitForIdle()

        assertEquals(listOf(Snd.ACERCAR), sonido.snds())
        assertEquals(listOf(Haptica.RISE), tacto.hapticas)
    }

    @Test
    fun `zooming out more than 0,1 plays alejar`() {
        montar(Encuadre.RESPIRAR)
        ir(Encuadre.FOCO)
        compose.waitForIdle()

        assertEquals(listOf(Snd.ALEJAR), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `less than 0,1 plays nothing`() {
        montar(Encuadre.FOCO)
        ir(Encuadre.TERMINADA)
        compose.waitForIdle()
        ir(Encuadre.TERMINADA)
        compose.waitForIdle()

        assertTrue(sonido.snds().isEmpty())
        assertTrue(tacto.hapticas.isEmpty())
    }

    @Test
    fun `the camera moves with the camara spring and a new ir starts from where it is`() {
        montar()
        compose.mainClock.autoAdvance = false
        ir(Encuadre.RESPIRAR)
        compose.mainClock.advanceTimeBy(100)
        val aMitad = estado.escala
        assertTrue(aMitad > 0.40f && aMitad < 1.00f)

        // Interrumpir: el siguiente encuadre parte de la escala de ahora, sin saltar.
        ir(Encuadre.FOCO)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(aMitad, estado.escala, 0.05f)

        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(0.74f, estado.escala, 0.0001f)
        assertEquals(440f, estado.pie, 0.01f)
        assertEquals(480f, estado.suelo, 0.01f)
        assertEquals(1f, estado.alfa, 0f)
    }

    @Test
    fun `reduced jumps with a 150 ms fade`() {
        montar(reducido = true)
        compose.mainClock.autoAdvance = false
        ir(Encuadre.RESPIRAR)
        compose.mainClock.advanceTimeByFrame()

        // El encuadre está ya en su sitio; solo la opacidad se funde de 0,4 a 1.
        assertEquals(1.00f, estado.escala, 0f)
        assertEquals(640f, estado.pie, 0f)
        assertEquals(700f, estado.suelo, 0f)
        assertTrue(estado.alfa < 0.6f)

        compose.mainClock.advanceTimeBy(75)
        assertNotEquals(1f, estado.alfa)
        assertTrue(estado.alfa > 0.4f)

        compose.mainClock.advanceTimeBy(120)
        assertEquals(1f, estado.alfa, 0f)
        // El sonido y la háptica no cambian con el modo reducido.
        assertEquals(listOf(Snd.ZOOM), sonido.snds())
    }

    @Test
    fun `the ground the tierra paints follows the camera and the content`() {
        montar(Encuadre.FOCO)
        assertEquals(480f, estado.sueloEn(915f, 0f), 0.01f)
        assertEquals(384f, estado.sueloEn(732f, 0f), 0.01f)
        assertEquals(415f, estado.sueloEn(915f, 500f), 0.01f)
    }
}
