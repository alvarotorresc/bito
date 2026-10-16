package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.unit.width as anchoDe

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

    private fun anchos(repartir: Boolean): List<Float> {
        rule.setContent {
            ConSistema(SonidoGrabado(), TactoGrabado()) {
                Segmentado(
                    listOf("Sí", "Una opción larga", "Media"),
                    0,
                    {},
                    modifier = Modifier.width(300.dp),
                    repartir = repartir,
                )
            }
        }
        val nodos = rule.onAllNodes(isSelectable())
        return (0..2).map { nodos[it].getUnclippedBoundsInRoot().anchoDe.value }
    }

    @Test
    fun `by default options keep their own width`() {
        val a = anchos(repartir = false)
        assertTrue("distintos: $a", a[0] < a[1] - 8f)
    }

    @Test
    fun `sharing the width gives every option the same share of the full width`() {
        val a = anchos(repartir = true)
        assertEquals(a[0], a[1], 1.5f)
        assertEquals(a[1], a[2], 1.5f)
        // Ocupan todo el ancho (300 dp) menos el relleno del contorno.
        assertTrue("suman ${3 * a[0]}", 3 * a[0] in 280f..300f)
    }
}
