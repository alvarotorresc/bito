package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.formatDayMedium
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.today.CardKind

/** Las acciones del pie de la ficha; las hojas y el diálogo de confirmación los abre el propio pie. */
data class AccionesPie(
    val pausar: (nota: String?) -> Unit,
    val reanudar: () -> Unit,
    val archivar: () -> Unit,
    val reactivar: () -> Unit,
    val recaer: () -> Unit,
    val irTienda: () -> Unit,
)

private enum class HojaPie { PAUSAR, ARCHIVAR, CONGELADORES }

private const val MAX_COPITOS = 5

/**
 * Lo que la ficha aprobada no dibuja (decisión 7), bajo la nota de Habi: el cumplimiento de 7 y 365 días, los
 * congeladores (solo en hábitos diarios sin archivar), «He recaído» (solo abstinencia), y pausar, archivar,
 * reanudar o reactivar. En modo lectura, o con el hábito archivado, solo queda «Reactivar».
 */
@Composable
fun PieFicha(
    estado: DetailUiState,
    acciones: AccionesPie,
    modifier: Modifier = Modifier,
) {
    var hoja by remember { mutableStateOf<HojaPie?>(null) }
    var recaida by remember { mutableStateOf(false) }
    val archivado = estado.status == HabitStatus.ARCHIVED
    val editable = !estado.lectura && !archivado

    Column(modifier.fillMaxWidth().testTag("pie-ficha"), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
        if (!estado.lectura) Cumplimiento(estado.windows)
        if (editable && estado.period == Period.DAY) {
            Congeladores(
                tiene = estado.freezersOwned,
                alInfo = { hoja = HojaPie.CONGELADORES },
                alTienda = acciones.irTienda,
            )
        }
        if (editable && estado.kind == CardKind.ABSTINENCE) {
            Boton(
                stringResource(R.string.relapse_action),
                { recaida = true },
                Modifier.fillMaxWidth().testTag("relapse-pill"),
                alto = Medidas.botonHoja,
            )
        }
        when {
            archivado ->
                Boton(
                    stringResource(R.string.unarchive_habit),
                    acciones.reactivar,
                    Modifier.fillMaxWidth().testTag("reactivate"),
                    estilo = EstiloBoton.PRINCIPAL,
                    alto = Medidas.botonHoja,
                )
            estado.lectura -> Unit
            else -> CicloDeVida(estado, acciones, alPausar = { hoja = HojaPie.PAUSAR }, alArchivar = { hoja = HojaPie.ARCHIVAR })
        }
    }

    when (hoja) {
        HojaPie.PAUSAR -> PauseSheet(onPause = acciones.pausar, onDismiss = { hoja = null })
        HojaPie.ARCHIVAR -> ArchiveSheet(onConfirm = acciones.archivar, onDismiss = { hoja = null })
        HojaPie.CONGELADORES ->
            FreezerInfoSheet(estado.personality, estado.userName, estado.equipped, onDismiss = { hoja = null })
        null -> Unit
    }
    Dialogo(
        abierto = recaida,
        titulo = stringResource(R.string.relapse_confirm_title),
        verbo = stringResource(R.string.ficha_recaida_verbo),
        alConfirmar = {
            recaida = false
            acciones.recaer()
        },
        alCancelar = { recaida = false },
        cuerpo = stringResource(R.string.relapse_confirm_body),
    )
}

/** «7 días · 86 %» y «365 días · 71 %», con lo juzgado en la descripción de accesibilidad. */
@Composable
private fun Cumplimiento(windows: List<WindowStats>) {
    val colores = BitoTema.colores
    val filas =
        listOf(
            stringResource(R.string.window_7) to windows.getOrNull(0),
            stringResource(R.string.ficha_dias_365) to windows.getOrNull(2),
        )
    Column(
        Modifier
            .fillMaxWidth()
            .background(colores.canto, BitoFormas.tarjeta)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(horizontal = Medidas.e16, vertical = Medidas.e12),
        verticalArrangement = Arrangement.spacedBy(Medidas.e4),
    ) {
        filas.forEach { (etiqueta, ventana) ->
            val porcentaje = ventana?.percent?.let { "$it %" } ?: "—"
            val descripcion =
                if (ventana == null) {
                    etiqueta
                } else {
                    stringResource(R.string.ficha_cumple_cd, etiqueta, ventana.fulfilled, ventana.judged)
                }
            Text(
                stringResource(R.string.ficha_cumple_linea, etiqueta, porcentaje),
                Modifier.semantics { contentDescription = descripcion },
                style = BitoTema.tipos.t16.fuerte(),
                color = colores.tinta,
            )
        }
    }
}

/**
 * «Tienes N» con sus copitos y la ⓘ. Sin congeladores, el chip lleva a la tienda; con ellos solo informa:
 * se usan desde el día fallado del calendario, a petición.
 */
@Composable
private fun Congeladores(
    tiene: Int,
    alInfo: () -> Unit,
    alTienda: () -> Unit,
) {
    val colores = BitoTema.colores
    val fuente = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val chip =
            Modifier
                .weight(1f)
                .heightIn(min = Medidas.toqueMinimo)
                .pulsar(fuente)
                .background(colores.hielo, BitoFormas.canto)
                .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                .let {
                    if (tiene == 0) {
                        it.clickable(
                            interactionSource = fuente,
                            indication = null,
                            role = Role.Button,
                            onClick = alTienda,
                        )
                    } else {
                        it
                    }
                }.padding(horizontal = Medidas.e16)
                .testTag("freezer-chip")
        Row(chip, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            if (tiene == 0) {
                Icon(IconosSistema.Snowflake, null, Modifier.size(20.dp), tint = colores.copo)
                Text(stringResource(R.string.ficha_sin_congeladores), style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
            } else {
                Text(stringResource(R.string.freezers_owned_label, tiene), style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
                repeat(tiene.coerceAtMost(MAX_COPITOS)) { Icon(IconosSistema.Snowflake, null, Modifier.size(20.dp), tint = colores.copo) }
            }
        }
        val descripcion = stringResource(R.string.freezer_info_hint)
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .clickable(role = Role.Button, onClick = alInfo)
                .testTag("freezer-info")
                .semantics { contentDescription = descripcion },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.Info, null, Modifier.size(24.dp), tint = colores.tintaSobreTAlta2)
        }
    }
}

@Composable
private fun CicloDeVida(
    estado: DetailUiState,
    acciones: AccionesPie,
    alPausar: () -> Unit,
    alArchivar: () -> Unit,
) {
    val pausado = estado.status == HabitStatus.PAUSED
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        if (pausado && estado.pausedSinceDay != null) {
            val desde = remember(estado.pausedSinceDay) { formatDayMedium(estado.pausedSinceDay) }
            Text(stringResource(R.string.paused_since, desde), style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSobreTAlta2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            if (pausado) {
                Boton(
                    stringResource(R.string.resume_habit),
                    acciones.reanudar,
                    Modifier.weight(1f).testTag("resume"),
                    icono = IconosSistema.Play,
                )
            } else {
                Boton(
                    stringResource(R.string.pause_habit),
                    alPausar,
                    Modifier.weight(1f).testTag("pause"),
                    icono = IconosSistema.Pause,
                )
            }
            Boton(stringResource(R.string.archive_habit), alArchivar, Modifier.weight(1f).testTag("archive"))
        }
    }
}
