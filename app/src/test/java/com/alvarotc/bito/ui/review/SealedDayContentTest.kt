package com.alvarotc.bito.ui.review

import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * Renders E2's [SealedDayContent] standalone with a synthetic [ReviewUiState] — no
 * [ReviewViewModel], no database, same standalone-state approach
 * [com.alvarotc.bito.ui.celebration.CelebrationSheetsTest] uses for the global sheets.
 * [ReviewScreenTest] covers this composable wired to the real VM; here the goal is the
 * INTERPOLATED speech-bubble text (parked M7 gap) and the E2 positive paths for
 * [ReviewUiState.streaksAdvanced] and [ReviewUiState.newBadges] (also parked M7 — only the
 * negative/hidden case was ever asserted).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SealedDayContentTest {
    @get:Rule
    val compose = createComposeRule()

    // SARGENTO so the interpolation assertions below exercise a real personality voice line,
    // same choice CelebrationSheetsTest's badge-sheet test below makes for the same reason.
    private val spec = HabiSpec(Mood.NORMAL, Personality.SARGENTO, EquippedSet())

    private fun state(
        perfectToday: Boolean = false,
        userName: String = "Álvaro",
        pointsToday: Int = 40,
        streaksAdvanced: Int = 0,
        newBadges: List<BadgeDef> = emptyList(),
    ) = ReviewUiState(
        perfectToday = perfectToday,
        userName = userName,
        pointsToday = pointsToday,
        streaksAdvanced = streaksAdvanced,
        newBadges = newBadges,
        spec = spec,
        loading = false,
    )

    @Test
    fun `the sealed speech bubble interpolates the user's name`() {
        compose.setContent { BitoTheme { SealedDayContent(state(perfectToday = false), onClose = {}) } }
        compose.waitForIdle()

        // habi_sealed_sargento: "Day closed, %1$s. Tomorrow, more." — a generic fallback name
        // or a broken %1$s substitution would both fail to find this exact sentence.
        compose.onNodeWithText("Day closed, Álvaro. Tomorrow, more.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the perfect day speech bubble interpolates the user's name`() {
        compose.setContent { BitoTheme { SealedDayContent(state(perfectToday = true), onClose = {}) } }
        compose.waitForIdle()

        // habi_perfect_sargento: "Perfect day, %1$s. That's how it's done. Don't get comfortable."
        compose
            .onNodeWithText("Perfect day, Álvaro. That's how it's done. Don't get comfortable.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `streaksAdvanced greater than zero renders the streaks chip with its count`() {
        compose.setContent { BitoTheme { SealedDayContent(state(streaksAdvanced = 3), onClose = {}) } }
        compose.waitForIdle()

        compose.onNodeWithTag("review-streaks", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("3 streaks advanced", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `newBadges renders a chip per badge with its own name`() {
        val badges = BadgeCatalog.all.filter { it.id == "streak-7" || it.id == "first-freezer" }

        compose.setContent { BitoTheme { SealedDayContent(state(newBadges = badges), onClose = {}) } }
        compose.waitForIdle()

        compose.onNodeWithTag("review-badge-streak-7", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("review-badge-first-freezer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("First flame", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Below zero", useUnmergedTree = true).assertExists()
    }
}
