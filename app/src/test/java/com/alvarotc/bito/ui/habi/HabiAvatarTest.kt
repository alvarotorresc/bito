package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HabiAvatar]'s `contentDescription` used to be a single static "Habi" string regardless of
 * [Mood] — the [D]/[E] a11y finding this closes. `animated = false` avoids fighting the idle
 * bob/blink infinite transitions, same reasoning [StoreSectionTest]'s own kdoc gives for skipping
 * a full [HabiScreen] render.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabiAvatarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(mood: Mood) {
        compose.setContent {
            BitoTheme {
                HabiAvatar(
                    spec = HabiSpec(mood, Personality.NEUTRA, EquippedSet()),
                    animated = false,
                )
            }
        }
    }

    @Test
    fun `a radiant Habi describes its mood, not just its name`() {
        setContent(Mood.RADIANT)

        compose.onNodeWithContentDescription("Habi, feeling great").assertExists()
    }

    @Test
    fun `a wilted Habi describes a different mood`() {
        setContent(Mood.WILTED)

        compose.onNodeWithContentDescription("Habi, feeling low").assertExists()
    }

    @Test
    fun `a guided avatar renders frozen and still describes its mood`() {
        compose.setContent {
            BitoTheme {
                HabiAvatar(
                    spec = HabiSpec(Mood.RADIANT, Personality.NEUTRA, EquippedSet()),
                    animated = false,
                    breath = HabiBreath(fill = 1f),
                )
            }
        }

        compose.onNodeWithContentDescription("Habi, feeling great").assertExists()
    }

    @Test
    fun `a fully dressed avatar and a costumed one compose and keep their description`() {
        compose.setContent {
            BitoTheme {
                Column {
                    HabiAvatar(
                        spec =
                            HabiSpec(
                                Mood.DRAMATIC,
                                Personality.SARGENTO,
                                EquippedSet(head = "head-copa", face = "face-gafas-sol", neck = "neck-bufanda", feet = "feet-botas-agua"),
                            ),
                        animated = false,
                    )
                    HabiAvatar(
                        spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet(costume = "costume-dinosaurio")),
                        animated = false,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Habi, having a hard time").assertExists()
        compose.onNodeWithContentDescription("Habi, feeling great").assertExists()
    }
}
