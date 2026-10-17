package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.Haptica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val FALTAN_60 = "Te faltan 60 bitos. Cada hábito hecho da 1 y un día completo, 3 más."

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BotonCongeladorTest {
    @get:Rule val rule = createComposeRule()
    private val banco = Banco(rule)

    private var saldo by mutableIntStateOf(140)
    private var compras = 0

    private fun boton(
        saldoInicial: Int,
        alComprar: () -> Unit = { compras++ },
    ) {
        saldo = saldoInicial
        banco.colocar(ancho = 372) { BotonCongelador(precio = 100, saldo = saldo, alComprar = alComprar) }
    }

    private fun estiloLinea(): androidx.compose.ui.text.TextStyle {
        val capas = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("tc-linea").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(capas) }
        return capas.single().layoutInput.style
    }

    @Test
    fun `the button is 60 dp high, pressed scales to 0,97 in 140 ms`() {
        boton(140)
        val caja = rule.onNodeWithTag("tc-buy").getBoundsInRoot()
        assertEquals(60f, caja.height.value, 0.5f)
        assertEquals(372f, caja.width.value, 0.5f)

        // El borde izquierdo del botón, leído en la fila del centro: pulsado se mete 372 x 1,5 % = 5,6 dp.
        fun bordeIzquierdo(): Int {
            val foto = banco.fotograma()
            val caja = rule.onNodeWithTag("tc-buy").fetchSemanticsNode().boundsInRoot
            val y = (caja.top + caja.height / 2).toInt()
            return (0 until foto.width).first { foto.getPixel(it, y) != android.graphics.Color.WHITE }
        }
        val reposo = bordeIzquierdo()
        rule.onNodeWithTag("tc-buy").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val pulsado = bordeIzquierdo()
        assertEquals("pulsado se encoge a 0,97", 372f * (1f - 0.97f) / 2f, (pulsado - reposo) / rule.density.density, 1f)
    }

    @Test
    fun `with balance the line says what remains`() {
        boton(140)
        rule.onNodeWithTag("tc-linea").assertTextEquals("100 bitos. Te quedarían 40.")
        rule.onNodeWithTag("tc-buy").performClick()
        assertEquals(1, compras)
    }

    @Test
    fun `without balance the line says what is missing and no progress dots`() {
        boton(40)
        rule.onNodeWithTag("tc-linea").assertTextEquals(FALTAN_60)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        rule.onNodeWithTag("tc-buy").assert(
            SemanticsMatcher("la descripción dice lo que falta") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().contains("Te faltan 60 bitos")
            },
        )
        val normal = estiloLinea()
        assertEquals(FontWeight.Normal, normal.fontWeight)
        assertEquals(banco.colores.tintaSuave, normal.color)
    }

    @Test
    fun `tapping without balance shakes -6 5 -2 in 260 ms, bolds the line, plays REJECT and no sound`() {
        boton(40)
        val reposo = rule.onNodeWithText("Un congelador", useUnmergedTree = true).getBoundsInRoot().left.value
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("tc-buy").performClick()

        fun desplazamiento(): Float = rule.onNodeWithText("Un congelador", useUnmergedTree = true).getBoundsInRoot().left.value - reposo
        // El temblor arranca en el primer fotograma tras el toque: se muestrea cada fotograma (16 ms) durante 350 ms.
        val muestras =
            (0 until 22).map {
                rule.mainClock.advanceTimeBy(16)
                desplazamiento()
            }
        val menor = muestras.min()
        val iMenor = muestras.indexOf(menor)
        val mayor = muestras.drop(iMenor).max()
        val iMayor = iMenor + muestras.drop(iMenor).indexOf(mayor)
        val luego = muestras.drop(iMayor).min()
        assertEquals("primero -6 dp", -6f, menor, 0.8f)
        assertEquals("luego 5 dp", 5f, mayor, 0.8f)
        assertEquals("luego -2 dp", -2f, luego, 0.8f)
        assertTrue(
            "el tramo entero dura unos 260 ms: ${muestras.map { (it * 10).toInt() / 10f }}",
            (
                muestras.indexOfLast {
                    abs(it) > 0.3f
                } + 1
            ) * 16 in 240..330,
        )
        assertEquals("vuelve a su sitio", 0f, muestras.last(), 0.3f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(0, compras)
        assertEquals(listOf(Haptica.REJECT), banco.tacto.hapticas)
        assertTrue(banco.sonido.sonados.isEmpty())
        val tocado = estiloLinea()
        assertEquals(FontWeight.Bold, tocado.fontWeight)
        assertEquals(banco.colores.tinta, tocado.color)
        // Sin saldo el botón queda a 0,5 (canto sobre blanco) y sin rojo.
        val medio = banco.pixel(banco.fotograma(), "tc-buy", 186f, 6f)
        val esperado =
            banco.colores.canto.toArgb().let { c ->
                android.graphics.Color.rgb(
                    (android.graphics.Color.red(c) + 255) / 2,
                    (android.graphics.Color.green(c) + 255) / 2,
                    (android.graphics.Color.blue(c) + 255) / 2,
                )
            }
        assertTrue("opacidad 0,5", cerca(esperado, medio, 10))
    }

    @Test
    fun `with 100 exactly it is enabled and goes to 0,5 afterwards`() {
        boton(100) {
            compras++
            saldo = 0
        }
        rule.onNodeWithTag("tc-linea").assertTextEquals("100 bitos. Te quedarían 0.")
        assertTrue("a pleno, sin atenuar", cerca(banco.colores.canto.toArgb(), banco.pixel(banco.fotograma(), "tc-buy", 186f, 6f), 8))
        rule.onNodeWithTag("tc-buy").performClick()
        rule.waitForIdle()
        assertEquals(1, compras)
        assertTrue(banco.tacto.hapticas.isEmpty())
        rule.onNodeWithTag("tc-linea").assertTextEquals(
            "Te faltan 100 bitos. Cada hábito hecho da 1 y un día completo, 3 más.",
        )
        assertFalse("ya atenuado", cerca(banco.colores.canto.toArgb(), banco.pixel(banco.fotograma(), "tc-buy", 186f, 6f), 8))
    }

    private fun pistaEn(
        idioma: String,
        voz: Personality,
    ): String = recursos(idioma).getString(pistaDe(voz))

    @Test
    fun `the catalog body shows the hint it is given for each voice`() {
        var voz by mutableStateOf(Personality.NEUTRA)
        banco.colocar { CatalogoCongeladores(voz, congeladores = 2, saldo = 140, precio = 100, alComprar = {}) }
        rule.onNodeWithTag("tc-pista").assertTextEquals(
            "Un congelador hace que la racha salte un día en vez de romperse. Lo usas tú desde el calendario de un día fallado.",
        )
        voz = Personality.SARGENTO
        rule.onNodeWithTag("tc-pista").assertTextEquals(
            "Un congelador, un hábito, un día. Lo usas tú desde el calendario de un día fallado. No los malgastes.",
        )
        voz = Personality.CHEERLEADER
        rule.onNodeWithTag("tc-pista").assertTextEquals(
            "¡Un congelador te salva la racha si fallas un día! Lo usas tú desde el calendario de un día fallado.",
        )
    }

    @Test
    fun `Neutral has no exclamation, Cheerleader a single pair and Sergeant none`() {
        for (idioma in listOf("es", "en")) {
            val neutra = pistaEn(idioma, Personality.NEUTRA)
            val sargento = pistaEn(idioma, Personality.SARGENTO)
            val animadora = pistaEn(idioma, Personality.CHEERLEADER)
            assertEquals(idioma, 0, neutra.count { it == '!' || it == '¡' })
            assertEquals(idioma, 0, sargento.count { it == '!' || it == '¡' })
            assertEquals(idioma, 1, animadora.count { it == '!' })
            assertEquals(idioma, if (idioma == "es") 1 else 0, animadora.count { it == '¡' })
        }
    }

    @Test
    fun `the text no longer says it spends itself`() {
        for (idioma in listOf("es", "en")) {
            for (voz in Personality.entries) {
                val t = pistaEn(idioma, voz).lowercase()
                assertFalse("$idioma $voz", t.contains("se gasta solo") || t.contains("spends itself") || t.contains("automatic"))
                assertTrue("$idioma $voz: lo usas tú", t.contains("tú") || t.contains("you use it yourself"))
            }
        }
    }

    @Test
    fun `the Sergeant and Cheerleader hints say a day not done and not a red day`() {
        val es = recursos("es")
        val en = recursos("en")
        for (id in listOf(R.string.freezer_info_sargento, R.string.freezer_info_cheerleader)) {
            val textoEs = es.getString(id, "Ana")
            val textoEn = en.getString(id, "Ana")
            assertTrue(textoEs, textoEs.contains("día sin hacer") && !textoEs.contains("en rojo"))
            assertTrue(textoEn, textoEn.contains("missed day") && !textoEn.contains("red day"))
        }
    }
}
