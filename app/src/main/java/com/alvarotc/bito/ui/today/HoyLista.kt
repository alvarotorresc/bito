@file:OptIn(ExperimentalFoundationApi::class)

package com.alvarotc.bito.ui.today

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
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
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.propuestas.Efecto
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
private const val ALFA_ESPERAN = 0.6f
private val HUNDIDA_CIERRE = 48.dp
private const val ESCALON_HUNDIDA_MS = 25
private const val ESCALONES_HUNDIDA = 7
private const val ESCALON_ENTRADA_MS = 60
private const val MAX_ESCALONADAS = 8
private const val SALIDA_TAREA_MS = 340L
private const val BROTE_VIGENCIA_MS = 1500L

/** Lo que se espera, al volver de crear, a que el id nuevo llegue al estado de Hoy (viejo o aún sin escribir). */
internal const val ESPERA_CREADO_MS = 1_500L
private const val PUNTOS_MAX = 12

/**
 * Cuánto se ha hundido la fila [indice] cuando la lista lleva [progreso] (0 nada, 1 del todo) de su hundimiento. Cada
 * fila tarda [Duraciones.HUNDIR_FILA] y la siguiente sale [ESCALON_HUNDIDA_MS] después; el escalón se detiene en
 * [ESCALONES_HUNDIDA] filas para que una lista larga no tarde de más. El progreso lo da un muelle, no un reloj: los
 * milisegundos son la proporción del escalón, no una duración exacta.
 */
internal fun hundidaDeFila(
    progreso: Float,
    indice: Int,
): Float {
    val total = (Duraciones.HUNDIR_FILA + ESCALON_HUNDIDA_MS * ESCALONES_HUNDIDA).toFloat()
    val retraso = (ESCALON_HUNDIDA_MS * indice.coerceIn(0, ESCALONES_HUNDIDA)).toFloat()
    return ((progreso * total - retraso) / Duraciones.HUNDIR_FILA).coerceIn(0f, 1f)
}

/** Lo que hunde una fila: baja y se apaga (solo se apaga con movimiento reducido). */
private fun Modifier.hundirFila(
    indice: Int,
    hundida: () -> Float,
    reducido: Boolean,
): Modifier =
    graphicsLayer {
        val k = hundidaDeFila(hundida().coerceIn(0f, 1f), indice)
        if (!reducido) translationY = HUNDIDA_CIERRE.toPx() * k
        alpha = 1f - k
    }

/** Ajustes donde se concede la alarma exacta de este paquete (API 31 o más). */
internal fun intentAlarmasExactas(paquete: String): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$paquete"))

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
    // El aviso de la mañana pide la hoja de lo gordo: la lista la abre y avisa de que lo vio.
    abrirGordo: Boolean = false,
    alGordoVisto: () -> Unit = {},
    // Lo que se ha hundido la lista porque se abre el cierre (0 nada, 1 del todo); se lee al dibujar. Cada fila
    // lo recoge con su escalón de 25 ms ([hundidaDeFila]).
    hundida: () -> Float = { 0f },
    // El hábito cuya veta de racha dura ahora: su «día N» sube y va en negrita.
    vetaRachaDe: String? = null,
    // Lo que pide al jardín del Inicio: el salto de Habi al resolver el permiso (sin voz) y, tras «Hecho» en la vuelta,
    // la voz del salto que el jardín ya da al revivir (con voz).
    alSaltoHabi: (conVoz: Boolean) -> Unit = {},
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val logged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val taskDone by viewModel.lastTaskDone.collectAsStateWithLifecycle()
    val propuesta by viewModel.propuesta.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gesto = rememberGesto()
    val contexto = LocalContext.current
    val registro = LocalAnclas.current
    val reducido = BitoTema.reducido
    var exactFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var recaerFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var sealDismissed by rememberSaveable { mutableStateOf(false) }
    var limitePendiente by remember { mutableStateOf(false) }
    var brotando by remember { mutableStateOf<String?>(null) }
    // Un hábito recién creado que cae en «Esperan sin prisa»: la sección ya está a la vista, falta su fila.
    var traerAVista by remember { mutableStateOf<String?>(null) }
    var gordoAbierto by remember { mutableStateOf(false) }
    // Quien pide la hoja (el aviso de la mañana, la tarjeta, la etiqueta): se abre cuando Hoy está cargado.
    var gordoPedido by remember { mutableIntStateOf(0) }
    var horaAbierta by remember { mutableStateOf(false) }
    var horaPedida by remember { mutableStateOf(Efecto.ElegirHora(0, MINUTOS_DIA - 1, 0)) }
    // Resuelto el permiso, la tarjeta pasa a agua clara y Habi salta (catálogo 3.10): todo en el mismo fotograma.
    var aguaClara by remember { mutableIntStateOf(0) }
    val saltar by rememberUpdatedState(alSaltoHabi)
    val permisoResuelto = {
        gesto(Snd.LLEGADA, Haptica.CONFIRM)
        aguaClara++
        saltar(false)
    }
    val pedirAvisos =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) permisoResuelto()
        }
    val pedirAlarmas =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (alarmasExactasConcedidas(contexto)) permisoResuelto()
        }
    // La entrada escalonada solo ocurre al cargar: se apaga pasada su ventana y las filas que vuelven por scroll no la repiten.
    var vistoCargando by remember { mutableStateOf(false) }
    if (estado.loading) vistoCargando = true
    LaunchedEffect(estado.loading) {
        if (!estado.loading && vistoCargando) {
            delay((MAX_ESCALONADAS * ESCALON_ENTRADA_MS + Duraciones.BROTAR_FILA + 100).toLong())
            vistoCargando = false
        }
    }
    LaunchedEffect(abrirGordo) {
        if (abrirGordo) {
            gordoPedido++
            alGordoVisto()
        }
    }
    // Nada se siembra con el estado de relleno: la hoja se abre con Hoy cargado. Un estado viejo de `WhileSubscribed`
    // también está cargado; para ese, la hoja vuelve a sembrar con lo que llegue mientras no se toque.
    LaunchedEffect(gordoPedido) {
        if (gordoPedido == 0) return@LaunchedEffect
        snapshotFlow { estado }.first { !it.loading }
        gordoAbierto = true
    }
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            when (efecto) {
                Efecto.Ninguno -> Unit
                is Efecto.Sesion -> navegar.abrirRuta(EntradaSesion.ruta(efecto.destino))
                Efecto.AbrirCierre -> navegar.abrirRepaso()
                Efecto.AbrirLoGordo -> gordoPedido++
                is Efecto.ElegirHora -> {
                    horaPedida = efecto
                    horaAbierta = true
                }
                // Editar el objetivo de un hábito se pide desde su ficha, no desde Hoy.
                is Efecto.EditarHabito -> Unit
                is Efecto.PedirPermiso ->
                    if (efecto.alarmas) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching { pedirAlarmas.launch(intentAlarmasExactas(contexto.packageName)) }
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
            }
        }
    }
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
    val textoCerrado = stringResource(R.string.habla_dia_cerrado)
    val textoDuerme = stringResource(R.string.habla_habi_duerme)
    val sellado = estado.todaySealed
    val textoTarea = stringResource(HabiVoice.taskDoneRes(estado.spec.personality), estado.userName.ifBlank { respaldo })

    // Habi duerme con el día sellado: tocar a Habi solo da un tick y avisa.
    LaunchedEffect(viewModel) {
        viewModel.habiDuerme.collect {
            gesto(h = Haptica.TICK)
            scope.launch { avisos.mostrar(textoDuerme) }
        }
    }

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
    val abrirLoGordo: () -> Unit = { gordoPedido++ }
    val gordoDe = { tipo: TipoRefGordo, id: String -> RefGordo(tipo, id) in estado.gordo }
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
                (it is FilaHoy.Habito && it.card.id == creado) || (it is FilaHoy.Tarea && it.row.id == creado) ||
                    (it is FilaHoy.Esperan && it.cards.any { c -> c.id == creado })
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
            if (filasDe(cargado).getOrNull(posicion) is FilaHoy.Esperan) traerAVista = creado
        }
        alCreadoVisto()
    }

    // Con el día sellado las filas no se marcan: tocarlas solo dice que el día está cerrado.
    fun avisarCerrado() {
        gesto(h = Haptica.TICK)
        scope.launch { avisos.mostrar(textoCerrado) }
    }

    fun sumar(card: HabitCardUi) {
        if (sellado) return avisarCerrado()
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
        if (sellado) return avisarCerrado()
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

    @Composable
    fun FilaDeHabito(
        card: HabitCardUi,
        modifier: Modifier,
        nombreModifier: Modifier = Modifier,
    ) {
        FilaHabito(
            card,
            alCirculo = { alCirculo(card) },
            alPaso = { sumar(card) },
            alExacto = {
                if (sellado) {
                    avisarCerrado()
                } else {
                    gesto(Snd.BROTE, Haptica.CLICK)
                    exactFor = card
                }
            },
            alAbrir = { navegar.abrirFicha(card.id) },
            alRecaer = { if (sellado) avisarCerrado() else recaerFor = card },
            modifier = modifier,
            nombreModifier = nombreModifier,
            rachaViva = vetaRachaDe == card.id,
            esGordo = gordoDe(TipoRefGordo.HABITO, card.id),
            alGordo = abrirLoGordo,
        )
    }

    Box(modifier) {
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
                    { r -> viewModel.responder(r) },
                    Modifier.padding(horizontal = Medidas.margen).hundirFila(0, hundida, reducido),
                    alSaltoHabi = { saltar(true) },
                    aguaClara = aguaClara,
                )
            }
            if (hayTuDia) {
                item(key = "tu-dia") {
                    TuDia(
                        estado.cards,
                        estado.ringDone,
                        estado.ringTotal,
                        Modifier.padding(horizontal = Medidas.margen).hundirFila(1, hundida, reducido),
                    )
                }
            }
            if (estado.loading) {
                items(FILAS_HUECO, key = { "hueco-$it" }) { i ->
                    FilaHueco(i, Modifier.padding(horizontal = Medidas.margen).hundirFila(i + 2, hundida, reducido))
                }
            }
            if (vacio) {
                item(
                    key = "vacio",
                ) { HoyVacio(navegar.crearHabito, Modifier.padding(horizontal = Medidas.margen).hundirFila(2, hundida, reducido)) }
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
                val margen = Modifier.padding(horizontal = Medidas.margen).hundirFila((if (hayTuDia) 2 else 1) + indice, hundida, reducido)
                val colocar =
                    Modifier.animateItem(
                        placementSpec = if (reducido) null else tween<IntOffset>(Duraciones.HUNDIR_FILA, easing = Curvas.out),
                    )
                when (fila) {
                    is FilaHoy.Habito -> {
                        val card = fila.card
                        ReorderableItem(reorderState, key = card.id) {
                            FilaDeHabito(
                                card,
                                margen.then(aparece),
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = { gesto(h = Haptica.CLICK) },
                                    onDragStopped = { viewModel.reorder(ordenadas.map { it.id }) },
                                ),
                            )
                        }
                    }
                    is FilaHoy.Esperan ->
                        Column(
                            margen.then(colocar).testTag("esperan-sin-prisa"),
                            verticalArrangement = Arrangement.spacedBy(Medidas.entreFilas),
                        ) {
                            Text(
                                stringResource(R.string.habla_esperan_sin_prisa),
                                style = BitoTema.tipos.t14.fuerte(),
                                color = BitoTema.colores.tintaSuave,
                            )
                            // Por id: al marcar una, la fila sale de la sección y las demás no heredan su estado.
                            fila.cards.forEach { card ->
                                key(card.id) {
                                    val traer = remember { BringIntoViewRequester() }
                                    LaunchedEffect(traerAVista) {
                                        if (traerAVista == card.id) {
                                            traer.bringIntoView()
                                            traerAVista = null
                                        }
                                    }
                                    val brota = brotando == card.id
                                    FilaDeHabito(
                                        card,
                                        Modifier
                                            .bringIntoViewRequester(traer)
                                            .graphicsLayer { alpha = ALFA_ESPERAN }
                                            .aparecer(activo = brota, retraso = 0, brotar = brota),
                                    )
                                }
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
                            manana = sellado,
                            esGordo = gordoDe(TipoRefGordo.TAREA, fila.row.id),
                            alGordo = abrirLoGordo,
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

    HojaLoGordo(
        abierta = gordoAbierto,
        candidatos = estado.candidatosGordo,
        sugerencia = estado.sugerenciaGordo,
        elegidos = estado.gordo,
        alGuardar = { refs ->
            gordoAbierto = false
            viewModel.guardarGordo(refs)
        },
        alCerrar = { gordoAbierto = false },
        alAvisar = { texto -> scope.launch { avisos.mostrar(texto) } },
    )
    HojaOtraHora(
        abierta = horaAbierta,
        desde = horaPedida.desde,
        hasta = horaPedida.hasta,
        actual = horaPedida.actual,
        alGuardar = { minuto ->
            horaAbierta = false
            viewModel.responder(Respuesta.OTRA_HORA, minuto)
        },
        alCerrar = { horaAbierta = false },
    )
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
private const val MINUTOS_DIA = 24 * 60

/** Si el sistema ya deja a la app programar alarmas exactas (antes de la API 31 siempre). */
private fun alarmasExactasConcedidas(contexto: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        contexto.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

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
