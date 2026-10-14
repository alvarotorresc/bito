package com.alvarotc.bito.ui.premios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VetaTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    private fun montar(
        tipo: TipoVeta,
        registro: RegistroAnclas? = null,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro) {
                    Veta(tipo, "Título $tipo", "Frase", 7, { Box {} })
                }
            }
        }
        rule.mainClock.advanceTimeBy(50)
    }

    @Test
    fun `every kind paints its tag, title and plus N`() {
        rule.setContent {
            ConSistema {
                Column {
                    TipoVeta.entries.forEach { tipo -> Veta(tipo, "Título $tipo", "Frase", 7, { Box {} }) }
                }
            }
        }
        TipoVeta.entries.forEach { tipo ->
            rule.onNodeWithTag("veta-$tipo").assertExists()
            rule.onNodeWithText("Título $tipo").assertExists()
        }
        assertEquals(4, rule.onAllNodesWithText("+7").fetchSemanticsNodes().size)
    }

    @Test
    fun `the streak vein sounds the streak, rises and clicks at 420 ms`() {
        montar(TipoVeta.RACHA)
        assertEquals(listOf(Snd.RACHA), sonido.snds())
        assertEquals(listOf(Haptica.RISE), tacto.hapticas)
        rule.mainClock.advanceTimeBy(450)
        assertEquals(listOf(Haptica.RISE, Haptica.CLICK), tacto.hapticas)
    }

    @Test
    fun `the day vein confirms and the month vein only sounds`() {
        montar(TipoVeta.DIA)
        assertEquals(listOf(Snd.DIA_COMPLETO), sonido.snds())
        assertEquals(listOf(Haptica.CONFIRM), tacto.hapticas)
    }

    @Test
    fun `the plus N registers the vein anchor for its coins`() {
        val registro = RegistroAnclas()
        montar(TipoVeta.DIA, registro)
        val rect = registro.posiciones[ORIGEN_VETA]
        assertTrue("el ancla de la veta se registra", rect != null && rect.width > 0f)
    }
}
