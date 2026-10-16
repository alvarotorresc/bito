package com.alvarotc.bito.ui.tienda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.Color as ColorCompose

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EscenaArmarioTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val luz = mutableStateOf(0f)
    private val sonda = SondaSalto()
    private lateinit var estado: EscenaArmarioEstado
    private lateinit var scope: CoroutineScope
    private lateinit var vista: View

    private fun montar(
        reducido: Boolean = false,
        capa: @Composable BoxScope.() -> Unit = {},
    ) {
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                scope = rememberCoroutineScope()
                vista = LocalView.current
                estado = remember { EscenaArmarioEstado() }.also { it.sonda = sonda }
                Box(Modifier.width(412.dp)) {
                    EscenaArmario(
                        look = EquippedSet(),
                        luz = { luz.value },
                        estado = estado,
                        capaSuperior = capa,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the niche is 332 by 236 with the arch radii and Habi is 165`() {
        montar()
        val nicho = rule.onNodeWithTag("tnicho").getUnclippedBoundsInRoot()
        assertEquals(332f, nicho.width.value, 0.5f)
        assertEquals(236f, nicho.height.value, 0.5f)
        val escena = rule.onNodeWithTag("tscene").getUnclippedBoundsInRoot()
        assertEquals(300f, escena.height.value, 0.5f)
        // Margen de la hornacina: 58 arriba, 40 a cada lado, 6 abajo.
        assertEquals(58f, nicho.top.value - escena.top.value, 0.5f)
        assertEquals(40f, nicho.left.value - escena.left.value, 0.5f)
        assertEquals(6f, escena.bottom.value - nicho.bottom.value, 0.5f)
        assertEquals(165.2f, MedidasArmario.habiCuerpo(nicho.height.value), 0.1f)
    }

    @Test
    fun `the crust is 6 dp below the 300 dp scene`() {
        montar()
        val costra = rule.onNodeWithTag("tcostra").getUnclippedBoundsInRoot()
        val escena = rule.onNodeWithTag("tscene").getUnclippedBoundsInRoot()
        assertEquals(6f, costra.height.value, 0.2f)
        assertEquals(escena.bottom.value, costra.top.value, 0.2f)
    }

    @Test
    fun `Habi's body is 165 dp tall and her feet stand at 90 percent of the niche`() {
        val caja = MedidasArmario.cajaHabi(332f, 236f)
        assertEquals(165.2f, MedidasArmario.habiCuerpo(236f), 0.1f)
        // pies al 90 %: el cuerpo (BODY_HEIGHT) termina a 0,9 de alto
        val k = 165.2f / com.alvarotc.bito.ui.habi.HabiAtlas.BODY_HEIGHT
        val pies = caja.top + (-com.alvarotc.bito.ui.habi.HabiAtlas.canvas.top + com.alvarotc.bito.ui.habi.HabiAtlas.BODY_HEIGHT) * k
        assertEquals(236f * 0.9f, pies, 0.5f)
        assertEquals(
            332f / 2f,
            caja.left + (-com.alvarotc.bito.ui.habi.HabiAtlas.canvas.left + com.alvarotc.bito.ui.habi.HabiAtlas.BODY_WIDTH / 2f) * k,
            0.5f,
        )
    }

    @Test
    fun `the light multiplies only the scene, not the soil below`() {
        luz.value = 1f
        montar()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        // captureToImage no llega a dibujar bajo Robolectric: se dibuja la vista en un bitmap.
        val lamina = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(lamina))
        val nicho = rule.onNodeWithTag("tnicho").fetchSemanticsNode().boundsInRoot
        val costra = rule.onNodeWithTag("tcostra").fetchSemanticsNode().boundsInRoot
        // Abajo de la hornacina, a un lado de Habi: su fondo (#CDAE8B) multiplicado por el degradado de noche.
        val p = lamina.getPixel((nicho.left + 12f).toInt(), (nicho.bottom - 6f).toInt())
        assertTrue("el armario de noche es más oscuro que su color de día", Color.red(p) < 0xCD / 2)
        assertTrue("y se queda azulado", Color.blue(p) > Color.red(p))
        // La costra de abajo conserva su color #B8956F.
        val c = lamina.getPixel(((costra.left + costra.right) / 2f).toInt(), ((costra.top + costra.bottom) / 2f).toInt())
        assertEquals(0xB8, Color.red(c))
        assertEquals(0x95, Color.green(c))
        assertEquals(0x6F, Color.blue(c))
    }

    @Test
    fun `at noon the multiplied light leaves the niche color as it was`() {
        luz.value = 0f
        montar()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        val lamina = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(lamina))
        val nicho = rule.onNodeWithTag("tnicho").fetchSemanticsNode().boundsInRoot
        val p = lamina.getPixel((nicho.left + 12f).toInt(), (nicho.bottom - 6f).toInt())
        assertEquals(0xCD, Color.red(p))
        assertEquals(0xAE, Color.green(p))
        assertEquals(0x8B, Color.blue(p))
    }

    @Test
    fun `the light is read at draw time, not in composition`() {
        var composiciones = 0
        rule.setContent {
            ConSistema(sonido, tacto) {
                composiciones++
                val e = remember { EscenaArmarioEstado() }
                EscenaArmario(look = EquippedSet(), luz = { luz.value }, estado = e)
            }
        }
        rule.waitForIdle()
        val antes = composiciones
        luz.value = 0.5f
        rule.waitForIdle()
        luz.value = 1f
        rule.waitForIdle()
        assertEquals(antes, composiciones)
    }

    @Test
    fun `the top layer is drawn inside the niche`() {
        montar(capa = { Box(Modifier.fillMaxSize().background(ColorCompose.Red).testTag("capa")) })
        rule.onNodeWithTag("capa").assertExists()
        val capa = rule.onNodeWithTag("capa").getUnclippedBoundsInRoot()
        val nicho = rule.onNodeWithTag("tnicho").getUnclippedBoundsInRoot()
        assertEquals(nicho.width.value, capa.width.value, 0.5f)
    }

    @Test
    fun `jump plays the system jump with the given amplitude`() {
        montar()
        estado.saltar(0.2f)
        rule.waitUntil(5_000) { sonda.picoFraccion > 0f }
        rule.waitForIdle()
        assertEquals(0.2f, sonda.picoFraccion, 0.01f)
    }

    @Test
    fun `reduced motion does not jump`() {
        montar(reducido = true)
        estado.saltar(0.2f)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        assertEquals(0f, sonda.picoFraccion, 0f)
    }

    @Test
    fun `tapping Habi jumps and plays HABI and TICK`() {
        montar()
        rule.onNodeWithTag("tnicho").performClick()
        rule.waitUntil(5_000) { sonda.picoFraccion > 0f }
        assertTrue(Snd.HABI in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
    }

    @Test
    fun `squash never goes negative`() {
        montar()
        val escala = mutableListOf<Float>()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { estado.aplastar(pct = 0.06f, ida = -80, vuelta = -240) } }
        repeat(10) {
            rule.mainClock.advanceTimeByFrame()
            escala += estado.escalaY
        }
        assertTrue(escala.all { it.isFinite() && it > 0f && it <= 1f })
        assertEquals(1f, estado.escalaY, 0.0001f)
    }

    @Test
    fun `a squash past 100 percent is held below zero height`() {
        montar()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { estado.aplastar(pct = 3f, ida = 80, vuelta = 240) } }
        val escala = mutableListOf<Float>()
        repeat(30) {
            rule.mainClock.advanceTimeByFrame()
            escala += estado.escalaY
        }
        assertTrue(escala.all { it > 0f })
    }

    @Test
    fun `squash goes down by pct and back`() {
        montar()
        rule.mainClock.autoAdvance = false
        val escala = mutableListOf(1f)
        rule.runOnIdle { scope.launch { estado.aplastar(pct = 0.06f, ida = 80, vuelta = 240) } }
        repeat(30) {
            rule.mainClock.advanceTimeByFrame()
            escala += estado.escalaY
        }
        assertEquals(0.94f, escala.min(), 0.01f)
        assertEquals(1f, estado.escalaY, 0.001f)
    }

    @Test
    fun `a second squash interrupts the first`() {
        montar()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { estado.aplastar(pct = 0.2f, ida = 400, vuelta = 400) } }
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        rule.runOnIdle { scope.launch { estado.aplastar(pct = 0.04f, ida = 40, vuelta = 80) } }
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(1f, estado.escalaY, 0.001f)
    }
}
