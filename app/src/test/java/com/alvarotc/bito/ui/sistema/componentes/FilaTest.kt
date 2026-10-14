package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FilaTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a switch row toggles from anywhere on the row`() {
        var on by mutableStateOf(false)
        rule.setContent { ConSistema { FilaInterruptor("Avisos de tareas", on, { on = it }) } }
        rule.onNodeWithText("Avisos de tareas").performClick()
        assertTrue(on)
    }
}
