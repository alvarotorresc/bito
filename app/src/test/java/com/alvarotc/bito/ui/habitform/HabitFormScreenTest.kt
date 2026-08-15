package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
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

/** Drives the real habit form over an in-memory Room database, mirroring TodayScreenTest's harness. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HabitFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var habits: HabitsRepository
    private var backCalled = false

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
        habits = HabitsRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** Builds the form VM and renders the screen. Each test calls this exactly once. */
    private fun launchScreen(habitId: String?): HabitFormViewModel {
        val settings = SettingsRepository(settingsStore("habit-form-screen"))
        val vm = HabitFormViewModel(habits, settings, habitId, now = { fixedNow }, zone = { utc })
        compose.setContent {
            BitoTheme {
                HabitFormScreen(vm, onBack = { backCalled = true })
            }
        }
        compose.waitForIdle()
        return vm
    }

    @Test
    fun `naming a habit, picking QUANTITY and bumping the target saves and goes back`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("name-field").performScrollTo().performTextInput("Agua")
        compose.onNodeWithTag("preset-QUANTITY").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("target-plus").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitForIdle()
        dispatcher.scheduler.advanceUntilIdle()

        val stored = runBlocking { db.habitDao().all().single() }
        assertEquals("Agua", stored.name)
        assertEquals(Metric.COUNT, stored.metric)
        assertEquals(9, stored.target)
        assertTrue(backCalled)
    }

    @Test
    fun `editing an existing habit locks its shape but keeps target, unit and step live`() {
        runBlocking {
            habits.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    period = Period.DAY,
                    target = 8,
                    unit = "vasos",
                    logMode = LogMode.COUNTER,
                    step = 1,
                    createdOnDay = today,
                ),
            )
        }
        launchScreen(habitId = "h1")

        // Value fields keep working: they're the only ones Task 10's edit-branch persists.
        compose.onNodeWithTag("name-field").assertIsEnabled()
        compose.onNodeWithTag("target-plus").assertIsEnabled()
        compose.onNodeWithTag("target-minus").assertIsEnabled()

        // Shape fields lock, same as the preset pills: the period pill and the binary switch.
        compose.onNodeWithText("day").assertIsNotEnabled()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsNotEnabled()

        compose.onNodeWithTag("target-plus").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitForIdle()
        dispatcher.scheduler.advanceUntilIdle()

        val updated = runBlocking { db.habitDao().byId("h1")!! }
        assertEquals(Metric.COUNT, updated.metric)
        assertEquals(Period.DAY, updated.period)
        assertEquals(LogMode.COUNTER, updated.logMode)
        assertEquals(9, updated.target)
    }

    @Test
    fun `duration targets jump by ten`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-DURATION").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-plus10").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("30").assertExists()
    }

    @Test
    fun `tapping the target number opens direct input`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-DURATION").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-value").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-input").assertExists()
    }

    @Test
    fun `quantity targets keep the single-step stepper`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-QUANTITY").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-plus").assertExists()
        compose.onNodeWithTag("target-plus10").assertDoesNotExist()
    }

    @Test
    fun `a minute limit gets the ten-jump stepper too`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-QUIT").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("With a limit").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Minutes").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-plus10").assertExists()
        compose.onNodeWithTag("target-minus10").assertExists()

        compose.onNodeWithTag("target-plus10").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("40").assertExists()
    }
}
