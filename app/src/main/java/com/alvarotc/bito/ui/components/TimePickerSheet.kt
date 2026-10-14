package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo
import com.alvarotc.bito.ui.sistema.rememberGesto

/** Lado del reloj: el componente no trae tamaño propio. */
private val RELOJ = 260.dp

/**
 * Shared minute-of-day picker: the sky clock ([RelojCielo], 5-minute steps) inside a [Hoja]. Speaks
 * minutes-since-midnight in and out, matching [com.alvarotc.bito.data.db.HabitEntity.reminderMinutes],
 * with no dependency on the habit form, so the settings screen can reuse it for its own reminder time.
 */
@Composable
fun TimePickerSheet(
    title: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutos by remember { mutableIntStateOf(initialMinutes) }
    val gesto = rememberGesto()
    Hoja(abierta = true, alCerrar = onDismiss, titulo = title) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RelojCielo(minutos, { minutos = it }, Modifier.size(RELOJ), paso = 5, descripcion = title)
            Spacer(Modifier.height(16.dp))
            BotonesHoja(
                principal = stringResource(R.string.save),
                alPrincipal = {
                    gesto(h = Haptica.CONFIRM)
                    cerrarY { onConfirm(minutos) }
                },
                suave = stringResource(R.string.cancel),
                alSuave = ::cerrar,
                modifier = Modifier.testTag("time-picker-confirm"),
            )
        }
    }
}
