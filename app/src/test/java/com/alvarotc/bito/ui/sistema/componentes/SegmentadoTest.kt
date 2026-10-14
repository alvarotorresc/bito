package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
@Config(sdk = [35])
class SegmentadoTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `choosing another option ticks and selects it`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        var elegida by mutableStateOf(0)
        rule.setContent { ConSistema(sonido, tacto) { Segmentado(listOf("Diaria", "Semanal"), elegida, { elegida = it }) } }
        rule.onNodeWithText("Semanal").performClick()
        rule.onNodeWithText("Semanal").assertIsSelected()
        assertEquals(listOf(Snd.TIC), sonido.snds())
        assertEquals(listOf(Haptica.SEG), tacto.hapticas)
        rule.onNodeWithText("Semanal").performClick()
        assertEquals(1, sonido.snds().size) // elegir la que ya está no suena
    }
}
