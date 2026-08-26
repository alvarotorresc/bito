package com.alvarotc.bito.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M9.5 final-review Important #1: re-tapping the already-selected tab must stay inert. Before
 * this fix, [BitoBottomBar]'s `NavSlot` attached `selectable(onClick = onClick)` unconditionally,
 * including on the selected slot — and `BitoNavHost`'s own `onStats`/`onHabi`/`onSettings`
 * callbacks are NOT idempotent when the target route is already current (`navigate(route) {
 * popUpTo("today"); launchSingleTop = true }` pops the current entry before `launchSingleTop` can
 * find it to reuse, so a new entry — new ViewModel, lost `remember` state — gets pushed anyway).
 * These tests exercise [BitoBottomBar] directly, isolated from that navigation-runtime detail:
 * a selected slot's [onClick] must never fire, and an unselected slot's must fire exactly as
 * before.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BitoBottomBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping the already-selected tab does not invoke its callback`() {
        var statsTaps = 0
        compose.setContent {
            BitoTheme {
                BitoBottomBar(
                    currentRoute = "stats",
                    onToday = {},
                    onStats = { statsTaps++ },
                    onCreate = {},
                    onHabi = {},
                    onSettings = {},
                )
            }
        }

        compose
            .onNode(hasContentDescription("Stats") and hasAnyAncestor(hasTestTag("bottom-bar")))
            .performClick()

        assertEquals(0, statsTaps)
    }

    @Test
    fun `tapping an unselected tab still invokes its callback`() {
        var settingsTaps = 0
        compose.setContent {
            BitoTheme {
                BitoBottomBar(
                    currentRoute = "today",
                    onToday = {},
                    onStats = {},
                    onCreate = {},
                    onHabi = {},
                    onSettings = { settingsTaps++ },
                )
            }
        }

        compose
            .onNode(hasContentDescription("Settings") and hasAnyAncestor(hasTestTag("bottom-bar")))
            .performClick()

        assertEquals(1, settingsTaps)
    }

    @Test
    fun `all four labels render complete`() {
        compose.setContent { BitoTheme { BitoBottomBar("today", {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Settings").assertIsDisplayed() // default locale EN: el slot que antes se recortaba
        compose.onNodeWithText("Stats").assertIsDisplayed()
    }
}
