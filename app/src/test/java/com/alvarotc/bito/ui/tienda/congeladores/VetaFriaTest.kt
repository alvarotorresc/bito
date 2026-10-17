package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.tan

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VetaFriaTest {
    @get:Rule val rule = createComposeRule()
    private val banco = Banco(rule)

    private fun veta(contenido: @androidx.compose.runtime.Composable (Color) -> Unit = {}) {
        banco.colocar {
            Column {
                Spacer(Modifier.height(40.dp))
                VetaFria(Modifier.testTag("veta")) { tinta -> contenido(tinta) }
            }
        }
    }

    @Test
    fun `-4 degrees with counter-rotated content`() {
        assertEquals(-4f, INCLINACION_GRADOS, 0f)
        val p = puntosVeta(412f, 200f)
        val t = tan(Math.toRadians(-4.0)).toFloat()
        assertEquals(t * -206f, p[0].y, 0.01f)
        assertEquals(t * 206f, p[1].y, 0.01f)
        assertTrue("el lado izquierdo baja y el derecho sube", p[0].y > 0f && p[1].y < 0f)
        assertEquals("el alto de la banda se conserva", 200f, p[3].y - p[0].y, 0.01f)

        veta { Box(Modifier.size(100.dp, 50.dp).testTag("dentro")) }
        val banda = rule.onNodeWithTag("veta").getBoundsInRoot()
        val dentro = rule.onNodeWithTag("dentro").getBoundsInRoot()
        assertEquals("el contenido va derecho: 20 dp del lado", 20f, (dentro.left - banda.left).value, 0.5f)
        assertEquals("y a 34 dp de arriba", 34f, (dentro.top - banda.top).value, 0.5f)
        assertEquals("sin inclinación, su alto es el suyo", 50f, dentro.height.value, 0.5f)
    }

    @Test
    fun `the band has the cold vein color and 34 dp of padding above and below`() {
        veta { Box(Modifier.size(100.dp, 50.dp)) }
        val banda = rule.onNodeWithTag("veta").getBoundsInRoot()
        assertEquals(34f + 50f + 34f, banda.height.value, 0.5f)
        val foto = banco.fotograma()
        assertTrue("el centro es la veta", cerca(banco.colores.vetaFria.toArgb(), banco.pixel(foto, "veta", 206f, 59f)))
        // La inclinación se ve en las esquinas: a la izquierda la banda empieza más abajo, a la derecha más arriba.
        assertTrue("arriba a la izquierda aún no hay veta", cerca(Color.White.toArgb(), banco.pixel(foto, "veta", 3f, 4f)))
        assertTrue("arriba a la derecha ya hay veta", cerca(banco.colores.vetaFria.toArgb(), banco.pixel(foto, "veta", 409f, 4f)))
        assertTrue("abajo a la izquierda sobresale", cerca(banco.colores.vetaFria.toArgb(), banco.pixel(foto, "veta", 3f, 120f)))
    }

    @Test
    fun `the soft ink is the AA value inside the band`() {
        var tinta = Color.Unspecified
        veta { tinta = it }
        rule.waitForIdle()
        assertEquals(Color(0xFF5E4A3A), tinta)
        assertNotEquals("no es la tinta suave del resto de la app", banco.colores.tintaSuave, tinta)

        fun contraste(
            a: Color,
            b: Color,
        ): Float {
            val (claro, oscuro) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (claro + 0.05f) / (oscuro + 0.05f)
        }
        assertTrue("AA sobre la veta", contraste(tinta, banco.colores.vetaFria.compositeOver(Color.White)) >= 4.5f)
    }
}
