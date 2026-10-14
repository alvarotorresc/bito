package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class CalendarioElegirTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a past day does nothing and a future one is chosen`() {
        var elegido: LocalDate? by mutableStateOf(null)
        rule.setContent { ConSistema { CalendarioElegir(LocalDate.of(2026, 10, 14), elegido, { elegido = it }) } }
        rule.onNodeWithText("13").performClick()
        assertNull(elegido)
        rule.onNodeWithText("16").performClick()
        assertEquals(LocalDate.of(2026, 10, 16), elegido)
        rule.onNodeWithTag("mes-anterior").assertIsNotEnabled()
    }

    @Test
    fun `next month shows the following title`() {
        rule.setContent { ConSistema { CalendarioElegir(LocalDate.of(2026, 10, 14), null, {}) } }
        rule.onNodeWithText("Octubre 2026").assertExists()
        rule.onNodeWithTag("mes-siguiente").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Noviembre 2026").assertExists()
    }
}
