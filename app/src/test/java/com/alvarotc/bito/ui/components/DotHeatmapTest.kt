package com.alvarotc.bito.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [DotHeatmap]'s [D]-rule fix: ONE aggregated description on the grid container (never per cell),
 * plus a per-cell description so each tappable day stays identifiable — a bare "Button" today.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DotHeatmapTest {
    @get:Rule
    val compose = createComposeRule()

    private val augFirst = LocalDate.of(2025, 8, 1).toEpochDay().toInt()

    @Test
    fun `the grid announces one aggregated month summary, not a description per cell`() {
        val days =
            listOf(
                HeatmapDay(augFirst, DayDot.FULFILLED, false),
                HeatmapDay(augFirst + 1, DayDot.FAILED, false),
                HeatmapDay(augFirst + 2, DayDot.FROZEN, false),
                HeatmapDay(augFirst + 3, DayDot.PAUSED, false),
                HeatmapDay(augFirst + 4, DayDot.PENDING, true),
                HeatmapDay(augFirst + 5, DayDot.OFF, false),
            )
        compose.setContent {
            BitoTheme {
                DotHeatmap(days = days, onDayTap = {})
            }
        }

        // done=1 (FULFILLED), failed=1 (FAILED), frozen=1, paused=1 -> total=4 of a 6-cell grid:
        // PENDING (today, not yet judged) and OFF (outside the habit's life) are not counted.
        compose.onNodeWithContentDescription("August 2025: 1 done, 1 failed, 1 frozen, 1 paused of 4 days")
            .assertExists()
    }

    @Test
    fun `a judged cell announces its date and state`() {
        val days = listOf(HeatmapDay(augFirst, DayDot.FULFILLED, false))
        compose.setContent {
            BitoTheme {
                DotHeatmap(days = days, onDayTap = {})
            }
        }

        compose.onNodeWithTag("heatmap-day-$augFirst", useUnmergedTree = true)
            .assertContentDescriptionEquals("August 1, Done")
    }

    @Test
    fun `an OFF cell stays undescribed and untappable, unlike every judged day`() {
        var tapped: Int? = null
        val days = listOf(HeatmapDay(augFirst, DayDot.FULFILLED, false), HeatmapDay(augFirst + 1, DayDot.OFF, false))
        compose.setContent {
            BitoTheme {
                DotHeatmap(days = days, onDayTap = { tapped = it })
            }
        }

        val offNode = compose.onNodeWithTag("heatmap-day-${augFirst + 1}", useUnmergedTree = true).fetchSemanticsNode()
        assertNull(offNode.config.getOrNull(SemanticsProperties.ContentDescription))

        compose.onNodeWithTag("heatmap-day-$augFirst", useUnmergedTree = true).performClick()
        assertEquals(augFirst, tapped)
        assertTrue(offNode.config.getOrNull(SemanticsActions.OnClick) == null)
    }
}
