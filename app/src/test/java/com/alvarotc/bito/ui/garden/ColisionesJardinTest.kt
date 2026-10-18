package com.alvarotc.bito.ui.garden

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Puerto a JVM de `tienda-jardin-colisiones.py` del mock: cuenta, píxel a píxel y con el alfa del arte
 * real, lo que se pisa en el jardín lleno. Las parcelas con su planta y la palmera sobre sus vecinas
 * son del arte y no cuentan, como en el script.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColisionesJardinTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    // Ancho de la escena de la tienda en el mock, en dp.
    private val ancho = 412f
    private val alto = ancho / GardenArt.SCENE_ASPECT

    // Etapa de cada parcela en el mock (1 a 5): la de la palmera es la 5.
    private val etapas = listOf(4, 4, 3, 5, 5, 2)

    private val lleno =
        GardenState.INITIAL.copy(
            plots = GardenSpecies.entries.mapIndexed { i, s -> GardenPlot(s, GrowthStage.entries[etapas[i] - 1]) },
            specials = GardenSpecial.entries.toSet(),
            objects = setOf("banco", "regadera"),
        )

    private fun mascara(
        res: Int,
        rect: SceneRect,
    ): Array<BooleanArray> {
        val origen = BitmapFactory.decodeResource(resources, res)
        val w = rect.width.roundToInt().coerceAtLeast(1)
        val h = rect.height.roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(origen, w, h, true)
        val lienzo = Array(alto.toInt() + 80) { BooleanArray(ancho.toInt() + 160) }
        val l = rect.left.roundToInt() + 80
        val t = rect.top.roundToInt() + 40
        for (y in 0 until h) {
            for (x in 0 until w) {
                val cy = t + y
                val cx = l + x
                if (cy in lienzo.indices && cx in lienzo[0].indices) lienzo[cy][cx] = (bitmap.getPixel(x, y) ushr 24) > 50
            }
        }
        return lienzo
    }

    // Habi: un óvalo de 80 x 123 con los pies al 88 %, como en el script.
    private fun habi(): Array<BooleanArray> {
        val lienzo = Array(alto.toInt() + 80) { BooleanArray(ancho.toInt() + 160) }
        val cx = ancho * GardenArt.habiSpot.xPct / 100f + 80
        val cy = alto * GardenArt.habiSpot.yPct / 100f - 123f * 0.45f + 40
        for (y in lienzo.indices) {
            for (x in lienzo[0].indices) {
                val dx = (x - cx) / 42f
                val dy = (y - cy) / (123f * 0.47f)
                lienzo[y][x] = dx * dx + dy * dy < 1f
            }
        }
        return lienzo
    }

    private fun cosas(): Map<String, Array<BooleanArray>> {
        val out = linkedMapOf<String, Array<BooleanArray>>()
        for (item in sceneItems(lleno, ancho, alto)) {
            val nombre =
                when (item) {
                    is SceneItem.Soil -> "parcela${item.plot}"
                    is SceneItem.Plant -> "planta${item.plot}"
                    is SceneItem.Special -> item.special.key
                    is SceneItem.Objeto -> item.clave
                }
            out[nombre] = mascara(item.res, item.rect)
        }
        out["Habi"] = habi()
        return out
    }

    private fun pisan(
        a: Array<BooleanArray>,
        b: Array<BooleanArray>,
    ): Int {
        var n = 0
        for (y in a.indices) for (x in a[y].indices) if (a[y][x] && b[y][x]) n++
        return n
    }

    // Parcela con planta y la palmera sobre sus vecinas son del arte (como en el script).
    private fun propios(
        a: String,
        b: String,
    ): Boolean =
        (a.dropLast(1) in setOf("parcela", "planta") && b.dropLast(1) in setOf("parcela", "planta")) ||
            a.startsWith("planta4") ||
            b.startsWith("planta4")

    @Test
    fun `zero pixels of overlap between objects, plots, plants and Habi`() {
        val cosas = cosas()
        val nombres = cosas.keys.toList()
        val choques = mutableListOf<String>()
        for (i in nombres.indices) {
            for (j in i + 1 until nombres.size) {
                val a = nombres[i]
                val b = nombres[j]
                if (propios(a, b)) continue
                val n = pisan(cosas.getValue(a), cosas.getValue(b))
                if (n > 0) choques += "$n px $a con $b"
            }
        }
        assertEquals(choques.joinToString("\n"), emptyList<String>(), choques)
    }

    // Lo que está en pie: los especiales y objetos que no son suelo plano (caminito, estanque).
    private fun enPie(): List<SceneItem> {
        val planos = setOf(GardenSpecial.STONE_PATH, GardenSpecial.POND)
        return sceneItems(lleno, ancho, alto).filter {
            it is SceneItem.Objeto || (it is SceneItem.Special && it.special !in planos)
        }
    }

    @Test
    fun `everything standing keeps 8 dp from the edges in the store and in Today`() {
        val malos = mutableListOf<String>()
        val margen = 8f
        // Hoy pinta la misma escena a la altura de su caja (366 dp) y recorta lo que sobra por los lados.
        val escalaHoy = 366f * GardenArt.SCENE_ASPECT / ancho
        val recorte = (ancho * escalaHoy - ancho) / 2f
        for (item in enPie()) {
            val r = item.rect
            val nombre = (item as? SceneItem.Special)?.special?.key ?: (item as SceneItem.Objeto).clave
            if (r.left < margen) malos += "$nombre izquierda en la tienda: ${r.left}"
            if (r.left + r.width > ancho - margen) malos += "$nombre derecha en la tienda: ${ancho - r.left - r.width}"
            if (r.top + r.height > alto - 2f) malos += "$nombre abajo: ${alto - r.top - r.height}"
            if (r.top < 0f) malos += "$nombre arriba: ${r.top}"
            if (r.left * escalaHoy - recorte < margen) malos += "$nombre izquierda en Hoy: ${r.left * escalaHoy - recorte}"
            if ((r.left + r.width) * escalaHoy - recorte > ancho - margen) {
                malos += "$nombre derecha en Hoy: ${ancho - (r.left + r.width) * escalaHoy + recorte}"
            }
        }
        assertEquals(malos.joinToString("\n"), emptyList<String>(), malos)
    }

    @Test
    fun `bench width is 17 percent and watering can 9 percent`() {
        val objetos = sceneItems(lleno, ancho, alto).filterIsInstance<SceneItem.Objeto>().associateBy { it.clave }
        assertEquals(17f, objetos.getValue("banco").rect.width / ancho * 100f, 0.1f)
        assertEquals(9f, objetos.getValue("regadera").rect.width / ancho * 100f, 0.1f)
    }

    @Test
    fun `bench and watering can stand where the mock puts them`() {
        assertEquals(69f, GardenArt.objectSpots.getValue("banco").xPct, 0f)
        assertEquals(72.5f, GardenArt.objectSpots.getValue("banco").yPct, 0f)
        assertEquals(44f, GardenArt.objectSpots.getValue("regadera").xPct, 0f)
        assertEquals(99f, GardenArt.objectSpots.getValue("regadera").yPct, 0f)
    }

    @Test
    fun `mushrooms are at 71, 97`() {
        val setas = GardenArt.specialSpots.getValue("setas")
        assertEquals(71f, setas.xPct, 0f)
        assertEquals(97f, setas.yPct, 0f)
        assertEquals(13.2f, setas.heightPct, 0f)
        assertTrue(GardenArt.specials.containsKey("setas"))
    }
}
