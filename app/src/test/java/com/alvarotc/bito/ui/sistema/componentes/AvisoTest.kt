package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.TactoGrabado
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AvisoTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `shows for four seconds, undo runs once, and dodging rises 64 dp while visible`() {
        val estado = AvisoEstado()
        var deshecho = 0
        val tacto = TactoGrabado()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(tacto = tacto) {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { scope.launch { estado.mostrar("Tarea hecha", "Deshacer") { deshecho++ } } }
                AvisoHost(estado)
            }
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Tarea hecha").assertIsDisplayed()
        assertEquals(64.dp, estado.desplazamiento)
        rule.onNodeWithText("Deshacer").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, deshecho)
        assertTrue(Haptica.TICK in tacto.hapticas)
        rule.mainClock.advanceTimeBy(5000)
        rule.onNodeWithText("Tarea hecha").assertDoesNotExist()
        assertEquals(0.dp, estado.desplazamiento)
    }
}
