package com.alvarotc.bito.ui.cierre

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.stats.BadgeStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El sello del cierre (§5.9 punto 5, §7.5): lo que dice y cómo brota capa a capa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SelloCierreTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val logro = BadgeCatalog.all.first()

    private fun datos(
        perfecto: Boolean = false,
        rachas: Int = 2,
        logros: Boolean = true,
        gordo: List<String> = emptyList(),
        animo: Int? = null,
        nombre: String = "Ana",
    ) = DatosSello(
        perfecto = perfecto,
        hechos = 3,
        total = 4,
        bitos = 7,
        rachas = rachas,
        logros = if (logros) listOf(logro) else emptyList(),
        gordo = gordo,
        voz = Personality.NEUTRA,
        nombre = nombre,
        animo = animo,
        dia = 20_300,
    )

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private var capas by mutableStateOf(emptySet<CapaSello>())

    private fun montar(d: DatosSello) {
        rule.setContent { ConSistema(sonido, tacto) { SelloCierre(d, capas) } }
        rule.waitForIdle()
    }

    @Test
    fun `each layer sprouts when its moment ends`() {
        montar(datos())
        // La base está al sellar; las capas que esperan a su momento, no.
        assertTrue(hay("cierre-sello-bitos"))
        assertFalse(hay("cierre-sello-rachas"))
        assertFalse(hay("cierre-sello-logros"))
        assertTrue(sonido.snds().isEmpty())

        capas = setOf(CapaSello.RACHAS) // termina la veta de racha
        rule.waitForIdle()
        assertTrue(hay("cierre-sello-rachas"))
        assertFalse(hay("cierre-sello-logros"))
        assertEquals(listOf(Snd.BROTE), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)

        capas = setOf(CapaSello.RACHAS, CapaSello.LOGROS) // termina el logro
        rule.waitForIdle()
        assertTrue(hay("cierre-sello-logros"))
        rule.onNodeWithText(rule.activity.getString(BadgeStrings.badgeNameRes(logro.id)), substring = true).assertExists()
        assertEquals(listOf(Snd.BROTE, Snd.BROTE), sonido.snds())
    }

    @Test
    fun `without a name the seal still speaks, in a phrase that asks for none`() {
        var d by mutableStateOf(datos(nombre = ""))
        rule.setContent { ConSistema(sonido, tacto) { SelloCierre(d, capas) } }
        for (dia in 0 until 12) {
            d = d.copy(dia = dia)
            rule.waitForIdle()
            val texto = rule.onNodeWithTag("cierre-sello-frase").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
            assertTrue("día $dia sin frase", texto.isNotBlank())
            assertFalse("día $dia deja un hueco: $texto", texto.contains("{") || texto.contains("Ana"))
        }
    }

    @Test
    fun `a layer with nothing to show never appears`() {
        montar(datos(rachas = 0, logros = false))
        capas = setOf(CapaSello.RACHAS, CapaSello.LOGROS)
        rule.waitForIdle()
        assertFalse(hay("cierre-sello-rachas"))
        assertFalse(hay("cierre-sello-logros"))
        assertTrue("no brota lo que no está", sonido.snds().isEmpty())
    }

    @Test
    fun `with the big thing undone it says tomorrow we start with it`() {
        montar(datos(gordo = listOf("Informe")))
        rule.onNodeWithTag("cierre-sello-gordo").assertTextContains(rule.activity.getString(R.string.cierre_sello_gordo, "Informe"))
    }

    @Test
    fun `without the big thing undone there is no tomorrow line`() {
        montar(datos())
        assertFalse(hay("cierre-sello-gordo"))
    }

    @Test
    fun `the title says perfect day or sealed day`() {
        montar(datos(perfecto = true))
        rule.onNodeWithTag("cierre-sello-titulo").assertTextContains(rule.activity.getString(R.string.cierre_sello_perfecto))
    }

    @Test
    fun `a sealed day says so, counts the habits and the bitos`() {
        montar(datos())
        rule.onNodeWithTag("cierre-sello-titulo").assertTextContains(rule.activity.getString(R.string.review_sealed_title))
        rule.onNodeWithTag("cierre-sello-cuenta").assertTextContains(rule.activity.getString(R.string.ring_of, 3, 4), substring = true)
        rule.onNodeWithTag("cierre-sello-bitos").assertTextContains("+7", substring = true)
    }

    @Test
    fun `Habi's phrase uses the name and adds the tail of a hard day`() {
        montar(datos(animo = 2))
        val frase = rule.onNodeWithTag("cierre-sello-frase")
        frase.assertTextContains("Ana", substring = true)
        val duro = rule.activity.resources.getStringArray(R.array.habla_cie_animo_duro_neutra).first()
        frase.assertTextContains(duro, substring = true)
    }

    @Test
    fun `a good day adds no tail`() {
        montar(datos(animo = 0))
        val bien = rule.activity.resources.getStringArray(R.array.habla_cie_animo_bien_neutra).first()
        assertFalse(
            rule.onAllNodesWithText(bien, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
