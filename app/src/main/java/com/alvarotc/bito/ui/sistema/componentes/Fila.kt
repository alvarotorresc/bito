package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalEstrato
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private val ALTO_FILA = 56.dp

/** El cuerpo común de las filas: alto, separador, etiqueta, subtítulo y lo de la derecha. */
@Composable
private fun CuerpoFila(
    etiqueta: String,
    modifier: Modifier,
    subtitulo: String?,
    habilitada: Boolean,
    derecha: (@Composable () -> Unit)?,
) {
    val estrato = LocalEstrato.current
    val tinta = estrato?.tinta ?: BitoTema.colores.tinta
    val tinta2 = estrato?.tinta2 ?: BitoTema.colores.tintaSuave
    val linea = estrato?.linea ?: BitoTema.colores.contorno.copy(alpha = 0.16f)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (habilitada) 1f else 0.45f)
            .heightIn(min = ALTO_FILA)
            .drawBehind {
                val grosor = 1.dp.toPx()
                val y = size.height - grosor / 2
                drawLine(linea, Offset(0f, y), Offset(size.width, y), grosor)
            }.padding(vertical = Medidas.e8),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(etiqueta, style = BitoTema.tipos.t16, color = tinta)
            subtitulo?.let { Text(it, style = BitoTema.tipos.t14, color = tinta2) }
        }
        derecha?.invoke()
    }
}

/** Fila de ajustes o de lista (GUIA `.arow`). Toma tinta y línea de `LocalEstrato` si la hay. */
@Composable
fun Fila(
    etiqueta: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    alPulsar: (() -> Unit)? = null,
    habilitada: Boolean = true,
    derecha: (@Composable () -> Unit)? = null,
) {
    val pulsable =
        if (alPulsar != null && habilitada) Modifier.clickable(role = Role.Button, onClick = alPulsar) else Modifier
    CuerpoFila(etiqueta, modifier.then(pulsable), subtitulo, habilitada, derecha)
}

/** Toda la fila es el mando: la semántica de interruptor la lleva la fila, no el dibujo. */
@Composable
fun FilaInterruptor(
    etiqueta: String,
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    habilitada: Boolean = true,
) {
    val gesto = rememberGesto()
    val mando =
        Modifier.toggleable(
            value = activo,
            enabled = habilitada,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            gesto(if (it) Snd.INTERRUPTOR_ON else Snd.INTERRUPTOR_OFF, Haptica.TICK)
            alCambiar(it)
        }
    CuerpoFila(etiqueta, modifier.then(mando), subtitulo, habilitada) { PistaInterruptor(activo) }
}

@Composable
fun FilaValor(
    etiqueta: String,
    valor: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    habilitada: Boolean = true,
) {
    val tinta2 = LocalEstrato.current?.tinta2 ?: BitoTema.colores.tintaSuave
    Fila(etiqueta, modifier, subtitulo, alPulsar, habilitada) {
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e4), verticalAlignment = Alignment.CenterVertically) {
            Text(valor, style = BitoTema.tipos.t16, color = tinta2)
            Icon(IconosSistema.CaretRight, contentDescription = null, Modifier.size(16.dp), tint = tinta2)
        }
    }
}

/** [externo]: abre fuera de la app (lleva la flecha de enlace externo). */
@Composable
fun FilaEnlace(
    etiqueta: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    externo: Boolean = false,
) {
    val tinta2 = LocalEstrato.current?.tinta2 ?: BitoTema.colores.tintaSuave
    Fila(etiqueta, modifier, subtitulo, alPulsar) {
        Icon(
            if (externo) IconosSistema.ArrowUpRight else IconosSistema.CaretRight,
            contentDescription = null,
            Modifier.size(16.dp),
            tint = tinta2,
        )
    }
}
