package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class PlegableTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a closed stratum shows its summary and opening shows the body`() {
        var abierto by mutableStateOf(false)
        rule.setContent {
            ConSistema {
                EstratoPlegable(
                    BitoClaro.estratos.general,
                    "General",
                    "Neutra · Español",
                    abierto,
                    { abierto = !abierto },
                ) { Text("Tu nombre") }
            }
        }
        rule.onNodeWithText("Neutra · Español").assertIsDisplayed()
        rule.onNodeWithText("Tu nombre").assertDoesNotExist()
        rule.onNodeWithText("General").performClick()
        rule.onNodeWithText("Tu nombre").assertIsDisplayed()
    }
}
