@file:OptIn(ExperimentalFoundationApi::class)

package com.alvarotc.bito.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.inicio.cantoCompartido
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.anclaParticula
import com.alvarotc.bito.ui.settings.hojas.hora
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tasks.taskDueLabel
import com.alvarotc.bito.ui.tasks.taskDueOverdue
import kotlinx.coroutines.delay

/** Lo que pinta la lista de Hoy después de la tarjeta de Habi y «Tu día», en orden (spec §5.3). */
sealed interface FilaHoy {
    data class Habito(val card: HabitCardUi) : FilaHoy

    data class Tarea(val row: TaskRowUi) : FilaHoy

    /** «Ver todas las tareas ›»: sale mientras exista alguna tarea, aunque estén todas hechas. */
    data object VerTareas : FilaHoy

    /** Hay tareas pero ninguna toca hoy. */
    data object TareasHechas : FilaHoy

    data class EnPausa(val paused: List<PausedHabitUi>) : FilaHoy

    /** «Esperan sin prisa» (día duro): los pendientes que la tarjeta no propone, atenuados. */
    data class Esperan(val cards: List<HabitCardUi>) : FilaHoy

    data class CerrarDia(val sellado: Boolean) : FilaHoy
}

/** El orden de la lista [spec §15.5]: hábitos (orden del usuario), tareas (por franja), «En pausa» y cerrar el día. */
internal fun filasDe(estado: TodayUiState): List<FilaHoy> =
    buildList {
        val espera =
            if (estado.diaDuro == null) emptyList() else estado.cards.filter { !it.doneToday && it.id != estado.diaDuro }
        estado.cards.filter { it !in espera }.forEach { add(FilaHoy.Habito(it)) }
        if (espera.isNotEmpty()) add(FilaHoy.Esperan(espera))
        estado.tasks.forEach { add(FilaHoy.Tarea(it)) }
        if (estado.hasAnyTasks) {
            if (estado.tasks.isEmpty()) add(FilaHoy.TareasHechas)
            add(FilaHoy.VerTareas)
        }
        if (estado.pausedHabits.isNotEmpty()) add(FilaHoy.EnPausa(estado.pausedHabits))
        if (estado.cards.isNotEmpty()) add(FilaHoy.CerrarDia(estado.todaySealed))
    }

internal fun FilaHoy.clave(): String =
    when (this) {
        is FilaHoy.Habito -> card.id
        is FilaHoy.Tarea -> "tarea-${row.id}"
        FilaHoy.VerTareas -> "ver-tareas"
        FilaHoy.TareasHechas -> "tareas-hechas"
        is FilaHoy.EnPausa -> "en-pausa"
        is FilaHoy.Esperan -> "esperan-sin-prisa"
        is FilaHoy.CerrarDia -> "cerrar-dia"
    }

/** Cuánto llena el agua del círculo: hecho = 1; contadores a x/y; el límite pasado o la recaída hunden sin rojo. */
internal fun llenado(card: HabitCardUi): Float =
    when {
        card.kind == CardKind.ABSTINENCE || card.direction == Direction.AT_MOST -> if (card.failed) 0.35f else 1f
        card.kind == CardKind.CHECK -> if (card.doneToday) 1f else 0f
        card.target <= 0 -> if (card.doneToday) 1f else 0f
        else -> (card.progress.toFloat() / card.target).coerceIn(0f, 1f)
    }

/** G28: el mantener dura 450 ms en el círculo y en el +paso. */
@Composable
private fun ConMantener(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config =
        remember(base) {
            object : ViewConfiguration by base {
                override val longPressTimeoutMillis: Long get() = Duraciones.MANTENER.toLong()
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides config, content = content)
}

/** Marca semántica de que la fila aún está entrando (alfa < 1); la leen los tests. */
internal val EntrandoKey = SemanticsPropertyKey<Boolean>("entrando")
internal var SemanticsPropertyReceiver.entrando by EntrandoKey

/** Aparece escalonada al cargar y, si [brotar], brota (G34: escala .9 a 1 y alfa). */
internal fun Modifier.aparecer(
    activo: Boolean,
    retraso: Int,
    brotar: Boolean = false,
): Modifier =
    composed {
        val reducido = BitoTema.reducido
        val a = remember { Animatable(if (activo) 0f else 1f) }
        LaunchedEffect(activo) {
            if (activo) {
                a.snapTo(0f)
                delay(retraso.toLong())
                a.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else Duraciones.BROTAR_FILA, easing = Curvas.out))
            }
        }
        semantics { entrando = a.value < 1f }.graphicsLayer {
            alpha = a.value
            if (brotar && !reducido) {
                val s = 0.9f + 0.1f * a.value
                scaleX = s
                scaleY = s
                translationY = (1f - a.value) * 18.dp.toPx()
            }
        }
    }

@Composable
private fun CirculoAgua(
    llenado: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marca: Boolean = llenado >= 1f,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val nivel by animateFloatAsState(
        llenado,
        if (reducido) tween(Duraciones.REDUCIDO) else Muelles.marcar<Float>(),
        label = "agua",
    )
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .drawBehind {
                val n = nivel.coerceIn(0f, 1f)
                if (n > 0f) drawRect(color, topLeft = Offset(0f, size.height * (1f - n)), size = Size(size.width, size.height * n))
            }.border(Medidas.linea, colores.contorno, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (marca) Icon(IconosSistema.Check, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(16.dp))
    }
}

private val FormaFila = BitoFormas.canto
private val SUBE_RACHA = 4.dp

/** La etiqueta «lo gordo» de una fila: reabre la hoja. Zona de 44 dp. */
@Composable
private fun EtiquetaGordo(
    id: String,
    alToque: () -> Unit,
) {
    Text(
        stringResource(R.string.habla_etiqueta_gordo),
        style = BitoTema.tipos.t14.fuerte(),
        // Agua (spec §7.3): la honda, que se lee sobre el canto de la fila.
        color = BitoTema.colores.aguaHonda,
        modifier =
            Modifier
                .heightIn(min = Medidas.toqueMinimo)
                .testTag("gordo-etiqueta-$id")
                .clickable(role = Role.Button, onClick = alToque)
                .padding(vertical = Medidas.e12),
    )
}

@Composable
internal fun FilaHabito(
    card: HabitCardUi,
    alCirculo: () -> Unit,
    alPaso: () -> Unit,
    alExacto: () -> Unit,
    alAbrir: () -> Unit,
    alRecaer: () -> Unit,
    modifier: Modifier = Modifier,
    nombreModifier: Modifier = Modifier,
    rachaViva: Boolean = false,
    esGordo: Boolean = false,
    alGordo: () -> Unit = {},
) {
    val colores = BitoTema.colores
    val t = BitoTema.tipos
    val limite = card.direction == Direction.AT_MOST
    val suma = card.kind == CardKind.COUNTER || card.kind == CardKind.DURATION
    val unidad = if (card.kind == CardKind.DURATION) stringResource(R.string.unit_min) else card.unit.orEmpty()
    val descripcionCirculo =
        when {
            suma -> stringResource(R.string.add_step_action, card.step, card.name)
            card.kind == CardKind.CHECK && card.doneToday -> stringResource(R.string.unmark_done_action, card.name)
            else -> stringResource(R.string.mark_done_action, card.name)
        }
    Row(
        modifier
            .fillMaxWidth()
            .testTag("fila-${card.id}")
            .cantoCompartido(card.id)
            .background(colores.canto, FormaFila)
            .border(Medidas.linea, colores.contorno, FormaFila)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
    ) {
        ConMantener {
            Box(
                Modifier
                    .size(Medidas.toqueMinimo)
                    .testTag("circulo-${card.id}")
                    .then(
                        when {
                            card.kind == CardKind.ABSTINENCE -> Modifier
                            suma ->
                                Modifier.combinedClickable(
                                    onClick = alCirculo,
                                    onLongClick = alExacto,
                                    onLongClickLabel = stringResource(R.string.exact_value_action),
                                    role = Role.Button,
                                )
                            else -> Modifier.clickable(role = Role.Button, onClick = alCirculo)
                        },
                    ).semantics { if (card.kind != CardKind.ABSTINENCE) contentDescription = descripcionCirculo },
                contentAlignment = Alignment.Center,
            ) {
                CirculoAgua(llenado(card), colores.agua, Modifier.anclaParticula(ClavesAncla.fila(card.id)))
            }
        }
        val rachaMin = if (card.kind == CardKind.ABSTINENCE) 1 else 2
        val rachaTexto =
            when {
                card.kind == CardKind.CHECK && card.period == Period.DAY -> null // sale a la derecha
                card.streak < rachaMin -> null
                card.period == Period.DAY -> stringResource(R.string.hoy_dia_n, card.streak)
                else -> stringResource(R.string.hoy_racha_n, card.streak)
            }
        val base =
            when {
                card.kind == CardKind.ABSTINENCE ->
                    stringResource(if (card.failed) R.string.relapse_today else R.string.clean_today)
                card.kind == CardKind.CHECK && card.period == Period.WEEK ->
                    stringResource(R.string.week_progress, card.progress, card.target)
                card.kind == CardKind.CHECK && card.period == Period.MONTH ->
                    stringResource(R.string.month_progress, card.progress, card.target)
                else -> null
            }
        val subtitulo = listOfNotNull(base, rachaTexto).joinToString(" · ").ifEmpty { null }
        // Nombre y subtítulo son una sola zona pulsable (toque y mantener para reordenar) de al menos 44 dp.
        Column(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(nombreModifier)
                    .testTag("nombre-${card.id}")
                    .heightIn(min = Medidas.toqueMinimo)
                    .clickable(onClick = alAbrir),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    card.name,
                    style = t.t16.fuerte(),
                    color = colores.tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = if (rachaViva && rachaTexto != null) t.t14.fuerte() else t.t14,
                        color = colores.tintaSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val minutoAviso = card.horaAceptada
            val hayHora = minutoAviso != null && !card.doneToday
            if (hayHora || esGordo) {
                Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalAlignment = Alignment.CenterVertically) {
                    if (hayHora) {
                        Text(hora(minutoAviso!!), style = t.t14, color = colores.tintaSuave, modifier = Modifier.testTag("hora-${card.id}"))
                    }
                    if (esGordo) EtiquetaGordo(card.id, alGordo)
                }
            }
        }
        when {
            suma -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    CifraRodillo(card.progress, estilo = t.t19.fuerte(), color = colores.tinta)
                    val meta =
                        if (limite) {
                            stringResource(R.string.limit_caption, "${card.target}")
                        } else {
                            "/${card.target}"
                        }
                    Text(
                        "$meta $unidad".trim(),
                        style = t.t14,
                        color = colores.tintaSuave,
                        modifier = Modifier.padding(start = 4.dp),
                        maxLines = 1,
                    )
                }
                ConMantener {
                    Box(
                        Modifier
                            .size(Medidas.toqueMinimo)
                            .testTag("paso-${card.id}")
                            .clip(CircleShape)
                            .background(colores.hielo)
                            .border(Medidas.linea, colores.contorno, CircleShape)
                            .combinedClickable(
                                onClick = alPaso,
                                onLongClick = alExacto,
                                onLongClickLabel = stringResource(R.string.exact_value_action),
                                role = Role.Button,
                            ).semantics { contentDescription = descripcionCirculo },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+${card.step}", style = t.t14.fuerte(), color = colores.tinta, maxLines = 1)
                    }
                }
            }
            card.kind == CardKind.ABSTINENCE ->
                if (!card.failed) BotonTexto(stringResource(R.string.hoy_he_recaido), alRecaer)
            card.streak > 0 && card.period == Period.DAY -> {
                // «día N» sube 4 dp y va en negrita mientras dura la veta de racha; con movimiento reducido solo cambia el peso.
                val sube by animateDpAsState(
                    if (rachaViva && !BitoTema.reducido) SUBE_RACHA else 0.dp,
                    Muelles.marcar(),
                    label = "diaNSube",
                )
                Text(
                    stringResource(R.string.hoy_dia_n, card.streak),
                    style = if (rachaViva) t.t14.fuerte() else t.t14,
                    color = colores.tintaSuave,
                    modifier = Modifier.padding(end = Medidas.e8).offset(y = -sube).testTag("dia-n-${card.id}"),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Una tarea: canto de piedra, círculo que la termina (G6) y la fila se abre al tocarla. */
@Composable
internal fun FilaTarea(
    task: TaskRowUi,
    hoy: LogicalDay,
    saliendo: Boolean,
    alTerminar: () -> Unit,
    alEmpezar: () -> Unit,
    alHoyNo: () -> Unit,
    modifier: Modifier = Modifier,
    manana: Boolean = false,
    esGordo: Boolean = false,
    alGordo: () -> Unit = {},
) {
    val colores = BitoTema.colores
    val t = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val anclas = LocalAnclas.current
    var abierta by remember { mutableStateOf(false) }
    val sale by animateFloatAsState(
        if (saliendo) 1f else 0f,
        tween(if (reducido) Duraciones.REDUCIDO else Duraciones.HUNDIR_FILA, delayMillis = if (reducido) 0 else 120, easing = Curvas.out),
        label = "tareaSale",
    )

    fun terminar() {
        if (saliendo) return
        anclas?.granos(ClavesAncla.tarea(task.id))
        gesto(Snd.ASENTAR, Haptica.FALL)
        alTerminar()
    }
    val forma = RoundedCornerShape(28.dp)
    val hecha = stringResource(R.string.task_done)
    Column(
        modifier
            .fillMaxWidth()
            .testTag("tarea-${task.id}")
            .graphicsLayer {
                alpha = 1f - sale
                if (!reducido) {
                    translationY = 10.dp.toPx() * sale
                    val s = 1f - 0.03f * sale
                    scaleX = s
                    scaleY = s
                }
            }.background(colores.canto, forma)
            .border(Medidas.linea, colores.piedra, forma)
            .clickable { abierta = !abierta }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Box(
                Modifier
                    .size(Medidas.toqueMinimo)
                    .testTag("circulo-tarea-${task.id}")
                    .clickable(role = Role.Button) { terminar() }
                    .semantics { contentDescription = hecha },
                contentAlignment = Alignment.Center,
            ) {
                CirculoAgua(
                    if (saliendo) 1f else 0f,
                    colores.piedra,
                    Modifier.anclaParticula(ClavesAncla.tarea(task.id)),
                    marca = saliendo,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = t.t16.fuerte(), color = colores.tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (task.firstStep != null) {
                    Text(task.firstStep, style = t.t14, color = colores.tintaSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val plazo = taskDueLabel(task.dueKind, task.dueDay, hoy)
                if (plazo != null) {
                    val vencida = task.dueKind == DueKind.DATE && taskDueOverdue(task.dueKind, task.dueDay, hoy)
                    Text(plazo, style = t.t14, color = if (vencida) colores.peligroTexto else colores.tintaSuave, maxLines = 1)
                }
                // Con el día sellado, lo que queda de las tareas es de mañana.
                if (manana) {
                    Text(
                        stringResource(R.string.habla_manana),
                        style = t.t14,
                        color = colores.tintaSuave,
                        maxLines = 1,
                        modifier = Modifier.testTag("manana-${task.id}"),
                    )
                }
                if (esGordo) EtiquetaGordo(task.id, alGordo)
            }
        }
        AnimatedVisibility(
            visible = abierta && !saliendo,
            enter =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    expandVertically(tween(Duraciones.HUNDIR_FILA, easing = Curvas.out)) + fadeIn(tween(Duraciones.HUNDIR_FILA))
                },
            exit =
                if (reducido) {
                    fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    shrinkVertically(tween(Duraciones.HUNDIR_FILA, easing = Curvas.out)) + fadeOut(tween(Duraciones.HUNDIR_FILA))
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
            ) {
                Boton(stringResource(R.string.task_start), alEmpezar, Modifier.testTag("tarea-empezar-${task.id}"))
                Boton(stringResource(R.string.task_not_today), alHoyNo, Modifier.testTag("tarea-hoy-no-${task.id}"))
                Boton(hecha, { terminar() }, Modifier.testTag("tarea-hecha-${task.id}"), estilo = EstiloBoton.PRINCIPAL)
            }
        }
    }
}

/** «En pausa»: nombre atenuado, tocar abre la ficha. */
@Composable
internal fun SeccionEnPausa(
    pausados: List<PausedHabitUi>,
    alAbrir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val estado = stringResource(R.string.paused_section_title)
    Column(modifier.fillMaxWidth().testTag("en-pausa"), verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        Text(estado, style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
        Text(stringResource(R.string.hoy_en_pausa_nota), style = BitoTema.tipos.t14, color = colores.tintaSuave)
        pausados.forEach { p ->
            Text(
                p.name,
                style = BitoTema.tipos.t16,
                color = colores.tintaSuave,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Medidas.toqueMinimo)
                        .testTag("pausado-${p.id}")
                        .clickable { alAbrir(p.id) }
                        .semantics { contentDescription = "${p.name}, $estado" }
                        .padding(vertical = Medidas.e12),
            )
        }
    }
}

/** Fila hueco de la carga: contorno discontinuo que respira (alfa .55 a 1 en 1,6 s). */
@Composable
internal fun FilaHueco(
    indice: Int,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val alfa =
        if (reducido) {
            1f
        } else {
            val t = rememberInfiniteTransition(label = "hueco")
            val v by t.animateFloat(
                0.55f,
                1f,
                infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                label = "huecoAlfa",
            )
            v
        }
    Box(
        modifier
            .fillMaxWidth()
            .testTag("fila-hueco-$indice")
            .heightIn(min = 56.dp)
            .graphicsLayer { alpha = alfa }
            .drawBehind {
                val grosor = Medidas.linea.toPx()
                drawRoundRect(
                    colores.contorno,
                    topLeft = Offset(grosor / 2, grosor / 2),
                    size = Size(size.width - grosor, size.height - grosor),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(grosor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
    )
}
