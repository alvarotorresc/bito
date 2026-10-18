package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.CalendarioElegir
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A rotation, a theme change or a language switch recreates the Activity mid-edit — this screen
 * has no ViewModel of its own to survive that (unlike [com.alvarotc.bito.ui.habitform.HabitFormScreen],
 * whose state lives in [com.alvarotc.bito.ui.habitform.HabitFormViewModel]), so `remember` alone
 * would lose whatever was typed. [TaskFormState] carries no Parcelable of its own and this screen
 * doesn't reach for kotlin-parcelize, so this flattens it to the five primitives a
 * [androidx.compose.runtime.saveable.rememberSaveable] Bundle already knows how to carry, [DueKind]
 * as its own [DueKind.name] rather than an ordinal (renumbering the enum would silently corrupt a
 * saved instance state otherwise).
 */
private val TaskFormStateSaver: Saver<TaskFormState, Any> =
    listSaver(
        save = { listOf(it.editingId, it.title, it.firstStep, it.dueKind.name, it.dueDay) },
        restore = {
            TaskFormState(
                editingId = it[0] as String?,
                title = it[1] as String,
                firstStep = it[2] as String,
                dueKind = DueKind.valueOf(it[3] as String),
                dueDay = it[4] as LogicalDay?,
            )
        },
    )

/**
 * Crea una tarea, o edita una cuando [initial] trae un [TaskFormState.editingId]. Pantalla completa
 * (la ruta baja un estrato, la transición es del `NavHost`): nombre, primer paso, plazo y Guardar.
 * [onDelete] solo llega en la ruta de edición: sin él no hay botón de borrar. El plazo «Fecha» abre
 * una [Hoja] con [CalendarioElegir]; la fecha elegida sustituye a «Fecha» en el [Segmentado].
 */
@Composable
fun TaskFormScreen(
    initial: TaskFormState,
    today: LogicalDay,
    onSave: (TaskFormState) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    var state by rememberSaveable(stateSaver = TaskFormStateSaver) { mutableStateOf(initial) }
    var calendarioAbierto by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf<LocalDate?>(null) }
    var borrando by remember { mutableStateOf(false) }
    // Guarda contra el doble toque en «Guardar»: la ruta saliente sigue compuesta (y pulsable)
    // mientras el NavHost la anima, y un segundo toque volvería a llamar a onSave.
    var guardada by remember { mutableStateOf(false) }
    // Cada toque en el Guardar apagado cuenta: el campo tiembla y dice qué falta.
    var intentos by remember { mutableIntStateOf(0) }

    fun abrirCalendario() {
        pendiente = (state.dueDay ?: today).aFecha()
        calendarioAbierto = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colores.tAlta)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Medidas.margen)
            .testTag("task-form-screen"),
        verticalArrangement = Arrangement.spacedBy(Medidas.e16),
    ) {
        CabeceraTareas(
            titulo = stringResource(if (state.isEditing) R.string.task_form_edit_title else R.string.task_form_title),
            onBack = onBack,
        )
        Campo(
            valor = state.title,
            alCambiar = { state = state.copy(title = it) },
            modifier = Modifier.fillMaxWidth().testTag("task-title-field"),
            pista = stringResource(R.string.task_form_name_hint),
            error = if (intentos > 0 && !state.canSave) stringResource(R.string.tasks2_falta_nombre) else null,
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            temblorClave = intentos,
        )
        Campo(
            valor = state.firstStep,
            alCambiar = { state = state.copy(firstStep = it) },
            modifier = Modifier.fillMaxWidth().testTag("task-first-step-field"),
            pista = stringResource(R.string.task_form_step_hint),
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        PlazoTarea(
            state = state,
            onSelect = { indice ->
                when (indice) {
                    0 -> state = state.copy(dueKind = DueKind.NONE, dueDay = null)
                    1 -> state = state.copy(dueKind = DueKind.WEEK, dueDay = null)
                    else -> abrirCalendario()
                }
            },
            onCambiarFecha = ::abrirCalendario,
        )
        Boton(
            texto = stringResource(R.string.sistema_guardar),
            alPulsar = {
                if (!guardada) {
                    guardada = true
                    gesto(Snd.BROTE, Haptica.CONFIRM)
                    onSave(state)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("task-form-save"),
            estilo = EstiloBoton.PRINCIPAL,
            apagado = !state.canSave,
            alPulsarApagado = {
                intentos++
                gesto(h = Haptica.REJECT)
            },
        )
        if (onDelete != null) {
            Boton(
                texto = stringResource(R.string.tasks2_borrar_boton),
                alPulsar = { borrando = true },
                modifier = Modifier.fillMaxWidth().testTag("task-delete"),
                estilo = EstiloBoton.BORRAR,
            )
        }
    }

    DialogoBorrarTarea(
        abierto = borrando,
        alConfirmar = {
            borrando = false
            gesto(Snd.HUNDIR)
            onDelete?.invoke()
        },
        alCancelar = { borrando = false },
    )

    Hoja(
        abierta = calendarioAbierto,
        alCerrar = { calendarioAbierto = false },
        titulo = stringResource(R.string.tasks2_fecha_titulo),
    ) {
        CalendarioElegir(
            hoy = today.aFecha(),
            elegido = pendiente,
            alElegir = { pendiente = it },
            modifier = Modifier.testTag("task-calendar"),
        )
        BotonesHoja(
            principal = stringResource(R.string.task_form_date_confirm),
            alPrincipal = {
                pendiente?.let { dia ->
                    gesto(Snd.HUNDIR, Haptica.CONFIRM)
                    state = state.copy(dueKind = DueKind.DATE, dueDay = dia.aDiaLogico())
                }
                cerrar()
            },
            modifier = Modifier.padding(top = Medidas.e12),
            suave = stringResource(R.string.sistema_cancelar),
            alSuave = { cerrar() },
            apagado = pendiente == null,
        )
    }
}

/** Los tres grados de plazo. La fecha elegida sustituye a «Fecha» («vie 16»). */
@Composable
private fun PlazoTarea(
    state: TaskFormState,
    onSelect: (Int) -> Unit,
    onCambiarFecha: () -> Unit,
) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val dia = state.dueDay
    val etiquetaFecha =
        if (state.dueKind == DueKind.DATE && dia != null) {
            DateTimeFormatter.ofPattern("EEE d", locale).format(dia.aFecha()).removeSuffix(".")
        } else {
            stringResource(R.string.task_form_due_date)
        }
    val elegida =
        when (state.dueKind) {
            DueKind.NONE -> 0
            DueKind.WEEK -> 1
            DueKind.DATE -> 2
        }
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        Segmentado(
            opciones = listOf(stringResource(R.string.task_form_due_none), stringResource(R.string.task_form_due_week), etiquetaFecha),
            elegida = elegida,
            alElegir = onSelect,
            modifier = Modifier.testTag("task-due"),
        )
        if (state.dueKind == DueKind.DATE) {
            BotonTexto(stringResource(R.string.tasks2_cambiar_fecha), onCambiarFecha, Modifier.testTag("task-change-date"))
        }
    }
}
