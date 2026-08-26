package com.alvarotc.bito.ui.review

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.ZoneId
import kotlin.test.assertTrue

/**
 * Drives the real [ReviewScreen] over an in-memory Room database, following
 * [com.alvarotc.bito.ui.today.TodayScreenTest]'s harness — both the main dispatcher and Room's
 * executors run on the same [UnconfinedTestDispatcher] so every VM write, Room emission and
 * recomposition settles synchronously within a plain `compose.waitForIdle()`.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
    private var storeIndex = 0

    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "review-screen-${storeIndex++}.preferences_pb") }

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

    private fun setContent(
        onClose: () -> Unit = {},
        backDispatcherOwner: OnBackPressedDispatcherOwner? = null,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val domainState = DomainStateRepository(db)
        val habits = HabitsRepository(db)
        val journal = JournalRepository(db)
        settingsRepo = SettingsRepository(settingsStore())
        val rewards = RewardsRepository(db)
        val reconciler = PointsReconciler(domainState, rewards)
        val habiSounds = HabiSounds(context, settingsRepo, dispatcher = dispatcher)
        val vm =
            ReviewViewModel(
                domainState,
                habits,
                journal,
                settingsRepo,
                reconciler,
                rewards,
                habiSounds,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
        compose.setContent {
            BitoTheme {
                if (backDispatcherOwner != null) {
                    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backDispatcherOwner) {
                        ReviewScreen(viewModel = vm, onClose = onClose)
                    }
                } else {
                    ReviewScreen(viewModel = vm, onClose = onClose)
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Minimal [OnBackPressedDispatcherOwner] the test drives directly: `createComposeRule()` has
     * no `Espresso.pressBack()` and no exposed `.activity`, so the only way to trigger
     * [androidx.activity.compose.BackHandler]'s callback deterministically is to hand the
     * composition our OWN dispatcher instance and invoke it ourselves from outside the
     * composition. [lifecycle] only exists to satisfy the interface — [BackHandler] actually
     * reads `LocalLifecycleOwner`, which the compose test host already provides and resumes.
     */
    private class FakeBackDispatcherOwner : OnBackPressedDispatcherOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = lifecycleRegistry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()

        init {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    @Test
    fun `undone habits render as rows and done ones do not`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
            HabitsRepository(db).create(
                habitEntity(id = "h2", name = "Agua", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
            db.entryDao().insert(entryEntity(id = "e-h2", habitId = "h2", logicalDay = today, value = 1))
        }
        setContent()

        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("review-row-h2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping Hecho logs today and the row disappears`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
        }
        setContent()

        compose.onNodeWithTag("review-done-h1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all() }
        assertTrue(entries.any { it.habitId == "h1" && it.logicalDay == today })
        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `acknowledging hides the row and writes nothing`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
        }
        setContent()

        compose.onNodeWithTag("review-ack-h1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertDoesNotExist()
        val entries = runBlocking { db.entryDao().all() }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `tapping add on a counter row logs the step amount`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    step = 2,
                    unit = "vasos",
                    createdOnDay = today,
                ),
            )
        }
        setContent()

        // LoggingRow (COUNTER, AT_LEAST — not CHECK/ABSTINENCE, so this is the "else" branch of
        // ReviewRowCard's when): step is 2, not 1, so a hardcoded amount would fail this.
        compose.onNodeWithTag("review-add-h1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all() }.filter { it.habitId == "h1" }
        assertEquals(listOf(2), entries.map { it.value })
        // 2 < target 8: still open, still asking.
        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tapping add on a limit row logs usage and blowing the limit hides the row`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "h1",
                    name = "Fumar",
                    metric = Metric.COUNT,
                    direction = Direction.AT_MOST,
                    target = 3,
                    step = 5,
                    unit = "cigarros",
                    createdOnDay = today,
                ),
            )
        }
        setContent()

        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertExists()
        // LimitRow (AT_MOST): its own "add" is the only affordance that logs usage against the
        // limit — there's no relapse sheet here (that's ABSTINENCE-only, covered below). Logging
        // past the limit is this row's equivalent of a relapse: Compliance.complianceOf's AT_MOST
        // branch fails the moment progress exceeds target, even unsealed.
        compose.onNodeWithTag("review-add-h1", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all() }.filter { it.habitId == "h1" }
        assertEquals(listOf(5), entries.map { it.value })
        // 5 > target 3: failed cards never render as review rows (reviewRowsOf's `!card.failed`).
        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping He recaido opens the relapse sheet and confirming logs it`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Fumar", metric = Metric.CHECK, direction = Direction.ZERO, target = 0, createdOnDay = today),
            )
        }
        setContent()

        compose.onNodeWithTag("review-relapse-h1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Log a relapse?", useUnmergedTree = true).assertExists()
        // Not performClick(): synthesized touch gestures don't reach a button inside a
        // ModalBottomSheet under this Robolectric harness (same limitation documented in
        // DetailScreenTest's freezer-sheet test) — invoking the node's own OnClick action
        // directly is what actually proves tapping "Log relapse" calls onConfirm. Merged tree
        // (no useUnmergedTree) so the match lands on the Button's own node, which is what
        // actually carries the OnClick action — the unmerged Text child underneath does not.
        val confirm = compose.onNodeWithText("Log relapse")
        confirm.assertExists()
        confirm.fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all() }
        assertTrue(entries.any { it.habitId == "h1" && it.value == 1 })
        compose.onNodeWithTag("review-row-h1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the seal button seals today`() {
        setContent()

        compose.onNodeWithTag("review-seal", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val seals = runBlocking { db.daySealDao().all() }
        assertEquals(today, seals.single().logicalDay)
    }

    @Test
    fun `with nothing pending the empty state shows`() {
        setContent()

        compose.onNodeWithTag("review-empty", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `sealed today shows the sealed state with points and streaks`() {
        runBlocking {
            db.daySealDao().insert(daySealEntity(logicalDay = today, sealedAtMillis = fixedNow))
        }
        setContent()

        compose.onNodeWithTag("review-ring", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("review-points", useUnmergedTree = true).assertExists()
        // No demandable habit today: nothing advanced a streak, so the chip stays hidden.
        compose.onNodeWithTag("review-streaks", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a perfect sealed day shows the perfect title and marks the celebration`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            db.daySealDao().insert(daySealEntity(logicalDay = today, sealedAtMillis = fixedNow))
        }
        setContent()

        compose.onNodeWithText("Perfect day!", useUnmergedTree = true).assertExists()
        assertEquals(today, runBlocking { settingsRepo.settings.first() }.perfectDayCelebratedDay)
    }

    @Test
    fun `closing marks badges as seen`() {
        runBlocking {
            db.daySealDao().insert(daySealEntity(logicalDay = today, sealedAtMillis = fixedNow))
        }
        var closed = false
        setContent(onClose = { closed = true })

        compose.onNodeWithTag("review-close", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertTrue(closed)
        assertEquals(fixedNow, runBlocking { settingsRepo.settings.first() }.badgesSeenUntilMillis)
    }

    @Test
    fun `leaving the sealed day by back also marks badges as seen`() {
        runBlocking {
            db.daySealDao().insert(daySealEntity(logicalDay = today, sealedAtMillis = fixedNow))
        }
        var closed = false
        val backOwner = FakeBackDispatcherOwner()
        setContent(onClose = { closed = true }, backDispatcherOwner = backOwner)

        compose.runOnIdle {
            backOwner.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        assertTrue(closed)
        assertTrue(runBlocking { settingsRepo.settings.first() }.badgesSeenUntilMillis > 0)
    }

    @Test
    fun `a row's name and streak merge into one talkback stop`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            // today itself stays undone (PENDING bridges, per Streaks.kt's own KDoc) so the row
            // still renders in review — the streak walks back through the 3 prior done days.
            for (day in (today - 3)..(today - 1)) {
                db.entryDao().insert(entryEntity(id = "e-med-$day", habitId = "h1", logicalDay = day, value = 1))
            }
        }
        setContent()

        // [E]: name Text + StreakChip (Icon(Flame, null) + count Text) used to be 2 separate
        // TalkBack stops. MERGED tree (no useUnmergedTree) on purpose: proves the override landed
        // on the row's own node, not a child underneath it — same evidence shape as StatsScreen's
        // "the streak wall card announces the streak" test.
        compose.onNodeWithTag("review-row-name-h1").assertContentDescriptionEquals("Meditar, streak 3")
        compose.onNodeWithTag("review-row-name-h1").onChildren().assertCountEquals(0)
    }
}
