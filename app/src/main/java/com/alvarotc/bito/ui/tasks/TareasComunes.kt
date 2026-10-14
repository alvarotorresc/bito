package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.fuerte
import java.time.LocalDate

/** Un [LogicalDay] es un día-época UTC; el calendario del sistema habla de [LocalDate]. */
internal fun LogicalDay.aFecha(): LocalDate = LocalDate.ofEpochDay(toLong())

internal fun LocalDate.aDiaLogico(): LogicalDay = toEpochDay().toInt()

/** Flecha de atrás y título: la cabecera de las pantallas de tareas. */
@Composable
internal fun CabeceraTareas(
    titulo: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Box(
            Modifier
                .defaultMinSize(Medidas.toqueMinimo, Medidas.toqueMinimo)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.CaretLeft, contentDescription = stringResource(R.string.back), tint = colores.tinta)
        }
        Text(titulo, style = BitoTema.tipos.t32.fuerte(), color = colores.tinta, modifier = Modifier.padding(vertical = Medidas.e4))
    }
}

/** «¿Borrar esta tarea?»: el mismo diálogo en el formulario y en el menú de fila. */
@Composable
internal fun DialogoBorrarTarea(
    abierto: Boolean,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
) {
    Dialogo(
        abierto = abierto,
        titulo = stringResource(R.string.tasks2_borrar_titulo),
        cuerpo = stringResource(R.string.tasks2_borrar_cuerpo),
        verbo = stringResource(R.string.task_delete),
        alConfirmar = alConfirmar,
        alCancelar = alCancelar,
        peligro = true,
    )
}
