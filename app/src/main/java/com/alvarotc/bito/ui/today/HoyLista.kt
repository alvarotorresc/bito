package com.alvarotc.bito.ui.today

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.inicio.EntradaSesion
import com.alvarotc.bito.ui.inicio.NavegacionInicio
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val FILAS_HUECO = 4
private val HUNDIDA_CIERRE = 48.dp
private const val ESCALON_ENTRADA_MS = 60
private const val MAX_ESCALONADAS = 8
private const val SALIDA_TAREA_MS = 340L
private const val BROTE_VIGENCIA_MS = 1500L

/** Lo que se espera, al volver de crear, a que el id nuevo llegue al estado de Hoy (viejo o aún sin escribir). */
internal const val ESPERA_CREADO_MS = 1_500L
private const val PUNTOS_MAX = 12

/** La lista de Hoy (spec §5.3): Habi, «Tu día», hábitos, tareas, «En pausa» y cerrar el día, en un solo `LazyColumn`. */
@Composable
fun HoyLista(
    viewModel: TodayViewModel,
    navegar: NavegacionInicio,
    listState: LazyListState,
    avisos: AvisoEstado,
    encabezado: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    creado: String? = null,
    alCreadoVisto: () -> Unit = {},
    // El aviso de la mañana pide la hoja de lo gordo; la lista avisa de que lo vio (la hoja la abre la Tarea 24).
    abrirGordo: Boolean = false,
    alGordoVisto: () -> Unit = {},
    // Lo que se ha hundido la lista porque se abre el cierre (0 nada, 1 del todo); se lee al dibujar. Provisional: la
    // lista entera baja y se apaga a la vez; el carril de Hoy (u1) lo cambia por un escalón de 25 ms por fila.
    hundida: () -> Float = { 0f },
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val logged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val taskDone by viewModel.lastTaskDone.collectAsStateWithLifecycle()
    val propuesta by viewModel.propuesta.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gesto = rememberGesto()
    val registro = LocalAnclas.current
    val reducido = BitoTema.reducido
    var exactFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var recaerFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var sealDismissed by rememberSaveable { mutableStateOf(false) }
    var limitePendiente by remember { mutableStateOf(false) }
    var brotando by remember { mutableStateOf<String?>(null) }
    // La entrada escalonada solo ocurre al cargar: se apaga pasada su ventana y las filas que vuelven por scroll no la repiten.
    var vistoCargando by remember { mutableStateOf(false) }
    if (estado.loading) vistoCargando = true
    LaunchedEffect(estado.loading) {
        if (!estado.loading && vistoCargando) {
            delay((MAX_ESCALONADAS * ESCALON_ENTRADA_MS + Duraciones.BROTAR_FILA + 100).toLong())
            vistoCargando = false
        }
    }
    LaunchedEffect(abrirGordo) { if (abrirGordo) alGordoVisto() }
    LaunchedEffect(brotando) {
        if (brotando != null) {
            delay(BROTE_VIGENCIA_MS)
            brotando = null
        }
    }
    // Tareas ya marcadas que siguen dibujando su salida: la tarea se marca al instante, la fila se va después.
    var saliendo by remember { mutableStateOf<Map<String, Pair<Int, TaskRowUi>>>(emptyMap()) }

    val textoRegistrado = stringResource(R.string.logged_snackbar)
    val textoLimite = stringResource(R.string.hoy_limite_pasado)
    val deshacer = stringResource(R.string.undo)
    val respaldo = stringResource(R.string.habi_name_fallback)
    val textoTarea = stringResource(HabiVoice.taskDoneRes(estado.spec.personality), estado.userName.ifBlank { respaldo })

    LaunchedEffect(logged) {
        if (logged != null) {
            val limite = limitePendiente
            limitePendiente = false
            avisos.mostrar(if (limite) textoLimite else textoRegistrado, deshacer) { viewModel.undo() }
            viewModel.consumeLogged()
        }
    }
    LaunchedEffect(taskDone) {
        if (taskDone != null) {
            avisos.mostrar(textoTarea, deshacer) { viewModel.undoTaskDone() }
            viewModel.consumeTaskDone()
        }
    }
    // La oferta de deshacer muere con la visita: no vuelve a salir al regresar a Hoy.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.consumeLogged()
            viewModel.consumeTaskDone()
        }
    }

    // Reordenar trabaja sobre una copia local para que el arrastre se vea al instante; la escritura va al soltar.
    val ordenadas = remember(estado.cards) { estado.cards.toMutableStateList() }
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val desde = ordenadas.indexOfFirst { it.id == from.key }
            val hasta = ordenadas.indexOfFirst { it.id == to.key }
            if (desde != -1 && hasta != -1) ordenadas.add(hasta, ordenadas.removeAt(desde))
        }
    val base = filasDe(estado.copy(cards = ordenadas.toList()))
    // La fila saliente vuelve a su sitio dentro del bloque de tareas, no al final.
    val filas =
        base.toMutableList().also { lista ->
            saliendo.values.filter { (_, r) -> estado.tasks.none { it.id == r.id } }.sortedBy { it.first }.forEach { (indice, r) ->
                val primera =
                    lista.indexOfFirst { it is FilaHoy.Tarea }.let {
                        if (it < 0) {
                            lista.indexOfFirst {
                                    f ->
                                f !is FilaHoy.Habito
                            }.let { n -> if (n < 0) lista.size else n }
                        } else {
                            it
                        }
                    }
                val tareas = lista.count { it is FilaHoy.Tarea }
                lista.add(primera + indice.coerceAtMost(tareas), FilaHoy.Tarea(r))
            }
        }
    val hayTuDia = estado.cards.isNotEmpty()
    val vacio = !estado.loading && estado.cards.isEmpty() && estado.pausedHabits.isEmpty() && !estado.hasAnyTasks

    // Volver de crear (§5.7): se espera a un estado cargado que ya traiga el id (con tope), se desplaza a la fila y brota.
    LaunchedEffect(creado) {
        if (creado == null) return@LaunchedEffect

        fun posicionEn(s: TodayUiState) =
            filasDe(s).indexOfFirst {
                (it is FilaHoy.Habito && it.card.id == creado) || (it is FilaHoy.Tarea && it.row.id == creado)
            }
        snapshotFlow { estado }.first { !it.loading }
        // Cargado no basta: puede ser un estado viejo (Hoy dejó de escuchar) o la tarea aún no estar escrita.
        val cargado = withTimeoutOrNull(ESPERA_CREADO_MS) { snapshotFlow { estado }.first { posicionEn(it) >= 0 } }
        val posicion = cargado?.let(::posicionEn) ?: -1
        if (cargado != null && posicion >= 0) {
            brotando = creado
            val indice = 2 + (if (cargado.cards.isNotEmpty()) 1 else 0) + posicion
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == indice } && visible.isNotEmpty()) {
                val media = visible.sumOf { it.size } / visible.size + 12
                listState.animateScrollBy(
                    ((indice - visible.first().index) * media).toFloat(),
                    tween(ENTRADA_SCROLL_MS, easing = Curvas.drawer),
                )
            }
            listState.animateScrollToItem(indice)
        }
        alCreadoVisto()
    }

    fun sumar(card: HabitCardUi) {
        val llega = card.direction == Direction.AT_LEAST && card.progress < card.target && card.progress + card.step >= card.target
        val pasa = card.direction == Direction.AT_MOST && !card.failed && card.progress + card.step > card.target
        viewModel.addAmount(card, card.step)
        when {
            llega -> {
                registro?.gota(ClavesAncla.fila(card.id))
                gesto(Snd.GORGOTEO, Haptica.TICKS)
            }
            pasa -> gesto(Snd.VACIAR, Haptica.LOW)
            else -> gesto(Snd.TIC, Haptica.TICK)
        }
        if (pasa) limitePendiente = true
    }

    fun alCirculo(card: HabitCardUi) {
        when (card.kind) {
            CardKind.CHECK -> {
                if (card.doneToday) {
                    registro?.apagarGota(ClavesAncla.fila(card.id))
                    gesto(Snd.VACIAR, Haptica.LOW)
                } else {
                    registro?.gota(ClavesAncla.fila(card.id))
                    gesto(Snd.GORGOTEO, Haptica.TICKS)
                }
                viewModel.tapPrimary(card)
            }
            CardKind.COUNTER, CardKind.DURATION -> sumar(card)
            CardKind.ABSTINENCE -> Unit
        }
    }

    Box(
        modifier.graphicsLayer {
            val k = hundida()
            if (!reducido) translationY = HUNDIDA_CIERRE.toPx() * k
            alpha = 1f - k
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().testTag("hoy-lista"),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Medidas.entreFilas),
        ) {
            item(key = "encabezado") { encabezado() }
            item(key = "habi") {
                TarjetaHabi(
                    propuesta,
                    { r ->
                        if (r == Respuesta.AHORA) navegar.abrirRuta(EntradaSesion.ruta(propuesta?.destino))
                        viewModel.responder(r)
                    },
                    Modifier.padding(horizontal = Medidas.margen),
                )
            }
            if (hayTuDia) {
                item(key = "tu-dia") {
                    TuDia(estado.cards, estado.ringDone, estado.ringTotal, Modifier.padding(horizontal = Medidas.margen))
                }
            }
            if (estado.loading) {
                items(FILAS_HUECO, key = { "hueco-$it" }) { i ->
                    FilaHueco(i, Modifier.padding(horizontal = Medidas.margen))
                }
            }
            if (vacio) {
                item(key = "vacio") { HoyVacio(navegar.crearHabito, Modifier.padding(horizontal = Medidas.margen)) }
            }
            items(filas, key = { it.clave() }) { fila ->
                val indice = filas.indexOf(fila)
                val aparece =
                    Modifier.aparecer(
                        activo =
                            vistoCargando || (fila is FilaHoy.Habito && brotando == fila.card.id) ||
                                (fila is FilaHoy.Tarea && brotando == fila.row.id),
                        retraso = minOf(indice, MAX_ESCALONADAS) * ESCALON_ENTRADA_MS,
                        brotar =
                            (fila is FilaHoy.Habito && brotando == fila.card.id) ||
                                (fila is FilaHoy.Tarea && brotando == fila.row.id),
                    )
                val margen = Modifier.padding(horizontal = Medidas.margen)
                val colocar =
                    Modifier.animateItem(
                        placementSpec = if (reducido) null else tween<IntOffset>(Duraciones.HUNDIR_FILA, easing = Curvas.out),
                    )
                when (fila) {
                    is FilaHoy.Habito -> {
                        val card = fila.card
                        ReorderableItem(reorderState, key = card.id) {
                            FilaHabito(
                                card,
                                alCirculo = { alCirculo(card) },
                                alPaso = { sumar(card) },
                                alExacto = {
                                    gesto(Snd.BROTE, Haptica.CLICK)
                                    exactFor = card
                                },
                                alAbrir = { navegar.abrirFicha(card.id) },
                                alRecaer = { recaerFor = card },
                                modifier = margen.then(aparece),
                                nombreModifier =
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = { gesto(h = Haptica.CLICK) },
                                        onDragStopped = { viewModel.reorder(ordenadas.map { it.id }) },
                                    ),
                            )
                        }
                    }
                    is FilaHoy.Tarea ->
                        FilaTarea(
                            fila.row,
                            estado.today,
                            saliendo = fila.row.id in saliendo,
                            alTerminar = {
                                val row = fila.row
                                viewModel.markTaskDone(row.id)
                                saliendo = saliendo + (row.id to (estado.tasks.indexOfFirst { it.id == row.id }.coerceAtLeast(0) to row))
                                scope.launch {
                                    delay(SALIDA_TAREA_MS)
                                    saliendo = saliendo - row.id
                                }
                            },
                            alEmpezar = { navegar.abrirRuta("focus?taskId=${fila.row.id}") },
                            alHoyNo = { viewModel.postponeTask(fila.row.id) },
                            modifier = margen.then(aparece).then(colocar),
                        )
                    FilaHoy.TareasHechas ->
                        Text(
                            stringResource(R.string.tasks_today_all_done),
                            style = BitoTema.tipos.t16,
                            color = BitoTema.colores.tintaSuave,
                            modifier = margen.then(colocar).testTag("tareas-hechas"),
                        )
                    FilaHoy.VerTareas ->
                        Text(
                            stringResource(R.string.hoy_ver_todas),
                            style = BitoTema.tipos.t16.fuerte(),
                            color = BitoTema.colores.tinta,
                            modifier =
                                margen
                                    .then(colocar)
                                    .heightIn(min = Medidas.toqueMinimo)
                                    .testTag("ver-tareas")
                                    .clickable(onClick = navegar.abrirTareas)
                                    .padding(vertical = Medidas.e12),
                        )
                    is FilaHoy.EnPausa -> SeccionEnPausa(fila.paused, navegar.abrirFicha, margen.then(colocar))
                    is FilaHoy.CerrarDia ->
                        Box(margen.then(colocar).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Boton(
                                stringResource(if (fila.sellado) R.string.today_day_sealed else R.string.today_close_day),
                                navegar.abrirRepaso,
                                Modifier.testTag("cerrar-dia"),
                                estilo = if (fila.sellado) EstiloBoton.NORMAL else EstiloBoton.PRINCIPAL,
                            )
                        }
                }
            }
        }
    }

    HojaTotalExacto(
        abierta = exactFor != null,
        card = exactFor,
        alGuardar = { valor ->
            exactFor?.let { viewModel.setExactToday(it, valor) }
            exactFor = null
        },
        alCerrar = { exactFor = null },
    )
    Dialogo(
        abierto = recaerFor != null,
        titulo = stringResource(R.string.relapse_confirm_title),
        verbo = stringResource(R.string.hoy_registrar),
        alConfirmar = {
            recaerFor?.let { viewModel.recaerHoy(it) }
            recaerFor = null
        },
        alCancelar = { recaerFor = null },
        cuerpo = stringResource(R.string.relapse_confirm_body),
    )
    val dias = estado.pendingSealDays
    val textoSellados = if (dias.isEmpty()) "" else pluralStringResource(R.plurals.hoy_sellados_n, dias.size, dias.size)
    HojaEstuvisteFuera(
        abierta = dias.isNotEmpty() && !sealDismissed,
        dias = dias,
        alSellar = {
            viewModel.sealPendingDays()
            scope.launch { avisos.mostrar(textoSellados) }
        },
        alCerrar = { sealDismissed = true },
    )
}

private const val ENTRADA_SCROLL_MS = 420

/** «Tu día»: «N de M» con la cifra en rodillo y un puntito de agua por hábito, lleno si está hecho. */
@Composable
private fun TuDia(
    cards: List<HabitCardUi>,
    hechos: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val descripcion = stringResource(R.string.hoy_n_de_m, hechos, total)
    Row(
        modifier.fillMaxWidth().testTag("tu-dia").semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.hoy_tu_dia), style = BitoTema.tipos.t24.fuerte(), color = colores.tinta)
        Row(Modifier.padding(start = Medidas.e8), verticalAlignment = Alignment.Bottom) {
            CifraRodillo(hechos, estilo = BitoTema.tipos.t16.fuerte(), color = colores.tintaSuave)
            Text(" " + stringResource(R.string.hoy_de_m, total), style = BitoTema.tipos.t16, color = colores.tintaSuave)
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.take(PUNTOS_MAX).forEach { c ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (c.doneToday) colores.agua else Color.Transparent)
                        .border(1.dp, colores.contorno, CircleShape),
                )
            }
        }
    }
}

/** Hoy sin nada: las parcelas vacías las pinta el jardín; aquí, la hornacina con la semilla y crear el primer hábito. */
@Composable
private fun HoyVacio(
    alCrear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Row(
        modifier.fillMaxWidth().testTag("hoy-vacio"),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La semilla es el arte de la primera etapa del jardín, el que ya va en el APK.
        Box(
            Modifier.size(
                SEMILLA_HORNACINA_ANCHO,
                SEMILLA_HORNACINA_ALTO,
            ).clip(BitoFormas.hornacina).background(colores.hornacina).testTag("hoy-semilla"),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painterResource(GardenArt.plants[0][0].res),
                contentDescription = null,
                modifier = Modifier.padding(bottom = Medidas.e8).width(SEMILLA_ANCHO),
                contentScale = ContentScale.FillWidth,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Text(stringResource(R.string.empty_today_title), style = BitoTema.tipos.t19.fuerte(), color = colores.tinta)
            Text(stringResource(R.string.empty_today_body), style = BitoTema.tipos.t16, color = colores.tintaSuave)
            Boton(stringResource(R.string.create_habit), alCrear, Modifier.padding(top = Medidas.e8), estilo = EstiloBoton.PRINCIPAL)
        }
    }
}

private val SEMILLA_HORNACINA_ANCHO = 84.dp
private val SEMILLA_HORNACINA_ALTO = 96.dp
private val SEMILLA_ANCHO = 60.dp
