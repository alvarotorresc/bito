package com.alvarotc.bito.ui.tienda.jardin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.AccionesGrabadas
import com.alvarotc.bito.ui.tienda.PestanaTienda
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.estadoTienda
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El cielo de la escena del jardín y la cabecera de noche (tienda-jardin.css `.tj-cielo`, `.noche .titulo`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class EscenaJardinTest {
    @get:Rule
    val compose = createComposeRule()

    /** Cambia el estado en el hilo de UI y lo aplica ya: con el reloj parado nadie más lo haría. */
    private fun poner(cambio: () -> Unit) =
        compose.runOnUiThread {
            cambio()
            Snapshot.sendApplyNotifications()
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the sky fades 104 dp under the header and darkens at night`() {
        val estado = EscenaJardinEstado()
        val sonda = SondaEscenaJardin()
        estado.sonda = sonda
        var luz by mutableFloatStateOf(0f)
        lateinit var vista: View
        // Reloj a mano: con el automático, en la suite entera el sandbox deja de quedar en reposo.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            vista = LocalView.current
            ConSistema {
                EscenaJardin(
                    GardenState.INITIAL,
                    EquippedSet(),
                    Personality.NEUTRA,
                    null,
                    { luz },
                    estado,
                    Modifier.size(412.dp, 300.dp),
                    animated = false,
                )
            }
        }
        repeat(4) {
            compose.mainClock.advanceTimeBy(250)
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
        }
        compose.onNodeWithTag("tj-cielo").assertHeightIsEqualTo(104.dp)
        assertEquals(0f, sonda.cieloNoche, 0f)
        // `smooth(1.2, 1.9, v)` del mock con la luz de 0 a 2: aquí de 0,6 a 0,95.
        assertEquals(0f, nocheDelCielo(0.5f), 0f)
        assertEquals(0.5f, nocheDelCielo(0.775f), 0.001f)
        assertEquals(1f, nocheDelCielo(1f), 0f)
        poner { luz = 1f }
        // La luz se lee al dibujar: cambia lo pintado sin recomponer (la captura obliga a pintar).
        compose.runOnIdle { vista.draw(Canvas(Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888))) }
        assertEquals(1f, sonda.cieloNoche, 0f)
    }

    private fun colorDelTitulo(): Color {
        val nodo = compose.onNodeWithText("Habi's store").fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.first().layoutInput.style.color
    }

    @Test
    fun `the title turns light at night in 300 ms`() {
        val colores = BitoClaro
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema {
                TiendaContenido(estadoTienda(pestana = PestanaTienda.JARDIN), AccionesGrabadas(), emptyFlow(), {})
            }
        }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(colores.tinta, colorDelTitulo())
        compose.onNodeWithText("Night").performClick()
        compose.mainClock.advanceTimeBy(150)
        assertNotEquals("a mitad", colores.tintaClara, colorDelTitulo())
        compose.mainClock.advanceTimeBy(200)
        assertEquals(colores.tintaClara, colorDelTitulo())
    }

    // Dos escenas del jardín de la tienda, con luces distintas, en una captura; devuelve la media de cada una.
    private fun mediasConLuz(vararg luces: Float): List<FloatArray> {
        val sondas = List(luces.size) { SondaEscenaJardin() }
        lateinit var vista: View
        compose.setContent {
            vista = LocalView.current
            ConSistema {
                androidx.compose.foundation.layout.Column {
                    luces.forEachIndexed { i, l ->
                        val estado = remember { EscenaJardinEstado().also { it.sonda = sondas[i] } }
                        EscenaJardin(
                            GardenState.INITIAL,
                            EquippedSet(),
                            Personality.NEUTRA,
                            null,
                            { l },
                            estado,
                            Modifier.size(412.dp, 300.dp),
                            animated = false,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        val captura = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(captura))
        val cajas = compose.onAllNodesWithTag("tj-escena").fetchSemanticsNodes().map { it.boundsInRoot }
        return cajas.map { c ->
            val suma = FloatArray(3)
            var n = 0
            for (y in (c.top.toInt() + 120) until c.bottom.toInt() step 6) {
                for (x in c.left.toInt() until c.right.toInt() step 6) {
                    val px = captura.getPixel(x, y)
                    suma[0] += android.graphics.Color.red(px)
                    suma[1] += android.graphics.Color.green(px)
                    suma[2] += android.graphics.Color.blue(px)
                    n++
                }
            }
            FloatArray(3) { suma[it] / n }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the afternoon of the store is orange and the night is a single multiply`() {
        val (dia, tarde, noche) = mediasConLuz(0f, 0.5f, 1f)
        assertEquals("el rojo se queda", dia[0], tarde[0], 3f)
        assertTrue("la tarde baja el azul: ${dia[2]} ${tarde[2]}", tarde[2] < dia[2] * 0.82f)
        // La noche (#31406E / #4B5A8A) multiplica una sola vez: el azul de la noche no baja de su tinte.
        assertTrue("la noche es azul oscuro, no negra: ${noche[2]}", noche[2] > dia[2] * 0.30f && noche[2] < dia[2] * 0.62f)
    }

    @Test
    fun `a tap on an object of the scene reaches the catalog gesture`() {
        val estado = EscenaJardinEstado()
        val tocados = mutableListOf<String>()
        compose.setContent {
            ConSistema {
                EscenaJardin(
                    GardenState.INITIAL.copy(objects = setOf("banco")),
                    EquippedSet(),
                    Personality.NEUTRA,
                    null,
                    { 0f },
                    estado,
                    Modifier.size(412.dp, 300.dp),
                    animated = false,
                    alTocar = { tocados += it },
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        // El banco va al 69 % del ancho y al 72,5 % del alto de la escena (la de 412 x 307,7 apoyada abajo).
        val vistaH = 300f * compose.density.density
        val escenaH = 412f / com.alvarotc.bito.ui.garden.GardenArt.SCENE_ASPECT * compose.density.density
        val x = 0.69f * 412f * compose.density.density
        val y = (vistaH - escenaH) + (0.725f - 0.12f) * escenaH
        compose.onNodeWithTag("tj-escena").performTouchInput { click(androidx.compose.ui.geometry.Offset(x, y)) }
        compose.waitForIdle()
        assertEquals(listOf("banco"), tocados)
    }

    private fun montarConSonda(
        sonda: SondaJardin,
        jardin: GardenState,
        luz: () -> Float,
    ) {
        compose.setContent {
            ConSistema {
                CompositionLocalProvider(LocalSondaJardin provides sonda) {
                    EscenaJardin(
                        jardin,
                        EquippedSet(),
                        Personality.NEUTRA,
                        null,
                        luz,
                        remember { EscenaJardinEstado() },
                        Modifier.size(412.dp, 300.dp),
                        animated = false,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun `going to night in the store never runs the water band`() {
        val sonda = SondaJardin()
        var luz by mutableFloatStateOf(0.5f)
        montarConSonda(sonda, GardenState.INITIAL, { luz })
        poner { luz = 0.7f }
        compose.mainClock.advanceTimeByFrame()
        poner { luz = 1f }
        var maximo = 0f
        repeat(190) {
            compose.mainClock.advanceTimeByFrame()
            maximo = maxOf(maximo, sonda.filtracion())
        }
        assertEquals("la franja de agua del cierre no cruza el jardín de la tienda", 0f, maximo, 0f)
    }

    @Test
    fun `the lantern of the store follows the mock sky from light 0,6 and glows with the smoothstep`() {
        val sonda = SondaJardin()
        var luz by mutableFloatStateOf(0.55f)
        montarConSonda(sonda, GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN)), { luz })
        compose.mainClock.advanceTimeBy(100)
        assertFalse("con luz 0,55 sigue bajo el tinte", sonda.farolEncima())
        assertEquals(0f, sonda.brilloFarolillo(), 0f)
        poner { luz = 0.65f }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("con luz 0,65 ya va encima", sonda.farolEncima())
        assertEquals(nocheDelCielo(0.65f), sonda.brilloFarolillo(), 0.001f)
        assertTrue(sonda.brilloFarolillo() > 0.04f)
        poner { luz = 0.775f }
        assertEquals(0.5f, sonda.brilloFarolillo(), 0.001f)
        poner { luz = 1f }
        assertEquals(1f, sonda.brilloFarolillo(), 0.001f)
    }
}
