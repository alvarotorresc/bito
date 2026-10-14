package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class CampoTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `an error shows its text and a hidden field can be revealed`() {
        var v by mutableStateOf("secreto")
        rule.setContent { ConSistema { Campo(v, { v = it }, etiqueta = "Passphrase", oculto = true, error = "Faltan 4 caracteres") } }
        rule.onNodeWithText("Faltan 4 caracteres").assertIsDisplayed()
        rule.onNodeWithText("secreto").assertDoesNotExist()
        rule.onNodeWithContentDescription("Mostrar").performClick()
        rule.onNodeWithText("secreto").assertIsDisplayed()
    }
}
