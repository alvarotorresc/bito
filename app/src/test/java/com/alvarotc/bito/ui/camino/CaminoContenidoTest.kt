package com.alvarotc.bito.ui.camino

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaminoContenidoTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `Camino composes inside its own scroll without nesting a second vertical scroll`() {
        rule.setContent {
            ConSistema { CaminoContenido(CaminoUiState(), rememberScrollState(), alVerEnJardin = {}) }
        }
        rule.onNodeWithTag("inicio-camino").assertIsDisplayed()
    }
}
