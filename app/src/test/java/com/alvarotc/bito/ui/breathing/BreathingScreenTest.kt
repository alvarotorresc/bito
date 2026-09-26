package com.alvarotc.bito.ui.breathing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BreathingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private var currentElapsed = 50_000_000L
    private var storeIndex = 0

    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private val liveViewModels = mutableListOf<BreathingViewModel>()

    private object NoMusic : BreathingMusic {
        override fun start() = Unit

        override fun stop() = Unit
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "breathing-screen-${storeIndex++}-$name.preferences_pb") }

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
        settingsRepo = SettingsRepository(store("settings"))
    }

    @After
    fun tearDown() {
        liveViewModels.forEach { it.viewModelScope.cancel() }
        liveViewModels.clear()
        Dispatchers.resetMain()
        db.close()
    }

    private fun newViewModel() =
        BreathingViewModel(
            BreathingRepository(db),
            settingsRepo,
            DomainStateRepository(db),
            RewardsRepository(db),
            NoMusic,
            now = { fixedNow },
            elapsed = { currentElapsed },
            zone = { ZoneId.of("UTC") },
        ).also { liveViewModels += it }

    private fun render(
        vm: BreathingViewModel,
        onClose: () -> Unit = {},
        buzz: () -> Unit = {},
    ) {
        compose.setContent {
            BitoTheme {
                BreathingScreen(viewModel = vm, onClose = onClose, animated = false, buzz = buzz)
            }
        }
        compose.waitForIdle()
    }

    /** Avanza el reloj de la sesion y deja correr un tick del ViewModel. */
    private fun advanceSession(millis: Long) {
        currentElapsed += millis
        dispatcher.scheduler.advanceTimeBy(BreathingViewModel.TICK_MS)
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun tag(name: String) = compose.onNodeWithTag(name, useUnmergedTree = true)

    private fun text(value: String) = compose.onNodeWithText(value, useUnmergedTree = true)

    @Test
    fun `at rest it shows the three chips, the chosen rhythm and Start`() {
        render(newViewModel())

        text("Calm down").assertExists()
        text("Sleep").assertExists()
        text("Focus").assertExists()
        text("4 in · 6 out · 2 min").assertExists()
        tag("breathing-start").assertExists()
        tag("breathing-phase").assertDoesNotExist()

        compose.onNodeWithText("Sleep").performClick()
        compose.waitForIdle()

        text("4 in · 7 hold · 8 out · 6 cycles").assertExists()
    }

    @Test
    fun `running shows the phase word, disables the chips and counts down`() {
        render(newViewModel())

        tag("breathing-start").performClick()
        compose.waitForIdle()

        tag("breathing-phase").assertExists()
        text("Breathe in").assertExists()
        text("2:00").assertExists()
        compose.onNodeWithText("Focus").assertIsNotEnabled()
        tag("breathing-start").assertDoesNotExist()

        advanceSession(4_000)

        text("Breathe out").assertExists()
        text("1:56").assertExists()
    }

    @Test
    fun `sleep counts cycles instead of a clock`() {
        render(newViewModel())
        compose.onNodeWithText("Sleep").performClick()
        compose.waitForIdle()

        tag("breathing-start").performClick()
        compose.waitForIdle()

        text("Cycle 1 of 6").assertExists()
        advanceSession(19_000)
        text("Cycle 2 of 6").assertExists()
    }

    @Test
    fun `stopping after ten seconds shows the closing line and the tally`() {
        render(newViewModel())
        tag("breathing-start").performClick()
        compose.waitForIdle()
        currentElapsed += 30_000

        tag("breathing-stop").performClick()
        compose.waitForIdle()

        tag("breathing-done-bubble").assertExists()
        text("There, champ.").assertExists()
        text("This week: 1 session · 1 min").assertExists()
        text("Since the start: 1 session · 1 min").assertExists()
        tag("breathing-again").assertExists()
        tag("breathing-done").assertExists()
    }

    @Test
    fun `stopping before ten seconds goes back to rest with no closing line`() {
        render(newViewModel())
        tag("breathing-start").performClick()
        compose.waitForIdle()
        currentElapsed += 5_000

        tag("breathing-stop").performClick()
        compose.waitForIdle()

        tag("breathing-start").assertExists()
        tag("breathing-done-bubble").assertDoesNotExist()
    }

    @Test
    fun `Done closes the screen`() {
        var closed = false
        render(newViewModel(), onClose = { closed = true })
        tag("breathing-start").performClick()
        compose.waitForIdle()
        currentElapsed += 30_000
        tag("breathing-stop").performClick()
        compose.waitForIdle()

        tag("breathing-done").performClick()
        compose.waitForIdle()

        assertTrue(closed)
    }

    @Test
    fun `the back arrow mid-session saves the session before closing`() {
        var closed = false
        render(newViewModel(), onClose = { closed = true })
        tag("breathing-start").performClick()
        compose.waitForIdle()
        currentElapsed += 30_000

        tag("breathing-back").performClick()
        compose.waitForIdle()

        assertTrue(closed)
        assertEquals(30, runBlocking { db.breathingSessionDao().all() }.single().durationSeconds)
    }

    @Test
    fun `each phase change buzzes once when vibration is on`() {
        var buzzes = 0
        render(newViewModel(), buzz = { buzzes++ })

        tag("breathing-start").performClick()
        compose.waitForIdle()
        assertEquals(1, buzzes)

        advanceSession(1_000)
        assertEquals(1, buzzes)
        advanceSession(3_000)
        assertEquals(2, buzzes)
    }

    @Test
    fun `no buzz at all when vibration is off`() {
        runBlocking { settingsRepo.update { it.copy(logHapticEnabled = false) } }
        var buzzes = 0
        render(newViewModel(), buzz = { buzzes++ })

        tag("breathing-start").performClick()
        compose.waitForIdle()
        advanceSession(4_000)

        assertEquals(0, buzzes)
    }

    @Test
    fun `the music button says what tapping it does`() {
        render(newViewModel())

        compose.onNodeWithContentDescription("Play background music").assertExists()
        tag("breathing-music").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Turn music off").assertExists()
    }

    @Test
    fun `a rotation is not a real stop, and a missing activity is`() {
        val rotating =
            object : Activity() {
                override fun isChangingConfigurations() = true
            }
        val stopping =
            object : Activity() {
                override fun isChangingConfigurations() = false
            }

        assertFalse(isRealStop(rotating))
        assertTrue(isRealStop(stopping))
        assertTrue(isRealStop(null))
    }

    @Test
    fun `findActivity unwraps context wrappers`() {
        val activity = object : Activity() {}
        val app = ApplicationProvider.getApplicationContext<Context>()

        assertSame(activity, ContextWrapper(ContextWrapper(activity)).findActivity())
        assertNull(app.findActivity())
    }

    @Test
    fun `the remaining clock is minutes and zero-padded seconds`() {
        assertEquals("2:00", formatRemaining(120))
        assertEquals("1:05", formatRemaining(65))
        assertEquals("0:01", formatRemaining(1))
    }
}
