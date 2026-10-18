package com.alvarotc.bito.ui.tienda.habi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.ui.sistema.BitoColores
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.estadoTienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RaizRanurasTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private lateinit var vista: View
    private lateinit var colores: BitoColores
    private val activa = mutableStateOf(RanuraHabi.CABEZA)
    private val pulsadas = mutableListOf<RanuraHabi>()

    private fun raiz(reducido: Boolean = false) {
        // Reloj a mano: con el automático, en la suite entera el sandbox de estas pruebas deja de quedar en reposo.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistemaLigero(sonido, tacto, reducido) {
                vista = LocalView.current
                colores = BitoTema.colores
                Box(Modifier.fillMaxSize()) {
                    RaizRanuras(activa.value, paddingSuperior = 56.dp, alPulsar = { pulsadas += it })
                }
            }
        }
        // varias vueltas: medir, escribir el estado y recomponer con él no cabe en un solo fotograma
        repeat(4) {
            rule.mainClock.advanceTimeBy(250)
            rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        }
    }

    private fun fotograma(): Bitmap {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        return foto
    }

    private fun pixel(
        foto: Bitmap,
        tag: String,
        xDp: Float,
        yDp: Float,
    ): Int {
        val caja = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return foto.getPixel((caja.left + xDp * rule.density.density).toInt(), (caja.top + yDp * rule.density.density).toInt())
    }

    private fun cerca(
        a: Int,
        b: Int,
        margen: Int = 14,
    ) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < margen &&
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < margen &&
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < margen

    @Test
    fun `seven buttons of 56 by 48 with a 40 dp node`() {
        raiz()
        RanuraHabi.entries.forEach {
            val boton = rule.onNodeWithTag("raiz-${it.id}", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(56f, boton.width.value, 0.5f)
            assertEquals(48f, boton.height.value, 0.5f)
            val nudo = rule.onNodeWithTag("nudo-${it.id}", useUnmergedTree = true).getBoundsInRoot()
            assertEquals(40f, nudo.width.value, 0.5f)
            assertEquals(40f, nudo.height.value, 0.5f)
        }
    }

    @Test
    fun `the visible slot fills with water`() {
        raiz()
        rule.mainClock.advanceTimeBy(400)
        val foto = fotograma()
        // a 6 dp del borde superior del nudo, dentro del círculo y fuera del dibujo de la pieza
        assertTrue(cerca(colores.agua.toArgb(), pixel(foto, "nudo-cabeza", 20f, 5f)))
        assertTrue(cerca(colores.tAlta.toArgb(), pixel(foto, "nudo-cara", 20f, 5f)))
        rule.runOnIdle {
            activa.value = RanuraHabi.CUELLO
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeBy(400)
        val despues = fotograma()
        assertTrue(cerca(colores.tAlta.toArgb(), pixel(despues, "nudo-cabeza", 20f, 5f)))
        assertTrue(cerca(colores.agua.toArgb(), pixel(despues, "nudo-cuello", 20f, 5f)))
    }

    @Test
    fun `tapping a node plays DESCENSO and TICK`() {
        raiz()
        rule.onNodeWithTag("raiz-pies").performClick()
        assertEquals(listOf(Snd.DESCENSO), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
        assertEquals(listOf(RanuraHabi.PIES), pulsadas)
    }

    @Test
    fun `press scales to 0 94`() {
        raiz()
        rule.mainClock.advanceTimeBy(400)
        val antes = pixel(fotograma(), "nudo-cara", 0.8f, 20f)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("raiz-cara").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        val pulsado = pixel(fotograma(), "nudo-cara", 0.8f, 20f)
        // pulsado, el círculo se encoge 1,2 dp por lado: a 0,8 dp del borde ya no hay nudo
        assertTrue(!cerca(antes, pulsado, 4))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaltoRaizTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val lista = LazyListState()

    private fun tira(reducido: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistemaLigero(sonido, tacto, reducido) {
                Box(Modifier.fillMaxSize()) { TiraHabi(estadoTienda(saldo = 140), alTocar = {}, lista = lista) }
            }
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private val destino =
        construirTira(false, null, false, com.alvarotc.bito.domain.model.EquippedSet())
            .indexOfFirst { it is EntradaTira.Cabecera && it.ranura == RanuraHabi.PIES }

    @Test
    fun `tapping a node scrolls 420 ms and plays DESCENSO and TICK`() {
        tira(reducido = false)
        rule.onNodeWithTag("raiz-pies").performClick()
        assertEquals(listOf(Snd.DESCENSO), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
        rule.mainClock.advanceTimeBy(100)
        assertTrue("a los 100 ms aún va de camino", lista.firstVisibleItemIndex < destino)
        rule.mainClock.advanceTimeBy(200)
        assertTrue("a los 300 ms aún no ha llegado", lista.firstVisibleItemIndex < destino || lista.firstVisibleItemScrollOffset > 0)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertEquals(destino, lista.firstVisibleItemIndex)
        assertEquals(0, lista.firstVisibleItemScrollOffset)
    }

    @Test
    fun `reduced jumps`() {
        tira(reducido = true)
        rule.onNodeWithTag("raiz-pies").performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        assertEquals(destino, lista.firstVisibleItemIndex)
        assertEquals(listOf(Snd.DESCENSO), sonido.snds())
    }
}
