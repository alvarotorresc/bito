package com.alvarotc.bito.ui.habi

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.garden.GardenArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El arte exportado llega al APK y se decodifica. NATIVE es obligatorio: en LEGACY BitmapFactory
 * devuelve bitmaps falsos sin píxeles y los tests de pintado no podrían mirar nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabiArtResourcesTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun decodes(res: Int): Boolean {
        val bitmap = BitmapFactory.decodeResource(resources, res) ?: return false
        return bitmap.width > 0 && bitmap.height > 0
    }

    @Test
    fun `every atlas piece and iris mask decodes with alpha`() {
        for ((key, piece) in HabiAtlas.pieces) {
            assertTrue(key, decodes(piece.res))
            piece.iris?.let { assertTrue("$key iris", decodes(it)) }
        }
        val body = BitmapFactory.decodeResource(resources, HabiAtlas.pieces.getValue("cuerpo").res)
        assertTrue(body.hasAlpha())
        assertEquals(0, android.graphics.Color.alpha(body.getPixel(0, 0)))
    }

    @Test
    fun `the face, mark and brow keys habiLayers needs are all in the atlas`() {
        val eyes =
            listOf("abierto", "cerrado", "feliz", "triste", "enfadado", "lloroso")
                .flatMap { listOf("ojo-$it-i", "ojo-$it-d") }
        val mouths = listOf("sonrisa", "feliz", "neutra", "triste", "o", "mueca").map { "boca-$it" }
        val marks = listOf("pintura-i-a", "pintura-i-b", "pintura-d-a", "pintura-d-b", "mofletes-i", "mofletes-d")
        val brows = listOf("dura", "alegre", "preocupada").flatMap { listOf("ceja-$it-i", "ceja-$it-d") }
        for (key in listOf("cuerpo", "sombra") + eyes + mouths + marks + brows) {
            assertNotNull(key, HabiAtlas.pieces[key])
        }
    }

    @Test
    fun `the rig canvas contains every piece`() {
        val canvas = HabiAtlas.canvas
        for ((key, p) in HabiAtlas.pieces) {
            assertTrue(key, p.left >= canvas.left - 0.01f && p.top >= canvas.top - 0.01f)
            assertTrue(key, p.left + p.width <= canvas.left + canvas.width + 0.01f)
            assertTrue(key, p.top + p.height <= canvas.top + canvas.height + 0.01f)
        }
    }

    @Test
    fun `every garden resource decodes and the garden has six of everything`() {
        assertTrue(decodes(GardenArt.background))
        assertEquals(6, GardenArt.plots.size)
        assertEquals(6, GardenArt.plotSpots.size)
        assertEquals(6, GardenArt.plants.size)
        GardenArt.plants.forEach { assertEquals(5, it.size) }
        assertEquals(setOf("farolillo", "caminito", "estanque", "setas", "cerezo-grande", "arbol-dorado"), GardenArt.specials.keys)
        assertEquals(GardenArt.specials.keys, GardenArt.specialSpots.keys)
        (GardenArt.plots + GardenArt.plants.flatten() + GardenArt.specials.values).forEach { assertTrue(decodes(it.res)) }
    }

    @Test
    fun `the plant species follow the order of GardenSpecies`() {
        assertEquals(GardenSpecies.entries.map { it.key }, GardenArt.plantKeys)
    }

    @Test
    fun `every accessory and costume of the catalog has its painted piece`() {
        val painted =
            setOf(
                CustomizationCategory.HEAD,
                CustomizationCategory.FACE,
                CustomizationCategory.NECK,
                CustomizationCategory.FEET,
                CustomizationCategory.COSTUME,
            )
        for (item in HabiCatalog.all.filter { it.category in painted }) {
            assertNotNull(item.id, HabiAtlas.pieces[item.id])
        }
    }

    @Test
    fun `every costume moves the face and clips it with a hole mask that decodes`() {
        val costumes = HabiCatalog.all.filter { it.category == CustomizationCategory.COSTUME }.map { it.id }
        assertEquals(costumes.toSet(), HabiAtlas.costumeFaces.keys)
        for ((key, face) in HabiAtlas.costumeFaces) {
            assertTrue("$key mask", decodes(face.mask.res))
            assertTrue("$key mask size", face.mask.width > 0f && face.mask.height > 0f)
        }
    }
}
