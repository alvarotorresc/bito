package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.formatDayWithPattern
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.componentes.Saldo

/** La fecha, el saldo y los ajustes de Hoy. Provisional: la rehace el carril c1. */
@Composable
fun CabeceraHoy(
    hoy: LogicalDay,
    saldo: Int,
    alSaldo: () -> Unit,
    alAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val patron = stringResource(R.string.today_date_pattern)
    val fecha = remember(hoy, patron) { formatDayWithPattern(hoy, patron) }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(fecha, style = MaterialTheme.typography.labelMedium, color = BitoTema.colores.tintaSuave)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Saldo(saldo, Modifier.clickable(onClick = alSaldo).testTag("cabecera-saldo"))
            Icon(
                IconosSistema.GearSix,
                contentDescription = stringResource(R.string.nav_settings),
                modifier = Modifier.clickable(onClick = alAjustes).testTag("cabecera-ajustes"),
            )
        }
    }
}
