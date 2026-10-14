package com.alvarotc.bito.ui.garden

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La escena en Compose: lo que se toca (que una planta se meza lo prueba onSceneTap). Lo que se ve se comprueba en el Pixel (§9.2). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GardenSceneTest {
    @get:Rule
    val compose = createComposeRule()

    private val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
    private val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.MUSHROOMS))

    private fun setContent(
        garden: GardenState = this.garden,
        onHabiTap: () -> Unit = {},
    ) {
        compose.setContent {
            BitoTheme {
                GardenScene(garden = garden, spec = spec, animated = false, onHabiTap = onHabiTap, modifier = Modifier.width(400.dp))
            }
        }
    }

    @Test
    fun `tapping Habi in the garden reaches the callback`() {
        var taps = 0
        setContent(onHabiTap = { taps++ })

        compose.onNodeWithContentDescription("Habi, doing okay").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `tapping a special shows the name of its badge`() {
        setContent()
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val mushrooms = sceneItems(garden, widthPx, heightPx).first { it is SceneItem.Special }.rect
        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(mushrooms.left + mushrooms.width / 2f, mushrooms.top + mushrooms.height / 2f))
        }

        compose.onNodeWithText("Resurrection").assertExists()
        compose.onNodeWithTag("garden-bubble-setas").assertExists()
        // El globito se va solo a los 2,5 s.
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("garden-bubble-setas").assertDoesNotExist()
    }

    @Test
    fun `tapping a plant opens no bubble`() {
        setContent()
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val plant = sceneItems(garden, widthPx, heightPx).first { it is SceneItem.Plant }.rect

        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(plant.left + plant.width / 2f, plant.top + plant.height / 2f))
        }
        compose.waitForIdle()

        compose.onNodeWithTag("garden-bubble-setas").assertDoesNotExist()
    }

    @Test
    fun `tapping the lantern beside Habi opens its bubble instead of petting Habi`() {
        var taps = 0
        val withLantern = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        setContent(withLantern) { taps++ }
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val lantern = sceneItems(withLantern, widthPx, heightPx).first { it is SceneItem.Special }.rect

        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(lantern.left + lantern.width / 2f, lantern.top + lantern.height / 2f))
        }

        compose.onNodeWithText("First flame").assertExists()
        assertEquals(0, taps)
    }

    // Las pruebas de la pieza 3: el jardín vivo, con sonido, tacto y anclas grabados.
    private var jardin by mutableStateOf(garden)
    private var salto by mutableIntStateOf(0)
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val registro = RegistroAnclas()
    private val sonda = SondaJardin()

    private fun montar(
        garden: GardenState = this.garden,
        reducido: Boolean = false,
        alTocarFondo: (() -> Unit)? = null,
        globitoPlanta: Boolean = false,
        llegando: GardenSpecial? = null,
        creciendo: Int? = null,
        destacar: GardenSpecial? = null,
    ) {
        jardin = garden
        compose.setContent {
            ConSistema(sonido, tacto, reducido) {
                CompositionLocalProvider(LocalAnclas provides registro, LocalSondaJardin provides sonda) {
                    GardenScene(
                        garden = jardin,
                        spec = spec,
                        animated = false,
                        onHabiTap = {},
                        modifier = Modifier.width(400.dp),
                        alTocarFondo = alTocarFondo,
                        globitoPlanta = globitoPlanta,
                        llegando = llegando,
                        creciendo = creciendo,
                        destacar = destacar,
                        saltoHabi = salto,
                    )
                }
            }
        }
    }

    private fun rectOf(
        garden: GardenState,
        pick: (SceneItem) -> Boolean,
    ): SceneRect {
        val widthPx = with(compose.density) { 400.dp.toPx() }
        return sceneItems(garden, widthPx, widthPx / GardenArt.SCENE_ASPECT).first(pick).rect
    }

    private fun tapCenter(rect: SceneRect) =
        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f))
        }

    @Test
    fun `tapping the background calls back, a short drag does not`() {
        var fondos = 0
        montar(alTocarFondo = { fondos++ })

        compose.onNodeWithTag("garden-scene").performTouchInput { click(Offset(20f, 20f)) }
        assertEquals(1, fondos)

        compose.onNodeWithTag("garden-scene").performTouchInput { swipeLeft() }
        assertEquals(1, fondos)
    }

    @Test
    fun `tapping a plant sways it with its sound and tick`() {
        montar()
        tapCenter(rectOf(garden) { it is SceneItem.Plant })

        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
    }

    @Test
    fun `tapping a plant in Camino shows its species and stage`() {
        val dos =
            GardenState.INITIAL.copy(
                plots =
                    listOf(
                        GardenPlot(GardenSpecies.ROUND_LEAVES, GrowthStage.SEED),
                        GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SPROUT),
                    ),
            )
        montar(dos, globitoPlanta = true)

        tapCenter(rectOf(dos) { it is SceneItem.Plant && it.plot == 0 })
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Stage 1 of 5.").assertExists()

        tapCenter(rectOf(dos) { it is SceneItem.Plant && it.plot == 1 })
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Sunflowers \u00b7 Stage 2 of 5. Grows now with what you check off.").assertExists()
    }

    @Test
    fun `tapping a special in Camino says which badge it came with`() {
        montar(globitoPlanta = true)
        tapCenter(rectOf(garden) { it is SceneItem.Special })

        compose.onNodeWithText("Mushrooms \u00b7 Came with Resurrection.").assertExists()
    }

    @Test
    fun `the growing plot registers its anchor`() {
        montar()
        compose.waitForIdle()

        assertTrue(registro.posiciones.containsKey(ClavesAncla.PARCELA))
    }

    @Test
    fun `Habi registers its anchor`() {
        montar()
        compose.waitForIdle()

        val scene = compose.onNodeWithTag("garden-scene").fetchSemanticsNode().boundsInRoot
        val habi = registro.posiciones.getValue(ClavesAncla.HABI)
        assertTrue(scene.contains(habi.center))
        assertTrue(habi.width > 0f && habi.height > 0f)
    }

    @Test
    fun `a special arriving plays its sound`() {
        montar(llegando = GardenSpecial.LANTERN)
        compose.mainClock.advanceTimeBy(600)

        assertTrue(Snd.POSAR in sonido.snds())
        assertTrue(Haptica.THUD in tacto.hapticas)
    }

    @Test
    fun `a growing plot sprouts with its sounds`() {
        montar(creciendo = 0)
        compose.mainClock.advanceTimeBy(600)

        assertTrue(Snd.BROTE in sonido.snds())
        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICKS in tacto.hapticas)
        // Parcela nueva (etapa 1): Habi salta.
        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `when the garden revives the plants sway and Habi jumps`() {
        montar(garden.copy(wilt = 3))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        assertEquals(3f, sonda.wiltDibujado(), 0f)

        compose.runOnUiThread { jardin = garden.copy(wilt = 0) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(450)
        val mitad = sonda.wiltDibujado()
        assertTrue("a mitad: $mitad", mitad > 0f && mitad < 3f)
        assertTrue(Snd.PLANTA in sonido.snds())
        assertEquals(1, sonda.saltosHabi())

        compose.mainClock.advanceTimeBy(500)
        assertEquals(0f, sonda.wiltDibujado(), 0f)
    }

    @Test
    fun `when the garden wilts nothing moves and nothing sounds`() {
        montar(garden.copy(wilt = 0))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { jardin = garden.copy(wilt = 3) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(50)

        assertEquals(3f, sonda.wiltDibujado(), 0f)
        assertTrue(sonido.snds().isEmpty())
        assertEquals(0, sonda.saltosHabi())
    }

    @Test
    fun `with reduced motion the garden revives in 300 ms without swaying or jumping`() {
        montar(garden.copy(wilt = 3), reducido = true)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { jardin = garden.copy(wilt = 0) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(350)

        assertEquals(0f, sonda.wiltDibujado(), 0f)
        assertEquals(0, sonda.saltosHabi())
    }

    @Test
    fun `raising saltoHabi makes Habi jump`() {
        montar()
        compose.waitForIdle()
        assertEquals(0, sonda.saltosHabi())

        compose.runOnUiThread { salto = 1 }
        compose.waitForIdle()

        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `the anchors follow a parent that only changes its graphicsLayer`() {
        var escala by mutableStateOf(1f)
        compose.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro) {
                    GardenScene(
                        garden = garden,
                        spec = spec,
                        animated = false,
                        modifier =
                            Modifier.width(400.dp).graphicsLayer {
                                scaleX = escala
                                scaleY = escala
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    )
                }
            }
        }
        compose.waitForIdle()
        val antes = registro.posiciones.getValue(ClavesAncla.HABI)

        compose.runOnUiThread { escala = 0.5f }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        val despues = registro.posiciones.getValue(ClavesAncla.HABI)
        assertEquals(antes.width / 2f, despues.width, 1f)
        assertEquals(antes.left / 2f, despues.left, 1f)
    }

    @Test
    fun `the anchors only follow the parent while the app is resumed`() {
        var escala by mutableStateOf(1f)
        val dueno =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            }
        compose.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro, LocalLifecycleOwner provides dueno) {
                    GardenScene(
                        garden = garden,
                        spec = spec,
                        animated = false,
                        modifier =
                            Modifier.width(400.dp).graphicsLayer {
                                scaleX = escala
                                scaleY = escala
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { (dueno.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        val antes = registro.posiciones.getValue(ClavesAncla.HABI)

        compose.runOnUiThread { escala = 0.5f }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)

        assertEquals(antes, registro.posiciones.getValue(ClavesAncla.HABI))
    }
}
