package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Estrato
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalEstrato
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Caret que gira 180° con el muelle `indicador`. */
@Composable
private fun Caret(
    abierto: Boolean,
    color: Color,
) {
    val giro by animateFloatAsState(
        targetValue = if (abierto) 180f else 0f,
        animationSpec = Muelles.indicador(BitoTema.reducido),
        label = "caret",
    )
    Icon(IconosSistema.CaretDown, contentDescription = null, tint = color, modifier = Modifier.size(20.dp).rotate(giro))
}

/** G36: se abre y se cierra con su caret. */
@Composable
fun Plegable(
    titulo: String,
    abierto: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores

    fun alternar() {
        if (abierto) gesto(Snd.HUNDIR, Haptica.LOW) else gesto(Snd.BROTE, Haptica.TICK)
        alCambiar(!abierto)
    }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.toqueMinimo)
                .clickable(role = Role.Button, onClick = ::alternar)
                .semantics {
                    if (abierto) {
                        collapse {
                            alternar()
                            true
                        }
                    } else {
                        expand {
                            alternar()
                            true
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta, modifier = Modifier.weight(1f))
            Caret(abierto, colores.tintaSuave)
        }
        AnimatedVisibility(
            visible = abierto,
            enter =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    expandVertically(tween(Duraciones.PLEGAR_ABRE, easing = Curvas.drawer)) + fadeIn(tween(Duraciones.PLEGAR_ABRE))
                },
            exit =
                if (reducido) {
                    fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    shrinkVertically(tween(Duraciones.PLEGAR_CIERRA, easing = Curvas.out)) + fadeOut(tween(Duraciones.PLEGAR_CIERRA))
                },
        ) {
            Column { contenido() }
        }
    }
}

/** Un estrato de Ajustes (3.5): cabecera con título y [resumen] (cerrado), cuerpo con `capa`. Provee `LocalEstrato`. */
@Composable
fun EstratoPlegable(
    estrato: Estrato,
    titulo: String,
    resumen: String,
    abierto: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido

    fun pulsar() {
        if (abierto) gesto(Snd.ASCENSO, Haptica.TICK) else gesto(Snd.DESCENSO, Haptica.TICK)
        alPulsar()
    }
    CompositionLocalProvider(LocalEstrato provides estrato) {
        Column(
            modifier
                .fillMaxWidth()
                .background(estrato.fondo)
                .animateContentSize(Muelles.capa(reducido)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.toqueMinimo)
                    .clickable(role = Role.Button, onClick = ::pulsar)
                    .semantics {
                        if (abierto) {
                            collapse {
                                pulsar()
                                true
                            }
                        } else {
                            expand {
                                pulsar()
                                true
                            }
                        }
                    }.padding(horizontal = Medidas.margen, vertical = Medidas.e12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = BitoTema.tipos.t19.fuerte(), color = estrato.tinta)
                    AnimatedVisibility(
                        visible = !abierto,
                        enter = fadeIn(tween(Duraciones.COLOR)),
                        exit = fadeOut(tween(160)),
                    ) {
                        Text(resumen, style = BitoTema.tipos.t14, color = estrato.tinta2)
                    }
                }
                Caret(abierto, estrato.tinta)
            }
            AnimatedVisibility(
                visible = abierto,
                enter = fadeIn(tween(Duraciones.ENTRAR)),
                exit = fadeOut(tween(Duraciones.SALIR)),
            ) {
                Column(Modifier.padding(horizontal = Medidas.margen).padding(bottom = Medidas.e16)) { contenido() }
            }
        }
    }
}
