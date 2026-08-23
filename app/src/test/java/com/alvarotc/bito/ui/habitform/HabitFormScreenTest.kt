package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
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

        // Shape fields lock, same as the preset pills: the period pill and the binary mode row.
        compose.onNodeWithText("day").assertIsNotEnabled()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()
        // isToggleable() now matches BinaryModeRow's own Row (its `.toggleable`), not the Switch
        // inside it: with `onCheckedChange = null` (the [E] a11y fix), the Switch itself no longer
        // contributes any toggleable semantics of its own — verified there's exactly one match
        // (onNode, not onAllNodes, would throw on more than one) and that it's tagged
        // "binary-mode-row".
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

    @Config(qualifiers = "w360dp-h800dp")
    @Test
    fun `the minute stepper stays fully visible on a narrow 360dp screen`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-DURATION").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-minus10").assertIsDisplayed()
        compose.onNodeWithTag("target-value").assertIsDisplayed()
        compose.onNodeWithTag("target-plus10").assertIsDisplayed()

        compose.onNodeWithTag("target-plus10").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("30").assertExists()
    }

    @Test
    fun `more options always has content — the reminder row`() {
        launchScreen(habitId = null)

        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reminder-row").assertExists()
    }

    @Test
    fun `tapping the reminder row opens the time picker sheet`() {
        launchScreen(habitId = null)

        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reminder-row").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("time-picker-confirm").assertIsDisplayed()
    }

    @Test
    fun `quit presets disable the reminder row with a hint`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-QUIT").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reminder-row").assertIsNotEnabled()
        compose.onNodeWithText("No reminders: Bito won't nag you about what you're avoiding").assertIsDisplayed()
    }

    @Test
    fun `an existing reminder shows its time and can be cleared from the row`() {
        runBlocking {
            habits.create(habitEntity(id = "h1", reminderMinutes = 9 * 60, createdOnDay = today))
        }
        launchScreen(habitId = "h1")

        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("09:00").assertExists()

        compose.onNodeWithTag("reminder-clear").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("None").assertExists()
    }

    /**
     * [C]: the checkmark on the active preset is an `Icon(contentDescription = null)`, invisible
     * to TalkBack — before this fix every pill just read "<label>, Button" with no selection
     * state. No `useUnmergedTree` needed: `selected`/`Role.Tab` land directly on the same node
     * this file's other lookups already tag (`preset-<NAME>`), not on a new merging ancestor.
     */
    @Test
    fun `preset pills announce which one is selected`() {
        launchScreen(habitId = null)

        // DAILY_CHECK is the form's own default preset (no pill tap needed to reach it).
        compose.onNodeWithTag("preset-DAILY_CHECK").assertIsSelected()
        compose.onNodeWithTag("preset-QUANTITY").assertIsNotSelected()

        compose.onNodeWithTag("preset-QUANTITY").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("preset-QUANTITY").assertIsSelected()
        compose.onNodeWithTag("preset-DAILY_CHECK").assertIsNotSelected()
    }

    /**
     * [A]: the ±1 chips wrapped a bare `Icon(contentDescription = null)`, and the ±10 chips a
     * plain "−10"/"+10" `Text` — ambiguous out of context. DURATION is the one preset that shows
     * all four chips together (`isMinuteTarget`), so one test covers all four descriptions.
     */
    @Test
    fun `the target stepper chips carry real action descriptions`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-DURATION").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-minus10").assertContentDescriptionEquals("Decrease target by 10")
        compose.onNodeWithTag("target-minus").assertContentDescriptionEquals("Decrease target by 1")
        compose.onNodeWithTag("target-plus").assertContentDescriptionEquals("Increase target by 1")
        compose.onNodeWithTag("target-plus10").assertContentDescriptionEquals("Increase target by 10")
    }

    /**
     * [E]: the "Just done / not done" Switch used to sit next to — not merged with — its label+
     * hint text, 2 disconnected TalkBack stops. `toggleable` on the row (not a plain
     * `mergeDescendants`, verified insufficient empirically on SettingsScreen's identical rows)
     * moves the action there and folds both Column texts and the Switch's own state into one stop.
     */
    @Test
    fun `the binary mode row merges its label and switch into one talkback stop`() {
        launchScreen(habitId = null)

        compose.onNodeWithTag("preset-QUANTITY").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("binary-mode-row").performScrollTo().assertIsOff()
        compose.onNodeWithTag("binary-mode-row").onChildren().assertCountEquals(0)
    }

    /**
     * Mirrors the Settings switch rows' own click-to-toggle test: proves the row's `toggleable`
     * (not just its merged semantics) actually drives [HabitFormViewModel]'s state, now that the
     * click moved off the `Switch` itself.
     */
    @Test
    fun `tapping the binary mode row flips and persists the setting`() {
        val vm = launchScreen(habitId = null)

        compose.onNodeWithTag("preset-QUANTITY").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More options").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("binary-mode-row").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("binary-mode-row").assertIsOn()
        assertTrue(vm.state.value.binaryMode)
    }
}
