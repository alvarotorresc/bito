package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.Curvas

// Cambio de bloque (`mostrar()` del mock). Lo viejo: opacidad 1 a 0 y +8 dp en 160 ms `out`. Lo nuevo entra 140 ms
// después, cada hijo desde +12 dp y opacidad 0 en 260 ms `out`, con un retraso de 60 + 40 · i ms.
internal const val SALE_MS = 160
internal const val ENTRA_MS = 260
internal const val ENTRA_RETRASO_MS = 140
internal const val RETRASO_BASE_MS = 60
internal const val RETRASO_HIJO_MS = 40
private const val SALE_DESPLAZA_DP = 8f
private const val ENTRA_DESPLAZA_DP = 12f

/** Lo que leen los tests del escalonado (opacidad y desplazamiento en dp de un hijo). No se anuncia. */
internal val EscalonAlfa = SemanticsPropertyKey<Float>("EscalonAlfa")
internal val EscalonDesplazaDp = SemanticsPropertyKey<Float>("EscalonDesplazaDp")

/** La transición del `AnimatedContent` del bloque que se está dibujando; `null` con movimiento reducido. */
internal val LocalEscalonado = compositionLocalOf<Transition<EnterExitState>?> { null }

/**
 * El hijo número [i] del bloque entra y sale por su cuenta (retraso `60 + 40 · i` ms detrás de los 140 ms de lo
 * viejo). Sin [LocalEscalonado] (movimiento reducido, o fuera del cambio de bloque) no hace nada.
 */
@Composable
internal fun Modifier.escalon(i: Int): Modifier {
    val transicion = LocalEscalonado.current ?: return this
    val retraso = ENTRA_RETRASO_MS + RETRASO_BASE_MS + RETRASO_HIJO_MS * i
    val alfa by transicion.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.PostExit) {
                tween(SALE_MS, easing = Curvas.out)
            } else {
                tween(ENTRA_MS, retraso, Curvas.out)
            }
        },
        label = "escalonAlfa",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val baja by transicion.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.PostExit) {
                tween(SALE_MS, easing = Curvas.out)
            } else {
                tween(ENTRA_MS, retraso, Curvas.out)
            }
        },
        label = "escalonBaja",
    ) {
        when (it) {
            EnterExitState.PreEnter -> ENTRA_DESPLAZA_DP
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> SALE_DESPLAZA_DP
        }
    }
    return this
        .graphicsLayer {
            this.alpha = alfa
            translationY = baja.dp.toPx()
        }.semantics {
            this[EscalonAlfa] = alfa
            this[EscalonDesplazaDp] = baja
        }
}
