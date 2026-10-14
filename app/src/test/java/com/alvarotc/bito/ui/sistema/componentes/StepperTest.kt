package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class StepperTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `stepping plays tic and seg, and at the top it refuses with a low tick`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        var v by mutableStateOf(29)
        rule.setContent { ConSistema(sonido, tacto) { Stepper(v, { v = it }, min = 1, max = 30, descripcion = "Conservar") } }
        rule.onNodeWithContentDescription("Más").performClick()
        assertEquals(30, v)
        rule.onNodeWithContentDescription("Más").performClick()
        assertEquals(30, v)
        assertEquals(listOf(Snd.TIC), sonido.snds())
        assertEquals(listOf(Haptica.SEG, Haptica.LOW), tacto.hapticas)
    }

    @Test
    fun `the stepper exposes its range to accessibility`() {
        rule.setContent { ConSistema { Stepper(5, {}, min = 1, max = 30, descripcion = "Conservar") } }
        rule.onNodeWithContentDescription("Conservar").assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(5f, 1f..30f)))
    }
}
