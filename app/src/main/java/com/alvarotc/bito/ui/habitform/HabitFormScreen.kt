@file:OptIn(ExperimentalFoundationApi::class)

package com.alvarotc.bito.ui.habitform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.NumberInputSheet
import com.alvarotc.bito.ui.components.TimePickerSheet
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.ElegirConAgua
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.FormaAgua
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.sistema.componentes.Plegable
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.componentes.temblor
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.launch

/** Minutes-of-day a fresh reminder opens on when none is set yet: 08:00. */
private const val DEFAULT_REMINDER_MINUTES = 8 * 60

/** Hueco sobre Habi para el globito, que se abre hacia arriba desde su ancla. */
private val HUECO_GLOBITO = 96.dp

private val PERIOD_OPTIONS = listOf(Period.DAY, Period.WEEK, Period.MONTH)

/** One screen, no wizard: create and edit share every field, the preset just gates what's shown. */
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val personality by viewModel.personality.collectAsStateWithLifecycle()
    val equipped by viewModel.equipped.collectAsStateWithLifecycle()
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    var borrando by remember { mutableStateOf(false) }
    var intentoSinNombre by remember { mutableStateOf(false) }
    var temblorNombre by remember { mutableIntStateOf(0) }
    val nombreEnVista = remember { BringIntoViewRequester() }

    // Same launcher SettingsScreen.kt uses for the first GLOBAL reminder: fire-and-forget, the
    // save below never waits on nor blocks on the result.
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun guardar() {
        // QUIT's own reminder can never fire (ReminderUseCase.kt HABIT branch: a QUIT card is
        // always doneToday or failed) — asking for the permission here would be fishing for
        // access with nothing to justify it (same rule SettingsScreen.kt documents for its own
        // first-reminder prompt).
        if (state.reminderMinutes != null &&
            state.preset != HabitPreset.QUIT &&
            Build.VERSION.SDK_INT >= 33 &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        gesto(h = Haptica.CONFIRM)
        viewModel.save(onBack)
    }

    // El botón apagado se puede pulsar: dice qué falta y dónde.
    fun explicarQueFalta() {
        intentoSinNombre = true
        temblorNombre++
        gesto(h = Haptica.REJECT)
        scope.launch { nombreEnVista.bringIntoView() }
    }

    Scaffold(containerColor = colores.tAlta) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Medidas.margen),
            verticalArrangement = Arrangement.spacedBy(Medidas.e12),
        ) {
            Cabecera(state.isEditing, onBack)
            HabiDelFormulario(
                texto =
                    stringResource(
                        if (state.isEditing) R.string.habitform_prompt_editar else HabiVoice.formPromptRes(personality),
                    ),
                avatar = {
                    HabiAvatar(HabiSpec(Mood.NORMAL, personality, equipped), Modifier.size(64.dp), animated = false)
                },
            )
            Campo(
                valor = state.name,
                alCambiar = viewModel::setName,
                modifier = Modifier.bringIntoViewRequester(nombreEnVista).testTag("name-field"),
                etiqueta = stringResource(R.string.habitform_nombre_etiqueta),
                pista = stringResource(R.string.habitform_nombre_pista),
                error = if (intentoSinNombre && state.name.isBlank()) stringResource(R.string.habitform_nombre_ayuda) else null,
                teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                temblorClave = temblorNombre,
            )
            TituloSeccion(stringResource(R.string.habitform_tipo))
            Tipos(state, viewModel::selectPreset)
            Objetivo(state, viewModel)
            MasOpciones(state, viewModel)
            Column(Modifier.padding(top = Medidas.e12), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
                Boton(
                    texto = stringResource(if (state.isEditing) R.string.save_habit else R.string.create_habit),
                    alPulsar = ::guardar,
                    modifier = Modifier.fillMaxWidth().testTag("save"),
                    estilo = EstiloBoton.PRINCIPAL,
                    apagado = !state.canSave,
                    alPulsarApagado = ::explicarQueFalta,
                    alto = Medidas.botonHoja,
                )
                if (state.isEditing) {
                    Boton(
                        texto = stringResource(R.string.habitform_borrar),
                        alPulsar = { borrando = true },
                        modifier = Modifier.fillMaxWidth().testTag("delete"),
                        estilo = EstiloBoton.BORRAR,
                        alto = Medidas.botonHoja,
                    )
                }
            }
        }
    }

    Dialogo(
        abierto = borrando,
        titulo = stringResource(R.string.habitform_borrar_titulo),
        verbo = stringResource(R.string.habitform_borrar_verbo),
        alConfirmar = {
            borrando = false
            viewModel.delete(onBack)
        },
        alCancelar = { borrando = false },
        cuerpo = stringResource(R.string.habitform_borrar_cuerpo),
        peligro = true,
    )
}

@Composable
private fun Cabecera(
    editando: Boolean,
    alVolver: () -> Unit,
) {
    val colores = BitoTema.colores
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = alVolver),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.ArrowLeft, contentDescription = stringResource(R.string.back), tint = colores.tinta)
        }
        Text(
            stringResource(if (editando) R.string.form_title_edit else R.string.form_title_new),
            style = BitoTema.tipos.t24.fuerte(),
            color = colores.tinta,
        )
    }
}

/** Habi con su frase en un globito que no se va solo: el mismo Habi que el jardín, con su ropa. */
@Composable
private fun HabiDelFormulario(
    texto: String,
    avatar: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = HUECO_GLOBITO)) {
        Box(Modifier.size(64.dp)) {
            avatar()
            Globito(
                visible = true,
                texto = texto,
                modifier = Modifier.widthIn(max = 280.dp),
                autoOcultar = false,
                cola = 0.15f,
            )
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        style = BitoTema.tipos.t19.fuerte(),
        color = BitoTema.colores.tinta,
        modifier = Modifier.padding(top = Medidas.e12, start = Medidas.e4),
    )
}

/** Superficie de canto con contorno, la de agrupar mandos. */
@Composable
private fun Bloque(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val colores = BitoTema.colores
    Column(
        modifier
            .fillMaxWidth()
            .background(colores.canto, BitoFormas.tarjeta)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(Medidas.e16),
    ) { contenido() }
}

/** Not private: [com.alvarotc.bito.ui.onboarding.OnboardingScreen]'s 7g preset pills reuse these
 * exact labels rather than duplicating the mapping — same strings, same five presets. */
internal fun HabitPreset.labelRes(): Int =
    when (this) {
        HabitPreset.DAILY_CHECK -> R.string.preset_daily
        HabitPreset.QUANTITY -> R.string.preset_quantity
        HabitPreset.DURATION -> R.string.preset_duration
        HabitPreset.WEEKLY_TIMES -> R.string.preset_weekly
        HabitPreset.QUIT -> R.string.preset_quit
    }

/** The line glyph each type leads with — canon pills.html + mockup 7g (check, dots, clock,
 * calendar, ban). Internal next to [labelRes] for the same reason: one preset→glyph mapping,
 * ready for onboarding's mirrored pills to reuse. */
internal fun HabitPreset.iconVector(): ImageVector =
    when (this) {
        HabitPreset.DAILY_CHECK -> BitoIcons.Check
        HabitPreset.QUANTITY -> BitoIcons.Ellipsis
        HabitPreset.DURATION -> BitoIcons.Clock
        HabitPreset.WEEKLY_TIMES -> BitoIcons.Calendar
        HabitPreset.QUIT -> BitoIcons.Ban
    }

private fun HabitPreset.descripcionRes(): Int =
    when (this) {
        HabitPreset.DAILY_CHECK -> R.string.habitform_tipo_diario
        HabitPreset.QUANTITY -> R.string.habitform_tipo_cantidad
        HabitPreset.DURATION -> R.string.habitform_tipo_duracion
        HabitPreset.WEEKLY_TIMES -> R.string.habitform_tipo_semana
        HabitPreset.QUIT -> R.string.habitform_tipo_dejar
    }

private fun HabitPreset.icono(): ImageVector =
    when (this) {
        HabitPreset.DAILY_CHECK -> IconosSistema.Check
        HabitPreset.QUANTITY -> IconosSistema.Hash
        HabitPreset.DURATION -> IconosSistema.Clock
        HabitPreset.WEEKLY_TIMES -> IconosSistema.CalendarBlank
        HabitPreset.QUIT -> IconosSistema.Prohibit
    }

/**
 * El tipo como cantos con aro de agua, nunca píldoras. Rule E2: la forma de un hábito no cambia una
 * vez creado, así que al editar solo se ve el tipo actual, con su aviso.
 */
@Composable
private fun Tipos(
    state: HabitFormState,
    alElegir: (HabitPreset) -> Unit,
) {
    val visibles = if (state.isEditing) listOf(state.preset) else HabitPreset.entries
    val opciones =
        visibles.map {
            OpcionAgua(it.name, stringResource(it.labelRes()), stringResource(it.descripcionRes()), it.icono())
        }
    ElegirConAgua(
        opciones = opciones,
        elegida = state.preset.name,
        alElegir = { alElegir(HabitPreset.valueOf(it)) },
        modifier = Modifier.fillMaxWidth().testTag("tipos"),
        forma = FormaAgua.CANTO,
        llena = true,
    )
    if (state.isEditing) {
        Row(
            Modifier.padding(horizontal = Medidas.e4),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                IconosSistema.Prohibit,
                contentDescription = null,
                tint = BitoTema.colores.tintaSuave,
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Text(
                stringResource(R.string.habitform_tipo_fijo),
                style = BitoTema.tipos.t14,
                color = BitoTema.colores.tintaSuave,
            )
        }
    }
}

/** Lo que va quieto al editar (la forma): al 50 % y sin responder al toque, pero sin cortar el scroll. */
@Composable
private fun Bloqueable(
    bloqueado: Boolean,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    Box(modifier.alpha(if (bloqueado) 0.5f else 1f)) {
        contenido()
        if (bloqueado) {
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        }
    }
}

private fun HabitFormState.esLimite() = preset == HabitPreset.QUIT && quitMode == QuitMode.LIMIT

private fun HabitFormState.esMinutos() = preset == HabitPreset.DURATION || (esLimite() && limitMetric == Metric.DURATION)

/** La forma del bloque de objetivo: cuando cambia, el contenido se funde en su sitio. */
private fun HabitFormState.claveObjetivo() = Triple(preset, quitMode, limitMetric)

/**
 * Oculto del todo en Diario. Rule E2 va más allá del tipo: [HabitFormViewModel.save] al editar solo
 * guarda nombre, objetivo, unidad, paso y recordatorio, así que el periodo, el modo de Dejar y su
 * métrica son forma y se bloquean junto con el tipo.
 */
@Composable
private fun Objetivo(
    state: HabitFormState,
    viewModel: HabitFormViewModel,
) {
    val reducido = BitoTema.reducido
    AnimatedVisibility(
        visible = state.preset != HabitPreset.DAILY_CHECK,
        enter =
            if (reducido) {
                fadeIn(tween(Duraciones.REDUCIDO))
            } else {
                expandVertically(tween(Duraciones.PLEGAR_ABRE, easing = Curvas.drawer)) + fadeIn(tween(Duraciones.PLEGAR_ABRE))
            },
        exit =
            if (reducido) {
                fadeOut(tween(Duraciones.REDUCIDO))
            } else {
                shrinkVertically(tween(Duraciones.PLEGAR_CIERRA, easing = Curvas.out)) + fadeOut(tween(Duraciones.PLEGAR_CIERRA))
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            TituloSeccion(
                stringResource(if (state.preset == HabitPreset.QUIT) R.string.habitform_como_dejarlo else R.string.habitform_objetivo),
            )
            Bloque { CuerpoObjetivo(state, viewModel) }
        }
    }
}

@Composable
private fun CuerpoObjetivo(
    state: HabitFormState,
    viewModel: HabitFormViewModel,
) {
    val reducido = BitoTema.reducido
    val bloqueado = state.isEditing
    Column(
        Modifier.animateContentSize(if (reducido) tween(Duraciones.REDUCIDO) else tween(240, easing = Curvas.drawer)),
        verticalArrangement = Arrangement.spacedBy(Medidas.e12),
    ) {
        if (state.preset == HabitPreset.QUIT) {
            Bloqueable(bloqueado) {
                Segmentado(
                    opciones = listOf(stringResource(R.string.quit_total), stringResource(R.string.quit_limit)),
                    elegida = if (state.quitMode == QuitMode.TOTAL) 0 else 1,
                    alElegir = { viewModel.selectQuitMode(if (it == 0) QuitMode.TOTAL else QuitMode.LIMIT) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.quitMode == QuitMode.LIMIT) {
                Bloqueable(bloqueado) {
                    Segmentado(
                        opciones = listOf(stringResource(R.string.limit_amount), stringResource(R.string.limit_time)),
                        elegida = if (state.limitMetric == Metric.COUNT) 0 else 1,
                        alElegir = { viewModel.selectLimitMetric(if (it == 0) Metric.COUNT else Metric.DURATION) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = state.claveObjetivo(),
            transitionSpec = {
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    (fadeIn(tween(Duraciones.COLOR)) + slideInVertically(tween(Duraciones.COLOR)) { 6 }) togetherWith
                        fadeOut(tween(Duraciones.COLOR / 2)) using SizeTransform(clip = false)
                }
            },
            contentAlignment = Alignment.TopStart,
            label = "objetivo",
        ) { _ ->
            if (state.preset == HabitPreset.QUIT && state.quitMode == QuitMode.TOTAL) {
                Text(
                    stringResource(R.string.habitform_dejar_del_todo),
                    style = BitoTema.tipos.t14,
                    color = BitoTema.colores.tintaSuave,
                    modifier = Modifier.testTag("dejar-del-todo"),
                )
            } else {
                ControlesObjetivo(state, viewModel, bloqueado)
            }
        }
    }
}

@Composable
private fun ControlesObjetivo(
    state: HabitFormState,
    viewModel: HabitFormViewModel,
    bloqueado: Boolean,
) {
    val colores = BitoTema.colores
    val limite = state.esLimite()
    var abrirNumero by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
        if (limite) {
            Text(stringResource(R.string.habitform_como_mucho), style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
        }
        ObjetivoConPasos(state, viewModel::adjustTarget) { abrirNumero = true }
        val conPeriodo = state.preset == HabitPreset.QUANTITY || state.preset == HabitPreset.DURATION || limite
        if (conPeriodo) {
            Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e12), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.habitform_cada), style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
                Bloqueable(bloqueado, Modifier.weight(1f)) {
                    Segmentado(
                        opciones =
                            listOf(
                                stringResource(R.string.period_day),
                                stringResource(R.string.period_week),
                                stringResource(R.string.period_month),
                            ),
                        elegida = PERIOD_OPTIONS.indexOf(state.period),
                        alElegir = { viewModel.selectPeriod(PERIOD_OPTIONS[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.preset == HabitPreset.QUANTITY) {
            // QA3 deliberately stops at the name field: units are written lowercase by convention
            // ("vasos", "min"), so no KeyboardCapitalization here.
            Campo(
                valor = state.unit,
                alCambiar = viewModel::setUnit,
                modifier = Modifier.testTag("unit-field"),
                etiqueta = stringResource(R.string.habitform_unidad),
                pista = stringResource(R.string.habitform_unidad_pista),
            )
        }
        Resumen(state)
    }

    if (abrirNumero) {
        val avisoMinimo = stringResource(R.string.habitform_aviso_minimo)
        val avisoSemana = stringResource(R.string.habitform_aviso_semana)
        NumberInputSheet(
            title = stringResource(R.string.target_input_title),
            initial = state.target,
            onConfirm = {
                viewModel.setTarget(it)
                abrirNumero = false
            },
            onDismiss = { abrirNumero = false },
            validar = { n ->
                when (state.avisoObjetivo(n)) {
                    AvisoObjetivo.AL_MENOS_UNO -> avisoMinimo
                    AvisoObjetivo.COMO_MUCHO_SEMANA -> avisoSemana
                    null -> null
                }
            },
        )
    }
}

@Composable
private fun unidadDe(
    state: HabitFormState,
    n: Int,
): String =
    when {
        state.esMinutos() -> stringResource(R.string.unit_min)
        state.preset == HabitPreset.QUANTITY -> state.unit.trim()
        state.preset == HabitPreset.WEEKLY_TIMES || state.esLimite() ->
            stringResource(if (n == 1) R.string.habitform_vez else R.string.habitform_veces)
        else -> ""
    }

@Composable
private fun Resumen(state: HabitFormState) {
    val n = state.target
    val cantidad = "$n ${unidadDe(state, n)}".trim()
    val periodo =
        stringResource(
            when (state.period) {
                Period.DAY -> R.string.habitform_en_dia
                Period.WEEK -> R.string.habitform_en_semana
                Period.MONTH -> R.string.habitform_en_mes
            },
        )
    val texto =
        when {
            state.preset == HabitPreset.WEEKLY_TIMES -> stringResource(R.string.habitform_resumen_semana, cantidad)
            state.esLimite() -> stringResource(R.string.habitform_resumen_limite, cantidad, periodo)
            else -> stringResource(R.string.habitform_resumen_hecho, cantidad, periodo)
        }
    Text(texto, style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSuave, modifier = Modifier.testTag("resumen"))
}

/**
 * QA4/QA5: los objetivos en minutos son penosos de alcanzar de uno en uno, así que llevan además ±10.
 * La cifra (t48, la protagonista) siempre se puede tocar y abre la hoja de número. En el tope la cifra
 * tiembla y solo vibra flojo, y el botón queda al 35 %.
 */
@Composable
private fun ObjetivoConPasos(
    state: HabitFormState,
    ajustar: (Int) -> Unit,
    abrirNumero: () -> Unit,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val rango = state.rangoObjetivo()
    val minutos = state.esMinutos()
    var empujones by remember { mutableIntStateOf(0) }
    val unidad = unidadDe(state, state.target)
    val escribir = stringResource(R.string.habitform_escribir_objetivo, state.target)

    fun mover(delta: Int) {
        if (state.target + delta in rango) {
            gesto(Snd.TIC, Haptica.SEG)
            ajustar(delta)
        } else {
            gesto(h = Haptica.LOW)
            empujones++
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        if (minutos) {
            PasoObjetivo(stringResource(R.string.target_minus10_cd), state.target - 10 < rango.first, "target-minus10", { mover(-10) }) {
                Text("−10", style = BitoTema.tipos.t14.fuerte(), color = colores.tinta)
            }
        }
        PasoObjetivo(stringResource(R.string.target_minus1_cd), state.target - 1 < rango.first, "target-minus", { mover(-1) }) {
            Icon(IconosSistema.Minus, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .temblor(empujones)
                .clickable(role = Role.Button, onClick = abrirNumero)
                .semantics { contentDescription = escribir }
                .testTag("objetivo"),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e4, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            CifraRodillo(state.target, estilo = BitoTema.tipos.t48.cifra(), color = colores.tinta)
            if (unidad.isNotEmpty()) {
                Text(unidad, style = BitoTema.tipos.t14, color = colores.tintaSuave, modifier = Modifier.padding(bottom = Medidas.e8))
            }
        }
        PasoObjetivo(stringResource(R.string.target_plus1_cd), state.target + 1 > rango.last, "target-plus", { mover(1) }) {
            Icon(IconosSistema.Plus, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
        }
        if (minutos) {
            PasoObjetivo(stringResource(R.string.target_plus10_cd), state.target + 10 > rango.last, "target-plus10", { mover(10) }) {
                Text("+10", style = BitoTema.tipos.t14.fuerte(), color = colores.tinta)
            }
        }
    }
}

/** Un paso del objetivo: canto redondo de 44 dp. En el tope queda al 35 % pero se puede pulsar (tiembla). */
@Composable
private fun PasoObjetivo(
    descripcion: String,
    tope: Boolean,
    etiqueta: String,
    alPulsar: () -> Unit,
    contenido: @Composable () -> Unit,
) {
    val colores = BitoTema.colores
    val fuente = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(Medidas.toqueMinimo)
            .pulsar(fuente, redondo = true)
            .alpha(if (tope) 0.35f else 1f)
            .clip(CircleShape)
            .background(colores.canto)
            .border(Medidas.linea, colores.contorno, CircleShape)
            .clickable(interactionSource = fuente, indication = null, role = Role.Button, onClick = alPulsar)
            .semantics { contentDescription = descripcion }
            .testTag(etiqueta),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

@Composable
private fun MasOpciones(
    state: HabitFormState,
    viewModel: HabitFormViewModel,
) {
    var abierto by remember { mutableStateOf(false) }
    var hora by remember { mutableStateOf(false) }
    Bloque {
        Plegable(stringResource(R.string.more_options), abierto, { abierto = it }) {
            if (state.preset == HabitPreset.QUANTITY || state.preset == HabitPreset.DURATION) {
                // logMode isn't persisted on edit (save() carries name/target/unit/step/reminderMinutes
                // only) — it's shape, like the preset, so it locks the same way.
                FilaInterruptor(
                    etiqueta = stringResource(R.string.binary_mode),
                    activo = state.binaryMode,
                    alCambiar = { viewModel.toggleBinary() },
                    modifier = Modifier.testTag("binary-mode-row"),
                    subtitulo =
                        stringResource(if (state.isEditing) R.string.habitform_binario_fijo else R.string.habitform_binario_ayuda),
                    habilitada = !state.isEditing,
                )
            }
            if (state.preset == HabitPreset.QUANTITY && !state.binaryMode) {
                Fila(
                    etiqueta = stringResource(R.string.habitform_paso),
                    subtitulo = stringResource(R.string.habitform_paso_ayuda),
                    modifier = Modifier.testTag("step-row"),
                    derecha = {
                        Stepper(
                            valor = state.step,
                            alCambiar = { viewModel.adjustStep(it - state.step) },
                            min = 1,
                            max = 99,
                            descripcion = stringResource(R.string.habitform_paso),
                        )
                    },
                )
            }
            // QA1: every preset lands here — Daily/Weekly/Quit had nothing of their own before, so
            // the panel used to open empty for three of the five presets. Reminder stays editable in
            // edit mode: it's a schedule detail, not part of the locked shape. QUIT is the one
            // exception: its own reminder can never fire, so the row says so instead of offering a
            // dead control.
            Recordatorio(state.reminderMinutes, inerte = state.preset == HabitPreset.QUIT, alAbrir = { hora = true }) {
                viewModel.setReminder(null)
            }
        }
    }
    if (hora) {
        TimePickerSheet(
            title = stringResource(R.string.form_reminder_label),
            initialMinutes = state.reminderMinutes ?: DEFAULT_REMINDER_MINUTES,
            onConfirm = {
                viewModel.setReminder(it)
                hora = false
            },
            onDismiss = { hora = false },
        )
    }
}

/** "HH:MM", zero-padded, 24h — the reminder's own clock face. */
private fun formatReminderTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * La fila que abre el reloj y la X que quita la hora son hermanas, no anidadas: un botón dentro de una
 * fila pulsable darían dos objetivos de toque peleándose por la misma zona. En Dejar la fila es inerte
 * y dice por qué: la hora guardada sobrevive a un cambio de tipo, así que no se finge que no existe.
 */
@Composable
private fun Recordatorio(
    minutos: Int?,
    inerte: Boolean,
    alAbrir: () -> Unit,
    alQuitar: () -> Unit,
) {
    val colores = BitoTema.colores
    if (inerte) {
        Fila(
            etiqueta = stringResource(R.string.form_reminder_label),
            subtitulo = stringResource(R.string.form_reminder_quit_hint),
            modifier = Modifier.testTag("reminder-row"),
            habilitada = false,
        )
        return
    }
    val gesto = rememberGesto()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilaValor(
            etiqueta = stringResource(R.string.form_reminder_label),
            valor = minutos?.let(::formatReminderTime) ?: stringResource(R.string.form_reminder_none),
            alPulsar = alAbrir,
            modifier = Modifier.weight(1f).testTag("reminder-row"),
        )
        if (minutos != null) {
            Box(
                Modifier
                    .size(Medidas.toqueMinimo)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        gesto(Snd.HUNDIR, Haptica.LOW)
                        alQuitar()
                    }.testTag("reminder-clear"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconosSistema.X, contentDescription = stringResource(R.string.form_reminder_clear), tint = colores.tintaSuave)
            }
        }
    }
}
