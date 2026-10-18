package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.garden.SceneItem
import com.alvarotc.bito.ui.garden.sceneItems
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El jardín de Hoy y Camino con lo comprado en la tienda (tienda-jardin.md «Tocar un objeto en Hoy o Camino», «Volver a Hoy»). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class JardinCompartidoTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())

    private var fondos = 0

    private fun montar(
        garden: GardenState,
        recienComprado: String? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(sonido, tacto) {
                // Camino: el jardín entero a escala 1, como el mock (412 de ancho).
                JardinCompartido(
                    garden,
                    spec,
                    q = { 1f },
                    encogido = { 0f },
                    modifier = Modifier.size(412.dp, 700.dp),
                    animado = false,
                    recienComprado = recienComprado,
                    alTocarFondo = { fondos++ },
                )
            }
        }
        compose.mainClock.advanceTimeBy(300)
    }

    private fun tocar(pick: (SceneItem) -> Boolean) {
        val w = 412f * compose.density.density
        val r = sceneItems(compose.runOnUiThread { garden }, w, w / GardenArt.SCENE_ASPECT).first(pick).rect
        compose.onNodeWithTag("garden-scene").performTouchInput { click(Offset(r.left + r.width / 2f, r.top + r.height / 2f)) }
        compose.mainClock.advanceTimeBy(200)
    }

    private lateinit var garden: GardenState

    @Test
    fun `tapping an object rocks it and opens the bubble with its text`() {
        garden = GardenState.INITIAL.copy(objects = setOf("banco"))
        montar(garden)
        tocar { it is SceneItem.Objeto }
        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
        compose.onNodeWithText("Bench · You bought it with your bitos.").assertExists()
        assertEquals("tocar un objeto ya no navega a Camino", 0, fondos)
    }

    @Test
    @Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
    fun `a special in Spanish says Llego con and does not navigate`() {
        garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        montar(garden)
        tocar { it is SceneItem.Special }
        compose.onNodeWithText("Farolillo \u00b7 Llegó con Primera llama.").assertExists()
        assertEquals("tocar un especial ya no navega a Camino", 0, fondos)
    }

    @Test
    fun `a special says Came with the badge`() {
        garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        montar(garden)
        tocar { it is SceneItem.Special }
        compose.onNodeWithText("Lantern · Came with First flame.").assertExists()
    }

    @Test
    fun `a special in Today rocks and uses the system bubble`() {
        garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        montar(garden)
        tocar { it is SceneItem.Special }
        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
        compose.onNodeWithTag("globito").assertExists()
        compose.onNodeWithText("Lantern \u00b7 Came with First flame.").assertExists()
    }

    @Test
    fun `the object bubble leaves with its exit`() {
        garden = GardenState.INITIAL.copy(objects = setOf("banco"))
        montar(garden)
        val t0 = compose.mainClock.currentTime
        tocar { it is SceneItem.Objeto }
        compose.onNodeWithTag("globito").assertExists()
        // A los 2,4 s empieza la salida (180 ms con `hundir`): no desaparece de golpe, sigue ahí un rato más.
        while (compose.onAllNodesWithTag("globito").fetchSemanticsNodes().isNotEmpty() && compose.mainClock.currentTime - t0 < 5000) {
            compose.mainClock.advanceTimeByFrame()
        }
        val duro = compose.mainClock.currentTime - t0
        assertTrue("el globito se va con su salida, no antes de 2,4 s + 80 ms: $duro", duro >= 2480)
        assertTrue("y se retira al acabar: $duro", duro < 3500)
    }

    @Test
    fun `coming back from the store rocks the new object once at 420 ms`() {
        garden = GardenState.INITIAL.copy(objects = setOf("banco"))
        sonido.sonados.clear()
        montar(garden, recienComprado = "banco")
        // 300 ms ya pasaron en `montar`: faltan 120 para los 420.
        assertTrue("aún no", Snd.PLANTA !in sonido.snds())
        compose.mainClock.advanceTimeBy(200)
        assertEquals(1, sonido.snds().count { it == Snd.PLANTA })
        compose.mainClock.advanceTimeBy(3000)
        assertEquals("una sola vez", 1, sonido.snds().count { it == Snd.PLANTA })
    }
}
