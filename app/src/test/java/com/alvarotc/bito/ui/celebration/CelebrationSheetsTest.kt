package com.alvarotc.bito.ui.celebration

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Renders [PerfectDaySheet] and [BadgeUnlockSheet] standalone with a synthetic
 * [CelebrationsUiState] — no [CelebrationsViewModel], no database.
 * [com.alvarotc.bito.ui.BitoNavHostTest] covers the real host wiring and the review-route
 * suppression.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CelebrationSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())

    private fun state(
        pointsToday: Int = 40,
        newBadges: List<BadgeDef> = emptyList(),
        userName: String = "Ana",
    ) = CelebrationsUiState(
        perfectDayPending = true,
        pointsToday = pointsToday,
        newBadges = newBadges,
        spec = spec,
        personality = Personality.NEUTRA,
        userName = userName,
    )

    @Test
    fun `the perfect day sheet shows habi, the title and the points`() {
        compose.setContent {
            BitoTheme {
                PerfectDaySheet(state(pointsToday = 40), onDismiss = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Habi", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Perfect day!", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+40 pts", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the badge sheet lists every new badge by name`() {
        val badges = BadgeCatalog.all.filter { it.id == "streak-7" || it.id == "first-freezer" }

        compose.setContent {
            BitoTheme {
                BadgeUnlockSheet(state(newBadges = badges), onDismiss = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("badge-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("First flame", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Below zero", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `continue invokes dismiss`() {
        var dismissed = false
        compose.setContent {
            BitoTheme {
                PerfectDaySheet(state(), onDismiss = { dismissed = true })
            }
        }
        compose.waitForIdle()

        // Not performClick(): synthesized touch gestures don't reliably reach a button inside a
        // ModalBottomSheet under this Robolectric harness (ReviewScreenTest/DetailScreenTest note)
        // — invoke the node's own OnClick action directly. Merged tree (no useUnmergedTree) so the
        // match lands on the Button's own node, which is what actually carries the OnClick action.
        val continueButton = compose.onNodeWithText("Continue")
        continueButton.fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        compose.waitForIdle()

        assertTrue(dismissed)
    }
}
