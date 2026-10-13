package com.alvarotc.bito.ui.garden

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
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

    private fun setContent(onHabiTap: () -> Unit = {}) {
        compose.setContent {
            BitoTheme {
                GardenScene(garden = garden, spec = spec, animated = false, onHabiTap = onHabiTap, modifier = Modifier.width(400.dp))
            }
        }
    }

    @Test
    fun `tapping Habi in the garden reaches the callback`() {
        var taps = 0
        setContent { taps++ }

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
}
