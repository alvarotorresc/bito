package com.alvarotc.bito.ui.habi

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
}
