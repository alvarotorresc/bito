package com.alvarotc.bito.ui.sistema.componentes

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
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
class HojaTest {
    @get:Rule val rule = createComposeRule()
    private var cierres = 0
    private var abierta by mutableStateOf(true)
    private val sonido = SonidoGrabado()

    private fun montar(reducido: Boolean = false) {
        rule.setContent {
            ConSistema(sonido = sonido, reducido = reducido) {
                Hoja(abierta = abierta, alCerrar = {
                    cierres++
                    abierta = false
                }, titulo = "Recordatorios") { Text("dentro") }
            }
        }
    }

    @Test
    fun `opens with its content and the bud sound`() {
        montar()
        rule.onNodeWithText("dentro").assertIsDisplayed()
        assertTrue(Snd.BROTE in sonido.snds())
    }

    @Test
    fun `tapping the veil closes once`() {
        montar()
        rule.onNodeWithTag("hoja-velo").performClick()
        rule.waitForIdle()
        assertEquals(1, cierres)
        rule.onNodeWithText("dentro").assertDoesNotExist()
    }

    @Test
    fun `closing twice through the scope calls alCerrar once`() {
        var scopeHoja: HojaScope? = null
        rule.setContent {
            ConSistema(sonido = sonido) {
                Hoja(abierta = abierta, alCerrar = {
                    cierres++
                    abierta = false
                }) {
                    scopeHoja = this
                    Text("dentro")
                }
            }
        }
        rule.runOnUiThread {
            scopeHoja!!.cerrar()
            scopeHoja!!.cerrar()
        }
        rule.waitForIdle()
        assertEquals(1, cierres)
    }

    @Test
    fun `dragging down past 30 percent closes`() {
        montar()
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-asa").performTouchInput { swipeDown(startY = top, endY = top + 150f, durationMillis = 200) }
        rule.waitForIdle()
        assertEquals(1, cierres)
    }

    @Test
    fun `back during the opening animation closes once and leaves no veil`() {
        rule.mainClock.autoAdvance = false
        montar()
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("hoja-velo").performClick()
        rule.mainClock.advanceTimeBy(20)
        rule.onNodeWithTag("hoja-velo").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, cierres)
        rule.onNodeWithTag("hoja-velo").assertDoesNotExist()
    }

    @Test
    fun `a programmatic close leaves without calling alCerrar`() {
        montar()
        abierta = false
        rule.waitForIdle()
        assertEquals(0, cierres)
        rule.onNodeWithTag("hoja-velo").assertDoesNotExist()
    }

    @Test
    fun `reduced motion still opens and closes`() {
        montar(reducido = true)
        rule.onNodeWithText("dentro").assertIsDisplayed()
        rule.onNodeWithTag("hoja-velo").performClick()
        rule.waitForIdle()
        assertEquals(1, cierres)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h320dp")
    fun `on a small screen the save button is reachable by scrolling the body`() {
        var guardado = 0
        rule.setContent {
            ConSistema(sonido = sonido) {
                Hoja(abierta = true, alCerrar = {}, titulo = "Hora") {
                    Spacer(Modifier.height(600.dp))
                    BotonesHoja("Guardar", { guardado++ })
                }
            }
        }
        rule.onNodeWithText("Guardar").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, guardado)
    }

    // La ventana del Dialog pedía el alto de la pantalla, pero sin LAYOUT_NO_LIMITS el sistema la recortaba a la
    // franja entre barras: el contenido se salía por abajo lo que miden las barras (152 + 62 px en el Pixel).
    @Test
    fun `the sheet window covers the whole screen, bars included`() {
        var ventana: Window? = null
        rule.setContent {
            ConSistema(sonido = sonido) {
                Hoja(abierta = true, alCerrar = {}) {
                    ventana = (LocalView.current.parent as DialogWindowProvider).window
                    Text("dentro")
                }
            }
        }
        rule.waitForIdle()
        val atributos = ventana!!.attributes
        assertTrue(atributos.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertEquals(0f, atributos.dimAmount)
    }
}
