package com.alvarotc.bito.ui.habi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.CustomizationCategory.BODY_COLOR
import com.alvarotc.bito.domain.model.CustomizationCategory.EYE_COLOR
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [StoreSection] a solas, sin ViewModel: la escalera de estados, los disfraces de temporada y las
 * miniaturas pintadas. TODAY es el 14 de agosto: ningún disfraz está en temporada.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StoreSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val halloween: LogicalDay = LocalDate.of(2026, 10, 20).toEpochDay().toInt()
    private val equipped = mutableListOf<String>()

    private fun setContent(initial: HabiUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            BitoTheme {
                StoreSection(
                    state = state,
                    onPreview = { state = state.copy(previewItemId = it) },
                    onEquip = { equipped += it },
                    onUnequipDefault = {},
                    onUnequip = {},
                    onBuy = {},
                    onBuyFreezer = {},
                )
            }
        }
    }

    private fun fixture(
        today: LogicalDay = TODAY,
        balance: Int = 100,
        owned: List<CustomizationItemEntity> = emptyList(),
    ) = buildHabiUiState(domainState(), owned = owned, balance = balance, personality = Personality.NEUTRA, today = today)

    private fun openAxis(axis: CustomizationCategory) {
        compose.onNodeWithTag("store-axis-${axis.name}", useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the pill row switches between the seven axes`() {
        setContent(fixture())
        compose.onNodeWithTag("store-item-body-salvia", useUnmergedTree = true).assertExists()

        openAxis(CustomizationCategory.HEAD)
        compose.onNodeWithTag("store-item-head-gorro-lana", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("store-item-body-salvia", useUnmergedTree = true).assertDoesNotExist()

        openAxis(CustomizationCategory.COSTUME)
        compose.onNodeWithTag("store-item-costume-reno", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `accessories and costumes show their painted piece, colors a tinted Habi`() {
        setContent(fixture())
        compose.onNodeWithTag("store-thumb-body-cielo", useUnmergedTree = true).assertExists()
        openAxis(CustomizationCategory.NECK)
        compose.onNodeWithTag("store-thumb-neck-bufanda", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping an affordable item opens the purchase sheet`() {
        setContent(fixture())
        compose.onNodeWithTag("store-item-body-vainilla", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a locked exclusive does not open anything when tapped`() {
        setContent(fixture())
        openAxis(CustomizationCategory.HEAD)
        compose.onNodeWithTag("store-item-head-corona", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a costume out of season says so and cannot be bought`() {
        setContent(fixture(balance = 1_000))
        openAxis(CustomizationCategory.COSTUME)
        compose.onNodeWithTag("store-item-costume-calabaza").assert(hasText("out of season"))
        compose.onNodeWithTag("store-item-costume-calabaza", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a costume in season opens the purchase sheet`() {
        setContent(fixture(today = halloween, balance = 200))
        openAxis(CustomizationCategory.COSTUME)
        compose.onNodeWithTag("store-item-costume-calabaza", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("purchase-sheet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an owned costume is worn out of season with a tap`() {
        val owned = listOf(CustomizationItemEntity("costume-reno", CustomizationCategory.COSTUME, 0L, equipped = false))
        setContent(fixture(owned = owned))
        openAxis(CustomizationCategory.COSTUME)
        compose.onNodeWithTag("store-item-costume-reno").assert(hasText("tap to equip"))
        compose.onNodeWithTag("store-item-costume-reno", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("costume-reno"), equipped)
    }

    @Test
    fun `the freezer card opens the freezer purchase sheet`() {
        setContent(fixture())
        compose.onNodeWithTag("open-freezer-sheet", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("freezer-sheet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an untappable item reads its name and status as one unit`() {
        setContent(fixture())
        compose.onNodeWithTag("store-item-body-salvia").assert(hasText("Sage")).assert(hasText("equipped"))
    }

    @Test
    fun `the active axis pill is announced as selected`() {
        setContent(fixture())
        compose.onNodeWithTag("store-axis-BODY_COLOR", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("store-axis-EYE_COLOR", useUnmergedTree = true).assertIsNotSelected()
    }

    @Test
    fun `every accessory and costume in the catalog has a painted piece in the atlas`() {
        val missing =
            HabiCatalog.all
                .filter { it.category != BODY_COLOR && it.category != EYE_COLOR }
                .map { it.id }
                .filter { it !in HabiAtlas.pieces }
        assertTrue("catalog ids without atlas piece: $missing", missing.isEmpty())
    }
}
