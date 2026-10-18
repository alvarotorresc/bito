package com.alvarotc.bito.ui.tienda

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.ui.sistema.BitoColores
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HornacinaPiezaTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var vista: View
    private lateinit var colores: BitoColores

    private fun enEspanol(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("es")) }
        val ctx = base.createConfigurationContext(cfg)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) { ConSistema { contenido() } }
        }
    }

    private fun pieza(
        modifier: Modifier = Modifier,
        nombre: String = "Gorro de lana",
        etiqueta: EtiquetaHornacina = EtiquetaHornacina.PRECIO,
        textoEtiqueta: String? = null,
        precio: Int? = 60,
        aro: AroHornacina = AroHornacina.NINGUNO,
        bloqueada: Boolean = false,
        tapada: Boolean = false,
        alTocar: () -> Unit = {},
    ) {
        enEspanol {
            vista = LocalView.current
            run {
                colores = BitoTema.colores
                HornacinaPieza(
                    imagen = { Box(Modifier.fillMaxSize().background(Color.Black).testTag("hornacina-arte")) },
                    nombre = nombre,
                    etiqueta = etiqueta,
                    textoEtiqueta = textoEtiqueta,
                    precio = precio,
                    aro = aro,
                    bloqueada = bloqueada,
                    tapada = tapada,
                    alTocar = alTocar,
                    modifier = modifier.testTag("item-gorro"),
                )
            }
        }
    }

    private fun fotograma(): Bitmap {
        rule.waitForIdle()
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        return foto
    }

    private fun pixel(
        foto: Bitmap,
        etiqueta: String,
        xDp: Float,
        yDp: Float,
    ): Int {
        val caja = rule.onNodeWithTag(etiqueta, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return foto.getPixel((caja.left + xDp * rule.density.density).toInt(), (caja.top + yDp * rule.density.density).toInt())
    }

    private fun cerca(
        a: Int,
        b: Int,
        margen: Int = 12,
    ) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < margen &&
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < margen &&
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < margen

    @Test
    fun `the niche is 104 dp high, the art at most 84 by 78 and the coin 18 dp`() {
        pieza(Modifier.width(110.dp))
        val hueco = rule.onNodeWithTag("hornacina-hueco", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(104f, hueco.height.value, 0.5f)
        assertEquals(110f, hueco.width.value, 0.5f)
        val arte = rule.onNodeWithTag("hornacina-arte", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(84f, arte.width.value, 0.5f)
        assertEquals(78f, arte.height.value, 0.5f)
        val moneda = rule.onNodeWithTag("hornacina-moneda", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(18f, moneda.width.value, 0.5f)
        assertEquals(18f, moneda.height.value, 0.5f)
    }

    @Test
    fun `the art sits 12 dp above the foot of the niche`() {
        pieza(Modifier.width(110.dp))
        val hueco = rule.onNodeWithTag("hornacina-hueco", useUnmergedTree = true).getBoundsInRoot()
        val arte = rule.onNodeWithTag("hornacina-arte", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(12f, (hueco.bottom - arte.bottom).value, 0.5f)
    }

    @Test
    fun `the tile sits at the top of its cell with one or two line names`() {
        enEspanol {
            run {
                Row(Modifier.height(240.dp).width(250.dp), verticalAlignment = Alignment.CenterVertically) {
                    HornacinaPieza(
                        imagen = {}, nombre = "Boina", etiqueta = EtiquetaHornacina.PRECIO, textoEtiqueta = null, precio = 70,
                        aro = AroHornacina.NINGUNO, bloqueada = false, tapada = false, alTocar = {},
                        modifier = Modifier.width(110.dp).fillMaxHeight().testTag("uno"),
                    )
                    HornacinaPieza(
                        imagen = {}, nombre = "Sombrero de paja largo largo", etiqueta = EtiquetaHornacina.PRECIO,
                        textoEtiqueta = null, precio = 80,
                        aro = AroHornacina.NINGUNO, bloqueada = false, tapada = false, alTocar = {},
                        modifier = Modifier.width(110.dp).fillMaxHeight().testTag("dos"),
                    )
                }
            }
        }
        val uno = rule.onNodeWithTag("uno").getBoundsInRoot()
        val dos = rule.onNodeWithTag("dos").getBoundsInRoot()
        assertEquals(0f, uno.top.value, 0.5f)
        assertEquals(uno.top.value, dos.top.value, 0.5f)
    }

    @Test
    fun `worn has the water ring and trying the outline`() {
        pieza(Modifier.width(110.dp), aro = AroHornacina.PUESTO)
        val puesto = fotograma()
        assertTrue(cerca(colores.aguaHonda.toArgb(), pixel(puesto, "hornacina-hueco", 1f, 70f)))
        assertTrue(
            "el hueco por dentro sigue siendo la hornacina",
            cerca(colores.hornacina.toArgb(), pixel(puesto, "hornacina-hueco", 6f, 70f)),
        )
    }

    @Test
    fun `trying has the outline ring`() {
        pieza(Modifier.width(110.dp), aro = AroHornacina.PROBANDO)
        assertTrue(cerca(colores.contorno.toArgb(), pixel(fotograma(), "hornacina-hueco", 1f, 70f)))
    }

    @Test
    fun `no ring means plain niche`() {
        pieza(Modifier.width(110.dp))
        assertTrue(cerca(colores.hornacina.toArgb(), pixel(fotograma(), "hornacina-hueco", 1f, 70f)))
    }

    private fun arteSobreHueco(foto: Bitmap) = pixel(foto, "hornacina-arte", 42f, 39f)

    private fun esperado(alfa: Float): Int {
        val h = colores.hornacina.toArgb()

        fun c(v: Int) = (v * (1f - alfa)).toInt()
        return android.graphics.Color.rgb(
            c(android.graphics.Color.red(h)),
            c(android.graphics.Color.green(h)),
            c(android.graphics.Color.blue(h)),
        )
    }

    @Test
    fun `locked image at 55 percent`() {
        pieza(Modifier.width(110.dp), bloqueada = true, etiqueta = EtiquetaHornacina.RACHA, textoEtiqueta = "Racha de 100", precio = null)
        rule.mainClock.advanceTimeBy(400)
        assertTrue(cerca(esperado(0.55f), arteSobreHueco(fotograma()), 16))
    }

    @Test
    fun `covered image at 50 percent keeps its ring`() {
        pieza(Modifier.width(110.dp), tapada = true, aro = AroHornacina.PUESTO, etiqueta = EtiquetaHornacina.TAPADO, precio = null)
        rule.mainClock.advanceTimeBy(400)
        val foto = fotograma()
        assertTrue(cerca(esperado(0.5f), arteSobreHueco(foto), 16))
        assertTrue(cerca(colores.aguaHonda.toArgb(), pixel(foto, "hornacina-hueco", 1f, 70f)))
    }

    @Test
    fun `a normal image is fully opaque`() {
        pieza(Modifier.width(110.dp))
        assertTrue(cerca(Color.Black.toArgb(), arteSobreHueco(fotograma())))
    }

    @Test
    fun `pressed scales to 0 97`() {
        pieza(Modifier.width(110.dp))
        val antes = pixel(fotograma(), "hornacina-hueco", 0f, 70f)
        assertTrue("sin pulsar, el borde es la hornacina", cerca(colores.hornacina.toArgb(), antes))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("item-gorro").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        val pulsado = pixel(fotograma(), "hornacina-hueco", 0f, 70f)
        assertTrue("pulsada, el borde se ha encogido", !cerca(colores.hornacina.toArgb(), pulsado))
        // 110 dp al 0,97: 1,65 dp menos por lado, así que a 3 dp del borde sigue siendo hornacina
        assertTrue(cerca(colores.hornacina.toArgb(), pixel(fotograma(), "hornacina-hueco", 3f, 70f)))
    }

    @Test
    fun `description says name label and price`() {
        pieza(Modifier.width(110.dp), precio = 60)
        val nodo = rule.onNodeWithTag("item-gorro").fetchSemanticsNode()
        assertEquals("Gorro de lana, 60 bitos", nodo.config[SemanticsProperties.ContentDescription].joinToString())
    }

    @Test
    fun `description includes a label and the state`() {
        pieza(Modifier.width(110.dp), etiqueta = EtiquetaHornacina.PUESTO, precio = null, aro = AroHornacina.PUESTO)
        val nodo = rule.onNodeWithTag("item-gorro").fetchSemanticsNode()
        assertEquals("Gorro de lana, Puesto", nodo.config[SemanticsProperties.ContentDescription].joinToString())
        assertEquals("Puesto", nodo.config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `a locked tile describes its streak as state`() {
        pieza(
            Modifier.width(110.dp),
            nombre = "Corona",
            etiqueta = EtiquetaHornacina.RACHA,
            textoEtiqueta = "Racha de 100",
            precio = null,
            bloqueada = true,
        )
        val nodo = rule.onNodeWithTag("item-gorro").fetchSemanticsNode()
        assertEquals("Corona, Racha de 100", nodo.config[SemanticsProperties.ContentDescription].joinToString())
        assertEquals("Racha de 100", nodo.config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `trying says Probando in bold ink and the labels show their texts`() {
        pieza(Modifier.width(110.dp), etiqueta = EtiquetaHornacina.PROBANDO, precio = null, aro = AroHornacina.PROBANDO)
        rule.onNodeWithText("Probando", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the price shows the number next to the coin`() {
        pieza(Modifier.width(110.dp), precio = 60)
        rule.onNodeWithText("60", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `touch target is at least 44 dp and taps call back`() {
        var toques = 0
        pieza(Modifier.width(110.dp), alTocar = { toques++ })
        val caja = rule.onNodeWithTag("item-gorro").getBoundsInRoot()
        assertTrue(caja.height.value >= 44f && caja.width.value >= 44f)
        rule.onNodeWithTag("item-gorro").performClick()
        assertEquals(1, toques)
    }
}
