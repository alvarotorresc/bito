package com.alvarotc.bito.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalTacto

/**
 * Una vibracion corta y suave (QA 2026-08-23/24): sale del vibrador de verdad, no de
 * performHapticFeedback, que obedece al ajuste de respuesta tactil del sistema. Quien llama decide
 * si toca vibrar (el ajuste logHapticEnabled); el tacto lo vuelve a comprobar.
 */
@Composable
fun rememberSoftBuzz(): () -> Unit {
    val tacto = LocalTacto.current
    return remember(tacto) { { tacto.haptica(Haptica.TICK) } }
}
