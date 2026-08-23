package com.alvarotc.bito.ui.onboarding

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habitform.HabitPreset
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * Drives the real onboarding screen (7a welcome + 7b-7d story) over an isolated in-memory VM,
 * same recipe as [OnboardingViewModelTest] — a fresh Room db + tmp DataStore per test, not
 * AppContainer. The full-height qualifier (matches most other screen tests) keeps "Empezar"
 * on-screen without needing a scroll, since [OnboardingScreen]'s welcome layout pins it to the
 * bottom with a weighted spacer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BitoDatabase

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    /**
     * [seedSettings] runs (via [runBlocking]) BEFORE the VM is constructed, so its `init` block's
     * one-shot `settings.settings.first()` read (see [OnboardingViewModel]'s own KDoc) already sees
     * whatever it wrote — needed by tests that plant a `languageTag` pre-existing settings would
     * carry (a reinstall over restored data), rather than one the VM's own [OnboardingViewModel.setLanguage]
     * would set live.
     */
    private fun newViewModel(
        name: String,
        seedSettings: suspend SettingsRepository.() -> Unit = {},
    ): OnboardingViewModel {
        val settings = SettingsRepository(settingsStore(name))
        runBlocking { settings.seedSettings() }
        val habits = HabitsRepository(db)
        val reconciler = PointsReconciler(DomainStateRepository(db), RewardsRepository(db))
        return OnboardingViewModel(settings, habits, reconciler)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * Minimal [OnBackPressedDispatcherOwner] the test drives directly: `createComposeRule()` has
     * no `Espresso.pressBack()` and no exposed `.activity`, so the only way to trigger
     * [androidx.activity.compose.BackHandler]'s callback deterministically is to hand the
     * composition our OWN dispatcher instance and invoke it ourselves from outside the
     * composition. Same recipe as `ReviewScreenTest`'s own copy — [lifecycle] only exists to
     * satisfy the interface, [BackHandler] actually reads `LocalLifecycleOwner`, which the compose
     * test host already provides and resumes.
     */
    private class FakeBackDispatcherOwner : OnBackPressedDispatcherOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = lifecycleRegistry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()

        init {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    /** WELCOME -> ... -> FIRST_HABIT, the same jump every 7g test below needs — a name is required
     * ([StoryPagerScaffold]'s own name-gate) even though this task's step never reads it again. */
    private fun goToFirstHabit(vm: OnboardingViewModel) {
        vm.setName("Alvaro")
        vm.skipStory() // -> NAME
        compose.waitForIdle()
        vm.next() // NAME -> PERSONALITY
        compose.waitForIdle()
        vm.next() // PERSONALITY -> FIRST_HABIT
        compose.waitForIdle()
    }

    @Test
    fun `welcome shows both language chips and starts the flow`() {
        val vm = newViewModel("onboarding-screen-welcome")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Español", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("English", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Get started", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(OnboardingStep.STORY_1, vm.uiState.value.step)
        compose.onNodeWithText("Habi wasn't always like this", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a system locale outside es and en highlights the english chip`() {
        // languageTag stays null ("follow the system") — only the JVM default locale changes, so
        // this exercises WelcomeScene's own fallback, not OnboardingViewModel/setLanguage at all.
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try {
            val vm = newViewModel("onboarding-screen-locale-fallback")
            compose.setContent {
                BitoTheme {
                    OnboardingScreen(vm)
                }
            }
            compose.waitForIdle()

            // Merged tree (no useUnmergedTree) on purpose here: `selectable()` merges its Text
            // child's semantics up into its own node, which is where `Selected` actually lives —
            // the unmerged child Text node this file's other assertions match against never
            // carries it.
            compose.onNodeWithText("English").assertIsSelected()
            compose.onNodeWithText("Español").assertIsNotSelected()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    /**
     * `languageTag = "fr"` is outside `locales_config`'s {es, en} — the same bucket Ajustes'
     * language row already treats as "system" (its `when` falls to the `else` branch for exactly
     * this tag). Before this fix, WelcomeScene took the raw out-of-set tag at face value instead of
     * falling through to the resolved system locale like Settings does, so a `languageTag` restored
     * from a backup (or carried over from a device whose language `locales_config` doesn't declare)
     * always lit the EN chip regardless of what the device actually resolves to — a lie this test
     * closes. Es is the system default here specifically so this doesn't collapse into the
     * pre-existing "system default outside es/en falls back to en" case above.
     */
    @Test
    fun `an out-of-set stored tag highlights the resolved locale chip`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("es"))
        try {
            val vm = newViewModel("onboarding-screen-out-of-set-tag") { update { it.copy(languageTag = "fr") } }
            compose.setContent {
                BitoTheme {
                    OnboardingScreen(vm)
                }
            }
            compose.waitForIdle()

            compose.onNodeWithText("Español").assertIsSelected()
            compose.onNodeWithText("English").assertIsNotSelected()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `the story pages advance and can be skipped to the name step`() {
        val vm = newViewModel("onboarding-screen-story")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.next() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Habi wasn't always like this", useUnmergedTree = true).assertExists()

        vm.next() // STORY_1 -> STORY_2
        compose.waitForIdle()
        compose.onNodeWithText(
            "Habi wanted to change, but didn't know where to start",
            useUnmergedTree = true,
        ).assertExists()

        vm.next() // STORY_2 -> STORY_3
        compose.waitForIdle()
        compose.onNodeWithText("Until Habi found a system", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Small steps, every day. It changed everything.", useUnmergedTree = true).assertExists()

        vm.skipStory()
        compose.waitForIdle()

        assertEquals(OnboardingStep.NAME, vm.uiState.value.step)
        compose.onNodeWithText("I've been through this. Now it's your turn.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `skip is not offered outside the story`() {
        val vm = newViewModel("onboarding-screen-skip")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Skip", useUnmergedTree = true).assertDoesNotExist()

        vm.next() // WELCOME -> STORY_1
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).assertExists()

        vm.skipStory() // STORY_1 -> NAME
        compose.waitForIdle()
        compose.onNodeWithText("Skip", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the name step gates continue until a name is typed`() {
        val vm = newViewModel("onboarding-screen-name-gate")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.skipStory() // -> NAME
        compose.waitForIdle()

        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).assertIsNotEnabled()

        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()

        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).assertIsEnabled()

        compose.onNodeWithTag("onb-continue", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(OnboardingStep.PERSONALITY, vm.uiState.value.step)
    }

    @Test
    fun `a swipe cannot bypass the name gate with a blank name`() {
        val vm = newViewModel("onboarding-screen-name-swipe-gate")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.skipStory() // -> NAME, name still blank
        compose.waitForIdle()

        // The pager itself now genuinely scrolls forward under this gesture (unlike the old
        // userScrollEnabled=false gate) and settles one page past NAME before snapping back --
        // waitForIdle() has to pump that animateScrollToPage snap-back, not just a plain
        // recomposition, which is why this assertion is on the VM's step rather than requiring
        // zero visible motion.
        compose.onNodeWithTag("onb-pager", useUnmergedTree = true).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(OnboardingStep.NAME, vm.uiState.value.step)
    }

    @Test
    fun `a blank name still allows swiping back to the story`() {
        val vm = newViewModel("onboarding-screen-name-swipe-back")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.skipStory() // -> NAME, name still blank
        compose.waitForIdle()

        // Only the FORWARD direction is gated on a non-blank name -- backward has nothing to do
        // with that gate and must keep working even while the field is empty.
        compose.onNodeWithTag("onb-pager", useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(OnboardingStep.STORY_3, vm.uiState.value.step)
    }

    /**
     * M9 final review minor 5. [OnboardingSwipeFadeLatch] is plain Kotlin, not Compose state
     * (see its own KDoc), specifically so this doesn't need a compose rule or any of the timing
     * gymnastics [androidx.compose.ui.test.junit4.ComposeContentTestRule] would need to catch a
     * 200ms alpha animation mid-flight — Compose's test semantics tree doesn't expose raw alpha
     * at all, so asserting the actual pixel fade isn't cheaply doable here. This instead proves
     * the DECISION the settle collector and the freshly-mounted page actually make: unmarked
     * (first mount, a button/skip tap) always plays the fade; a swipe settle's [markSwipe] makes
     * the very next consume skip it exactly once, then reverts to the default.
     */
    @Test
    fun `a swipe settle does not replay the entrance fade`() {
        val latch = OnboardingSwipeFadeLatch()

        assertFalse("unmarked: first mount / a button-driven advance must still fade", latch.consumeSkipsFade())

        latch.markSwipe()
        assertTrue("a swipe settle's mark must make the next mount skip its fade", latch.consumeSkipsFade())

        assertFalse("consuming clears the mark: the NEXT mount defaults back to fading", latch.consumeSkipsFade())
    }

    @Test
    fun `system back steps back one beat during the flow instead of exiting`() {
        val vm = newViewModel("onboarding-screen-back-handler")
        val backOwner = FakeBackDispatcherOwner()
        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                    OnboardingScreen(vm)
                }
            }
        }
        compose.waitForIdle()
        vm.next() // WELCOME -> STORY_1
        compose.waitForIdle()

        compose.runOnIdle {
            backOwner.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)
    }

    @Test
    fun `typing a name makes habi react`() {
        val vm = newViewModel("onboarding-screen-name-reaction")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.skipStory() // -> NAME
        compose.waitForIdle()

        compose.onNodeWithText("I like it. We're going to do great things.", substring = true, useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("onb-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val reaction = context.getString(R.string.onb_name_reaction, "Alvaro")
        compose.onNodeWithText(reaction, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `picking a personality changes the live bubble voice`() {
        val vm = newViewModel("onboarding-screen-personality-voice")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.setName("Alvaro")
        vm.skipStory() // -> NAME
        compose.waitForIdle()
        vm.next() // NAME -> PERSONALITY
        compose.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val neutraGreeting = context.getString(R.string.onb_personality_preview_neutra, "Alvaro")
        compose.onNodeWithText(neutraGreeting, useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("onb-personality-card-sargento", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val sargentoGreeting = context.getString(R.string.onb_personality_preview_sargento, "Alvaro")
        compose.onNodeWithText(neutraGreeting, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(sargentoGreeting, useUnmergedTree = true).assertExists()
        assertEquals(Personality.SARGENTO, vm.uiState.value.personality)
    }

    /**
     * The genuine end-to-end assertion (habitDone -> "today" actually rendering) needs the real
     * [com.alvarotc.bito.ui.BitoNavHost] this file's own [OnboardingScreen]-only setContent never
     * mounts — see `BitoNavHostTest`'s own "the first habit step creates and lands on today" for
     * that. This one instead proves 7g's own contribution: filling the habit-name field and
     * tapping "Crear y empezar" drives [OnboardingViewModel.finish] and creates the habit through
     * the real write path, ending in [OnboardingUiState.done].
     */
    @Test
    fun `the first habit step creates the habit and completes onboarding`() {
        val vm = newViewModel("onboarding-screen-first-habit")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        // Default preset (DAILY_CHECK) needs no pill tap — matches the mockup's own selected state.
        compose.onNodeWithTag("onb-habit-name-field", useUnmergedTree = true).performTextInput("Beber agua")
        compose.waitForIdle()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { vm.uiState.value.done }

        val created = runBlocking { db.habitDao().all().single { it.name == "Beber agua" } }
        assertEquals(Metric.CHECK, created.metric)
        assertEquals(1, created.target)
    }

    @Test
    fun `an empty habit name still finishes the onboarding`() {
        val vm = newViewModel("onboarding-screen-empty-habit")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        // The ⚑CALL ruling from T5: a blank habit name is a skip path, not an error -- the button
        // reads "Empezar"/"Start" instead of "Crear y empezar", but stays enabled and still finishes.
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithText(context.getString(R.string.onb_habit_start), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithTag("onb-create-start", useUnmergedTree = true).performClick()

        compose.waitUntil(timeoutMillis = 5_000) { vm.uiState.value.done }

        assertTrue(vm.uiState.value.done)
        assertEquals(emptyList<String>(), runBlocking { db.habitDao().all() }.map { it.name })
    }

    @Test
    fun `the widget hint is present`() {
        val vm = newViewModel("onboarding-screen-widget-hint")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        compose.onNodeWithTag("onb-widget-hint", useUnmergedTree = true).assertExists()
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose
            .onNodeWithText(context.getString(R.string.onb_habit_widget_bold), substring = true, useUnmergedTree = true)
            .assertExists()
    }

    /**
     * The ledger warning's own scenario: onboarding has no quit-mode picker of its own, so
     * [OnboardingViewModel.finish] always builds a TOTAL/abstinence QUIT habit (target pinned to 0
     * regardless of anything picked here — see [OnboardingViewModelTest]'s VM-level proof of that).
     * The goal row must not lie about it with a numeric "0 vez al día" — it re-labels instead,
     * naming the mode with the exact same string the real form's own Total/Limit pills use.
     */
    @Test
    fun `the quit preset relabels the goal instead of showing a numeric target`() {
        val vm = newViewModel("onboarding-screen-quit-goal")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        val context = ApplicationProvider.getApplicationContext<Context>()
        // DAILY_CHECK (the default preset) shows the static "1 vez al día"/"1 time a day" goal.
        compose.onNodeWithText("1", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.QUIT.name}", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(HabitPreset.QUIT, vm.uiState.value.habitKind)
        compose.onNodeWithText("1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.quit_total), useUnmergedTree = true).assertExists()
    }

    /**
     * [C]: the check-mark on the active preset is an `Icon(contentDescription = null)`, invisible
     * to TalkBack — every pill used to read "<label>, Button" with no selection state, same gap
     * `HabitFormScreen`'s own `PresetPills` had.
     */
    @Test
    fun `the habit preset pills announce which one is selected`() {
        val vm = newViewModel("onboarding-screen-preset-selected")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        // DAILY_CHECK is the form's own default preset (no pill tap needed to reach it).
        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.DAILY_CHECK.name}", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.QUANTITY.name}", useUnmergedTree = true).assertIsNotSelected()

        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.QUANTITY.name}", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.QUANTITY.name}", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.DAILY_CHECK.name}", useUnmergedTree = true).assertIsNotSelected()
    }

    /** [A]: `GoalStepChip`'s Minus/Plus `Icon`s used to carry `contentDescription = null`. */
    @Test
    fun `the goal stepper chips carry real action descriptions`() {
        val vm = newViewModel("onboarding-screen-goal-stepper-cd")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        goToFirstHabit(vm)

        compose.onNodeWithTag("onb-habit-preset-${HabitPreset.QUANTITY.name}", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // MERGED tree (no useUnmergedTree) on purpose: the description lives on the Icon child,
        // merged up onto GoalStepChip's own tagged Box via its `.clickable`'s auto-merge — the
        // same reason this file's `LanguageChip`/`selectable()` assertions stay off the unmerged
        // tree (see the locale-fallback test's own comment on that).
        compose.onNodeWithTag("onb-goal-minus").assertContentDescriptionEquals("Decrease goal")
        compose.onNodeWithTag("onb-goal-plus").assertContentDescriptionEquals("Increase goal")
    }

    /**
     * [D]/pager state: `PagerDots` used to carry no text at all, so nothing in the flow announced
     * WHICH step was showing (only the "Seguir"/"Saltar" buttons and the story headline, neither
     * naming a position). One aggregate description on the whole row, updating as the pager moves.
     */
    @Test
    fun `the pager dots announce which step is showing`() {
        val vm = newViewModel("onboarding-screen-pager-dots")
        compose.setContent {
            BitoTheme {
                OnboardingScreen(vm)
            }
        }
        compose.waitForIdle()
        vm.next() // WELCOME -> STORY_1 (pageIndex 0 of 6)
        compose.waitForIdle()

        compose.onNodeWithTag("onb-pager-dots", useUnmergedTree = true).assertContentDescriptionEquals("Step 1 of 6")

        vm.skipStory() // -> NAME (pageIndex 3 of 6)
        compose.waitForIdle()

        compose.onNodeWithTag("onb-pager-dots", useUnmergedTree = true).assertContentDescriptionEquals("Step 4 of 6")
    }
}
