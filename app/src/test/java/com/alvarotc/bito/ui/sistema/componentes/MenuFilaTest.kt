package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MenuFilaTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `choosing closes the sheet and the action lands 160 ms later`() {
        var editado = 0
        var abierto by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema {
                MenuFila(
                    abierto = abierto,
                    alCerrar = { abierto = false },
                    opciones = listOf(OpcionMenu("Editar", IconosSistema.PencilSimple, TonoOpcion.AGUA) { editado++ }),
                )
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Editar").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(0, editado)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, editado)
    }
}
