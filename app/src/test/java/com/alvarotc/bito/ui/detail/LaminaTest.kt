package com.alvarotc.bito.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.PlantaFicha
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaminaTest {
    @get:Rule
    val rule = createComposeRule()

    private val manzano = PlantaFicha(GardenSpecies.APPLE_TREE, GrowthStage.TREE, 25)

    @Test
    fun `plant size grows with the stage and is capped`() {
        assertEquals(168.dp, tamanoPlanta(GrowthStage.SEED, 0.5f).height) // 140 + 28·1
        assertEquals(280.dp, tamanoPlanta(GrowthStage.TREE, 0.5f).height) // 140 + 28·5, el tope
        assertTrue(tamanoPlanta(GrowthStage.TREE, 2f).width <= 236.dp)
        // Al escalar por el ancho se mantiene la proporción.
        val ancha = tamanoPlanta(GrowthStage.TREE, 2f)
        assertEquals(2f, ancha.width.value / ancha.height.value, 0.001f)
    }

    @Test
    fun `the unit follows the period`() {
        assertEquals(R.plurals.ficha_dias_seguidos, unidadRacha(Period.DAY, CardKind.CHECK, 5, false).actual.res)
        assertEquals(R.plurals.ficha_semanas_seguidas, unidadRacha(Period.WEEK, CardKind.CHECK, 5, false).actual.res)
        assertEquals(R.plurals.ficha_meses_seguidos, unidadRacha(Period.MONTH, CardKind.COUNTER, 5, false).actual.res)
        assertEquals(R.plurals.ficha_dias_limpios, unidadRacha(Period.DAY, CardKind.ABSTINENCE, 5, false).actual.res)
        assertEquals(R.string.ficha_hoy_empieza_otra, unidadRacha(Period.DAY, CardKind.CHECK, 0, true).actual.res)
        assertEquals(R.string.ficha_mejor_ahora, unidadRacha(Period.DAY, CardKind.CHECK, 9, true).mejor.res)
        // La mejor solo es «la de ahora» si pasa de 2.
        assertEquals(R.string.ficha_mejor_racha, unidadRacha(Period.DAY, CardKind.CHECK, 2, true).mejor.res)
        assertEquals(R.string.ficha_mejor_racha, unidadRacha(Period.DAY, CardKind.CHECK, 9, false).mejor.res)
    }

    @Test
    fun `the plate says the unit of the period in words`() {
        rule.setContent {
            ConSistema { Lamina(manzano, 3, WindowStats(3, 4), 3, Period.WEEK, CardKind.CHECK) }
        }
        rule.onNodeWithText("semanas seguidas").assertExists()
        rule.onNodeWithText("mejor racha: la de ahora").assertExists()
    }

    @Test
    fun `a zero streak says today starts another`() {
        rule.setContent { ConSistema { Lamina(manzano, 4, WindowStats(0, 0), 0, Period.DAY, CardKind.CHECK) } }
        rule.onNodeWithText("días seguidos; hoy empieza otra").assertExists()
        // Sin días juzgados, el porcentaje es «—».
        rule.onNodeWithText("—").assertExists()
    }

    @Test
    fun `the plate shows the three data`() {
        rule.setContent { ConSistema { Lamina(manzano, 21, WindowStats(26, 30), 9, Period.DAY, CardKind.CHECK) } }
        rule.onNodeWithText("21").assertExists()
        rule.onNodeWithText("86 %").assertExists()
        rule.onNodeWithText("9").assertExists()
        rule.onNodeWithText("de los últimos 30 días").assertExists()
        rule.onNodeWithText("mejor racha").assertExists()
        rule.onNodeWithText("días seguidos").assertExists()
    }

    @Test
    fun `tapping the plant sways it with sound and a tick`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        rule.setContent { ConSistema(sonido, tacto) { Lamina(manzano, 21, WindowStats(26, 30), 9, Period.DAY, CardKind.CHECK) } }
        rule.onNodeWithTag("lamina-planta").performClick()
        assertEquals(listOf(Snd.PLANTA), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `the arrows leave the plant towards each datum`() {
        val f = flechasLamina(10f, 50f, 200f, 250f, floatArrayOf(0.8f, 0.2f, 0.85f, 0.45f, 0.5f, 0.7f), 258f)
        assertEquals(3, f.size)
        // Salen de la planta y acaban junto al dato, a la altura de cada uno (26 + 16, 112 + 16, 200 + 24).
        assertEquals(10f + 200f * 0.8f, f[0].x1, 0.001f)
        assertEquals(50f + 250f * 0.2f, f[0].y1, 0.001f)
        assertEquals(listOf(42f, 128f, 224f), f.map { it.y2 })
        assertTrue(f.all { it.x2 == 258f })
    }

    @Test
    fun `every species and stage has anchors`() {
        GardenSpecies.entries.forEach { e ->
            GrowthStage.entries.forEach { s -> assertNotNull(AnclasPlanta.tabla["${e.key}-${s.ordinal + 1}"]) }
        }
    }

    @Test
    fun `the plate draws the plant and the three arrows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // La pieza ya en la caché: así la prueba no espera a la precarga en otro hilo.
        HabiArt.get(context).image(GardenArt.plants[GardenSpecies.APPLE_TREE.ordinal][GrowthStage.TREE.ordinal].res)
        lateinit var vista: View
        rule.setContent {
            vista = LocalView.current
            ConSistema { Lamina(manzano, 31, WindowStats(22, 30), 12, Period.DAY, CardKind.CHECK) }
        }
        rule.mainClock.advanceTimeBy(1_500) // las flechas se trazan en unos 700 ms
        rule.waitForIdle()
        // captureToImage no llega a dibujar bajo Robolectric: se dibuja la vista en un bitmap.
        val lamina = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(lamina))

        val cielo = BitoClaro.cielo.toArgb()
        val caja = rule.onNodeWithTag("lamina-planta").fetchSemanticsNode().boundsInRoot
        var distintos = 0
        for (x in caja.left.toInt() until caja.right.toInt()) {
            for (y in caja.top.toInt() until caja.bottom.toInt()) if (lamina.getPixel(x, y) != cielo) distintos++
        }
        assertTrue("la planta no se pinta ($distintos píxeles)", distintos > 200)

        val tinta = BitoClaro.contorno.toArgb()
        val xFin = lamina.width - 146 - 8 // donde acaba cada flecha
        listOf(42, 128, 224).forEach { yFin ->
            var encontrado = false
            for (x in (xFin - 24)..xFin) {
                for (y in (yFin - 14)..(yFin + 14)) if (cerca(lamina.getPixel(x, y), tinta)) encontrado = true
            }
            assertTrue("falta la flecha que acaba en y=$yFin", encontrado)
        }
    }

    private fun cerca(
        a: Int,
        b: Int,
    ): Boolean = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) < 30
}
