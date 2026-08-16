package com.alvarotc.bito.ui.detail

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        val vm =
            DetailViewModel(
                habitId,
                domainState,
                habits,
                journal,
                RewardsRepository(db),
                settings,
                reconciler,
                now = { fixedNow },
                zone = { utc },
            )
        compose.setContent {
            BitoTheme {
                DetailScreen(viewModel = vm, onBack = {}, onEdit = {})
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
}
