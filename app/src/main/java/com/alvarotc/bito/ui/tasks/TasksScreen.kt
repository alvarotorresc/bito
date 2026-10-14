package com.alvarotc.bito.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.MenuFila
import com.alvarotc.bito.ui.sistema.componentes.OpcionMenu
import com.alvarotc.bito.ui.sistema.componentes.Plegable
import com.alvarotc.bito.ui.sistema.componentes.TonoOpcion
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** De qué sección sale una fila: decide si su menú ofrece «Traer a hoy» (solo Con plazo y Sin plazo). */
private enum class Seccion { HOY, SEMANA, PLAZO, SUELTA, HECHA }

private data class MenuDeFila(
    val fila: TaskListRowUi,
    val seccion: Seccion,
)

/**
 * Todas las tareas: Hoy, Esta semana, Con plazo, Sin plazo y, al final y plegada, Hechas. Terminar
 * una tarea (G6) deja caer granos y avisa con Deshacer; el menú de fila lleva Empezar, Traer a hoy,
 * Editar y Borrar. Las firmas de navegación no cambian: las llama `BitoNavHost`.
 */
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onBack: () -> Unit,
    onStartFocus: (String) -> Unit = {},
    onEditTask: (String) -> Unit = {},
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val taskDone by viewModel.lastTaskDone.collectAsStateWithLifecycle()
    var hechasAbiertas by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuDeFila?>(null) }
    var menuAbierto by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf<TaskListRowUi?>(null) }
    val avisos = rememberAvisoEstado()
    val fallbackName = stringResource(R.string.habi_name_fallback)
    val taskDoneLabel = stringResource(HabiVoice.taskDoneRes(state.personality), state.userName.ifBlank { fallbackName })
    val deshacer = stringResource(R.string.sistema_deshacer)
    val traida = stringResource(R.string.tasks2_traida)

    // Terminar una tarea: el aviso lleva Deshacer, que la reabre. Los bitos ganados no se quitan.
    LaunchedEffect(taskDone) {
        val id = taskDone ?: return@LaunchedEffect
        viewModel.consumeTaskDone()
        scope.launch {
            avisos.mostrar(taskDoneLabel, deshacer) {
                gesto(Snd.DESCENSO, Haptica.LOW)
                viewModel.reopen(id)
            }
        }
    }

    fun traerAHoy(fila: TaskListRowUi) {
        gesto(Snd.ASCENSO, Haptica.TICK)
        viewModel.bringToToday(fila.id) { apunte ->
            scope.launch {
                avisos.mostrar(traida, deshacer) {
                    gesto(Snd.DESCENSO, Haptica.LOW)
                    viewModel.undoBring(apunte)
                }
            }
        }
    }

    fun abrirMenu(
        fila: TaskListRowUi,
        seccion: Seccion,
    ) {
        menu = MenuDeFila(fila, seccion)
        menuAbierto = true
    }

    Box(Modifier.fillMaxSize().background(colores.tAlta)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("tasks-list"),
            contentPadding = PaddingValues(Medidas.margen),
            verticalArrangement = Arrangement.spacedBy(Medidas.entreFilas),
        ) {
            item(key = "cabecera") { CabeceraTareas(stringResource(R.string.tasks_screen_title), onBack) }
            if (!state.loading && state.isEmpty) {
                item(key = "vacia") { TareasVacias(state.personality) }
            } else {
                seccion(state.todayTasks, Seccion.HOY, R.string.tasks_section_today, state.today, ::abrirMenu, viewModel::markDone)
                seccion(state.weekTasks, Seccion.SEMANA, R.string.tasks_section_week, state.today, ::abrirMenu, viewModel::markDone)
                seccion(state.datedTasks, Seccion.PLAZO, R.string.tasks_section_dated, state.today, ::abrirMenu, viewModel::markDone)
                seccion(state.looseTasks, Seccion.SUELTA, R.string.tasks_section_loose, state.today, ::abrirMenu, viewModel::markDone)
                if (state.doneTasks.isNotEmpty()) {
                    item(key = "sec-hechas") {
                        Plegable(
                            titulo = stringResource(R.string.tasks_section_done),
                            abierto = hechasAbiertas,
                            alCambiar = { hechasAbiertas = it },
                            modifier = Modifier.testTag("tasks-done-toggle"),
                        ) {
                            Column(Modifier.padding(top = Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.entreFilas)) {
                                state.doneTasks.forEach { fila ->
                                    FilaTarea(fila, state.today, onDone = {}, onMenu = { abrirMenu(fila, Seccion.HECHA) })
                                }
                            }
                        }
                    }
                }
            }
        }
        AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
    }

    val actual = menu
    if (actual != null) {
        val fila = actual.fila
        val opciones =
            buildList {
                if (actual.seccion != Seccion.HECHA) {
                    add(OpcionMenu(stringResource(R.string.task_start), IconosSistema.Play, TonoOpcion.AGUA) { onStartFocus(fila.id) })
                }
                if (actual.seccion == Seccion.PLAZO || actual.seccion == Seccion.SUELTA) {
                    add(OpcionMenu(stringResource(R.string.task_bring_today), IconosSistema.ArrowUp, TonoOpcion.AGUA) { traerAHoy(fila) })
                }
                add(OpcionMenu(stringResource(R.string.task_edit), IconosSistema.PencilSimple, TonoOpcion.PIEDRA) { onEditTask(fila.id) })
                add(OpcionMenu(stringResource(R.string.task_delete), IconosSistema.Trash, TonoOpcion.PELIGRO) { borrando = fila })
            }
        MenuFila(
            abierto = menuAbierto,
            alCerrar = { menuAbierto = false },
            opciones = opciones,
            titulo = fila.title,
            modifier = Modifier.testTag("task-row-menu"),
        )
    }

    DialogoBorrarTarea(
        abierto = borrando != null,
        alConfirmar = {
            borrando?.let { viewModel.delete(it.id) }
            borrando = null
        },
        alCancelar = { borrando = null },
    )
}

/** Una sección con filas: su título y sus filas. Si está vacía no deja ni el título. */
private fun LazyListScope.seccion(
    filas: List<TaskListRowUi>,
    seccion: Seccion,
    titulo: Int,
    hoy: LogicalDay,
    abrirMenu: (TaskListRowUi, Seccion) -> Unit,
    terminar: (String) -> Unit,
) {
    if (filas.isEmpty()) return
    item(key = "sec-${seccion.name}") {
        Text(
            stringResource(titulo),
            style = BitoTema.tipos.t14.fuerte(),
            color = BitoTema.colores.tintaSuave,
            modifier = animateItemSuave(),
        )
    }
    items(filas, key = { it.id }) { fila ->
        FilaTarea(
            fila = fila,
            hoy = hoy,
            onDone = { terminar(fila.id) },
            onMenu = { abrirMenu(fila, seccion) },
            modifier = animateItemSuave(),
        )
    }
}

/** Las filas viajan entre secciones con el muelle `camino` (G35); con reducido no viajan. */
@Composable
private fun LazyItemScope.animateItemSuave(): Modifier =
    Modifier.animateItem(
        placementSpec = if (BitoTema.reducido) null else Muelles.camino(umbral = IntOffset.VisibilityThreshold),
    )

@Composable
private fun TareasVacias(personality: com.alvarotc.bito.domain.model.Personality) {
    val colores = BitoTema.colores
    Column(
        Modifier.fillMaxWidth().padding(top = Medidas.e24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Medidas.e12),
    ) {
        Box(
            Modifier
                .size(width = 140.dp, height = 150.dp)
                .background(colores.hornacina, BitoFormas.hornacina),
            contentAlignment = Alignment.BottomCenter,
        ) {
            HabiAvatar(
                spec = HabiSpec(Mood.NORMAL, personality, EquippedSet()),
                modifier = Modifier.size(112.dp),
                animated = false,
            )
        }
        Text(stringResource(R.string.tasks_empty), style = BitoTema.tipos.t19.fuerte(), color = colores.tinta)
    }
}

private const val GRANOS = 14
private const val ESPERA_CAIDA_MS = 120

/**
 * Una fila de tarea: círculo para terminarla (G6), nombre con su primer paso y su plazo, y los tres
 * puntos del menú. Al terminarla el círculo se llena de piedra, caen granos, y la fila baja 10 dp,
 * escala .97 y se funde; solo entonces se escribe en la base.
 */
@Composable
private fun FilaTarea(
    fila: TaskListRowUi,
    hoy: LogicalDay,
    onDone: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    var terminando by remember { mutableStateOf(false) }
    val granos = remember { Animatable(0f) }
    val caida = remember { Animatable(0f) }
    val hechaLabel = stringResource(R.string.task_done)
    val onDoneActual by androidx.compose.runtime.rememberUpdatedState(onDone)

    LaunchedEffect(terminando) {
        if (!terminando) return@LaunchedEffect
        if (reducido) {
            caida.animateTo(1f, tween(Duraciones.REDUCIDO))
        } else {
            coroutineScope {
                launch { granos.animateTo(1f, tween(Duraciones.GRANOS, easing = LinearEasing)) }
                launch {
                    delay(ESPERA_CAIDA_MS.toLong())
                    caida.animateTo(1f, tween(Duraciones.CAER))
                }
            }
        }
        onDoneActual()
    }

    val lleno = fila.done || terminando
    val relleno by animateColorAsState(
        if (lleno) colores.piedra else colores.canto,
        tween(Duraciones.COLOR),
        label = "circuloTarea",
    )
    val hecha = fila.done
    Row(
        modifier
            .fillMaxWidth()
            .testTag("task-${fila.id}")
            .graphicsLayer {
                val c = caida.value
                alpha = 1f - c
                if (!reducido) {
                    translationY = 10.dp.toPx() * c
                    val e = 1f - 0.03f * c
                    scaleX = e
                    scaleY = e
                }
            }.background(colores.canto, BitoFormas.tarjeta)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(horizontal = Medidas.e12, vertical = Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
    ) {
        val circulo =
            Modifier
                .size(Medidas.toqueMinimo)
                .drawBehind {
                    val p = granos.value
                    if (p > 0f && p < 1f) {
                        repeat(GRANOS) { i ->
                            val tramo = (Duraciones.GRANOS - GRANOS * Duraciones.GRANOS_ESCALON).toFloat()
                            val t = ((p * Duraciones.GRANOS - i * Duraciones.GRANOS_ESCALON) / tramo).coerceIn(0f, 1f)
                            val x = size.width / 2f + ((i * 37) % 11 - 5) * 2.2.dp.toPx()
                            val y = size.height * 0.8f + 26.dp.toPx() * t * t
                            drawCircle(colores.piedra.copy(alpha = 1f - t), radius = 1.8.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }.background(relleno, CircleShape)
                .border(Medidas.linea, if (lleno) colores.piedra else colores.contorno, CircleShape)
        if (hecha) {
            Box(circulo, contentAlignment = Alignment.Center) {
                Icon(IconosSistema.Check, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(
                circulo
                    .testTag("task-done-${fila.id}")
                    .clickable(role = Role.Button) {
                        if (!terminando) {
                            terminando = true
                            gesto(Snd.ASENTAR, Haptica.FALL)
                        }
                    }.semantics { contentDescription = hechaLabel },
                contentAlignment = Alignment.Center,
            ) {
                if (terminando) {
                    Icon(IconosSistema.Check, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onMenu).defaultMinSize(minHeight = Medidas.toqueMinimo),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(fila.title, style = tipos.t16.fuerte(), color = if (hecha) colores.tintaSuave else colores.tinta)
            if (fila.firstStep != null) {
                Text(fila.firstStep, style = tipos.t14, color = colores.tintaSuave)
            }
            // Una suelta no tiene día que darle a taskDueLabel: «sin plazo» lo dice en vez de callar.
            val plazo =
                if (fila.dueKind == DueKind.NONE) {
                    stringResource(R.string.task_no_due)
                } else {
                    taskDueLabel(fila.dueKind, fila.dueDay, hoy)
                }
            if (plazo != null) {
                val vencida = taskDueOverdue(fila.dueKind, fila.dueDay, hoy)
                Text(plazo, style = tipos.t14, color = if (vencida) colores.peligro else colores.tintaSuave)
            }
        }
        val menuCd = stringResource(R.string.task_row_menu_cd)
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .testTag("task-menu-${fila.id}")
                .clickable(role = Role.Button, onClick = onMenu)
                .semantics { contentDescription = menuCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.DotsThree, contentDescription = null, tint = colores.tintaSuave)
        }
    }
}
