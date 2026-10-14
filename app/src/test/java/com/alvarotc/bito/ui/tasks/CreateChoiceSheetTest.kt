package com.alvarotc.bito.ui.tasks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class CreateChoiceSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the sheet offers habit, task and session now`() {
        rule.setContent { ConSistema { CreateChoiceSheet({}, {}, {}) } }

        rule.onNodeWithText("¿Qué añadimos?").assertExists()
        rule.onNodeWithText("Hábito").assertExists()
        rule.onNodeWithText("Tarea").assertExists()
        rule.onNodeWithText("Sesión ahora").assertExists()
    }

    @Test
    fun `choosing a habit calls onHabit once the sheet is closing`() {
        var habito = 0
        rule.setContent { ConSistema { CreateChoiceSheet(onHabit = { habito++ }, onTask = {}, onDismiss = {}) } }

        rule.onNodeWithText("Hábito").performClick()
        rule.mainClock.advanceTimeBy(400)

        assertEquals(1, habito)
    }

    @Test
    fun `session now offers focus and breathing in a second step`() {
        var foco = 0
        var respirar = 0
        rule.setContent {
            ConSistema { CreateChoiceSheet({}, {}, {}, onFocus = { foco++ }, onBreathing = { respirar++ }) }
        }

        rule.onNodeWithText("Sesión ahora").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("Respirar").assertExists()
        rule.onNodeWithText("Foco").performClick()
        rule.mainClock.advanceTimeBy(400)

        assertEquals(1, foco)
        assertEquals(0, respirar)
    }
}
