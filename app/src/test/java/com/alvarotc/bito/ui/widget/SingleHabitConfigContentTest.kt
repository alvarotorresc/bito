package com.alvarotc.bito.ui.widget

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SingleHabitConfigContent] standalone — the ViewModel and the Glance plumbing live in
 * [SingleHabitConfigActivity], but the state that broke the widget (an empty habit list) is pure
 * UI. Configuration is mandatory for this widget, so Android discards the placement unless Save
 * runs: every test here that clicks Save is really asserting "the widget survives".
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SingleHabitConfigContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `with no active habits the list explains itself instead of going blank`() {
        compose.setContent {
            BitoTheme {
                SingleHabitConfigContent(habits = emptyList(), selected = null, onSelect = {}, onSave = {})
            }
        }

        compose.onNodeWithTag("single-habit-config-empty").assertExists()
    }

    @Test
    fun `saving works with nothing to choose, so the widget is never silently discarded`() {
        // The whole point of the fix: before it, Save was disabled here and a back-press cancelled
        // the placement — the widget vanished off the home screen with no explanation.
        var saved = false
        compose.setContent {
            BitoTheme {
                SingleHabitConfigContent(habits = emptyList(), selected = null, onSelect = {}, onSave = { saved = true })
            }
        }

        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        assertTrue(saved)
    }

    @Test
    fun `saving works with habits on offer but none picked yet`() {
        // Same escape hatch when the list is full: an unchosen save stores no id and the widget
        // renders its tappable "choose a habit" fallback (SingleHabitModel.Missing).
        var saved = false
        compose.setContent {
            BitoTheme {
                SingleHabitConfigContent(
                    habits = listOf(habitEntity(id = "agua", name = "Beber agua")),
                    selected = null,
                    onSelect = {},
                    onSave = { saved = true },
                )
            }
        }

        compose.onNodeWithText("Save").performClick()

        assertTrue(saved)
    }

    @Test
    fun `the offered habits are listed and picking one reports its id`() {
        var picked: String? = null
        compose.setContent {
            BitoTheme {
                SingleHabitConfigContent(
                    habits =
                        listOf(
                            habitEntity(id = "agua", name = "Beber agua"),
                            habitEntity(id = "pasos", name = "Caminar"),
                        ),
                    selected = null,
                    onSelect = { picked = it },
                    onSave = {},
                )
            }
        }

        compose.onNodeWithTag("single-habit-config-empty").assertDoesNotExist()
        compose.onNodeWithText("Caminar").performClick()

        assertEquals("pasos", picked)
    }
}
