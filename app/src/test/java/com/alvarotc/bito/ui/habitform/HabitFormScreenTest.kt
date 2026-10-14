package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

/** Drives the real habit form (Spanish texts) over an in-memory Room database, mirroring TodayScreenTest's harness. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
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
        val rewards = RewardsRepository(db)
        val reconciler = PointsReconciler(DomainStateRepository(db), rewards, settings = settings)
        val vm = HabitFormViewModel(habits, settings, rewards, reconciler, habitId, now = { fixedNow }, zone = { utc })
        compose.setContent {
            BitoTheme {
                HabitFormScreen(vm, onBack = { backCalled = true })
            }
        }
        compose.waitForIdle()
        return vm
    }

    private val ayudaNombre get() = string(R.string.habitform_nombre_ayuda)

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun campoNombre() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("name-field")))

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun habitosGuardados() = runBlocking { db.habitDao().all().size }

    private fun elegirTipo(etiqueta: String) {
        compose.onNodeWithText(etiqueta).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun montarNuevo(tipo: String? = null): HabitFormViewModel {
        val vm = launchScreen(habitId = null)
        tipo?.let(::elegirTipo)
        return vm
    }

    private fun montarEditando(
        metric: Metric = Metric.COUNT,
        direction: Direction = Direction.AT_LEAST,
        period: Period = Period.DAY,
        target: Int = 8,
        unit: String? = "vasos",
        reminderMinutes: Int? = null,
        step: Int = 1,
    ): HabitFormViewModel {
        runBlocking {
            habits.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = metric,
                    period = period,
                    direction = direction,
                    target = target,
                    unit = unit,
                    logMode = LogMode.COUNTER,
                    step = step,
                    reminderMinutes = reminderMinutes,
                    createdOnDay = today,
                ),
            )
        }
        return launchScreen(habitId = "h1")
    }

    @Test
    fun `naming a habit, picking QUANTITY and bumping the target saves and goes back`() {
        montarNuevo()

        campoNombre().performTextInput("Agua")
        elegirTipo("Cantidad")
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
    fun `saving without a name explains instead of saving`() {
        montarNuevo()

        compose.onNodeWithText("Crear hábito").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(ayudaNombre).assertIsDisplayed()
        assertEquals(0, habitosGuardados())
    }

    @Test
    fun `typing a name clears the missing-name help`() {
        montarNuevo()
        compose.onNodeWithText("Crear hábito").performScrollTo().performClick()
        compose.waitForIdle()

        campoNombre().performTextInput("Leer")
        compose.waitForIdle()

        compose.onNodeWithText(ayudaNombre).assertDoesNotExist()
    }

    @Test
    fun `editing locks the shape but keeps the name, target, unit and step live`() {
        val vm = montarEditando()

        campoNombre().assertIsEnabled()
        compose.onNodeWithTag("target-plus").assertIsEnabled()
        compose.onNodeWithTag("target-minus").assertIsEnabled()

        // The period is shape: a tap on it changes nothing.
        compose.onNodeWithText("semana").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Period.DAY, vm.state.value.period)

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("binary-mode-row").performScrollTo().assertIsNotEnabled()

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
    fun `editing shows only the current type with its note`() {
        montarEditando(metric = Metric.COUNT)

        compose.onNodeWithText(string(R.string.habitform_tipo_fijo)).assertIsDisplayed()
        compose.onAllNodes(hasRole(Role.RadioButton)).assertCountEquals(1)
        compose.onNodeWithText("Duración").assertDoesNotExist()
    }

    @Test
    fun `a new habit offers the five types as radios`() {
        montarNuevo()

        compose.onAllNodes(hasRole(Role.RadioButton)).assertCountEquals(5)
    }

    @Test
    fun `a number out of range warns and is not clamped`() {
        val vm = montarNuevo(tipo = "X por semana")

        compose.onNodeWithTag("objetivo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("target-input"))).performTextReplacement("9")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Como mucho 7: una por día.").assertIsDisplayed()
        assertNotEquals(7, vm.state.value.target) // no se recorta a 7 a escondidas
        assertEquals(3, vm.state.value.target)
    }

    @Test
    fun `a five-digit number from the sheet is kept whole`() {
        val vm = montarNuevo(tipo = "Cantidad")

        compose.onNodeWithTag("objetivo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("target-input"))).performTextReplacement("12500")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitForIdle()

        assertEquals(12500, vm.state.value.target)
    }

    @Test
    fun `a step of 250 can still be bumped`() {
        val vm = montarEditando(step = 250)

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription(string(R.string.sistema_mas)) and hasAnyAncestor(hasTestTag("step-row")))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertEquals(251, vm.state.value.step)
    }

    @Test
    fun `the ten-jump clamps to the minimum instead of being refused`() {
        val vm = montarNuevo(tipo = "Duración")

        compose.onNodeWithTag("target-minus10").performScrollTo().performClick()
        compose.onNodeWithTag("target-minus10").performClick()
        compose.waitForIdle()

        assertEquals(1, vm.state.value.target)
    }

    @Test
    fun `editing disables the period segments for accessibility too`() {
        montarEditando()

        compose.onNodeWithText("semana").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a valid number from the sheet sets the target`() {
        val vm = montarNuevo(tipo = "Cantidad")

        compose.onNodeWithTag("objetivo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("target-input"))).performTextReplacement("12")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitForIdle()

        assertEquals(12, vm.state.value.target)
    }

    @Test
    fun `quit entirely shows the sentence instead of a zero counter`() {
        montarNuevo(tipo = "Dejar de hacer")

        compose.onNodeWithText("Cada día sin hacerlo suma a la racha.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("objetivo").assertDoesNotExist()
    }

    @Test
    fun `deleting asks first`() {
        montarEditando()

        compose.onNodeWithTag("delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("¿Borrar para siempre?").assertIsDisplayed()
        assertEquals(1, habitosGuardados())

        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertEquals(1, habitosGuardados())

        compose.onNodeWithTag("delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Borrar").performClick()
        compose.waitForIdle()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(runBlocking { db.habitDao().byId("h1") })
        assertTrue(backCalled)
    }

    @Test
    fun `duration targets jump by ten`() {
        val vm = montarNuevo(tipo = "Duración")

        compose.onNodeWithTag("target-plus10").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(30, vm.state.value.target)
    }

    @Test
    fun `tapping the target number opens direct input`() {
        montarNuevo(tipo = "Duración")

        compose.onNodeWithTag("objetivo").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("target-input").assertExists()
    }

    @Test
    fun `quantity targets keep the single-step stepper`() {
        montarNuevo(tipo = "Cantidad")

        compose.onNodeWithTag("target-plus").assertExists()
        compose.onNodeWithTag("target-plus10").assertDoesNotExist()
    }

    @Test
    fun `at the top of the weekly range the plus button does not move the target`() {
        val vm = montarNuevo(tipo = "X por semana")

        repeat(6) {
            compose.onNodeWithTag("target-plus").performScrollTo().performClick()
            compose.waitForIdle()
        }

        assertEquals(7, vm.state.value.target)
    }

    @Test
    fun `a minute limit gets the ten-jump stepper too`() {
        val vm = montarNuevo(tipo = "Dejar de hacer")
        elegirTipo("Con límite")
        elegirTipo("Minutos")

        compose.onNodeWithTag("target-plus10").assertExists()
        compose.onNodeWithTag("target-minus10").assertExists()

        compose.onNodeWithTag("target-plus10").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(40, vm.state.value.target)
    }

    @Config(qualifiers = "es-w360dp-h800dp")
    @Test
    fun `the minute stepper stays fully visible on a narrow 360dp screen`() {
        val vm = montarNuevo(tipo = "Duración")

        compose.onNodeWithTag("target-minus10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("objetivo").assertIsDisplayed()
        compose.onNodeWithTag("target-plus10").assertIsDisplayed()

        compose.onNodeWithTag("target-plus10").performClick()
        compose.waitForIdle()

        assertEquals(30, vm.state.value.target)
    }

    /** La intención del viejo test de píldoras: en una pantalla estrecha el tipo se ve entero. */
    @Config(qualifiers = "es-w360dp-h800dp")
    @Test
    fun `every type label is shown on a narrow screen`() {
        montarNuevo()

        listOf("Diario", "Cantidad", "Duración", "X por semana", "Dejar de hacer").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `more options always has content, the reminder row`() {
        montarNuevo()

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reminder-row").assertExists()
    }

    @Test
    fun `tapping the reminder row opens the sky clock sheet`() {
        montarNuevo()

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reminder-row").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("time-picker-confirm").assertIsDisplayed()
    }

    @Test
    fun `saving the reminder sheet sets the hour`() {
        val vm = montarNuevo()

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("reminder-row").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitForIdle()

        assertEquals(8 * 60, vm.state.value.reminderMinutes)
    }

    @Test
    fun `quit presets disable the reminder row with a hint`() {
        montarNuevo(tipo = "Dejar de hacer")

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("reminder-row").assertHasNoClickAction()
        compose.onNodeWithText("Sin avisos: Bito no te recuerda lo que intentas evitar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an existing reminder shows its time and can be cleared from the row`() {
        montarEditando(reminderMinutes = 9 * 60)

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("09:00", substring = true).assertExists()

        compose.onNodeWithTag("reminder-clear").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Sin recordatorio", substring = true).assertExists()
    }

    @Test
    fun `types announce which one is selected`() {
        montarNuevo()

        compose.onNodeWithText("Diario").assertIsSelected()
        compose.onNodeWithText("Cantidad").assertIsNotSelected()

        elegirTipo("Cantidad")

        compose.onNodeWithText("Cantidad").assertIsSelected()
        compose.onNodeWithText("Diario").assertIsNotSelected()
    }

    @Test
    fun `the target stepper chips carry real action descriptions`() {
        montarNuevo(tipo = "Duración")

        compose.onNodeWithTag("target-minus10").assertContentDescriptionEquals("Reducir el objetivo en 10")
        compose.onNodeWithTag("target-minus").assertContentDescriptionEquals("Reducir el objetivo en 1")
        compose.onNodeWithTag("target-plus").assertContentDescriptionEquals("Aumentar el objetivo en 1")
        compose.onNodeWithTag("target-plus10").assertContentDescriptionEquals("Aumentar el objetivo en 10")
    }

    @Test
    fun `tapping the binary mode row flips and persists the setting`() {
        val vm = montarNuevo(tipo = "Cantidad")

        compose.onNodeWithText("Más opciones").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("binary-mode-row").performScrollTo().assertIsOff()

        compose.onNodeWithTag("binary-mode-row").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("binary-mode-row").assertIsOn()
        assertTrue(vm.state.value.binaryMode)
    }

    /** The form's Habi next to its bubble: HabiAvatar's aggregated description ("Habi, <mood>") is
     * the one node that proves the face actually rendered. */
    @Test
    fun `the habi shows its avatar`() {
        montarNuevo()

        compose.onNode(hasContentDescription("Habi", substring = true)).assertIsDisplayed()
    }
}
