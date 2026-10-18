package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private val ALTO_COLA = 10.dp
private val ANCHO_COLA = 20.dp

/** Globito (G11): crece desde su cola y se va solo a los 2,4 s si [autoOcultar]. [cola] de 0 (izquierda) a 1 (derecha). */
@Composable
fun Globito(
    visible: Boolean,
    texto: String,
    modifier: Modifier = Modifier,
    alIrse: () -> Unit = {},
    autoOcultar: Boolean = true,
    cola: Float = 0.5f,
    // El mismo texto con estilo (p. ej. el nombre en negrita); si está, se pinta en vez de [texto].
    textoRico: AnnotatedString? = null,
    // Se llama cuando el globito ya acabó su salida y no se ve.
    alTerminar: () -> Unit = {},
) {
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val densidad = LocalDensity.current
    val alIrseActual = rememberUpdatedState(alIrse)
    val estado = remember { MutableTransitionState(false) }
    estado.targetState = visible
    LaunchedEffect(visible) {
        if (visible) {
            gesto(Snd.BROTE)
            if (autoOcultar) {
                kotlinx.coroutines.delay(Duraciones.GLOBITO.toLong())
                estado.targetState = false
                alIrseActual.value()
            }
        }
    }
    val alTerminarActual = rememberUpdatedState(alTerminar)
    val terminado = !estado.currentState && !estado.targetState
    LaunchedEffect(terminado) { if (terminado) alTerminarActual.value() }
    if (terminado) return
    val subida = with(densidad) { 6.dp.roundToPx() }
    Popup(popupPositionProvider = remember(cola) { SobreElAncla(cola) }) {
        AnimatedVisibility(
            visibleState = estado,
            enter =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    scaleIn(Muelles.hoja(), initialScale = .9f, transformOrigin = TransformOrigin(cola, 1f)) +
                        fadeIn(tween(Duraciones.COLOR)) +
                        slideInVertically(Muelles.hoja(umbral = IntOffset.VisibilityThreshold)) { subida }
                },
            exit =
                if (reducido) {
                    fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    scaleOut(Muelles.hundir(), targetScale = .9f, transformOrigin = TransformOrigin(cola, 1f)) +
                        fadeOut(tween(Duraciones.SALIR))
                },
        ) {
            Column(modifier.testTag("globito")) {
                Text(
                    textoRico ?: AnnotatedString(texto),
                    style = BitoTema.tipos.t14,
                    color = colores.tinta,
                    modifier =
                        Modifier
                            .background(colores.canto, BitoFormas.globo)
                            .border(Medidas.linea, colores.contorno, BitoFormas.globo)
                            .padding(Medidas.e12),
                )
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(ALTO_COLA)
                        .drawBehind {
                            val ancho = ANCHO_COLA.toPx()
                            val x = (size.width - ancho) * cola
                            val punta =
                                Path().apply {
                                    moveTo(x, 0f)
                                    lineTo(x + ancho / 2, size.height)
                                    lineTo(x + ancho, 0f)
                                }
                            drawPath(punta, colores.canto)
                            drawPath(punta, colores.contorno, style = Stroke(Medidas.linea.toPx()))
                            drawLine(colores.canto, Offset(x, 0f), Offset(x + ancho, 0f), strokeWidth = Medidas.linea.toPx() * 2)
                        },
                )
            }
        }
    }
}

/** Pone el globito centrado sobre su ancla, con la cola apuntándole al centro. */
private class SobreElAncla(
    private val cola: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.center.x - (popupContentSize.width * cola).toInt()
        val y = anchorBounds.top - popupContentSize.height
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y.coerceAtLeast(0))
    }
}
