package com.alvarotc.bito.ui.today

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
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

/**
 * Drives the real Today screen over an in-memory Room database. Both the main
 * dispatcher and Room's executors run on the same [UnconfinedTestDispatcher] so
 * every VM write, Room emission and recomposition settles synchronously — a
 * plain [org.junit.Rule] compose test has no `runTest`/`advanceUntilIdle` of its
 * own, so [compose]'s `waitForIdle` is the only pump available and it only
 * drains Robolectric's looper and pending recomposition, not a queued dispatcher.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private var openedId: String? = null

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

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
        val habits = HabitsRepository(db)
        val journal = JournalRepository(db)
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore("today-screen"))
        val reconciler = PointsReconciler(domainState, RewardsRepository(db))

        // Both habits must be created today: an older createdOnDay (the fixture default,
        // DAY_ZERO) would leave hundreds of pending seal days and the BatchSealSheet would
        // cover the whole screen, swallowing every click below it.
        runBlocking {
            habits.create(
                habitEntity(id = "agua", name = "Agua", metric = Metric.COUNT, target = 8, step = 1, createdOnDay = today, sortOrder = 0),
            )
            habits.create(
                habitEntity(id = "cama", name = "Cama", metric = Metric.CHECK, target = 1, createdOnDay = today, sortOrder = 1),
            )
        }

        val vm =
            TodayViewModel(
                domainState,
                habits,
                journal,
                settings,
                reconciler,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )

        compose.setContent {
            BitoTheme {
                TodayScreen(vm, onCreateHabit = {}, onOpenHabit = { openedId = it })
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `tapping the CHECK primary logs today and advances the ring`() {
        compose.onNodeWithTag("primary-cama", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all() }
        assertTrue(entries.any { it.habitId == "cama" && it.logicalDay == today })
        compose.onNodeWithText("1 of 2").assertExists()
    }

    @Test
    fun `tapping the COUNTER primary twice shows the hero number and updates the dots`() {
        compose.onNodeWithTag("primary-agua", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("primary-agua", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("2", useUnmergedTree = true).assertExists()
        val value = runBlocking { db.entryDao().all().filter { it.habitId == "agua" }.sumOf { it.value } }
        assertEquals(2, value)
    }

    @Test
    fun `logging shows the Bito-styled snackbar and its undo action reverts the entry`() {
        compose.onNodeWithTag("primary-cama", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bito-snackbar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Undo", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        val entries = runBlocking { db.entryDao().all().filter { it.habitId == "cama" } }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `tapping the card surface opens the detail route`() {
        compose.onNodeWithTag("card-agua", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("agua", openedId)
    }

    @Test
    fun `tapping the duration bar opens the habit detail instead of swallowing the tap`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "lectura",
                    name = "Lectura",
                    metric = Metric.DURATION,
                    target = 30,
                    unit = "min",
                    createdOnDay = today,
                    sortOrder = 2,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("bar-lectura", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("lectura", openedId)
        val entries = runBlocking { db.entryDao().all().filter { it.habitId == "lectura" } }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `the duration card offers a visible exact-value chip that opens the sheet`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "lectura",
                    name = "Lectura",
                    metric = Metric.DURATION,
                    target = 30,
                    unit = "min",
                    createdOnDay = today,
                    sortOrder = 2,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("exact-lectura", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Set today's total").assertExists()
    }

    @Test
    fun `an at-most duration habit clean with partial progress shows its chips`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "redes",
                    name = "Redes",
                    metric = Metric.DURATION,
                    direction = Direction.AT_MOST,
                    target = 30,
                    unit = "min",
                    createdOnDay = today,
                    sortOrder = 2,
                ),
            )
            db.entryDao().insert(entryEntity(id = "e1", habitId = "redes", logicalDay = today, value = 10))
        }
        compose.waitForIdle()

        // Chips stay visible while clean: that is exactly when you log real usage. Its
        // TextDecoration cannot be asserted from the semantics tree (Compose does not expose
        // it there); that half of the guarantee is covered at the pure-decision level by
        // TodayUiStateTest's `nameStruckThrough` cases.
        compose.onNodeWithText("Redes", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("max 30 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a paused habit drops off the cards and its row opens detail`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "yoga", name = "Yoga", metric = Metric.CHECK, target = 1, createdOnDay = today, sortOrder = 3),
            )
            HabitsRepository(db).pause("yoga", startDay = today, note = null)
        }
        compose.waitForIdle()

        compose.onNodeWithTag("card-yoga", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Paused", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("paused-yoga", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("yoga", openedId)
    }

    @Test
    fun `pausing every habit hides the empty-today prompt and shows the paused section`() {
        runBlocking {
            HabitsRepository(db).pause("agua", startDay = today, note = null)
            HabitsRepository(db).pause("cama", startDay = today, note = null)
        }
        compose.waitForIdle()

        compose.onNodeWithText("Nothing here yet", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Paused", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a binary WEEK habit with a wide target renders a bar instead of thousands of dots`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(
                    id = "pasos",
                    name = "Pasos",
                    metric = Metric.COUNT,
                    period = Period.WEEK,
                    logMode = LogMode.BINARY,
                    target = 9000,
                    unit = null,
                    createdOnDay = today,
                    sortOrder = 2,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("period-bar-pasos", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("0 of 9000 this week", useUnmergedTree = true).assertExists()
    }
}
