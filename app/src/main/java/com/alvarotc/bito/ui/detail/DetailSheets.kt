package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.components.formatDayWithPattern
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.CampoGrande
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.componentes.HojaScope
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind

/** El mayor total que acepta la hoja de un día: cinco cifras. */
private const val TOTAL_MAXIMO = 99_999

/** Las acciones de registro retroactivo que una [DaySheet] puede lanzar sobre un día. */
data class DaySheetActions(
    val onMarkDone: () -> Unit,
    val onMarkNotDone: () -> Unit,
    val onSetValue: (Int) -> Unit,
    val onRelapse: () -> Unit,
    val onStayedClean: () -> Unit,
    val onUseFreezer: () -> Unit,
)

/**
 * La hoja de un día pasado (G7), según [kind]: un check ofrece «Hecho» y «No lo hice»; un contador o una duración,
 * el total con [Stepper] y [CampoGrande] prellenado con lo registrado ([currentValue]); la abstinencia, «Estuve
 * limpio» y «Recaí ese día», con la nota de que sella el día. Si [freezerOffered], un congelador se usa solo a
 * petición y con un paso de confirmación (G8). Cada acción se aplica y la hoja se cierra con su salida.
 */
@Composable
fun DaySheet(
    day: LogicalDay,
    kind: CardKind,
    currentValue: Int,
    freezerOffered: Boolean,
    freezersOwned: Int,
    actions: DaySheetActions,
    onDismiss: () -> Unit,
    unit: String? = null,
) {
    val patron = stringResource(R.string.heatmap_day_pattern)
    val titulo = remember(day, patron) { formatDayWithPattern(day, patron) }
    var paso by remember { mutableIntStateOf(0) }
    Hoja(abierta = true, alCerrar = onDismiss, modifier = Modifier.testTag("day-sheet"), titulo = titulo) {
        HojaPasos(paso) { p ->
            if (p == 0) {
                PasoDia(kind, currentValue, unit, freezerOffered, freezersOwned, actions) { paso = 1 }
            } else {
                PasoCongelador(freezersOwned, actions.onUseFreezer, alVolver = { paso = 0 })
            }
        }
    }
}

@Composable
private fun HojaScope.PasoDia(
    kind: CardKind,
    currentValue: Int,
    unit: String?,
    freezerOffered: Boolean,
    freezersOwned: Int,
    actions: DaySheetActions,
    alCongelador: () -> Unit,
) {
    val gesto = rememberGesto()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
        when (kind) {
            CardKind.CHECK -> {
                Boton(
                    stringResource(R.string.day_done),
                    {
                        gesto(Snd.LLEGADA, Haptica.CLICK)
                        actions.onMarkDone()
                        cerrar()
                    },
                    Modifier.fillMaxWidth(),
                    estilo = EstiloBoton.PRINCIPAL,
                    alto = Medidas.botonHoja,
                )
                Boton(
                    stringResource(R.string.day_not_done),
                    {
                        gesto(Snd.VACIAR, Haptica.LOW)
                        actions.onMarkNotDone()
                        cerrar()
                    },
                    Modifier.fillMaxWidth(),
                    alto = Medidas.botonHoja,
                )
            }
            CardKind.COUNTER, CardKind.DURATION -> {
                var texto by remember { mutableStateOf(currentValue.toString()) }
                val valor = (texto.toIntOrNull() ?: 0).coerceIn(0, TOTAL_MAXIMO)
                Stepper(
                    valor = valor,
                    alCambiar = { texto = it.toString() },
                    min = 0,
                    max = TOTAL_MAXIMO,
                    descripcion = stringResource(R.string.ficha_total_cd),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                CampoGrande(texto, { texto = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), unidad = unit)
                BotonesHoja(
                    principal = stringResource(R.string.sistema_guardar),
                    alPrincipal = {
                        if (valor > currentValue) gesto(Snd.LLEGADA, Haptica.CLICK) else gesto(Snd.VACIAR, Haptica.LOW)
                        actions.onSetValue(valor)
                        cerrar()
                    },
                )
            }
            CardKind.ABSTINENCE -> {
                Text(
                    stringResource(R.string.ficha_sella_dia),
                    style = BitoTema.tipos.t14,
                    color = BitoTema.colores.tintaSuave,
                )
                Boton(
                    stringResource(R.string.stayed_clean),
                    {
                        gesto(Snd.LLEGADA, Haptica.CLICK)
                        actions.onStayedClean()
                        cerrar()
                    },
                    Modifier.fillMaxWidth(),
                    estilo = EstiloBoton.PRINCIPAL,
                    alto = Medidas.botonHoja,
                )
                Boton(
                    stringResource(R.string.relapsed_that_day),
                    {
                        gesto(Snd.VACIAR, Haptica.LOW)
                        actions.onRelapse()
                        cerrar()
                    },
                    Modifier.fillMaxWidth(),
                    alto = Medidas.botonHoja,
                )
            }
        }
        if (freezerOffered) {
            BotonTexto(stringResource(R.string.ficha_usar_congelador, freezersOwned), alCongelador, Modifier.fillMaxWidth())
        }
    }
}

/** G8: el paso de confirmación de un congelador, con los copitos y lo que queda. */
@Composable
private fun HojaScope.PasoCongelador(
    owned: Int,
    alUsar: () -> Unit,
    alVolver: () -> Unit,
) {
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    Column(
        Modifier.fillMaxWidth().testTag("congelador-confirmar"),
        verticalArrangement = Arrangement.spacedBy(Medidas.e12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.ficha_congelador_pregunta), style = BitoTema.tipos.t19.fuerte(), color = colores.tinta)
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            repeat(owned.coerceAtMost(MAX_COPITOS)) { i ->
                Icon(
                    IconosSistema.Snowflake,
                    contentDescription = null,
                    tint = colores.copo,
                    // El último es el que se gasta.
                    modifier = Modifier.size(28.dp).alpha(if (i == owned.coerceAtMost(MAX_COPITOS) - 1) GASTADO else 1f),
                )
            }
        }
        Text(
            stringResource(R.string.ficha_te_quedara, (owned - 1).coerceAtLeast(0)),
            style = BitoTema.tipos.t16,
            color = colores.tintaSuave,
        )
        BotonesHoja(
            principal = stringResource(R.string.ficha_congelador_verbo),
            alPrincipal = {
                gesto(Snd.CAMPANILLA, Haptica.CONFIRM)
                alUsar()
                cerrar()
            },
            suave = stringResource(R.string.cancel),
            alSuave = alVolver,
        )
    }
}

private const val MAX_COPITOS = 6
private const val GASTADO = 0.35f

/**
 * Qué son los congeladores y cómo se gastan: información pura, sin escribir nada. Una hoja con Habi (la del usuario,
 * con lo que lleva puesto) que habla en su personalidad ([HabiVoice.freezerInfoRes]). La abre la ficha y también la
 * tienda de Habi, por eso su firma no cambia.
 */
@Composable
fun FreezerInfoSheet(
    personality: Personality,
    userName: String,
    equipped: EquippedSet,
    onDismiss: () -> Unit,
) {
    Hoja(
        abierta = true,
        alCerrar = onDismiss,
        modifier = Modifier.testTag("freezer-info-sheet"),
        titulo = stringResource(R.string.freezer_info_sheet_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            SpeechBubble(
                speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(personality))),
                text =
                    stringResource(
                        HabiVoice.freezerInfoRes(personality),
                        userName.ifBlank { stringResource(R.string.habi_name_fallback) },
                    ),
                modifier = Modifier.fillMaxWidth(),
                avatar = {
                    HabiAvatar(
                        HabiSpec(Mood.NORMAL, personality, equipped),
                        Modifier.size(40.dp),
                        animated = false,
                    )
                },
            )
            BotonesHoja(principal = stringResource(R.string.got_it), alPrincipal = { cerrar() })
        }
    }
}

/** Pausar suspende el registro y el juicio de la racha hasta reanudar; la nota es contexto opcional. */
@Composable
fun PauseSheet(
    onPause: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var nota by remember { mutableStateOf("") }
    Hoja(
        abierta = true,
        alCerrar = onDismiss,
        modifier = Modifier.testTag("pause-sheet"),
        titulo = stringResource(R.string.pause_sheet_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            Campo(nota, { nota = it }, Modifier.fillMaxWidth(), pista = stringResource(R.string.pause_note_hint))
            BotonesHoja(
                principal = stringResource(R.string.pause_habit),
                alPrincipal = {
                    onPause(nota.trim().ifBlank { null })
                    cerrar()
                },
            )
        }
    }
}

/** Archivar lo conserva todo: no es destructivo, así que confirma sin rojo. */
@Composable
fun ArchiveSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Hoja(
        abierta = true,
        alCerrar = onDismiss,
        modifier = Modifier.testTag("archive-sheet"),
        titulo = stringResource(R.string.archive_sheet_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            Text(stringResource(R.string.ficha_archivar_cuerpo), style = BitoTema.tipos.t16, color = BitoTema.colores.tintaSuave)
            BotonesHoja(
                principal = stringResource(R.string.archive_habit),
                alPrincipal = {
                    onConfirm()
                    cerrar()
                },
                suave = stringResource(R.string.cancel),
                alSuave = { cerrar() },
            )
        }
    }
}
