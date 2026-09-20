package com.alvarotc.bito.ui.tasks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TaskFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = 20_000

    private var savedState: TaskFormState? = null
    private var backCalled = false

    private fun launchScreen(initial: TaskFormState) {
        savedState = null
        backCalled = false
        compose.setContent {
            BitoTheme {
                TaskFormScreen(
                    initial = initial,
                    today = today,
                    onSave = { savedState = it },
                    onBack = { backCalled = true },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a blank title keeps save disabled`() {
        launchScreen(TaskFormState())

        compose.onNodeWithTag("task-form-save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `typing a title enables save and saving reports the new state`() {
        launchScreen(TaskFormState())

        compose.onNodeWithTag("task-title-field").performScrollTo().performTextInput("Llamar al banco")
        compose.waitForIdle()

        compose.onNodeWithTag("task-form-save").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals("Llamar al banco", savedState?.title)
        assertNull(savedState?.editingId)
    }

    @Test
    fun `the first step travels into the saved state too`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        compose.onNodeWithTag("task-first-step-field").performScrollTo().performTextInput("Buscar el numero")
        compose.waitForIdle()
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("Buscar el numero", savedState?.firstStep)
    }

    @Test
    fun `choosing this week sets the week due kind`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        compose.onNodeWithText("This week").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("task-form-save").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(DueKind.WEEK, savedState?.dueKind)
    }

    @Test
    fun `editing preloads the existing fields under the edit title`() {
        launchScreen(TaskFormState(editingId = "t1", title = "Titulo original", firstStep = "Paso original"))

        compose.onNodeWithText("Edit task").assertExists()
        compose.onNodeWithText("Titulo original").assertExists()
        compose.onNodeWithText("Paso original").assertExists()
    }

    @Test
    fun `creating shows the new-task title, not the edit one`() {
        launchScreen(TaskFormState())

        compose.onNodeWithText("New task").assertExists()
        compose.onNodeWithText("Edit task").assertDoesNotExist()
    }

    @Test
    fun `the back chevron calls onBack without saving`() {
        launchScreen(TaskFormState(title = "Llamar al banco"))

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        assertTrue(backCalled)
        assertNull(savedState)
    }
}
