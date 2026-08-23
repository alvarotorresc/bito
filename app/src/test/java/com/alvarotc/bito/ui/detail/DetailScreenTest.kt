package com.alvarotc.bito.ui.detail

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.habi.HabiSounds
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

/**
 * Drives the real [DetailScreen] over an in-memory Room database, following
 * [com.alvarotc.bito.ui.today.TodayScreenTest]'s harness: both Room's executors and the main
 * dispatcher share one [UnconfinedTestDispatcher] so `uiState` settles synchronously on
 * [androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForIdle] — see
 * [DetailViewModelTest]'s header comment for why the [kotlinx.coroutines.test.StandardTestDispatcher]
 * VM-only harness cannot observe `uiState` the same way.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private var storeIndex = 0
    private var habiOpened = false

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "detail-screen-${storeIndex++}.preferences_pb") }

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

    private fun setContent(habitId: String) {
        val habits = HabitsRepository(db)
        val journal = JournalRepository(db)
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore())
        val reconciler = PointsReconciler(domainState, RewardsRepository(db))
        val habiSounds = HabiSounds(ApplicationProvider.getApplicationContext(), settings, dispatcher = dispatcher)
        val vm =
            DetailViewModel(
                habitId,
                domainState,
                habits,
                journal,
                RewardsRepository(db),
                settings,
                reconciler,
                habiSounds,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
        compose.setContent {
            BitoTheme {
                DetailScreen(viewModel = vm, onBack = {}, onEdit = {}, onOpenHabi = { habiOpened = true })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `tapping a heatmap day opens the day sheet`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 1, value = 1))
        }
        setContent("h1")

        compose.onNodeWithTag("heatmap-day-${today - 1}", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("day-sheet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the monument card merges its unit line and record chip into one TalkBack stop`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent("h1")

        // Before the fix these were two separate semantics nodes (Text + RecordChip's own Row) —
        // same node id now proves mergeDescendants folded them into one TalkBack stop.
        val unitNode = compose.onNodeWithText("days").fetchSemanticsNode()
        val recordNode = compose.onNodeWithText("Record:", substring = true).fetchSemanticsNode()
        assertEquals(unitNode.id, recordNode.id)
    }

    @Test
    fun `the next-month chevron is truly disabled, not just tinted, once the displayed month reaches today's`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        // The screen opens on today's own calendar month, so the next-month chevron starts
        // disabled with no navigation needed — PlainIconButton's enabled param must reach
        // Modifier.clickable so this is real a11y semantics, not just a dimmed icon that still
        // eats the tap in its handler.
        setContent("h1")

        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    @Test
    fun `a counter daily habit's failed day offers the freezer row`() {
        runBlocking {
            HabitsRepository(db).create(
                // COUNT metric == CardKind.COUNTER; no entry on today - 2 with an AT_LEAST target
                // leaves that day FAILED, the eligibility FreezerEngine (and the chip) already
                // agree on — this is the day sheet regression: freezerOffered reaches DaySheet
                // but the COUNTER branch used to drop it silently instead of offering the row.
                habitEntity(id = "h1", name = "Agua", metric = Metric.COUNT, target = 8, createdOnDay = today - 5),
            )
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(id = "buy1", delta = 0, reason = PointsReason.BUY_FREEZER, refId = null, logicalDay = today - 5),
            )
        }
        setContent("h1")

        compose.onNodeWithTag("heatmap-day-${today - 2}", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val freezerRow = compose.onNodeWithText("Use a freezer (you have 1)")
        freezerRow.assertExists()
        // Not performClick(): synthesized touch gestures don't reach a button inside a
        // ModalBottomSheet under this Robolectric harness (confirmed in isolation — the gesture
        // reports success against a displayed, clickable node but never runs the callback), so
        // invoking the row's own OnClick action directly is what actually proves tapping it calls
        // onUseFreezer.
        freezerRow.fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        compose.waitForIdle()

        runBlocking {
            assertEquals("h1", db.freezerUseDao().all().single().habitId)
        }
    }

    @Test
    fun `the freezer info sheet opens from the info icon`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent("h1")

        compose.onNodeWithTag("freezer-info", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Freezers").assertExists()
        // T13: the sheet's body is now HabiVoice's personality-voiced text behind a speaker
        // label — Settings.personality defaults to NEUTRA, so "HABI · NEUTRAL" (values/strings.xml).
        compose.onNodeWithText("HABI · NEUTRAL").assertExists()
        compose.onNodeWithText("Got it").assertExists()
    }

    @Test
    fun `the freezer pill without inventory opens the habi store`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent("h1")

        // QA 2026-08-23: with nothing to use, the pill keeps navigating away to the store —
        // buying stays the store's job (docs/05 §1) and no purchase sheet opens in place.
        compose.onNodeWithTag("freezer-chip", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(true, habiOpened)
        compose.onNodeWithTag("freezer-sheet", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the freezer pill with inventory opens the usage guidance instead of the store`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(id = "buy1", delta = 0, reason = PointsReason.BUY_FREEZER, refId = null, logicalDay = today - 5),
            )
        }
        setContent("h1")

        compose.onNodeWithTag("freezer-chip", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // QA 2026-08-23: owning freezers means the tap answers "how do I use one" (the info
        // sheet pointing at the red day) instead of navigating away to buy more.
        compose.onNodeWithText("Freezers").assertExists()
        assertEquals(false, habiOpened)
    }

    @Test
    fun `heatmap day cells meet the 44dp touch floor`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent("h1")

        // The suite's own qualifier (w411dp-h891dp) is where the pre-fix 20dp BitoCard padding +
        // 4dp gaps measured exactly 44.0dp per cell — the guide's floor with zero margin, and
        // Compose's assertIsAtLeast has its own ~0.5dp tolerance, so asserting the bare 44.dp
        // floor would not actually fail against that value. 46dp sits with real headroom above
        // the pre-fix measurement and below the post-fix one (48.0dp), so this is a genuine
        // regression guard rather than a boundary-exact check the tolerance would swallow — see
        // DetailScreen.kt's HeatmapSection comment for the fix and the w393dp/w411dp accounting.
        compose.onNodeWithTag("heatmap-day-$today", useUnmergedTree = true)
            .assertWidthIsAtLeast(46.dp)
            .assertHeightIsAtLeast(46.dp)
    }

    @Test
    fun `the relapse pill renders for abstinence habits`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "zero",
                    name = "No fumar",
                    metric = Metric.CHECK,
                    direction = Direction.ZERO,
                    target = 0,
                    createdOnDay = today,
                ),
            )
        }
        setContent("zero")

        compose.onNodeWithTag("relapse-pill", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the relapse pill does not render for a check habit`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "check", name = "Cama", metric = Metric.CHECK, target = 1, createdOnDay = today),
            )
        }
        setContent("check")

        compose.onNodeWithTag("relapse-pill", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an archived habit only offers reactivation`() {
        runBlocking {
            // ZERO/target 0 so the relapse pill would render if not gated by status; period DAY
            // so the freezer chip would render if not gated either. archivedOnDay = today - 1
            // leaves today - 3 as a real pre-archive day with data still on the heatmap — the
            // exact regression D2's review flagged (a past day still opening a write sheet).
            HabitsRepository(db).create(
                habitEntity(
                    id = "h1",
                    name = "No fumar",
                    metric = Metric.CHECK,
                    direction = Direction.ZERO,
                    target = 0,
                    period = Period.DAY,
                    status = HabitStatus.ARCHIVED,
                    createdOnDay = today - 5,
                    archivedOnDay = today - 1,
                ),
            )
        }
        setContent("h1")

        compose.onNodeWithTag("freezer-chip", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("relapse-pill", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("heatmap-day-${today - 3}", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("day-sheet", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("pause", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("archive", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("reactivate", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `paused habits offer resume instead of pause`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    target = 1,
                    status = HabitStatus.PAUSED,
                    createdOnDay = today - 2,
                ),
            )
        }
        setContent("h1")

        compose.onNodeWithTag("resume", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pause", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a habit with no entries shows the empty hint`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent("h1")

        compose.onNodeWithText("Log your first day and this comes alive").assertExists()
    }

    @Test
    fun `the hint disappears after the first entry`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 3, value = 1))
        }
        setContent("h1")

        compose.onNodeWithText("Log your first day and this comes alive").assertDoesNotExist()
    }
}
