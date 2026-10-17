package com.alvarotc.bito.ui.tienda.jardin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.AccionesGrabadas
import com.alvarotc.bito.ui.tienda.PestanaTienda
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.estadoTienda
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        compose.onNodeWithTag("tj-cielo").assertHeightIsEqualTo(104.dp)
        compose.waitForIdle()
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
}
