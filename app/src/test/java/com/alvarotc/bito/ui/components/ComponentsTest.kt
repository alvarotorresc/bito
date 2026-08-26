package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DayRing]'s sweep animates now (T9, motion spec §8: 150-250ms ease-out) — this guards the
 * actual END state landing on target, not merely that the value moves. The animated fraction is
 * read back through a standard [androidx.compose.ui.semantics.ProgressBarRangeInfo] semantics
 * property (set on `DayRing`'s own root, keyed off the exact `sweep` float the Canvas draws) —
 * the least invasive honest hook available: no pixel-reading, no new public state holder, and it
 * reuses the semantics vocabulary Compose already ships for progress-style widgets.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun currentFraction(): Float =
        compose
            .onNodeWithTag("day-ring")
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    @Test
    fun `the ring lands on the target fraction`() {
        val done = mutableStateOf(2)
        compose.setContent {
            BitoTheme {
                DayRing(done = done.value, total = 6, modifier = Modifier.size(88.dp)) {}
            }
        }
        compose.waitForIdle()
        // First composition: DayRing's contract is to land on the initial fraction immediately,
        // no draw-in animation from zero.
        assertEquals(2f / 6f, currentFraction(), 0.001f)

        // A LATER change to `done` is what should animate: waitForIdle settles finite animations
        // (waits for `tween(250)` to finish), so the sweep must arrive at the new fraction, not
        // stop short mid-flight.
        compose.runOnIdle { done.value = 5 }
        compose.waitForIdle()

        assertEquals(5f / 6f, currentFraction(), 0.001f)
    }

    @Test
    fun `DotProgress exposes its fraction as progress bar range info`() {
        compose.setContent {
            BitoTheme {
                DotProgress(filled = 2, total = 8, modifier = Modifier.testTag("dots"))
            }
        }

        val fraction = compose.onNodeWithTag("dots").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(2f / 8f, fraction, 0.001f)
    }

    @Test
    fun `RoundedBar exposes its progress as progress bar range info`() {
        compose.setContent {
            BitoTheme {
                RoundedBar(progress = 0.6f, modifier = Modifier.testTag("bar").fillMaxWidth())
            }
        }

        val fraction = compose.onNodeWithTag("bar").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals(0.6f, fraction, 0.001f)
    }

    @Test
    fun `SegmentedPills announces which option is selected as a tab, not a bare button`() {
        val selectedIndex = mutableStateOf(0)
        compose.setContent {
            BitoTheme {
                SegmentedPills(
                    options = listOf("Day", "Week", "Month"),
                    selectedIndex = selectedIndex.value,
                    onSelect = { selectedIndex.value = it },
                )
            }
        }

        val dayNode = compose.onNodeWithText("Day")
        dayNode.assertIsSelected()
        assertEquals(Role.Tab, dayNode.fetchSemanticsNode().config[SemanticsProperties.Role])
        compose.onNodeWithText("Week").assertIsNotSelected()

        compose.onNodeWithText("Week").performClick()

        compose.onNodeWithText("Week").assertIsSelected()
        compose.onNodeWithText("Day").assertIsNotSelected()
    }

    @Test
    fun `a disabled SegmentedPills option stays disabled under the new selectable semantics`() {
        compose.setContent {
            BitoTheme {
                SegmentedPills(options = listOf("Day", "Week"), selectedIndex = 0, onSelect = {}, enabled = false)
            }
        }

        compose.onNodeWithText("Day").assertIsNotEnabled()
    }
}
