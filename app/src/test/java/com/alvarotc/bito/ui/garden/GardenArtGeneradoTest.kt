package com.alvarotc.bito.ui.garden

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `GardenArt.kt` es un archivo generado por `tools/arte/exportar.py`: editarlo a mano se pierde al
 * regenerar. Estas pruebas lo atan a `tools/arte/jardin.json` y al arte que de verdad hay en `res`;
 * la comparación del texto entero con lo que emite el script está en `tools/arte/tests/test_objetos.py`
 * (necesita las fuentes del arte, que no están en CI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GardenArtGeneradoTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun archivo(ruta: String): File =
        listOf("..", ".").map { File(it, ruta) }.firstOrNull { it.exists() } ?: error("no encuentro $ruta")

    private val jardin = archivo("tools/arte/jardin.json").readText()
    private val garden = archivo("app/src/main/java/com/alvarotc/bito/ui/garden/GardenArt.kt").readText()

    // Los tres números de un sitio de `jardin.json`: "clave": [x, y, alto].
    private fun sitioDelJson(
        seccion: String,
        clave: String,
    ): List<Float> {
        val bloque = jardin.substringAfter("\"escena\"").substringAfter("\"$seccion\"")
        val numeros = bloque.substringAfter("\"$clave\": [").substringBefore("]")
        return numeros.split(",").map { it.trim().toFloat() }
    }

    @Test
    fun `GardenArt keeps the header of a generated file`() {
        assertTrue(garden.startsWith("// Generado por tools/arte/exportar.py. No editar a mano."))
    }

    @Test
    fun `GardenArt matches what exportar generates for every spot`() {
        for ((clave, sitio) in GardenArt.specialSpots) {
            val json = sitioDelJson("especiales", clave)
            assertEquals(clave, listOf(sitio.xPct, sitio.yPct, sitio.heightPct), json)
        }
        for ((clave, sitio) in GardenArt.objectSpots) {
            val json = sitioDelJson("objetos", clave)
            assertEquals(clave, listOf(sitio.xPct, sitio.yPct, sitio.heightPct), json)
        }
        assertEquals(setOf("banco", "regadera"), GardenArt.objectSpots.keys)
        assertEquals(GardenArt.objectSpots.keys, GardenArt.objectPieces.keys)
    }

    @Test
    fun `banco and regadera pieces exist as resources`() {
        for ((clave, pieza) in GardenArt.objectPieces) {
            val bitmap = BitmapFactory.decodeResource(resources, pieza.res)
            assertNotNull(clave, bitmap)
            assertTrue(clave, bitmap.hasAlpha())
            assertEquals("$clave: el aspecto es el del recurso", bitmap.width.toFloat() / bitmap.height, pieza.aspect, 0.002f)
        }
        assertEquals(com.alvarotc.bito.R.drawable.garden_banco, GardenArt.objectPieces.getValue("banco").res)
        assertEquals(com.alvarotc.bito.R.drawable.garden_regadera, GardenArt.objectPieces.getValue("regadera").res)
    }
}
