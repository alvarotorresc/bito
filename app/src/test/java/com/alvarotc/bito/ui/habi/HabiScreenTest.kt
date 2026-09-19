package com.alvarotc.bito.ui.habi

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

/**
 * [PersonalityPills] standalone — same reasoning [StoreSectionTest] gives for testing
 * [StoreSection] without a full [HabiScreen] (no [HabiViewModel], no real [HabiAvatar] bob/blink
 * transition to fight). It's `internal` (not `private`) precisely so this test can reach it
 * directly.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HabiScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    // Local midnight — the default reviewTimeMinutes (21:30) is still hours away, so a fresh
    // settings store lands AWAKE with zero habits. The WAITING test overrides reviewTimeMinutes
    // to 0 instead of seeding habits/entries, since HabiDay.phaseOf enters WAITING "queden cosas
    // o no haya ninguna" once minutesOfDay >= reviewTimeMinutes.
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")

    /** [db] and [settingsRepo] stay reachable after mounting [vm] so a test can seed/assert on them. */
    private data class HabiScreenHarness(
        val db: BitoDatabase,
        val vm: HabiViewModel,
        val settingsRepo: SettingsRepository,
    )

    /** Wires a real [HabiViewModel] over an in-memory Room DB, [HabiViewModelTest]'s harness. */
    private fun habiViewModel(
        dispatcher: TestDispatcher,
        storeName: String,
        reviewTimeMinutes: Int? = null,
    ): HabiScreenHarness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        val settingsRepo =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(dispatcher + Job()),
                ) { File(tmp.root, "$storeName.preferences_pb") },
            )
        if (reviewTimeMinutes != null) {
            runBlocking { settingsRepo.update { it.copy(reviewTimeMinutes = reviewTimeMinutes) } }
        }
        val domainStateRepo = DomainStateRepository(db)
        val rewardsRepo = RewardsRepository(db)
        val reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        val habiSounds = HabiSounds(context, settingsRepo, dispatcher = dispatcher)
        val vm =
            HabiViewModel(
                domainStateRepo,
                rewardsRepo,
                settingsRepo,
                reconciler,
                habiSounds,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
        return HabiScreenHarness(db, vm, settingsRepo)
    }

    @Test
    fun `waiting for the close says its line, once`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val (db, vm, _) = habiViewModel(dispatcher, "habi-screen-waiting", reviewTimeMinutes = 0)
        try {
            compose.setContent { BitoTheme { HabiScreen(vm) } }
            compose.waitForIdle()

            compose.onNodeWithTag("habi-day-line", useUnmergedTree = true).assertExists()
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    @Test
    fun `an awake day says nothing about itself`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val (db, vm, _) = habiViewModel(dispatcher, "habi-screen-awake")
        try {
            compose.setContent { BitoTheme { HabiScreen(vm) } }
            compose.waitForIdle()

            compose.onNodeWithTag("habi-day-line", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    /**
     * Regression for the fix commit: [HabiViewModel.onDayLineSeen] must fire ONLY when the
     * WAITING line was actually showing on dispose — never off the back of ASLEEP's own line,
     * which needs no marker (sealing is terminal within a day). Toggling [showScreen] off is what
     * disposes [HabiScreen]'s composition and runs its `DisposableEffect.onDispose`.
     */
    @Test
    fun `dismounting while asleep leaves the waiting marker untouched`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val (db, vm, settingsRepo) = habiViewModel(dispatcher, "habi-screen-asleep-marker")
        try {
            val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
            runBlocking { db.daySealDao().insert(daySealEntity(logicalDay = today)) }

            val showScreen = mutableStateOf(true)
            compose.setContent { BitoTheme { if (showScreen.value) HabiScreen(vm) } }
            compose.waitForIdle()
            compose.onNodeWithTag("habi-day-line", useUnmergedTree = true).assertExists()

            showScreen.value = false
            compose.waitForIdle()

            assertEquals(-1, runBlocking { settingsRepo.settings.first().habiWaitingSaidDay })
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    @Test
    fun `dismounting while waiting writes today into the marker`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val (db, vm, settingsRepo) = habiViewModel(dispatcher, "habi-screen-waiting-marker", reviewTimeMinutes = 0)
        try {
            val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

            val showScreen = mutableStateOf(true)
            compose.setContent { BitoTheme { if (showScreen.value) HabiScreen(vm) } }
            compose.waitForIdle()
            compose.onNodeWithTag("habi-day-line", useUnmergedTree = true).assertExists()

            showScreen.value = false
            compose.waitForIdle()

            assertEquals(today, runBlocking { settingsRepo.settings.first().habiWaitingSaidDay })
        } finally {
            Dispatchers.resetMain()
            db.close()
        }
    }

    @Test
    fun `the active personality pill is announced as selected, not just clickable`() {
        // Before the [C] fix, the active pill skipped `.clickable` entirely — a TalkBack pass
        // found only the 2 non-selected options, no sign a 3rd, active one existed.
        compose.setContent {
            BitoTheme {
                PersonalityPills(selected = Personality.NEUTRA, onSelect = {})
            }
        }

        compose.onNodeWithText("Neutra").assertIsSelected()
        compose.onNodeWithText("Sargento").assertIsNotSelected()
        compose.onNodeWithText("Cheerleader").assertIsNotSelected()
    }

    @Test
    fun `tapping a different pill selects it`() {
        var selected: Personality? = null
        compose.setContent {
            BitoTheme {
                PersonalityPills(selected = Personality.NEUTRA, onSelect = { selected = it })
            }
        }

        compose.onNodeWithText("Sargento").performClick()

        assertEquals(Personality.SARGENTO, selected)
    }

    @Test
    fun `the points sheet lists the economy values, never hardcoded copy`() {
        compose.setContent {
            BitoTheme {
                PointsInfoSheet(economy = EconomyConfig(), onDismiss = {})
            }
        }

        compose.onNodeWithTag("points-info-sheet").assertExists()
        compose.onNodeWithText("+3").assertExists() // perfect day, straight from EconomyConfig
        compose.onNodeWithText("+300").assertExists() // the 365-day streak milestone
        compose.onNodeWithText("a freezer costs 100 pts").assertExists()
    }

    @Test
    fun `the balance chip invokes its click-through`() {
        var opened = false
        compose.setContent {
            BitoTheme {
                BalanceChip(balance = 42, onClick = { opened = true })
            }
        }

        compose.onNodeWithTag("balance-chip").performClick()

        assertEquals(true, opened)
    }
}
