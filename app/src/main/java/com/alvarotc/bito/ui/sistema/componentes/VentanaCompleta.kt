package com.alvarotc.bito.ui.sistema.componentes

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * La ventana del `Dialog` que la llama ocupa la pantalla entera, barras incluidas, sin oscurecer lo de detrás.
 * Con `decorFitsSystemWindows = false` Compose da a la ventana el tamaño de la pantalla, pero sin
 * `FLAG_LAYOUT_NO_LIMITS` el sistema encaja la ventana entre las barras: el pie de la hoja quedaba fuera por
 * abajo lo que miden las dos barras. Así el velo cubre también la barra de estado y los insets llegan enteros
 * para `navigationBarsPadding`/`imePadding`.
 */
@Composable
internal fun VentanaAPantallaCompleta() {
    val ventana = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        ventana.setDimAmount(0f)
        ventana.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
}
