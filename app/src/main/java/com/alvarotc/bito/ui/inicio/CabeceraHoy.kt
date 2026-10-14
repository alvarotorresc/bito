package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.formatDayWithPattern
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.anclaParticula
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Saldo

/**
 * Parte la fecha del patrón del día en dos líneas: «martes, 13 de octubre» da «Martes 13» y «de octubre»;
 * «Tuesday, October 13» da «Tuesday» y «October 13».
 */
internal fun partirFecha(texto: String): Pair<String, String> {
    val partes = texto.split(',', limit = 2).map { it.trim() }
    val cabeza = partes[0]
    val cola = partes.getOrElse(1) { "" }
    val numeroPrimero = cola.firstOrNull()?.isDigit() == true
    val (primera, segunda) =
        if (numeroPrimero) {
            val trozos = cola.split(' ', limit = 2)
            "$cabeza ${trozos[0]}" to trozos.getOrElse(1) { "" }
        } else {
            cabeza to cola
        }
    return primera.replaceFirstChar { it.uppercase() } to segunda
}

/**
 * La fecha en dos líneas a la izquierda; a la derecha el saldo en un canto (abre «Tus bitos») y el engranaje.
 * Va sobre el jardín.
 */
@Composable
fun CabeceraHoy(
    hoy: LogicalDay,
    saldo: Int,
    alSaldo: () -> Unit,
    alAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val patron = stringResource(R.string.today_date_pattern)
    val fecha = remember(hoy, patron) { partirFecha(formatDayWithPattern(hoy, patron)) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = Medidas.margen, vertical = Medidas.e12),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                fecha.first,
                style = BitoTema.tipos.t24.copy(fontWeight = FontWeight.Bold),
                color = colores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fecha.second.isNotEmpty()) {
                Text(
                    fecha.second,
                    style = BitoTema.tipos.t16,
                    color = colores.tintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // La zona pulsable llega a 44 dp aunque la píldora del saldo sea más baja.
            Box(
                Modifier
                    .testTag("cabecera-saldo")
                    .heightIn(min = Medidas.toqueMinimo)
                    .clickable(role = Role.Button, onClick = alSaldo),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .anclaParticula(ClavesAncla.SALDO)
                        .background(colores.canto, BitoFormas.canto)
                        .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                        .padding(horizontal = Medidas.e12, vertical = Medidas.e8),
                ) { Saldo(saldo) }
            }
            Box(
                Modifier
                    .padding(start = Medidas.e4)
                    .size(Medidas.toqueMinimo)
                    .clickable(role = Role.Button, onClick = alAjustes)
                    .testTag("cabecera-ajustes"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IconosSistema.GearSix,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = colores.tinta,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
