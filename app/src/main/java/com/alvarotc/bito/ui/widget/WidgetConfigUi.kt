package com.alvarotc.bito.ui.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Marco común de las dos configuraciones: título, frase, contenido y «Guardar» (fuera.html, config-*). */
@Composable
internal fun MarcoConfigWidget(
    titulo: String,
    cuerpo: String,
    guardar: String,
    alGuardar: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val colores = BitoTema.colores
    Column(
        Modifier
            .fillMaxSize()
            .background(colores.tAlta)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Medidas.margen),
    ) {
        Text(titulo, style = BitoTema.tipos.t24.fuerte(), color = colores.tinta)
        Spacer(Modifier.height(Medidas.e4))
        Text(cuerpo, style = BitoTema.tipos.t14, color = colores.tintaSuave)
        Spacer(Modifier.height(Medidas.e16))
        contenido()
        Boton(
            texto = guardar,
            alPulsar = alGuardar,
            estilo = EstiloBoton.PRINCIPAL,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Selección múltiple con el aro de agua (G24): se llena al elegir y se vacía al soltar. Nunca una casilla. */
@Composable
internal fun ElegirVariasConAgua(
    habitos: List<HabitEntity>,
    elegidos: Set<String>,
    alAlternar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    Column(modifier.selectableGroup()) {
        habitos.forEach { habito ->
            val marcado = habito.id in elegidos
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Medidas.toqueMinimo)
                    .toggleable(
                        value = marcado,
                        role = Role.Checkbox,
                        onValueChange = {
                            if (marcado) gesto(Snd.VACIAR, Haptica.LOW) else gesto(Snd.TIC, Haptica.SEG)
                            alAlternar(habito.id)
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
            ) {
                AroAgua(marcado)
                Text(habito.name, style = BitoTema.tipos.t16.fuerte(), color = BitoTema.colores.tinta)
            }
        }
    }
}

@Composable
private fun AroAgua(marcado: Boolean) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val nivel = remember { Animatable(if (marcado) 1f else 0f) }
    LaunchedEffect(marcado) {
        when {
            reducido -> nivel.animateTo(if (marcado) 1f else 0f, tween(Duraciones.REDUCIDO))
            marcado -> nivel.animateTo(1f, Muelles.marcar())
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
