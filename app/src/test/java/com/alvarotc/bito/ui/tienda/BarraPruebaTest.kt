package com.alvarotc.bito.ui.tienda

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
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
import android.graphics.Color as AndroidColor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarraPruebaTest {
    @get:Rule val rule = createComposeRule()

    private val gorro = BarraPrueba.Comprar("Gorro de lana", 60, 80, null, objeto = false)
    private val sinSaldo = BarraPrueba.SinSaldo("Sombrero de copa", 120, 50, objeto = false)
    private val banco = BarraPrueba.Comprar("Banco", 80, 60, TextoBarra.Plano(R.string.store_bar_garden, listOf(80, 60)), objeto = true)

    private lateinit var vista: View

    private fun enIdioma(
        idioma: String,
        reducido: Boolean = false,
        contenido: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
        val ctx = base.createConfigurationContext(cfg)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                vista = LocalView.current
                ConSistema(reducido = reducido) { contenido() }
            }
        }
    }

    private fun montar(
        barra: BarraPrueba?,
        comprar: String = "Comprar por 60",
        ahora: String = "Ahora no",
        quitar: String = "Quitárselo",
        reducido: Boolean = false,
        alComprar: () -> Unit = {},
        alAhoraNo: () -> Unit = {},
        alQuitar: () -> Unit = {},
    ) {
        enIdioma("es", reducido) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(barra, comprar, ahora, quitar, alComprar, alAhoraNo, alQuitar)
            }
        }
    }

    @Test
    fun `Comprar shows both buttons with the translated texts`() {
        var compras = 0
        var ahoras = 0
        montar(gorro, "Comprar por 60", "Ahora no", alComprar = { compras++ }, alAhoraNo = { ahoras++ })
        rule.onNodeWithTag("barra").assertIsDisplayed()
        rule.onNodeWithText("Gorro de lana").assertIsDisplayed()
        rule.onNodeWithText("60 bitos. Te quedarían 80.").assertIsDisplayed()
        rule.onNodeWithTag("barra-comprar").assertIsDisplayed()
        rule.onNodeWithText("Comprar por 60").assertIsDisplayed()
        rule.onNodeWithText("Ahora no").assertIsDisplayed()
        rule.onNodeWithTag("barra-quitar").assertDoesNotExist()
        rule.onNodeWithTag("barra-comprar").performClick()
        rule.onNodeWithTag("barra-ahora-no").performClick()
        assertEquals(1, compras)
        assertEquals(1, ahoras)
    }

    @Test
    fun `SinSaldo shows only Take it off and no buy button`() {
        var quitas = 0
        montar(sinSaldo, alQuitar = { quitas++ })
        rule.onNodeWithTag("barra-comprar").assertDoesNotExist()
        rule.onNodeWithTag("barra-ahora-no").assertDoesNotExist()
        rule.onNodeWithText("Cuesta 120 bitos y tienes 50: te faltan 70.", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("barra-quitar").performClick()
        rule.onNodeWithText("Quitárselo").assertIsDisplayed()
        assertEquals(1, quitas)
    }

    @Test
    fun `an object bar says Quitarlo and Remove it`() {
        val sin = BarraPrueba.SinSaldo("Regadera", 25, 3, objeto = true)
        enIdioma("es") { BarraPruebaView(sin, "", "", "Quitárselo", {}, {}, {}) }
        rule.onNodeWithText("Quitarlo").assertIsDisplayed()
        rule.onNodeWithText("Quitárselo").assertDoesNotExist()
    }

    @Test
    fun `an object bar says Remove it in English`() {
        val sin = BarraPrueba.SinSaldo("Watering can", 25, 3, objeto = true)
        enIdioma("en") { BarraPruebaView(sin, "", "", "Take it off", {}, {}, {}) }
        rule.onNodeWithText("Remove it").assertIsDisplayed()
    }

    @Test
    fun `NoSeCompra never shows a buy button`() {
        val racha =
            BarraPrueba.NoSeCompra(
                "Corona",
                MotivoNoSeCompra.RACHA,
                TextoBarra.Plano(R.string.store_bar_streak, listOf(100, 31, 69)),
                false,
            )
        montar(racha)
        rule.onNodeWithTag("barra-comprar").assertDoesNotExist()
        rule.onNodeWithTag("barra-ahora-no").assertDoesNotExist()
        rule.onNodeWithTag("barra-quitar").assertIsDisplayed()
        rule.onNodeWithText("Llega con una racha de 100 días. Tu mejor racha es de 31: te faltan 69. No se compra.").assertIsDisplayed()
    }

    @Test
    fun `no bar means nothing on screen`() {
        montar(null)
        rule.onAllNodesWithTag("barra").assertCountEquals(0)
    }

    @Test
    fun `the bar is not inside a modal and has no scrim`() {
        montar(gorro)
        rule.onAllNodes(isDialog()).assertCountEquals(0)
        rule.onAllNodes(isPopup()).assertCountEquals(0)
        // sin velo: lo que hay detrás sigue recibiendo toques fuera de la barra
        val caja = rule.onNodeWithTag("barra").getBoundsInRoot()
        assertTrue(caja.height < 300.dp)
    }

    private fun casos(ingles: Boolean): List<BarraPrueba> {
        fun f(
            es: String,
            en: String,
        ) = if (ingles) en else es
        return listOf(
            BarraPrueba.Comprar(f("Gorro de lana", "Wool hat"), 60, 80, null, false),
            BarraPrueba.SinSaldo(f("Sombrero de copa", "Top hat"), 120, 50, false),
            BarraPrueba.NoSeCompra(
                f("Corona", "Crown"),
                MotivoNoSeCompra.RACHA,
                TextoBarra.Plano(R.string.store_bar_streak, listOf(100, 31, 69)),
                false,
            ),
            BarraPrueba.Comprar(
                f("Calabaza", "Pumpkin"),
                150,
                -10 + 60,
                TextoBarra.Plano(R.string.store_bar_in_season, listOf(150, 50, f("2 de noviembre", "November 2"))),
                false,
            ),
            BarraPrueba.NoSeCompra(
                f("Reno", "Reindeer"),
                MotivoNoSeCompra.TEMPORADA,
                TextoBarra.Plano(R.string.store_bar_out_of_season, listOf(f("1 de diciembre", "December 1"), f("6 de enero", "January 6"))),
                false,
            ),
            BarraPrueba.NoSeCompra(
                f("Dinosaurio", "Dinosaur"),
                MotivoNoSeCompra.TEMPORADA,
                TextoBarra.Plano(
                    R.string.store_bar_out_of_season,
                    listOf(f("el 9 de febrero", "February 9"), f("el 22 de febrero", "February 22")),
                ),
                false,
            ),
            BarraPrueba.NoSeCompra(
                f("Pañuelo brasa", "Ember scarf"),
                MotivoNoSeCompra.LOGRO,
                TextoBarra.Plano(R.string.store_bar_badge, listOf(f("Constancia", "Constancy"), f("racha de 7 días", "a 7-day streak"))),
                false,
            ),
        )
    }

    private fun alturas(idioma: String): List<Float> {
        val ingles = idioma == "en"
        val lista = casos(ingles)
        var indice by mutableStateOf(0)
        enIdioma(idioma) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(
                    lista[indice],
                    if (ingles) "Buy for 150" else "Comprar por 150",
                    if (ingles) "Not now" else "Ahora no",
                    if (ingles) "Take it off" else "Quitárselo",
                    {},
                    {},
                    {},
                )
            }
        }
        return lista.indices.map {
            indice = it
            rule.waitForIdle()
            rule.onNodeWithTag("barra").getBoundsInRoot().height.value
        }
    }

    @Test
    fun `the bar is at most 159 dp in Spanish with the longest case`() {
        val es = alturas("es")
        assertTrue("ES: $es", es.all { it <= 159f })
    }

    @Test
    fun `the bar is at most 159 dp in English with the longest case`() {
        val en = alturas("en")
        assertTrue("EN: $en", en.all { it <= 159f })
    }

    private fun notaCortada(idioma: String): List<Boolean> {
        val lista = casos(idioma == "en")
        var indice by mutableStateOf(0)
        enIdioma(idioma) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(lista[indice], "Comprar por 150", "Ahora no", "Quitárselo", {}, {}, {})
            }
        }
        return lista.indices.map {
            indice = it
            rule.waitForIdle()
            val resultados = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("barra-nota").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
            resultados.single().let { r -> r.didOverflowHeight || r.lineCount > 2 }
        }
    }

    @Test
    fun `no note is cut in Spanish`() {
        assertEquals(List(7) { false }, notaCortada("es"))
    }

    @Test
    fun `no note is cut in English`() {
        assertEquals(List(7) { false }, notaCortada("en"))
    }

    @Test
    fun `buttons do not wrap in English`() {
        val ingles = casos(true)
        var indice by mutableStateOf(0)
        enIdioma("en") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(ingles[indice], "Buy for 150", "Not now", "Take it off", {}, {}, {})
            }
        }
        ingles.indices.forEach { i ->
            indice = i
            rule.waitForIdle()
            listOf("barra-comprar", "barra-ahora-no", "barra-quitar").forEach { tag ->
                val nodos = rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
                nodos.forEach { assertEquals("$tag en el caso $i", 48f, it.size.height / rule.density.density, 0.5f) }
            }
        }
    }

    @Test
    fun `the bar rises with a spring and sinks again`() {
        var barra by mutableStateOf<BarraPrueba?>(null)
        rule.mainClock.autoAdvance = false
        enIdioma("es") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(barra, "Comprar por 60", "Ahora no", "Quitárselo", {}, {}, {})
            }
        }
        rule.onAllNodesWithTag("barra").assertCountEquals(0)
        Snapshot.withMutableSnapshot { barra = gorro }
        rule.mainClock.advanceTimeBy(16)
        rule.onNodeWithTag("barra").assertExists()
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithTag("barra").assertIsDisplayed()
        Snapshot.withMutableSnapshot { barra = null }
        rule.mainClock.advanceTimeBy(3000)
        rule.onAllNodesWithTag("barra").assertCountEquals(0)
    }

    /** Cuánto se ve la barra (0 a 1) sobre un fondo blanco, leído del azul del borde de abajo. */
    private fun visible(): Float {
        // captureToImage no dibuja bajo Robolectric: se dibuja la vista en un bitmap.
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        val azul = AndroidColor.blue(foto.getPixel(foto.width / 2, foto.height - 4)) / 255f
        return ((1f - azul) / (1f - CANTO_AZUL)).coerceIn(0f, 1f)
    }

    private fun montarSobreBlanco(
        reducido: Boolean,
        barra: () -> BarraPrueba?,
    ) {
        rule.mainClock.autoAdvance = false
        enIdioma("es", reducido) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.BottomCenter) {
                BarraPruebaView(barra(), "Comprar por 60", "Ahora no", "Quitárselo", {}, {}, {})
            }
        }
    }

    @Test
    fun `reduced fades in 150 ms`() {
        var barra by mutableStateOf<BarraPrueba?>(null)
        montarSobreBlanco(reducido = true) { barra }
        Snapshot.withMutableSnapshot { barra = gorro }
        rule.mainClock.advanceTimeBy(16)
        val alInicio = visible()
        rule.mainClock.advanceTimeBy(60)
        val aMedias = visible()
        rule.mainClock.advanceTimeBy(150)
        val alFinal = visible()
        assertTrue("empieza casi transparente: $alInicio", alInicio < 0.3f)
        assertTrue("a medias: $aMedias", aMedias in 0.05f..0.95f)
        assertEquals(1f, alFinal, 0.02f)
    }

    private companion object {
        const val CANTO_AZUL = 0xD3 / 255f
    }
}
