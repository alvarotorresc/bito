package com.alvarotc.bito.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.celebration.CelebrationsViewModel
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * The bottom bar lives above the NavHost now; these tests guard it surviving a route change.
 * Uses a bare [Application] (not the manifest's BitoApp) so onCreate()'s AppStartup.start() —
 * which builds its own AppContainer — never opens a second DataStore on the same settings file
 * as the one built explicitly below.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = Application::class)
class BitoNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        // NavRequests is a process-wide singleton (T11): a route left pending here would leak
        // into whichever test class runs next in this JVM fork.
        NavRequests.consume()
    }

    /**
     * The screen title Text, as opposed to a bottom-bar tab wearing the same label — the bar now
     * shows visible Hoy/Stats/Ajustes labels too (this restyle's rule 5), so a bare
     * `onNodeWithText(text)` is ambiguous whenever both are on screen together.
     */
    private fun screenTitleNode(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("bottom-bar")).not(), useUnmergedTree = true)

    /** The conditional start gates on Settings' first emission (loading -> today/onboarding);
     * wait for that placeholder to clear before any assertion, the same way the settings-reminder
     * test below already has to wait out a DataStore-backed emission that waitForIdle() alone
     * doesn't pump. */
    private fun waitPastLoadingGate() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("app-loading", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Every test below except the onboarding-route ones themselves assumes a completed
     * onboarding (landing on "today") — a fresh container otherwise defaults onboardingDone to
     * false and the conditional start would open on "onboarding" instead. */
    private suspend fun completeOnboarding(container: AppContainer) {
        container.settings.update { it.copy(onboardingDone = true) }
    }

    private fun setContent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        runBlocking { completeOnboarding(container) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    /** Like [setContent], but [seed] runs against the container's real database first. */
    private fun setContentSeeded(seed: suspend AppContainer.() -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        runBlocking {
            completeOnboarding(container)
            container.seed()
        }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()
    }

    private suspend fun seedPerfectDayToday(container: AppContainer) {
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
        container.database.pointsLedgerDao().insert(
            pointsLedgerEntity(reason = PointsReason.PERFECT_DAY, refId = "day:$today", logicalDay = today),
        )
    }

    /**
     * A bare [ViewModelStoreOwner] the test provides in place of the compose-test host's own
     * (`createComposeRule()` has no exposed `.activity` to read the real one back off of, same
     * limitation [FakeBackDispatcherOwner] documents in
     * [com.alvarotc.bito.ui.review.ReviewScreenTest]). `BitoNavHost`'s [CelebrationsViewModel]
     * is created with `viewModel(factory = ...)` reading `LocalViewModelStoreOwner.current` — a
     * SECOND [ViewModelProvider] built against the SAME store and the SAME default key returns
     * that exact cached instance, letting the test read [CelebrationsViewModel.lastCued] straight
     * off the real VM the composition is driving. Every NavHost `composable { }` block re-provides
     * its own owner (the [androidx.navigation.NavBackStackEntry]), so route-scoped VMs elsewhere
     * in the tree are unaffected by this override.
     */
    private class FakeViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun `the bottom bar survives navigating to settings`() {
        setContent()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()

        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // The reminders card only renders once SettingsViewModel's DataStore-backed flow has
        // emitted (real dispatcher, not the test's) — waitForIdle alone doesn't pump that.
        val reminderCopy = "Phone nudges so you don't forget to log your habits."
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(reminderCopy, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        screenTitleNode("Settings").assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(reminderCopy, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the home icon returns from settings to today`() {
        setContent()
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Today", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        screenTitleNode("Today").assertExists()
    }

    @Test
    fun `the current tab announces itself as selected, and switching updates which one does`() {
        setContent()

        compose
            .onNode(hasContentDescription("Today") and hasAnyAncestor(hasTestTag("bottom-bar")), useUnmergedTree = true)
            .assertIsSelected()
        compose
            .onNode(hasContentDescription("Stats") and hasAnyAncestor(hasTestTag("bottom-bar")), useUnmergedTree = true)
            .assertIsNotSelected()

        compose.onNodeWithContentDescription("Stats", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose
            .onNode(hasContentDescription("Stats") and hasAnyAncestor(hasTestTag("bottom-bar")), useUnmergedTree = true)
            .assertIsSelected()
        compose
            .onNode(hasContentDescription("Today") and hasAnyAncestor(hasTestTag("bottom-bar")), useUnmergedTree = true)
            .assertIsNotSelected()
    }

    @Test
    fun `the stats tab navigates to the stats screen and keeps the bottom bar`() {
        setContent()

        compose.onNodeWithContentDescription("Stats", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        screenTitleNode("Stats").assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the habi tab shows in the bar and navigates to the habi screen`() {
        setContent()
        // HabiAvatar's infinite bob/blink transitions never settle on their own — freeze the
        // clock BEFORE navigating there, so the framework's post-click idle-sync doesn't spin
        // forever trying to reach a steady state that never comes (T9 note).
        compose.mainClock.autoAdvance = false

        // The Habi tab's content description ("Habi") collides with HabiAvatar's own
        // (habi_avatar_cd is also "Habi") twice over here: once the Habi screen renders its own
        // avatar, AND already on Today, whose header now carries its own corner HabiAvatar (T14)
        // — scope to the bottom bar to pick the tab, not either avatar. Assert arrival by
        // testTag, not by content description, to avoid the same collision on the way in.
        compose
            .onNode(hasContentDescription("Habi") and hasAnyAncestor(hasTestTag("bottom-bar")), useUnmergedTree = true)
            .performClick()
        // Not waitForIdle(): with autoAdvance false it pumps no frames at all, so the nav
        // recomposition from the click above would never actually run. A couple of manual frames
        // is enough to let it settle (route change -> HabiScreen mounts) without ever giving the
        // clock a chance to auto-advance into HabiAvatar's infinite transition.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // QA 2026-08-23: HabiScreen gates its first frame on `loading` (calm paper, tag
        // "habi-loading") until the VM's combine emits — under this frame-pumped clock the
        // emission can land after our two frames, so arrival at the route is either tag.
        compose
            .onNode(hasTestTag("habi-loading") or hasTestTag("habi-screen"), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the bottom bar hides on the habit form but survives on stats`() {
        setContent()

        compose.onNodeWithContentDescription("New habit", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Stats", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a pending review request opens the review flow and hides the bar`() {
        // Set BEFORE setContent: BitoNavHost's LaunchedEffect must observe the already-pending
        // route on its very first composition, the same way a notification tap's Intent extra
        // would already be waiting when onCreate() first builds the NavHost.
        NavRequests.open("review")

        setContent()

        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertDoesNotExist()
        assertNull(NavRequests.pending.value)
    }

    @Test
    fun `a pending perfect day shows the sheet on today but not on the review route`() {
        setContentSeeded { seedPerfectDayToday(this) }

        // CelebrationsViewModel's uiState combines off Dispatchers.Default (real, not the
        // test's) — same hazard the settings-reminder test above documents, so waitForIdle
        // alone doesn't pump it. Wait for the sheet here FIRST so the state is provably pending
        // (and the flow has already emitted) before testing suppression below — otherwise an
        // absence assertion racing that same unpumped emission would pass just as well with a
        // broken suppression guard, proving nothing.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("perfect-day-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertExists()

        // Now that the state is known pending and already emitted, navigate to review the same
        // way a notification tap would post-composition: if the `currentRoute != "review"` guard
        // were broken, the sheet would still show here too.
        NavRequests.open("review")
        compose.waitForIdle()

        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertExists() // confirms the navigation actually landed
        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The onboarding half of the `currentRoute != "review"` guard's sibling check (see
     * [com.alvarotc.bito.ui.BitoNavHost]'s own comment on that `if`). Deliberately NOT a bare
     * absence assertion during onboarding — [CelebrationsUiState] combines off a real dispatcher
     * (same hazard "a pending perfect day shows the sheet on today but not on the review route"
     * documents above), so an absence check alone would pass just as well with a broken guard,
     * proving nothing. Walking the real flow to completion and then WAITING for the sheet to
     * appear is what forces that emission and proves the celebration stayed genuinely pending
     * rather than being lost — the exact behavior the M9 finding asked for: "celebrations stay
     * pending and fire after landing on Today".
     */
    @Test
    fun `a pending perfect day stays hidden behind onboarding and shows once today loads`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        runBlocking { seedPerfectDayToday(container) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()

        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick() // blank habit name still finishes

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        screenTitleNode("Today").assertExists()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("perfect-day-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertExists()
    }

    /**
     * The M9 finding's own guard ("celebrations stay pending and fire after landing on Today")
     * only ever had its VISIBLE half asserted (the test above): the sheet itself. Its silent half
     * — [CelebrationsViewModel]'s own habi cue — was never checked, so a regression that fired the
     * cue WHILE suppressed (behind onboarding) would have shipped mute. [CelebrationsViewModel.cue]
     * latches idempotently per signature ([CelebrationsViewModel.lastCued] is a `String?`, not a
     * counter), so "fires exactly once" is provable only as "still null at every suppressed
     * checkpoint below, then the one expected signature once Today loads" — the once-only property
     * itself is [CelebrationsViewModel.lastCuedSignature]'s own job and is already covered by
     * `CelebrationsViewModelTest`'s idempotency tests.
     */
    @Test
    fun `the celebration cue stays untouched while suppressed and fires once Today loads`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        runBlocking { seedPerfectDayToday(container) }
        val vmOwner = FakeViewModelStoreOwner()

        fun celebrations() = ViewModelProvider(vmOwner, CelebrationsViewModel.factory(container))[CelebrationsViewModel::class.java]

        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides vmOwner) {
                    BitoNavHost(container)
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(celebrations().lastCued) // suppressed behind onboarding: never cued yet

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        assertNull(celebrations().lastCued) // still on onboarding: still suppressed

        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick() // blank habit name still finishes

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        screenTitleNode("Today").assertExists()

        compose.waitUntil(timeoutMillis = 5_000) { celebrations().lastCued != null }
        val cutoff = runBlocking { container.settings.settings.first().dayCutoffMinutes }
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), cutoff, ZoneId.systemDefault())
        assertEquals("$today:perfect-day", celebrations().lastCued)
    }

    @Test
    fun `a fresh install opens on the onboarding route, not today`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertDoesNotExist()
        screenTitleNode("Today").assertDoesNotExist()
    }

    @Test
    fun `onboardingDone true opens straight on today`() {
        setContent() // seeds onboardingDone = true

        screenTitleNode("Today").assertExists()
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * M9.5 final-review Important #2: [com.alvarotc.bito.ui.onboarding.OnboardingReconciler.reconcile]
     * now runs INSIDE this gate, before `startDestination` is decided (see [BitoNavHost]'s own
     * comment on that `produceState` block) — so a v1/v2 restore's habits-but-`onboardingDone =
     * false` state resolves deterministically to "today," never a race the reconciler could lose
     * against `remember`.
     */
    @Test
    fun `a restorer with habits and onboarding not done lands on today, not onboarding`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        runBlocking { container.database.habitDao().upsert(habitEntity(id = "restored-habit")) }
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        waitPastLoadingGate()

        screenTitleNode("Today").assertExists()
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * M9.5 T6 (fix round 1): [NavRequests] must not navigate OVER onboarding — same philosophy as
     * [com.alvarotc.bito.ui.BitoNavHost]'s celebration guard (a mid-flow user shouldn't get
     * sandwiched into e.g. "review"), and now genuinely the same MECHANISM: `BitoNavHost` simply
     * does not call [NavRequests.consume] while `onboarding` is the current route, so
     * [NavRequests.pending] itself — a process-wide, rotation-surviving `MutableStateFlow` — stays
     * the one source of truth for "review is still waiting," same as [NavRequests] is asserted
     * elsewhere. Deliberately NOT a bare absence assertion followed by nothing else: proving the
     * route STAYS pending here is necessary but not sufficient (a build that dropped the request
     * entirely would look identical at this checkpoint too) — only watching it actually fire once
     * onboarding hands off to "today," and get consumed there, proves it survived intact. Same
     * reasoning "a pending perfect day stays hidden behind onboarding and shows once today loads"
     * already uses for the celebration sheet.
     */
    @Test
    fun `a pending review request stays held behind onboarding and opens once onboarding hands off`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        NavRequests.open("review")
        compose.waitForIdle()

        // Held, not navigated: onboarding is still the one showing and review never opened over it.
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertDoesNotExist()
        // Genuinely still pending (not consumed into any ephemeral state): this is what makes the
        // hold rotation-safe -- NavRequests.pending is the one and only source of truth throughout.
        assertEquals("review", NavRequests.pending.value)

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("review-seal", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("onboarding-screen", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("review-seal", useUnmergedTree = true).assertExists()
        assertNull(NavRequests.pending.value) // fired through the normal branch and consumed there
    }

    /**
     * M9.5 final-review Minor #6: extends the test above with a pending perfect-day celebration.
     * `nav.navigate("review")` (the held request firing) runs in the SAME recomposition that flips
     * `currentRoute` from "onboarding" to "today" — without `pendingRoute != null` folded into
     * [BitoNavHost]'s own `celebrationsSuppressed`, the celebration would get that one transient
     * "today" frame to itself, cue there (idempotently latched — see
     * [com.alvarotc.bito.ui.celebration.CelebrationsViewModel.cue]), then get suppressed again on
     * "review" and never cue a second time once genuinely shown after review closes — cued once,
     * silently, behind the transition; sheet later renders mute.
     */
    @Test
    fun `a celebration behind a held review request does not cue during the transient today frame`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        runBlocking { seedPerfectDayToday(container) }
        val vmOwner = FakeViewModelStoreOwner()

        fun celebrations() = ViewModelProvider(vmOwner, CelebrationsViewModel.factory(container))[CelebrationsViewModel::class.java]

        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides vmOwner) {
                    BitoNavHost(container)
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        NavRequests.open("review")
        compose.waitForIdle()
        assertNull(celebrations().lastCued) // suppressed behind onboarding: never cued yet

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("review-seal", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The held request fired straight through to review: the celebration never got a "today"
        // frame to itself, so it must still be un-cued and its sheet must not exist behind review.
        assertNull(celebrations().lastCued)
        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertDoesNotExist()

        // Leaving review (unsealed, via the header's back chevron) is the celebration's first
        // genuine chance to be seen.
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("perfect-day-sheet", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("perfect-day-sheet", useUnmergedTree = true).assertExists()
        assertTrue(celebrations().lastCued != null)
    }

    /**
     * The full first-run journey through the REAL [BitoNavHost] — the only place [done]'s own
     * `navigate("today") { popUpTo("onboarding") { inclusive = true } }` (wired in the "onboarding"
     * composable above) can actually be observed landing. `OnboardingScreenTest`'s own
     * "the first habit step creates the habit and completes onboarding" proves the write path and
     * [OnboardingUiState.done] in isolation, without a NavHost to navigate anywhere.
     */
    @Test
    fun `the first habit step creates and lands on today`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app) // onboardingDone defaults to false: nothing seeded here
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).performClick() // -> NAME
        compose.waitForIdle()
        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // NAME -> PERSONALITY
        compose.waitForIdle()
        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("onboarding-screen", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        screenTitleNode("Today").assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()

        val created = runBlocking { container.database.habitDao().all().single { it.name == "Beber agua" } }
        assertEquals(Metric.CHECK, created.metric)
    }
}
