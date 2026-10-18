package com.alvarotc.bito.ui.sistema.componentes

import android.view.WindowManager
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.TactoGrabado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class DialogoTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the yes repeats the verb, cancel sits above, and a dangerous one rejects on open`() {
        val tacto = TactoGrabado()
        var si = 0
        rule.setContent {
            ConSistema(tacto = tacto) {
                Dialogo(
                    abierto = true,
                    titulo = "¿Borrar para siempre?",
                    verbo = "Borrar",
                    alConfirmar = { si++ },
                    alCancelar = {},
                    peligro = true,
                )
            }
        }
        val cancelar = rule.onNodeWithText("Cancelar").getBoundsInRoot()
        val borrar = rule.onNodeWithText("Borrar").getBoundsInRoot()
        assertTrue("«Cancelar» va encima", cancelar.top < borrar.top)
        assertTrue(Haptica.REJECT in tacto.hapticas)
        rule.onNodeWithText("Borrar").performClick()
        rule.waitForIdle()
        assertEquals(1, si)
        assertTrue(Haptica.FALL in tacto.hapticas)
    }

    // Como la hoja: la ventana no se encaja entre las barras ni respeta el recorte; si no, con barras desiguales
    // (152 arriba, 63 abajo en el Pixel) la ventana centrada bajaba 45 px y el pie quedaba bajo la barra de gestos.
    @Test
    fun `the dialog window ignores the system bars and the cutout`() {
        rule.setContent {
            ConSistema {
                Dialogo(abierto = true, titulo = "¿Seguro?", verbo = "Sí", alConfirmar = {}, alCancelar = {})
            }
        }
        rule.waitForIdle()
        val atributos = ShadowDialog.getLatestDialog().window!!.attributes
        // LAYOUT_NO_LIMITS deja la ventana sin insets (navegación 0): el pie se metía bajo la barra de gestos.
        assertTrue(atributos.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS == 0)
        assertEquals(0, atributos.fitInsetsTypes)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, atributos.layoutInDisplayCutoutMode)
    }
}
