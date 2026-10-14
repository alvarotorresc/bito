package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** G25: el indicador corre y cambia de ancho. Suena `tic`, vibra `seg`. */
@Composable
fun Segmentado(
    opciones: List<String>,
    elegida: Int,
    alElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val densidad = LocalDensity.current
    val gesto = rememberGesto()
    // Posición y ancho medidos de cada opción, en px.
    val medidas: SnapshotStateMap<Int, Pair<Float, Float>> = remember { mutableStateMapOf() }
    val actual = medidas[elegida]
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(actual != null) { if (actual != null) listo = true }
    val destinoX = with(densidad) { (actual?.first ?: 0f).toDp() }
    val destinoAncho = with(densidad) { (actual?.second ?: 0f).toDp() }
    val spec = if (listo) Muelles.indicador<Dp>(reducido) else snap()
    val x by animateDpAsState(destinoX, spec, label = "indicadorX")
    val ancho by animateDpAsState(destinoAncho, spec, label = "indicadorAncho")
    Box(
        modifier
            .clip(BitoFormas.canto)
            .background(colores.canto)
            .border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto)
            .padding(Medidas.e4),
    ) {
        if (actual != null) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(ancho)
                    .height(Medidas.boton - Medidas.e4)
                    .clip(BitoFormas.canto)
                    .background(colores.agua),
            )
        }
        Row(Modifier.selectableGroup()) {
            opciones.forEachIndexed { i, texto ->
                Box(
                    Modifier
                        .onGloballyPositioned { medidas[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(Medidas.boton - Medidas.e4)
                        .clip(BitoFormas.canto)
                        .selectable(
                            selected = i == elegida,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (i != elegida) {
                                gesto(Snd.TIC, Haptica.SEG)
                                alElegir(i)
                            }
                        }.padding(horizontal = Medidas.e16),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        texto,
                        style = BitoTema.tipos.t16.fuerte(),
                        color = if (i == elegida) colores.tinta else colores.tintaSuave,
                    )
                }
            }
        }
    }
}
