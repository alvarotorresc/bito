package com.alvarotc.bito.ui.camino

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.Totals
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.stats.RecordRow
import java.text.NumberFormat

/** Récords: el mejor de cada hábito, también los que están en pausa o archivados. Mejor racha primero. */
@Composable
fun HojaRecords(
    abierta: Boolean,
    records: List<RecordRow>,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar, titulo = stringResource(R.string.stats_teaser_records)) {
        Column(Modifier.testTag("hoja-records")) {
            if (records.isEmpty()) {
                Text(stringResource(R.string.records_empty), style = BitoTema.tipos.t16, color = BitoTema.colores.tintaSuave)
            }
            records.forEachIndexed { i, fila -> FilaRecord(fila, mejorDeSiempre = i == 0) }
        }
    }
}

/** La primera fila es la mejor racha de todas (la lista llega ordenada): lo dice, como la pantalla de récords de antes. */
@Composable
private fun FilaRecord(
    fila: RecordRow,
    mejorDeSiempre: Boolean,
) {
    val actual =
        if (fila.current == 0) {
            stringResource(R.string.records_no_current_streak)
        } else {
            stringResource(R.string.records_current_streak, fila.current, stringResource(unidadDe(fila.period)))
        }
    val conEstado = if (fila.archived) "$actual · ${stringResource(R.string.records_archived_label)}" else actual
    val subtitulo = if (mejorDeSiempre) "${stringResource(R.string.records_best_caption)} · $conEstado" else conEstado
    Fila(fila.name, Modifier.testTag("record-${fila.habitId}"), subtitulo = subtitulo) {
        Text(stringResource(R.string.record_chip, fila.best), style = BitoTema.tipos.t16.fuerte(), color = BitoTema.colores.tinta)
    }
}

/** Tus números: los contadores de toda la app, en tres grupos (Registro, Recompensas y Hábitos). */
@Composable
fun HojaNumeros(
    abierta: Boolean,
    totals: Totals,
    alCerrar: () -> Unit,
) {
    val formato = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val vacio = stringResource(R.string.no_data_placeholder)
    Hoja(abierta, alCerrar, titulo = stringResource(R.string.stats_teaser_numbers)) {
        Column(Modifier.testTag("hoja-numeros"), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            Grupo(
                R.string.numbers_section_log,
                listOf(
                    R.string.numbers_entries to formato.format(totals.entriesCount),
                    R.string.stats_perfect_days_label to formato.format(totals.perfectDays),
                    R.string.numbers_days_since_first to (totals.daysSinceFirstHabit?.let { formato.format(it) } ?: vacio),
                ),
            )
            Grupo(
                R.string.numbers_section_rewards,
                listOf(
                    R.string.numbers_points_earned to formato.format(totals.pointsEarned),
                    R.string.numbers_balance to formato.format(totals.balance),
                    R.string.numbers_freezers_used to formato.format(totals.freezersUsed),
                    R.string.numbers_freezers_owned to formato.format(totals.freezersOwned),
                ),
            )
            Grupo(
                R.string.numbers_section_habits,
                listOf(
                    R.string.numbers_active_habits to formato.format(totals.activeHabits),
                    R.string.numbers_paused_habits to formato.format(totals.pausedHabits),
                    R.string.numbers_archived_habits to formato.format(totals.archivedHabits),
                ),
            )
        }
    }
}

@Composable
private fun Grupo(
    titulo: Int,
    filas: List<Pair<Int, String>>,
) {
    Column {
        Text(
            stringResource(titulo),
            Modifier.padding(bottom = 4.dp),
            style = BitoTema.tipos.t14.fuerte(),
            color = BitoTema.colores.tintaSuave,
        )
        filas.forEach { (etiqueta, valor) ->
            Fila(stringResource(etiqueta)) {
                Text(valor, style = BitoTema.tipos.t19.cifra(), color = BitoTema.colores.tinta)
            }
        }
    }
}
