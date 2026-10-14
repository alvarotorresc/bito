package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

enum class FormaAgua { FILA, CANTO }

data class OpcionAgua(
    val clave: String,
    val texto: String,
    val subtitulo: String? = null,
    val icono: ImageVector? = null,
)

/** G24: el aro de lo elegido se llena de agua y el anterior se vacía. Nunca una fila de chips. */
@Composable
fun ElegirConAgua(
    opciones: List<OpcionAgua>,
    elegida: String?,
    alElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
    forma: FormaAgua = FormaAgua.FILA,
) {
    val gesto = rememberGesto()
    val reglas: (OpcionAgua) -> Modifier = { o ->
        Modifier.selectable(
            selected = o.clave == elegida,
            role = Role.RadioButton,
            onClick = {
                if (o.clave != elegida) {
                    gesto(Snd.TIC, Haptica.SEG)
                    alElegir(o.clave)
                }
            },
        )
    }
    if (forma == FormaAgua.FILA) {
        Column(modifier.selectableGroup()) {
            opciones.forEach { o ->
                Row(
                    reglas(o).fillMaxWidth().heightIn(min = Medidas.toqueMinimo),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
                ) {
                    Aro(o.clave == elegida)
                    Textos(o, TextAlign.Start)
                }
            }
        }
    } else {
        Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            opciones.forEach { o ->
                Column(
                    reglas(o)
                        .width(96.dp)
                        .border(Medidas.linea, BitoTema.colores.contorno, BitoFormas.campo)
                        .padding(Medidas.e12),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Medidas.e8),
                ) {
                    Aro(o.clave == elegida)
                    Textos(o, TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Textos(
    o: OpcionAgua,
    alineacion: TextAlign,
) {
    val colores = BitoTema.colores
    if (o.icono != null) Icon(o.icono, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(24.dp))
    Column {
        Text(o.texto, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta, textAlign = alineacion)
        if (o.subtitulo != null) Text(o.subtitulo, style = BitoTema.tipos.t14, color = colores.tintaSuave, textAlign = alineacion)
    }
}

/** El aro de 22 dp: el nivel de agua sube con `marcar` y baja con `hundir`; uno por opción. */
@Composable
private fun Aro(elegido: Boolean) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val nivel = remember { Animatable(if (elegido) 1f else 0f) }
    LaunchedEffect(elegido) {
        when {
            reducido -> nivel.animateTo(if (elegido) 1f else 0f, tween(Duraciones.REDUCIDO))
            elegido -> nivel.animateTo(1f, Muelles.marcar())
            else -> nivel.animateTo(0f, Muelles.hundir())
        }
    }
    val linea = Medidas.linea
    Canvas(Modifier.size(22.dp)) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radio = size.minDimension / 2f - linea.toPx() / 2f
        drawCircle(colores.agua, radius = (radio * nivel.value).coerceAtLeast(0f), center = centro)
        drawCircle(colores.contorno, radius = radio, center = centro, style = Stroke(linea.toPx()))
    }
}
