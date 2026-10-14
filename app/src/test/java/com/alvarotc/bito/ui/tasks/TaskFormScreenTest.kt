package com.alvarotc.bito.ui.tasks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Drives the real [TaskFormScreen] in isolation, the same way [com.alvarotc.bito.ui.habitform.HabitFormScreenTest]
 * drives [com.alvarotc.bito.ui.habitform.HabitFormScreen] — this screen carries no ViewModel of its
 * own (same as the sheet it replaces), so tests supply [TaskFormState] and fake callbacks directly,
 * no Room database needed. Deliberately stops short of driving the DatePickerDialog, same call the
 * old TaskFormSheetTest coverage (folded into BitoNavHostTest before this ola) already made: that
 * dialog has no logic worth a Robolectric test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class TaskFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // Un jueves conocido, para que «vie 16» no dependa del día en que corra el test.
    private val today = LocalDate.of(2026, 10, 15).toEpochDay().toInt()

    private var savedState: TaskFormState? = null
    private var saveCalls = 0
    private var backCalled = false
    private var deleted = false

    private fun launchScreen(
        initial: TaskFormState,
        allowDelete: Boolean = false,
    ) {
        savedState = null
        saveCalls = 0
        backCalled = false
        deleted = false
        compose.setContent {
            ConSistema {
                TaskFormScreen(
                    initial = initial,
                    today = today,
                    onSave = {
                        savedState = it
                        saveCalls++
                    },
                    onBack = { backCalled = true },
                    onDelete = if (allowDelete) ({ deleted = true }) else null,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun escribirNombre(texto: String) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(texto)
        compose.waitForIdle()
    }

    private fun escribirPaso(texto: String) {
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(texto)
        compose.waitForIdle()
    }

    private val apagado = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

    @Test
    fun `a blank title keeps save disabled`() {
        launchScreen(TaskFormState())

        compose.onNodeWithTag("task-form-save").performScrollTo().assert(apagado)
    }

    @Test
    fun `typing a title enables save and saving reports the new state`() {
        launchScreen(TaskFormState())

        escribirNombre("Llamar al banco")

        compose.onNodeWithTag("task-form-save").performScrollTo().assert(!apagado).performClick()
        compose.waitForIdle()

        assertEquals("Llamar al banco", savedState?.title)
        assertNull(savedState?.editingId)
    }

    @Test
    fun `the first step travels into the saved state too`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        escribirPaso("Buscar el numero")
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("Buscar el numero", savedState?.firstStep)
    }

    @Test
    fun `choosing this week sets the week due kind`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        compose.onNodeWithText("Esta semana").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(DueKind.WEEK, savedState?.dueKind)
    }

    @Test
    fun `editing preloads the existing fields under the edit title`() {
        launchScreen(TaskFormState(editingId = "t1", title = "Titulo original", firstStep = "Paso original"))

        compose.onNodeWithText("Editar la tarea").assertExists()
        compose.onNodeWithText("Titulo original").assertExists()
        compose.onNodeWithText("Paso original").assertExists()
    }

    @Test
    fun `creating shows the new-task title, not the edit one`() {
        launchScreen(TaskFormState())

        compose.onNodeWithText("Nueva tarea").assertExists()
        compose.onNodeWithText("Editar la tarea").assertDoesNotExist()
    }

    /**
     * The NavHost's own exit transition keeps the outgoing route composed (and pulsable) while it
     * animates away, so a second tap landing in that window before the guard existed called
     * [onSave] again -- two tasks created from one tap-tap. Invokes the button's own OnClick
     * semantics action twice back to back, not two performClick()s: those would race a
     * recomposition that disables the button between them, which would pass even without the
     * guard this test exists to prove.
     */
    @Test
    fun `tapping save twice only calls onSave once`() {
        launchScreen(TaskFormState())

        escribirNombre("Llamar al banco")

        val saveAction =
            compose.onNodeWithTag("task-form-save").fetchSemanticsNode().config[SemanticsActions.OnClick].action
        saveAction?.invoke()
        saveAction?.invoke()
        compose.waitForIdle()

        assertEquals(1, saveCalls)
    }

    /**
     * A rotation, a theme change or a language switch recreates the Activity mid-edit, and this
     * screen has no ViewModel to survive that on its own -- unlike a real Activity recreation,
     * [StateRestorationTester] doesn't route through [launchScreen]'s own `compose.setContent`
     * (it needs to own that call to actually save and restore instance state), so this test builds
     * its own minimal harness instead.
     */
    @Test
    fun `a typed title and the chosen due kind survive a config change`() {
        var restoredSave: TaskFormState? = null
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            ConSistema {
                TaskFormScreen(
                    initial = TaskFormState(),
                    today = today,
                    onSave = { restoredSave = it },
                    onBack = {},
                )
            }
        }
        escribirNombre("Llamar al banco")
        compose.onNodeWithText("Esta semana").performScrollTo().performClick()
        compose.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("Llamar al banco", restoredSave?.title)
        assertEquals(DueKind.WEEK, restoredSave?.dueKind)
    }

    @Test
    fun `the back chevron calls onBack without saving`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.waitForIdle()

        assertTrue(backCalled)
        assertNull(savedState)
    }

    @Test
    fun `creating shows no delete button`() {
        launchScreen(TaskFormState())

        compose.onNodeWithTag("task-delete").assertDoesNotExist()
    }

    /**
     * [onDelete] alone gates the button — not [TaskFormState.isEditing] — so this proves the
     * screen itself never assumes editing implies deletable; the caller (BitoNavHost's own
     * "task?id={id}" route) is the one that only ever supplies [onDelete] on the editing branch.
     */
    @Test
    fun `editing without a delete callback still shows no delete button`() {
        launchScreen(TaskFormState(editingId = "t1", title = "Titulo original"))

        compose.onNodeWithTag("task-delete").assertDoesNotExist()
    }

    @Test
    fun `editing with a delete callback asks for confirmation before deleting`() {
        launchScreen(TaskFormState(editingId = "t1", title = "Titulo original"), allowDelete = true)

        compose.onNodeWithTag("task-delete").performScrollTo().performClick()
        compose.waitForIdle()

        // El diálogo está puesto y todavía no ha pasado nada.
        compose.onNodeWithText("¿Borrar esta tarea?").assertExists()
        compose.onNodeWithText("Los bitos que ganaste son tuyos.").assertExists()
        assertFalse(deleted)

        compose.onNodeWithText("Borrar").performClick()
        compose.waitForIdle()

        assertTrue(deleted)
    }

    @Test
    fun `dismissing the delete confirmation does not delete`() {
        launchScreen(TaskFormState(editingId = "t1", title = "Titulo original"), allowDelete = true)

        compose.onNodeWithTag("task-delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("¿Borrar esta tarea?").assertDoesNotExist()
        assertFalse(deleted)
    }

    @Test
    fun `choosing a date replaces Fecha in the segmented`() {
        launchScreen(TaskFormState(title = "Regalo de Lucía"))

        compose.onNodeWithText("Fecha").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("16").performClick()
        compose.onNodeWithText("Elegir").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("vie 16").assertExists()
        compose.onNodeWithText("Fecha").assertDoesNotExist()
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DueKind.DATE, savedState?.dueKind)
        assertEquals(today + 1, savedState?.dueDay)
    }

    @Test
    fun `cancelling the calendar leaves the deadline as it was`() {
        launchScreen(TaskFormState(title = "Regalo de Lucía"))

        compose.onNodeWithText("Fecha").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("16").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Fecha").assertExists()
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DueKind.NONE, savedState?.dueKind)
    }

    @Test
    fun `pressing the disabled save says what is missing`() {
        launchScreen(TaskFormState())

        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Falta el nombre.").assertExists()
        assertEquals(0, saveCalls)
    }
}
