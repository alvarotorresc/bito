package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones

private const val RELLENO_MS = 900

/** «Hacer backup ahora»: se llena de agua de izquierda a derecha mientras [trabajando] (900 ms `drawer`). */
@Composable
fun BotonAgua(
    texto: String,
    trabajando: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    val relleno = remember { Animatable(0f) }
    LaunchedEffect(trabajando, reducido) {
        if (trabajando) {
            val spec = if (reducido) tween<Float>(Duraciones.REDUCIDO) else tween<Float>(RELLENO_MS, easing = Curvas.drawer)
            relleno.animateTo(1f, spec)
        } else {
            relleno.snapTo(0f)
        }
    }
    Boton(
        texto,
        alPulsar,
        modifier.semantics { if (trabajando) progressBarRangeInfo = ProgressBarRangeInfo(relleno.value, 0f..1f) },
        apagado = trabajando,
        progreso = { relleno.value },
    )
}
