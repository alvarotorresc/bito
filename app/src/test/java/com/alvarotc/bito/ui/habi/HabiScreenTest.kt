package com.alvarotc.bito.ui.habi

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PersonalityPills] standalone — same reasoning [StoreSectionTest] gives for testing
 * [StoreSection] without a full [HabiScreen] (no [HabiViewModel], no real [HabiAvatar] bob/blink
 * transition to fight). It's `internal` (not `private`) precisely so this test can reach it
 * directly.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HabiScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the active personality pill is announced as selected, not just clickable`() {
        // Before the [C] fix, the active pill skipped `.clickable` entirely — a TalkBack pass
        // found only the 2 non-selected options, no sign a 3rd, active one existed.
        compose.setContent {
            BitoTheme {
                PersonalityPills(selected = Personality.NEUTRA, onSelect = {})
            }
        }

        compose.onNodeWithText("NEUTRAL").assertIsSelected()
        compose.onNodeWithText("SERGEANT").assertIsNotSelected()
        compose.onNodeWithText("CHEERLEADER").assertIsNotSelected()
    }

    @Test
    fun `tapping a different pill selects it`() {
        var selected: Personality? = null
        compose.setContent {
            BitoTheme {
                PersonalityPills(selected = Personality.NEUTRA, onSelect = { selected = it })
            }
        }

        compose.onNodeWithText("SERGEANT").performClick()

        assertEquals(Personality.SARGENTO, selected)
    }
}
