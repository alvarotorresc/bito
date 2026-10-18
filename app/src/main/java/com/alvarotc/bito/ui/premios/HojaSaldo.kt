package com.alvarotc.bito.ui.premios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.EtiquetaMovimiento
import com.alvarotc.bito.domain.FechaMovimiento
import com.alvarotc.bito.domain.Movimiento
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.ui.habi.itemNameRes
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.Saldo
import com.alvarotc.bito.ui.sistema.fuerte
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MOVIMIENTOS_VISIBLES = 5

/**
 * «Tus bitos» (§6.5): el saldo grande, las reglas con las cifras de la economía, lo que se ha ganado y gastado
 * últimamente y, lado a lado, «Tienda» y «Cerrar». «Tienda» cierra la hoja y navega a los 160 ms (`cerrarY`). Con [alIrTienda] nulo
 * (dentro de la tienda) no hay botón de tienda y «Cerrar» ocupa el ancho.
 */
@Composable
fun HojaSaldo(
    abierta: Boolean,
    saldo: Int,
    movimientos: List<Movimiento>,
    economia: EconomyConfig,
    alIrTienda: (() -> Unit)? = null,
    alCerrar: () -> Unit,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    Hoja(abierta, alCerrar, modifier = Modifier.testTag("hoja-saldo"), titulo = stringResource(R.string.premios_tus_bitos)) {
        Saldo(saldo, Modifier.padding(top = Medidas.e4), tamanoMoneda = 48.dp, estilo = tipos.t48.cifra())
        Spacer(Modifier.height(Medidas.e16))
        Column(verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            val racha = economia.streakMilestonePoints
            Regla(economia.habitDonePoints, stringResource(R.string.premios_regla_habito))
            Regla(economia.taskDonePoints, stringResource(R.string.premios_regla_tarea, economia.taskOnTimePoints))
            Regla(economia.perfectDayPoints, stringResource(R.string.premios_regla_dia))
            Regla(
                economia.perfectWeekPoints,
                buildAnnotatedString {
                    append(stringResource(R.string.premios_regla_semana) + " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colores.oroTexto)) {
                        append(stringResource(R.string.premios_mas_n, economia.perfectMonthPoints))
                    }
                    append(" " + stringResource(R.string.premios_regla_mes))
                },
            )
            Regla(
                racha[7] ?: 0,
                stringResource(R.string.premios_regla_racha, racha[30] ?: 0, racha[100] ?: 0, racha[365] ?: 0),
            )
        }
        Spacer(Modifier.height(Medidas.e12))
        Text(stringResource(R.string.premios_nota_tuyo), style = tipos.t14, color = colores.tintaSuave)
        if (movimientos.isNotEmpty()) {
            Spacer(Modifier.height(Medidas.e16))
            Text(stringResource(R.string.premios_ultimos), style = tipos.t16.fuerte(), color = colores.tintaSuave)
            Spacer(Modifier.height(Medidas.e4))
            movimientos.take(MOVIMIENTOS_VISIBLES).forEach { FilaMovimiento(it) }
        }
        Spacer(Modifier.height(Medidas.e16))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            if (alIrTienda != null) {
                Boton(
                    stringResource(R.string.premios_ir_tienda),
                    { cerrarY(alIrTienda) },
                    Modifier.weight(1f).testTag("saldo-tienda"),
                    estilo = EstiloBoton.PRINCIPAL,
                    alto = Medidas.botonHoja,
                )
            }
            Boton(
                stringResource(R.string.sistema_cerrar),
                { cerrar() },
                Modifier.weight(1f).testTag("saldo-cerrar"),
                alto = Medidas.botonHoja,
            )
        }
    }
}

@Composable
private fun Regla(
    puntos: Int,
    texto: String,
) = Regla(puntos, AnnotatedString(texto))

@Composable
private fun Regla(
    puntos: Int,
    texto: AnnotatedString,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            stringResource(R.string.premios_mas_n, puntos),
            Modifier.defaultMinSize(minWidth = 48.dp).padding(end = Medidas.e12),
            style = BitoTema.tipos.t16.fuerte(),
            color = BitoTema.colores.oroTexto,
            textAlign = TextAlign.End,
        )
        Text(texto, Modifier.weight(1f), style = BitoTema.tipos.t16, color = BitoTema.colores.tinta)
    }
}

@Composable
private fun FilaMovimiento(m: Movimiento) {
    val colores = BitoTema.colores
    val etiqueta =
        when (val e = m.etiqueta) {
            is EtiquetaMovimiento.Habito -> e.nombre ?: stringResource(R.string.base_mov_habito_borrado)
            is EtiquetaMovimiento.Tarea -> e.titulo ?: stringResource(R.string.base_mov_tarea_borrada)
            EtiquetaMovimiento.DiaCompleto -> stringResource(R.string.base_mov_dia)
            EtiquetaMovimiento.SemanaCompleta -> stringResource(R.string.base_mov_semana)
            EtiquetaMovimiento.MesCompleto -> stringResource(R.string.base_mov_mes)
            is EtiquetaMovimiento.Racha ->
                stringResource(R.string.base_mov_racha, e.n, e.nombre ?: stringResource(R.string.base_mov_habito_borrado))
            EtiquetaMovimiento.Congelador -> stringResource(R.string.base_mov_congelador)
            is EtiquetaMovimiento.Articulo -> stringResource(itemNameRes(e.itemId))
        }
    val fecha =
        when (m.fecha) {
            FechaMovimiento.HOY -> stringResource(R.string.base_hoy)
            FechaMovimiento.AYER -> stringResource(R.string.base_ayer)
            FechaMovimiento.FECHA -> stringResource(R.string.base_el_dia, diaEscrito(m.dia))
        }
    val cifra = if (m.delta >= 0) "+${m.delta}" else "−${-m.delta}"
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            cifra,
            Modifier.defaultMinSize(minWidth = 48.dp).padding(end = Medidas.e12),
            style = BitoTema.tipos.t16.fuerte(),
            color = if (m.delta >= 0) colores.oroTexto else colores.tintaSuave,
            textAlign = TextAlign.End,
        )
        Text(etiqueta, Modifier.weight(1f), style = BitoTema.tipos.t16, color = colores.tinta)
        Text(fecha, style = BitoTema.tipos.t14, color = colores.tintaSuave)
    }
}

/** «2 de octubre» o «October 2», según el idioma en uso. */
@Composable
private fun diaEscrito(dia: Int): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val patron = if (locale.language == "es") "d 'de' MMMM" else "MMMM d"
    return DateTimeFormatter.ofPattern(patron, locale).format(LocalDate.ofEpochDay(dia.toLong()))
}
