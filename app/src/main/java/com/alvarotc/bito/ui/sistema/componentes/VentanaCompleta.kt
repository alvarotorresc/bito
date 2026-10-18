package com.alvarotc.bito.ui.sistema.componentes

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * La ventana del `Dialog` que la llama ocupa la pantalla entera, barras y recorte incluidos, sin oscurecer lo de
 * detrás, y recibe los insets enteros para `navigationBarsPadding`/`imePadding`.
 *
 * Compose pide para la ventana el tamaño de la pantalla (`screenHeightDp`) con gravedad `CENTER`, y el sistema la
 * centra dentro de su marco padre, que por defecto es la franja entre las barras (`fitInsetsTypes`). Con barras
 * desiguales la ventana se desplaza: en el Pixel 9a (152 px arriba, 63 abajo) bajaba 45 px. Con `fitInsetsTypes = 0`
 * el marco padre es la pantalla. No se usa `FLAG_LAYOUT_NO_LIMITS`: el sistema da a esas ventanas insets 0 y el pie
 * de la hoja se metía bajo la barra de gestos.
 */
@Composable
internal fun VentanaAPantallaCompleta() {
    val ventana = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect { ventana.aPantallaCompleta() }
}

private const val NO_LIMITS = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
private const val EN_PANTALLA = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

private fun Window.aPantallaCompleta() {
    setDimAmount(0f)
    val actuales = attributes
    val recorte =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else -> null
        }
    val banderasHechas = actuales.flags and (NO_LIMITS or EN_PANTALLA) == EN_PANTALLA
    val sinEncajar = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || actuales.fitInsetsTypes == 0
    val recorteHecho = recorte == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && actuales.layoutInDisplayCutoutMode == recorte)
    if (banderasHechas && sinEncajar && recorteHecho) return // cada cambio de atributos relanza el layout de la ventana
    attributes =
        WindowManager.LayoutParams().apply {
            copyFrom(actuales)
            flags = (flags or EN_PANTALLA) and NO_LIMITS.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
            if (recorte != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = recorte
        }
}
