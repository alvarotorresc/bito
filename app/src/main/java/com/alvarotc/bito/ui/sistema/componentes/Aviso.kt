package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class AvisoDatos(
    val texto: String,
    val accion: String? = null,
    val alAccion: (() -> Unit)? = null,
)

/** Estado del aviso con Deshacer (G9). Uno cada vez: el siguiente espera su turno. */
@Stable
class AvisoEstado {
    var actual: AvisoDatos? by mutableStateOf(null)
        internal set

    /** Lo que esquiva el aviso (el +) sube esto mientras se ve: 64 dp. Lo anima `AvisoHost`. */
    var desplazamiento: Dp by mutableStateOf(0.dp)
        internal set

    private val turno = Mutex()
    private var pulsado: CompletableDeferred<Unit>? = null

    /** Quita el aviso que se ve sin esperar a que se acaben sus 4 s. */
    internal fun accionar() {
        pulsado?.complete(Unit)
    }

    /** Enseña [texto] 4 s, o hasta que se pulse la acción. Vuelve cuando se ha ido. */
    suspend fun mostrar(
        texto: String,
        accion: String? = null,
        alAccion: (() -> Unit)? = null,
    ) = turno.withLock {
        val espera = CompletableDeferred<Unit>()
        pulsado = espera
        actual = AvisoDatos(texto, accion, alAccion)
        try {
            withTimeoutOrNull(Duraciones.AVISO.toLong()) { espera.await() }
        } finally {
            actual = null
            pulsado = null
        }
    }
}

@Composable
fun rememberAvisoEstado(): AvisoEstado = remember { AvisoEstado() }

/** Va en la raíz de cada pantalla que enseña avisos, abajo. */
@Composable
fun AvisoHost(
    estado: AvisoEstado,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    val actual = estado.actual
    val retenido = remember { arrayOfNulls<AvisoDatos>(1) }
    if (actual != null) retenido[0] = actual
    val subida by animateDpAsState(
        targetValue = if (actual != null) 64.dp else 0.dp,
        animationSpec = if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hoja(umbral = Dp.VisibilityThreshold),
        label = "avisoSubida",
    )
    val alturaActual = subida
    SideEffect { estado.desplazamiento = alturaActual }
    Box(modifier) {
        AnimatedVisibility(
            visible = actual != null,
            enter =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    slideInVertically(Muelles.hoja(umbral = IntOffset.VisibilityThreshold)) { it }
                },
            exit =
                if (reducido) {
                    fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    slideOutVertically(Muelles.hundir(umbral = IntOffset.VisibilityThreshold)) { it }
                },
        ) {
            val aviso = retenido[0]
            if (aviso != null) {
                Row(
                    Modifier
                        .testTag("aviso")
                        .padding(Medidas.margen)
                        .background(colores.roca, BitoFormas.campo)
                        .border(BorderStroke(Medidas.linea, colores.tintaClaraSuave), BitoFormas.campo)
                        .padding(horizontal = Medidas.e16, vertical = Medidas.e8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(aviso.texto, style = BitoTema.tipos.t16, color = colores.tintaClara)
                    if (aviso.accion != null) {
                        Text(
                            aviso.accion,
                            style = BitoTema.tipos.t16,
                            color = colores.agua,
                            modifier =
                                Modifier
                                    .padding(start = Medidas.e12)
                                    .defaultMinSize(minHeight = Medidas.toqueMinimo)
                                    .clickable {
                                        gesto(Snd.BROTE, Haptica.TICK)
                                        aviso.alAccion?.invoke()
                                        estado.accionar()
                                    }.padding(horizontal = Medidas.e8),
                        )
                    }
                }
            }
        }
    }
}
