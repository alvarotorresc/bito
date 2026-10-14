package com.alvarotc.bito.ui.sistema

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GaleriaSistemaTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the gallery offers every sound and every haptic`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        rule.setContent { ConSistema(sonido, tacto) { GaleriaSistema(alCerrar = {}) } }
        Snd.entries.filterNot { it.bucle }.forEach { snd ->
            rule.onNodeWithTag("galeria").performScrollToNode(hasTestTag("snd-${snd.name}"))
            rule.onNodeWithTag("snd-${snd.name}").performClick()
        }
        Haptica.entries.forEach { h ->
            rule.onNodeWithTag("galeria").performScrollToNode(hasTestTag("hap-${h.name}"))
            rule.onNodeWithTag("hap-${h.name}").performClick()
        }
        assertEquals(Snd.entries.filterNot { it.bucle }, sonido.snds())
        assertEquals(Haptica.entries.toList(), tacto.hapticas.takeLast(Haptica.entries.size))
    }

    @Test
    fun `the gallery shows the thirty plates, six species by five stages`() {
        rule.setContent { ConSistema(SonidoGrabado(), TactoGrabado()) { GaleriaSistema(alCerrar = {}) } }
        rule.onNodeWithTag("galeria").performScrollToNode(hasTestTag("galeria-laminas"))
        rule.onAllNodesWithTag("lamina").assertCountEquals(30)
    }
}
