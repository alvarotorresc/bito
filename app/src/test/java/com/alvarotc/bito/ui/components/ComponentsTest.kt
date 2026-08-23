package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
