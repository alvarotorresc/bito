package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class RelojCieloTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `tapping the right edge goes to 6,00 and out of range stays at the end`() {
        var m by mutableStateOf(480)
        rule.setContent {
            ConSistema {
                RelojCielo(m, { m = it }, Modifier.size(300.dp).testTag("reloj"), paso = 5, desde = 0, hasta = 360, descripcion = "Hora")
            }
        }
        rule.onNodeWithTag("reloj").performTouchInput { click(centerRight - Offset(8f, 0f)) }
        rule.waitForIdle()
        assertEquals(360, m)
        rule.onNodeWithTag("reloj").performTouchInput { click(bottomCenter - Offset(0f, 8f)) }
        rule.waitForIdle()
        assertEquals(360, m) // 12:00 queda fuera de 0:00-6:00: el extremo más cercano es 6:00
    }

    @Test
    fun `arrow keys move one step without animating`() {
        var m by mutableStateOf(480)
        rule.setContent {
            ConSistema {
                RelojCielo(m, { m = it }, Modifier.size(300.dp).testTag("reloj"), paso = 30, descripcion = "Hora")
            }
        }
        rule.onNodeWithTag("reloj").requestFocus()
        rule.onNodeWithTag("reloj").assertIsFocused()
        rule.onNodeWithTag("reloj").performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(510, m)
    }
}
