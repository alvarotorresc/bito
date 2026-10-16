package com.alvarotc.bito.ui.inicio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.BuildConfig
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.camino.CaminoContenido
import com.alvarotc.bito.ui.camino.CaminoViewModel
import com.alvarotc.bito.ui.cierre.CierreContenido
import com.alvarotc.bito.ui.cierre.CierreViewModel
import com.alvarotc.bito.ui.cierre.rememberEstadoCierre
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.garden.GardenDebugPanel
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.HojaSaldo
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.PremiosEnInicio
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tasks.CreateChoiceSheet
import com.alvarotc.bito.ui.today.ESPERA_CREADO_MS
import com.alvarotc.bito.ui.today.HoyLista
import com.alvarotc.bito.ui.today.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

/** Los avisos del Inicio. Los tests lo proveen para lanzar uno; fuera de ahí el Inicio crea el suyo. */
internal val LocalAvisosInicio = compositionLocalOf<AvisoEstado?> { null }

/** Cuánto se ve cada cosa con [q] (0 Hoy, 1 Camino), spec §7.1. */
internal data class ProgresoCamino(
    /** La lista se funde antes de la mitad. */
    val alfaLista: Float,
    /** Las estadísticas aparecen después de la mitad. */
    val alfaEstadisticas: Float,
    /** El título «Tu camino» aparece en el último 45 %. */
    val alfaTitulo: Float,
    /** La cabecera de Hoy y el + se funden enseguida. */
    val alfaCabecera: Float,
)

internal fun progresoCamino(q: Float): ProgresoCamino =
    ProgresoCamino(
        alfaLista = (1f - q / 0.5f).coerceIn(0f, 1f),
        alfaEstadisticas = ((q - 0.5f) / 0.5f).coerceIn(0f, 1f),
        alfaTitulo = ((q - 0.55f) / 0.45f).coerceIn(0f, 1f),
        alfaCabecera = (1f - q / 0.25f).coerceIn(0f, 1f),
    )

private const val LISTA_BAJA = 0.55f
private const val ESTADISTICAS_SUBEN = 0.5f
private const val DESTACAR_MS = 1_200L
private const val CARICIAS_MS = 1_500L
private const val CONTENTO_MS = 1_200L
private const val TOQUES_CARICIA = 3
private const val CREAR_A_NAVEGAR_MS = 90L
private val AIRE_LISTA = 112.dp
private val BARRA_CAMINO = 56.dp
private val MARGEN_ABAJO = 20.dp
private val SUBIDA_TARJETA = 14.dp
private val AVISO_SOBRE_PILDORA = 88.dp
private val TARJETA_BAJO_CABECERA = 72.dp

// Catálogo 3.8: con el cierre, el jardín y la tierra suben 76 dp (la lista de Hoy se hunde por su cuenta).
private val SUBIDA_CIERRE = 76.dp

/**
 * Consume el desplazamiento hacia arriba hasta que [estado] llegue a [maximo] (el jardín encoge o sube) y
 * lo devuelve cuando la lista está arriba del todo y el dedo sigue bajando.
 */
internal class ConexionEncoger(
    private val estado: MutableFloatState,
    private val maximo: Float,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y >= 0f || estado.floatValue >= maximo) return Offset.Zero
        val nuevo = (estado.floatValue - available.y).coerceAtMost(maximo)
        val usado = nuevo - estado.floatValue
        estado.floatValue = nuevo
        return Offset(0f, -usado)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y <= 0f || estado.floatValue <= 0f) return Offset.Zero
        val nuevo = (estado.floatValue - available.y).coerceAtLeast(0f)
        val usado = estado.floatValue - nuevo
        estado.floatValue = nuevo
        return Offset(0f, usado)
    }
}

/** «Tu camino ›», el enlace de Hoy bajo el jardín. */
@Composable
internal fun EnlaceTuCamino(
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Medidas.margen, vertical = Medidas.e8),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            Modifier
                .testTag("tu-camino")
                .heightIn(min = Medidas.toqueMinimo)
                .clickable(role = Role.Button, onClick = alPulsar)
                .padding(vertical = Medidas.e8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.inicio_tu_camino),
                style = BitoTema.tipos.t16.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = colores.tinta,
            )
            Icon(IconosSistema.CaretRight, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(18.dp))
        }
    }
}

/** El título «Tu camino» que se queda arriba: se asienta en tierra cuando el jardín pasa por debajo. */
@Composable
private fun TituloCamino(
    asentado: () -> Float,
    alfa: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Box(
        modifier
            .fillMaxWidth()
            .height(BARRA_CAMINO)
            .graphicsLayer { this.alpha = alfa() }
            .testTag("camino-titulo"),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = asentado() }
                .background(colores.tAlta)
                .border(Medidas.linea, colores.tAlta2),
        )
        Text(
            stringResource(R.string.inicio_tu_camino),
            style = BitoTema.tipos.t24.copy(fontWeight = FontWeight.Black),
            color = colores.tinta,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = Medidas.margen),
        )
    }
}

private fun Modifier.bajoElJardin(borde: () -> Float): Modifier =
    layout { medible, c ->
        val arriba = borde().roundToInt().coerceIn(0, c.maxHeight)
        val p = medible.measure(Constraints.fixed(c.maxWidth, c.maxHeight - arriba))
        layout(c.maxWidth, c.maxHeight) { p.place(0, arriba) }
    }

/**
 * Hoy y Camino en una sola pantalla sobre un solo jardín. [q] (0 Hoy, 1 Camino) lo mueve un `Animatable`
 * con el muelle `camino`; su destino se guarda, así que Camino sobrevive a ir a otra ruta y volver.
 * La lista de Hoy sale de la composición con q = 1 y Camino con q = 0.
 *
 * El cierre del día también vive aquí (spec §7.4): su `c` (0 Hoy, 1 cierre) esconde la píldora y el +, hunde la
 * lista y sube el jardín, y [CierreContenido] se pinta encima. Lo abren [cierre] (la ruta, sin animar),
 * `abrirRepaso` (el botón de cerrar el día y el efecto de la tarjeta) y se cierra con atrás. [gordo] llega a la
 * lista para abrir la hoja de lo gordo; [alCierreVisto] y [alGordoVisto] apagan cada bandera una vez vista.
 */
@Composable
fun InicioScreen(
    hoy: TodayViewModel,
    camino: CaminoViewModel,
    celebraciones: CelebracionesViewModel,
    navegar: NavegacionInicio,
    creado: String? = null,
    alCreadoVisto: () -> Unit = {},
    enPrimerPlano: Boolean = true,
    registro: RegistroAnclas = remember { RegistroAnclas() },
    cierre: Boolean = false,
    alCierreVisto: () -> Unit = {},
    cierreViewModel: CierreViewModel? = null,
    gordo: Boolean = false,
    alGordoVisto: () -> Unit = {},
) {
    val estado by hoy.uiState.collectAsStateWithLifecycle()
    val estadoCamino by camino.uiState.collectAsStateWithLifecycle()
    val estadoActual = rememberUpdatedState(estado)
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current
    val cierreEstado = rememberEstadoCierre(abiertoAlEmpezar = cierre)
    val cierreAbierto by remember { derivedStateOf { cierreEstado.abierto } }
    val cierreEnPantalla by remember { derivedStateOf { cierreEstado.abierto || cierreEstado.c.value > 0f } }
    val hoyTapado by remember { derivedStateOf { cierreEstado.c.value >= 1f } }

    // El destino se guarda; el Animatable arranca ya en él, sin animar, al volver de otra ruta.
    var destino by rememberSaveable { mutableFloatStateOf(0f) }
    val q = remember { Animatable(destino) }
    val qGeometria: () -> Float = { if (reducido) (if (q.value >= 0.5f) 1f else 0f) else q.value }
    val encogidoPx = remember { mutableFloatStateOf(0f) }
    val subidaPx = remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val scrollCamino = rememberScrollState()

    var crear by remember { mutableStateOf(false) }
    var saldoAbierto by remember { mutableStateOf(false) }
    val avisos = LocalAvisosInicio.current ?: rememberAvisoEstado()
    var llegando by remember { mutableStateOf<GardenSpecial?>(null) }
    var creciendo by remember { mutableStateOf<Int?>(null) }
    var destacar by remember { mutableStateOf<GardenSpecial?>(null) }
    var asomada by remember { mutableStateOf(false) }
    val asomo = animateFloatAsState(if (asomada) 1f else 0f, tween(Duraciones.REDUCIDO), label = "asomo")

    fun ir(a: Float) {
        if (destino == a) return
        destino = a
        gesto(if (a > 0f) Snd.ABRIR else Snd.CERRAR, if (a > 0f) Haptica.RISE else Haptica.FALL)
        scope.launch {
            if (reducido) q.animateTo(a, tween(Duraciones.REDUCIDO)) else q.animateTo(a, Muelles.camino<Float>(umbral = 0.001f))
        }
    }

    fun verEnJardin(especial: GardenSpecial?) {
        ir(1f)
        scope.launch {
            launch { animate(subidaPx.floatValue, 0f, animationSpec = tween(460, easing = Curvas.out)) { v, _ -> subidaPx.floatValue = v } }
            launch { scrollCamino.animateScrollTo(0, tween(460, easing = Curvas.out)) }
            destacar = especial
            delay(DESTACAR_MS)
            destacar = null
        }
    }

    fun moverCierre(a: Float) {
        scope.launch {
            if (reducido) {
                cierreEstado.c.animateTo(a, tween(Duraciones.REDUCIDO))
            } else {
                cierreEstado.c.animateTo(a, Muelles.camara<Float>(umbral = 0.001f))
            }
        }
    }
    // Cerrar el día (el botón y, desde la Tarea 23, el efecto de la tarjeta) abre el cierre aquí dentro.
    val navegarHoy =
        remember(navegar, cierreViewModel) { if (cierreViewModel == null) navegar else navegar.copy(abrirRepaso = { moverCierre(1f) }) }

    BackHandler(enabled = destino > 0f) { ir(0f) }
    // Después del de Camino: con el cierre abierto, atrás lo cierra a él.
    BackHandler(enabled = cierreAbierto) { moverCierre(0f) }
    // La ruta pide el cierre: se abre sin animar (si no lo abrió ya al empezar) y la bandera se apaga.
    LaunchedEffect(cierre) {
        if (cierre) {
            if (!cierreEstado.abierto) cierreEstado.c.snapTo(1f)
            alCierreVisto()
        }
    }
    // Al abrir, el jardín arranca de noche si el día ya está sellado; se decide con el estado ya cargado.
    LaunchedEffect(cierreAbierto) {
        if (cierreAbierto) {
            val cargado = snapshotFlow { estadoActual.value }.first { !it.loading }
            cierreEstado.ajustarNoche(cargado.todaySealed)
        }
    }
    // Lo sellado que sabe el jardín: el estado en carga no lo cambia (se queda el último cargado).
    var selladoCargado by remember { mutableStateOf(false) }
    LaunchedEffect(estado.loading, estado.todaySealed) { if (!estado.loading) selladoCargado = estado.todaySealed }
    // El estado en carga trae el jardín inicial, no el tuyo: si contara como anterior, cada arranque celebraría todo el jardín.
    LaunchedEffect(estado.garden, estado.loading) { if (!estado.loading) celebraciones.jardin(estado.garden) }
    val enHoyQuieto by remember { derivedStateOf { q.value == 0f } }
    DisposableEffect(enPrimerPlano, enHoyQuieto, cierreAbierto) {
        // Primero el cierre y luego Hoy: así no se suelta un momento que el cierre retiene en el mismo paso.
        celebraciones.retenerCierre(cierreAbierto)
        celebraciones.enHoy(enPrimerPlano && enHoyQuieto)
        onDispose { celebraciones.enHoy(false) }
    }
    LaunchedEffect(Unit) { snapshotFlow { q.value == 0f }.collect { if (it) subidaPx.floatValue = 0f } }

    // Tres caricias en 1,5 s alegran a Habi (el mismo cálculo que HabiScreen).
    val caricias = remember { ArrayDeque<Long>() }
    var contento by remember { mutableStateOf(false) }
    var pulso by remember { mutableIntStateOf(0) }
    LaunchedEffect(pulso) {
        if (pulso > 0) {
            contento = true
            delay(CONTENTO_MS)
            contento = false
        }
    }

    // Volver de crear: el Inicio guarda su copia, porque la lista borra la señal al desplazarse.
    var creadoVisto by remember { mutableStateOf<String?>(null) }
    var salto by remember { mutableIntStateOf(0) }
    var fiesta by remember { mutableIntStateOf(0) }
    var frase by remember { mutableStateOf<Int?>(null) }
    var enTarjeta by remember { mutableStateOf(false) }
    var puntoGlobito by remember { mutableStateOf<Offset?>(null) }
    var origen by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(creado) { if (creado != null) creadoVisto = creado }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(BitoTema.colores.tAlta)
            .onGloballyPositioned { origen = it.positionInRoot() }
            .testTag("inicio"),
    ) {
        val alto = constraints.maxHeight.toFloat()
        val ancho = constraints.maxWidth.toFloat()
        val escenaAlto = ancho / GardenArt.SCENE_ASPECT
        val extra = with(densidad) { MedidasInicio.ARRIBA_CAMINO.toPx() }
        val altoCamino = altoJardinCamino(alto, escenaAlto, extra)
        val maxEncoger = (MedidasInicio.ALTO_HOY - MedidasInicio.ALTO_MIN) * alto
        val maxSubida = (altoCamino - with(densidad) { BARRA_CAMINO.toPx() }).coerceAtLeast(1f)
        val encogido = { (encogidoPx.floatValue / maxEncoger).coerceIn(0f, 1f) }
        val borde = { bordeJardin(qGeometria(), encogido(), alto, escenaAlto, extra, subidaPx.floatValue) }
        val conexionHoy = remember(maxEncoger) { ConexionEncoger(encogidoPx, maxEncoger) }
        val conexionCamino = remember(maxSubida) { ConexionEncoger(subidaPx, maxSubida) }
        val hoyVisible by remember { derivedStateOf { q.value < 1f } }
        val caminoVisible by remember { derivedStateOf { q.value > 0f } }
        val cabeceraVisible by remember { derivedStateOf { q.value < 0.25f && (encogido() < 0.85f || asomada) } }
        val subidaTarjeta = with(densidad) { SUBIDA_TARJETA.roundToPx() }
        val masActivo by remember { derivedStateOf { q.value < 0.25f } }

        LaunchedEffect(creadoVisto) {
            val id = creadoVisto ?: return@LaunchedEffect
            snapshotFlow { estadoActual.value }.first { !it.loading }
            // El estado cargado puede ser viejo (Hoy dejó de escuchar) o la tarea aún no estar escrita: se espera al id, con tope.
            val e =
                withTimeoutOrNull(ESPERA_CREADO_MS) {
                    snapshotFlow { estadoActual.value }.first { s -> s.cards.any { it.id == id } || s.tasks.any { it.id == id } }
                }
            if (e != null) {
                if (!reducido) salto++
                val rect = registro.posiciones[ClavesAncla.HABI]
                val visible = rect != null && qGeometria() == 0f && rect.center.y > 0f && rect.center.y < borde()
                puntoGlobito = if (visible) Offset(rect!!.center.x, rect.top) else null
                enTarjeta = !visible
                frase = HabiVoice.bubbleRes(e.spec.mood, e.spec.personality)
                delay(Duraciones.GLOBITO.toLong())
                frase = null
            }
            creadoVisto = null
        }

        CompositionLocalProvider(LocalAnclas provides registro) {
            Box(Modifier.fillMaxSize()) {
                if (caminoVisible) {
                    Box(
                        Modifier
                            .bajoElJardin(borde)
                            .graphicsLayer {
                                val p = progresoCamino(q.value)
                                translationY = if (reducido) 0f else ESTADISTICAS_SUBEN * alto * (1f - q.value)
                                alpha = p.alfaEstadisticas
                            }.nestedScroll(conexionCamino),
                    ) {
                        CaminoContenido(estadoCamino, scrollCamino, alVerEnJardin = ::verEnJardin, modifier = Modifier.fillMaxSize())
                    }
                }
                if (hoyVisible) {
                    // inicio-hoy es de InicioScreen; inicio-camino lo pone CaminoContenido
                    Box(
                        Modifier
                            .bajoElJardin(borde)
                            .graphicsLayer {
                                val p = progresoCamino(q.value)
                                translationY = if (reducido) 0f else LISTA_BAJA * alto * q.value
                                alpha = p.alfaLista
                            }.nestedScroll(conexionHoy)
                            // Tapada del todo por el cierre, la lista sale del árbol de accesibilidad.
                            .then(if (hoyTapado) Modifier.clearAndSetSemantics {} else Modifier)
                            .testTag("inicio-hoy"),
                    ) {
                        HoyLista(
                            hoy,
                            navegarHoy,
                            listState,
                            avisos,
                            encabezado = {
                                Column {
                                    EnlaceTuCamino({ ir(1f) })
                                    if (BuildConfig.DEBUG) {
                                        GardenDebugPanel(
                                            current = estado.garden,
                                            onForce = hoy::forzarJardin,
                                            modifier = Modifier.padding(horizontal = Medidas.margen),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = AIRE_LISTA),
                            creado = creado,
                            alCreadoVisto = alCreadoVisto,
                            abrirGordo = gordo,
                            alGordoVisto = alGordoVisto,
                            hundida = { cierreEstado.c.value.coerceIn(0f, 1f) },
                        )
                    }
                }
                JardinCompartido(
                    estado.garden,
                    estado.spec,
                    q = qGeometria,
                    encogido = encogido,
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            if (!reducido) translationY = -SUBIDA_CIERRE.toPx() * cierreEstado.c.value.coerceIn(0f, 1f)
                        },
                    contento = contento,
                    animado = enPrimerPlano,
                    alTocarHabi = {
                        if (!contento) {
                            val ahora = System.currentTimeMillis()
                            caricias.addLast(ahora)
                            while (caricias.isNotEmpty() && ahora - caricias.first() > CARICIAS_MS) caricias.removeFirst()
                            if (caricias.size >= TOQUES_CARICIA) {
                                caricias.clear()
                                pulso++
                                hoy.alAcariciarHabi()
                            } else {
                                hoy.alTocarHabi()
                            }
                        }
                    },
                    alTocarFondo = { ir(1f) },
                    llegando = llegando,
                    creciendo = creciendo,
                    destacar = destacar,
                    saltoHabi = salto,
                    fiesta = fiesta,
                    subida = { subidaPx.floatValue },
                    // Ruling P22: con el cierre abierto sigue al anochecer; fuera, de noche solo con el día sellado.
                    noche = { cierreEstado.nocheDelJardin(selladoCargado) },
                )
                if (caminoVisible) {
                    TituloCamino(
                        asentado = { (subidaPx.floatValue / maxSubida).coerceIn(0f, 1f) },
                        alfa = { progresoCamino(q.value).alfaTitulo },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                if (cabeceraVisible) {
                    CabeceraHoy(
                        estado.today,
                        estado.saldo,
                        alSaldo = { saldoAbierto = true },
                        alAjustes = navegar.abrirAjustes,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .graphicsLayer {
                                    val base = (1f - encogido() / 0.85f).coerceIn(0f, 1f) * progresoCamino(q.value).alfaCabecera
                                    alpha = maxOf(base, asomo.value)
                                    translationY = -0.6f * encogidoPx.floatValue * (1f - asomo.value)
                                },
                    )
                }

                // La píldora y el +: en línea, sin tocarse aunque el ancho sea corto.
                val anchoPildora = with(densidad) { ANCHO_PILDORA.toPx() }
                val mas = with(densidad) { (Medidas.mas + Medidas.margen).toPx() }
                val aire = with(densidad) { Medidas.margen.toPx() }
                val xPildora = min((ancho - anchoPildora) / 2f, ancho - mas - aire - anchoPildora).coerceAtLeast(0f)
                // Con el cierre, la píldora y el + se van: el cierre tiene su propia salida.
                if (!cierreEnPantalla) {
                    Pildora(
                        q = qGeometria,
                        alHoy = { ir(0f) },
                        alCamino = { ir(1f) },
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .offset { IntOffset(xPildora.roundToInt(), 0) }
                                .padding(bottom = MARGEN_ABAJO),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Medidas.margen, bottom = MARGEN_ABAJO)
                            .graphicsLayer {
                                translationY = -avisos.desplazamiento.toPx()
                                alpha = progresoCamino(q.value).alfaCabecera
                            },
                    ) {
                        BotonMas(activo = masActivo, alPulsar = { crear = true })
                    }
                }
                // Sobre Hoy y fuera de las listas (el cierre trae su propio scroll), pero bajo los premios: las monedas
                // de las filas y las vetas de la cadena se ven encima de la tierra del cierre; avisos y globitos, también.
                if (cierreEnPantalla && cierreViewModel != null) {
                    CierreContenido(
                        cierreEstado,
                        cierreViewModel,
                        alCerrar = { moverCierre(0f) },
                        alBuenasNoches = { moverCierre(0f) },
                        modifier = Modifier.fillMaxSize(),
                        celebraciones = celebraciones,
                    )
                }
                PremiosEnInicio(
                    celebraciones,
                    registro,
                    costuraY = borde,
                    alVerEnJardin = ::verEnJardin,
                    alJardin = { l, c ->
                        llegando = l
                        creciendo = c
                    },
                    alAsomarCabecera = { asomada = it },
                    alFiesta = { fiesta++ },
                    personalidad = estado.spec.personality,
                    nombre = estado.userName,
                    modifier = Modifier.fillMaxSize().testTag("premios"),
                )
                AvisoHost(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = AVISO_SOBRE_PILDORA - Medidas.margen))
                val texto = frase?.let { stringResource(it) }
                val punto = puntoGlobito
                if (punto != null) {
                    Box(
                        Modifier
                            .offset { IntOffset((punto.x - origen.x).roundToInt(), (punto.y - origen.y).roundToInt()) }
                            .size(1.dp),
                    ) {
                        Globito(visible = texto != null && !enTarjeta, texto = texto.orEmpty(), alIrse = { frase = null })
                    }
                }
                Box(Modifier.align(Alignment.TopCenter).padding(top = TARJETA_BAJO_CABECERA)) {
                    AnimatedVisibility(
                        visible = texto != null && enTarjeta,
                        enter =
                            fadeIn(tween(Duraciones.COLOR)) +
                                scaleIn(Muelles.hoja(reducido), initialScale = 0.96f) +
                                slideInVertically(Muelles.hoja(reducido, IntOffset.VisibilityThreshold)) { subidaTarjeta },
                        exit = fadeOut(tween(Duraciones.SALIR)),
                    ) {
                        Text(
                            texto.orEmpty(),
                            style = BitoTema.tipos.t16,
                            color = BitoTema.colores.tinta,
                            modifier =
                                Modifier
                                    .padding(horizontal = Medidas.margen)
                                    .background(BitoTema.colores.canto, BitoFormas.globo)
                                    .border(Medidas.linea, BitoTema.colores.contorno, BitoFormas.globo)
                                    .padding(Medidas.e12)
                                    .testTag("habi-asoma"),
                        )
                    }
                }
            }
        }
    }
    HojaSaldo(
        saldoAbierto,
        estado.saldo,
        estado.movimientos,
        estado.economia,
        alIrTienda = {
            saldoAbierto = false
            navegar.abrirTienda()
        },
        alCerrar = { saldoAbierto = false },
    )
    if (crear) {
        CreateChoiceSheet(
            onHabit = {
                crear = false
                scope.launch {
                    delay(CREAR_A_NAVEGAR_MS)
                    navegar.crearHabito()
                }
            },
            onTask = {
                crear = false
                scope.launch {
                    delay(CREAR_A_NAVEGAR_MS)
                    navegar.crearTarea()
                }
            },
            onFocus = {
                crear = false
                navegar.abrirRuta("focus")
            },
            onBreathing = {
                crear = false
                navegar.abrirRuta("breathing")
            },
            onDismiss = { crear = false },
        )
    }
}

/** El + redondo de 56 dp, de agua y con sombra flotante: abre la hoja de crear. */
@Composable
private fun BotonMas(
    activo: Boolean,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    Box(
        Modifier
            .testTag("boton-crear")
            .size(Medidas.mas)
            .shadow(12.dp, CircleShape, ambientColor = colores.sombraFlota, spotColor = colores.sombraFlota)
            .background(colores.agua, CircleShape)
            .border(Medidas.linea, colores.contorno, CircleShape)
            .clickable(enabled = activo, role = Role.Button, onClickLabel = stringResource(R.string.inicio_crear), onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            IconosSistema.Plus,
            contentDescription = stringResource(R.string.nav_new_habit),
            tint = colores.contorno,
            modifier = Modifier.size(28.dp),
        )
    }
}
