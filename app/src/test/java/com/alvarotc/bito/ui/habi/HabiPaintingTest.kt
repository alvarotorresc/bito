package com.alvarotc.bito.ui.habi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.min

/**
 * El pintor de capas sobre píxeles reales (spec §9.1 «Pintado»). NATIVE es obligatorio: en LEGACY
 * ni se decodifica el WebP ni existe el bitmap de Compose. No son tests de instantánea: solo fijan
 * dónde hay pintura, qué cambia un tinte y que nada rompe con piezas que no existen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabiPaintingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = 400
    private val plain = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())

    private fun render(
        spec: HabiSpec,
        px: Int = size,
    ): Bitmap = renderHabiBitmap(context, spec, px)

    /** Pinta [layers] tal cual con el mismo pintor que [renderHabiBitmap]. */
    private fun renderLayers(
        layers: List<HabiLayer>,
        px: Int = size,
    ): Bitmap {
        val image = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
            drawHabiLayers(HabiArt.get(context), layers, sampleSizeFor(px.toFloat(), drawHeightDp = 0f))
        }
        return image.asAndroidBitmap()
    }

    /** El píxel de una imagen de [px] de lado donde cae el punto (x, y) en coordenadas del cuerpo. */
    private fun pixelOf(
        x: Float,
        y: Float,
        px: Int = size,
    ): Pair<Int, Int> {
        val fit = rigFit(px.toFloat(), px.toFloat())
        return (fit.originX + x * fit.scale).toInt() to (fit.originY + y * fit.scale).toInt()
    }

    private fun Bitmap.at(p: Pair<Int, Int>) = getPixel(p.first, p.second)

    private fun luminance(c: Int) = 0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)

    private fun hue(c: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        return hsv[0]
    }

    private fun hueDistance(
        a: Float,
        b: Float,
    ): Float = min(abs(a - b), 360f - abs(a - b))

    /** Píxeles con alfa > 128 en la franja de [piece] que queda por encima de la cabeza (y < 0 en el cuerpo). */
    private fun opaqueAboveHead(
        bitmap: Bitmap,
        piece: AtlasPiece,
    ): Int {
        val (x0, y0) = pixelOf(piece.left, piece.top)
        val (x1, y1) = pixelOf(piece.left + piece.width, 0f)
        var count = 0
        for (x in x0.coerceAtLeast(0) until x1.coerceAtMost(size)) {
            for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(size)) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) count++
            }
        }
        return count
    }

    @Test
    fun `renderHabiBitmap returns a non empty image of the requested size`() {
        val bitmap = render(plain)
        assertEquals(size, bitmap.width)
        assertEquals(size, bitmap.height)
        assertTrue(Color.alpha(bitmap.at(pixelOf(HabiAtlas.BODY_WIDTH / 2, HabiAtlas.BODY_HEIGHT * 0.75f))) > 200)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }

    @Test
    fun `the beanie paints above the head where the bare head has nothing`() {
        val beanie = HabiAtlas.pieces.getValue("head-gorro-lana")
        assertTrue("el gorro tiene que asomar por encima del cuerpo", beanie.top < 0f)
        assertTrue(opaqueAboveHead(render(plain.copy(equipped = EquippedSet(head = "head-gorro-lana"))), beanie) > 50)
        assertEquals(0, opaqueAboveHead(render(plain), beanie))
    }

    @Test
    fun `a costume hides the beanie that stays equipped underneath`() {
        // Las astas del reno y el tallo de la calabaza viven por encima de la cabeza: no se cuentan
        // píxeles, se compara con el disfraz solo.
        val costume = plain.copy(equipped = EquippedSet(costume = "costume-reno"))
        val dressed = plain.copy(equipped = EquippedSet(head = "head-gorro-lana", costume = "costume-reno"))
        assertTrue(render(dressed).sameAs(render(costume)))
    }

    @Test
    fun `with a costume no face pixel lands outside the hole`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.CHEERLEADER, EquippedSet(costume = "costume-reno"))
        val layers = habiLayers(spec)
        val hole = HabiAtlas.costumeFaces.getValue("costume-reno").mask
        val full = renderLayers(layers)
        val withoutFace = renderLayers(layers.filter { it.part !in FACE_PARTS })
        val unmasked = renderLayers(layers.map { it.copy(masked = false) })

        val mask = HabiArt.get(context).image(hole.res)!!.asAndroidBitmap()
        val fit = rigFit(size.toFloat(), size.toFloat())
        val margin = 2f / fit.scale

        /** Si el hueco tiene algo de alfa a menos de 2 px del píxel (x, y) de la imagen. */
        fun nearHole(
            x: Int,
            y: Int,
        ): Boolean {
            val bx = (x + 0.5f - fit.originX) / fit.scale
            val by = (y + 0.5f - fit.originY) / fit.scale
            for (ox in -2..2) {
                for (oy in -2..2) {
                    val mx = ((bx + ox * margin / 2 - hole.left) / hole.width * mask.width).toInt()
                    val my = ((by + oy * margin / 2 - hole.top) / hole.height * mask.height).toInt()
                    if (mx in 0 until mask.width && my in 0 until mask.height && Color.alpha(mask.getPixel(mx, my)) > 0) return true
                }
            }
            return false
        }

        var outside = 0
        var faceWouldShow = 0
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (nearHole(x, y)) continue
                outside++
                assertEquals("píxel ($x, $y) fuera del hueco", withoutFace.getPixel(x, y), full.getPixel(x, y))
                if (unmasked.getPixel(x, y) != withoutFace.getPixel(x, y)) faceWouldShow++
            }
        }
        assertTrue(outside > 0)
        // Sin la máscara, los mofletes desplazados caen sobre el traje: el test tiene dientes.
        assertTrue("píxeles de cara fuera del hueco sin máscara: $faceWouldShow", faceWouldShow > 20)
    }

    @Test
    fun `the art cache stays under its byte cap and reuses decoded pieces`() {
        val cap = 2 * 1024 * 1024
        val art = HabiArt(context.resources, maxBytes = cap)
        val body = HabiAtlas.pieces.getValue("cuerpo")
        val first = art.image(body.res, 2)
        assertTrue(first === art.image(body.res, 2))
        HabiAtlas.pieces.values.take(20).forEach { art.image(it.res, 1) }
        assertTrue("caché ${art.cachedBytes} > $cap", art.cachedBytes <= cap)
        assertTrue(art.cachedBytes > 0)
    }

    @Test
    fun `an unknown costume leaves Habi as if nothing were worn`() {
        val future = plain.copy(equipped = EquippedSet(costume = "costume-del-futuro"))
        assertTrue(render(future).sameAs(render(plain)))
    }

    @Test
    fun `an unknown resource decodes to null instead of crashing`() {
        assertEquals(null, HabiArt.get(context).image(0x7f7f7f7f))
    }

    @Test
    fun `the body color changes the hue and keeps which part of the body is lighter`() {
        val salvia = render(plain)
        val cielo = render(plain.copy(equipped = EquippedSet(bodyColor = "body-cielo")))
        val column =
            (250..1300 step 50)
                .map { pixelOf(300f, it.toFloat()) }
                .filter { Color.alpha(salvia.at(it)) == 255 }
        assertTrue(column.size >= 8)

        val shift = column.map { hueDistance(hue(salvia.at(it)), hue(cielo.at(it))) }.average()
        assertTrue("giro medio de tono $shift", shift > 30)

        val lightest = column.maxBy { luminance(salvia.at(it)) }
        val darkest = column.minBy { luminance(salvia.at(it)) }
        assertTrue(luminance(cielo.at(lightest)) > luminance(cielo.at(darkest)))
    }

    @Test
    fun `the eye color tints the iris and nothing outside the eyes`() {
        val tinta = render(plain)
        val azul = render(plain.copy(equipped = EquippedSet(eyeColor = "eyes-azul")))
        val eye = HabiAtlas.pieces.getValue("ojo-abierto-i")
        val (x0, y0) = pixelOf(eye.left, eye.top)
        val (x1, y1) = pixelOf(eye.left + eye.width, eye.top + eye.height)
        var changed = 0
        for (x in x0 until x1) for (y in y0 until y1) if (tinta.getPixel(x, y) != azul.getPixel(x, y)) changed++
        assertTrue(changed > 0)
        val cheek = pixelOf(300f, 900f)
        assertEquals(tinta.at(cheek), azul.at(cheek))
    }

    @Test
    fun `unknown atlas keys are skipped without crashing`() {
        val future = plain.copy(equipped = EquippedSet(head = "head-del-futuro", feet = "feet-nope"))
        assertTrue(render(future).sameAs(render(plain)))
    }

    @Test
    fun `a 96 px render decodes subsampled pieces and still shows Habi`() {
        assertTrue(sampleSizeFor(96f, drawHeightDp = 0f) > 1)
        val small = render(plain, 96)
        assertEquals(96, small.width)
        assertTrue(Color.alpha(small.at(pixelOf(HabiAtlas.BODY_WIDTH / 2, HabiAtlas.BODY_HEIGHT * 0.75f, 96))) > 200)
        val body = HabiAtlas.pieces.getValue("cuerpo")
        val full = HabiArt.get(context).image(body.res, 1)!!
        val sampled = HabiArt.get(context).image(body.res, sampleSizeFor(96f, drawHeightDp = 0f))!!
        assertTrue(sampled.height < full.height)
    }

    /** Mayor alfa de las filas y columnas del borde de [bitmap]. */
    private fun edgeAlpha(bitmap: Bitmap): Map<String, Int> {
        val w = bitmap.width
        val h = bitmap.height
        return mapOf(
            "top" to (0 until w).maxOf { Color.alpha(bitmap.getPixel(it, 0)) },
            "bottom" to (0 until w).maxOf { Color.alpha(bitmap.getPixel(it, h - 1)) },
            "left" to (0 until h).maxOf { Color.alpha(bitmap.getPixel(0, it)) },
            "right" to (0 until h).maxOf { Color.alpha(bitmap.getPixel(w - 1, it)) },
        )
    }

    // Una capa que el marco cortó al extraerla llega al borde de su pieza con pintura opaca (las
    // botas de agua sin suela); un borde de verdad se desvanece.
    @Test
    fun `no accessory or costume piece arrives cut at its edge`() {
        val prefixes = listOf("head-", "face-", "neck-", "feet-", "costume-")
        val pieces = HabiAtlas.pieces.filterKeys { key -> prefixes.any { key.startsWith(it) } }
        assertTrue(pieces.isNotEmpty())
        val cut =
            pieces.mapNotNull { (key, piece) ->
                val image = HabiArt.get(context).image(piece.res, 1)!!.asAndroidBitmap()
                val edges = edgeAlpha(image).filterValues { it >= OPAQUE_EDGE }
                if (edges.isEmpty()) null else "$key ${edges.keys}"
            }
        assertEquals(emptyList<String>(), cut)
    }

    @Test
    fun `the soles of every shoe fit inside the rendered Habi`() {
        for (feet in listOf("feet-calcetines", "feet-botas-agua", "feet-zapatillas")) {
            val shod = render(plain.copy(equipped = EquippedSet(feet = feet)))
            assertTrue("$feet llega cortado al borde de abajo", edgeAlpha(shod).getValue("bottom") < OPAQUE_EDGE)
        }
    }

    private companion object {
        const val OPAQUE_EDGE = 128
    }
}
