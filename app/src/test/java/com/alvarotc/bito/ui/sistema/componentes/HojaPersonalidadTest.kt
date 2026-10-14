package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HojaPersonalidadTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `choosing a voice reports it`() {
        var elegida: Personality? = null
        rule.setContent { ConSistema { HojaPersonalidad(true, Personality.NEUTRA, { elegida = it }, {}) } }
        rule.onNodeWithText("Sargento").performClick()
        assertEquals(Personality.SARGENTO, elegida)
    }
}
