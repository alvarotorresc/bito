package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlobitoTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `grows, speaks and leaves on its own after 2,4 s`() {
        var ido = 0
        var visible by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema {
                Globito(visible, "Hola", alIrse = {
                    ido++
                    visible = false
                })
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Hola").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2600)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, ido)
        rule.onNodeWithText("Hola").assertDoesNotExist()
    }
}
