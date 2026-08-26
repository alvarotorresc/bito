package com.alvarotc.bito.ui.habi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders [StoreSection] standalone — no [HabiViewModel], no [HabiAvatar] — so there is no
 * infinite bob/blink transition to fight, unlike a full [HabiScreen] render (T9/T11 note).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StoreSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(initial: HabiUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            BitoTheme {
                StoreSection(
                    state = state,
                    onPreview = { state = state.copy(previewItemId = it) },
                    onEquip = {},
                    onUnequipDefault = {},
                    onUnequip = {},
                    onBuy = {},
                    onBuyFreezer = {},
                )
            }
        }
    }

    private fun fixture(previewItemId: String? = null) =
        buildHabiUiState(
            domainState(),
            owned = emptyList(),
            balance = 100,
            personality = Personality.NEUTRA,
            today = TODAY,
            previewItemId = previewItemId,
        )

    @Test
    fun `the grid renders the selected axis and the pill row switches it`() {
        setContent(fixture())

        compose.onNodeWithTag("store-item-body-salvia", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("store-item-pattern-motas", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("store-axis-PATTERN", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("store-item-pattern-motas", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("store-item-body-salvia", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping an affordable item opens the purchase sheet`() {
        setContent(fixture())

        // body-vainilla: price 15, well under the fixture's balance of 100 -> Affordable.
        compose.onNodeWithTag("store-item-body-vainilla", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("buy-item", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a locked exclusive does not open anything when tapped`() {
        setContent(fixture())

        compose.onNodeWithTag("store-axis-BODY_COLOR", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // body-dorado is racha-365-locked in a fresh domainState() (no habits, no streaks).
        compose.onNodeWithTag("store-item-body-dorado", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the freezer card opens the freezer purchase sheet moved in from Detail`() {
        setContent(fixture())

        compose.onNodeWithTag("open-freezer-sheet", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("freezer-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("buy-freezer", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an untappable item reads its name and status as one unit`() {
        // body-salvia: the default BODY_COLOR, always-Equipped and BODY_COLOR isn't in
        // UNPINNABLE_AXES, so it's untappable (isTappable) — before the [E] fix, name ("Sage")
        // and status ("equipped") had no merging ancestor and were 2 separate semantics nodes;
        // querying the MERGED tree (no useUnmergedTree) for a single node carrying both texts
        // only succeeds once `Modifier.semantics(mergeDescendants = true)` is on the Column.
        setContent(fixture())

        compose.onNode(hasText("Sage") and hasText("equipped")).assertExists()
    }

    @Test
    fun `the active axis pill is announced as selected, not just clickable`() {
        // Before the [C] fix, the active pill skipped `.clickable` entirely — not even a
        // focusable node, let alone one carrying `selected = true`.
        setContent(fixture())

        compose.onNodeWithTag("store-axis-BODY_COLOR", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("store-axis-PATTERN", useUnmergedTree = true).assertIsNotSelected()
    }
}
