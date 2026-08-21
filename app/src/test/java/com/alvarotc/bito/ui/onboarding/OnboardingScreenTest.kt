package com.alvarotc.bito.ui.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun newViewModel(name: String): OnboardingViewModel {
        val settings = SettingsRepository(settingsStore(name))
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
        // T7 owns the real 7e content — until then the step renders as a bare placeholder.
        compose.onNodeWithText("NAME", useUnmergedTree = true).assertExists()
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
}
