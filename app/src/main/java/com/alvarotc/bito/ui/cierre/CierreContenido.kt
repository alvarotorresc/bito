package com.alvarotc.bito.ui.cierre

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habla.BolsasRecursos
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.inicio.MedidasInicio
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.HojaTotalExacto
import com.alvarotc.bito.ui.today.TarjetaHabi
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Lo que la cámara sube el jardín al abrir el cierre (el Inicio lo mueve; aquí solo se deja sitio). */
private val SUBIDA_JARDIN = 76.dp

/** La luz de la tarde que llega sobre el jardín: un tinte cálido multiplicado por [tarde]. No tiene token en el sistema. */
private val TINTE_TARDE = Color(0x33E39B4A)

private const val TARDE_ENTRA_MS = 900
private const val TARDE_SALE_MS = 600
private const val BLOQUE_MS = 280
private const val ESCALON_BLOQUE_MS = 45
private const val PIE_MS = 300
private const val MAX_ESCALONES = 8
private val DESPLAZA_BLOQUE = 12.dp
private val DESPLAZA_PIE = 64.dp

/**
 * Lo que se ve con el cierre abierto, encima del Inicio (spec §7.4, catálogo 3.8). El jardín se queda arriba: el
 * cierre pinta la tierra de abajo, con la cabecera «Hoy: N de M», el comentario de Habi, las filas que quedan, la
 * línea de las tareas de mañana, «¿Qué tal el día?» y, en el pie, «Buenas noches». Cada apertura (cada montaje)
 * empieza limpia. La raíz se queda los toques: lo que hay debajo (Hoy, el jardín) no los recibe.
 *
 * Al abrir llega la luz de la tarde (900 ms), los bloques brotan con 45 ms entre ellos y el pie sube; si se abre
 * con la cámara ya arriba (la ruta, el enlace) no suena. Volver (✕ o atrás) deja ir la tarde en 600 ms.
 *
 * - [alCerrar]: ✕ o atrás sin sellar; el Inicio baja la cámara a Hoy.
 * - [alBuenasNoches]: el día se ha sellado desde aquí; el Inicio vuelve a Hoy.
 * - [celebraciones]: la cola de las celebraciones, que el Inicio retiene mientras el cierre está abierto (ruling P23).
 */
@Composable
fun CierreContenido(
    estado: EstadoCierre,
    viewModel: CierreViewModel,
    alCerrar: () -> Unit,
    alBuenasNoches: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") celebraciones: CelebracionesViewModel? = null,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    var exactoDe by remember { mutableStateOf<HabitCardUi?>(null) }
    var recaidaDe by remember { mutableStateOf<HabitCardUi?>(null) }

    val tarde = remember { Animatable(0f) }
    val entrada = remember { Animatable(0f) }
    val pie = remember { Animatable(0f) }
    // Cada apertura empieza limpia. Si ya está arriba (la ruta) no hay descenso que oír.
    LaunchedEffect(viewModel) {
        viewModel.reiniciar()
        if (estado.c.value < 1f) gesto(Snd.DESCENSO, Haptica.TICK)
    }
    LaunchedEffect(Unit) {
        launch { tarde.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else TARDE_ENTRA_MS, easing = Curvas.out)) }
        launch { entrada.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else DURACION_ENTRADA, easing = Curvas.out)) }
        launch { pie.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else PIE_MS, easing = Curvas.drawer)) }
    }

    fun cerrar() {
        gesto(Snd.ASCENSO, Haptica.LOW)
        scope.launch { tarde.animateTo(0f, tween(if (reducido) Duraciones.REDUCIDO else TARDE_SALE_MS, easing = Curvas.out)) }
        alCerrar()
    }
    BackHandler { cerrar() }

    fun buenasNoches() {
        val escritura = viewModel.buenasNoches()
        if (escritura != null || ui.sellado) alBuenasNoches()
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = estado.c.value.coerceIn(0f, 1f) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }.testTag("cierre"),
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind { drawRect(TINTE_TARDE.copy(alpha = TINTE_TARDE.alpha * tarde.value)) },
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val arriba = (maxHeight * MedidasInicio.ALTO_HOY - SUBIDA_JARDIN).coerceAtLeast(0.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = arriba)
                    .background(colores.tAlta),
            ) {
                CuerpoCierre(
                    ui = ui,
                    entrada = { entrada.value },
                    viewModel = viewModel,
                    alCerrar = ::cerrar,
                    alExacto = { exactoDe = it },
                    alRecaer = { recaidaDe = it },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = pie.value
                            if (!reducido) translationY = (1f - pie.value) * DESPLAZA_PIE.toPx()
                        }.navigationBarsPadding()
                        .padding(horizontal = Medidas.margen, vertical = Medidas.e12),
                ) {
                    Boton(
                        stringResource(R.string.cierre_buenas_noches),
                        { buenasNoches() },
                        Modifier.fillMaxWidth().testTag("cierre-buenas-noches"),
                        estilo = EstiloBoton.PRINCIPAL,
                        alto = Medidas.botonHoja,
                    )
                }
            }
        }
    }

    HojaTotalExacto(
        abierta = exactoDe != null,
        card = exactoDe,
        alGuardar = { valor ->
            exactoDe?.let { viewModel.setExact(it, valor) }
            exactoDe = null
        },
        alCerrar = { exactoDe = null },
    )
    Dialogo(
        abierto = recaidaDe != null,
        titulo = stringResource(R.string.relapse_confirm_title),
        verbo = stringResource(R.string.hoy_registrar),
        alConfirmar = {
            recaidaDe?.let {
                gesto(Snd.VACIAR, Haptica.LOW)
                viewModel.logRelapse(it)
            }
            recaidaDe = null
        },
        alCancelar = { recaidaDe = null },
        cuerpo = stringResource(R.string.relapse_confirm_body),
    )
}

/** Lo que dura la entrada entera: el último bloque empieza tras sus escalones y brota en [BLOQUE_MS]. */
private const val DURACION_ENTRADA = BLOQUE_MS + ESCALON_BLOQUE_MS * MAX_ESCALONES

/** Cuánto ha brotado el bloque [indice] (0 a 1) con la entrada en [t] (0 a 1 sobre toda la duración). */
private fun brotado(
    t: Float,
    indice: Int,
    reducido: Boolean,
): Float {
    if (reducido) return t
    val ms = t * DURACION_ENTRADA - minOf(indice, MAX_ESCALONES) * ESCALON_BLOQUE_MS
    return Curvas.out.transform((ms / BLOQUE_MS).coerceIn(0f, 1f))
}

@Composable
private fun CuerpoCierre(
    ui: CierreUiState,
    entrada: () -> Float,
    viewModel: CierreViewModel,
    alCerrar: () -> Unit,
    alExacto: (HabitCardUi) -> Unit,
    alRecaer: (HabitCardUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido

    fun Modifier.brota(indice: Int): Modifier =
        graphicsLayer {
            val b = brotado(entrada(), indice, reducido)
            alpha = b
            if (!reducido) translationY = (1f - b) * DESPLAZA_BLOQUE.toPx()
        }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Medidas.margen, vertical = Medidas.e8),
        verticalArrangement = Arrangement.spacedBy(Medidas.entreFilas),
    ) {
        item(key = "cabecera") { CabeceraCierre(ui, alCerrar, Modifier.brota(0)) }
        item(key = "comentario") {
            val vacio = !ui.loading && ui.filas.isEmpty()
            TarjetaHabi(
                propuesta = ui.comentario?.copy(acciones = emptyList()),
                alResponder = {},
                modifier = Modifier.brota(1).then(if (vacio) Modifier.testTag("cierre-vacio") else Modifier),
            )
        }
        items(ui.filas, key = { it.id }) { card ->
            val indice = 2 + ui.filas.indexOf(card)
            FilaCierre(
                card = card,
                decidida = card.id in ui.decididas,
                alHecho = { viewModel.markDone(card) },
                alSumar = { viewModel.addAmount(card, it) },
                alFijar = { viewModel.setExact(card, it) },
                alExacto = { alExacto(card) },
                alDecidir = { viewModel.decidir(card.id) },
                alRecaer = { alRecaer(card) },
                alDeshacer = { viewModel.deshacer(card.id) },
                modifier = Modifier.brota(indice),
            )
        }
        if (ui.tareasManana.isNotEmpty()) {
            item(key = "tareas") { LineaTareas(ui, Modifier.brota(2 + ui.filas.size)) }
        }
        item(key = "animo") {
            AnimoCierre(
                elegido = ui.animo,
                voz = ui.voz,
                dia = ui.hoy,
                alElegir = viewModel::elegirAnimo,
                modifier = Modifier.brota(3 + ui.filas.size),
            )
        }
    }
}

@Composable
private fun CabeceraCierre(
    ui: CierreUiState,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val descripcion = stringResource(R.string.cierre_cerrar)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .testTag("cierre-cerrar")
                .semantics { contentDescription = descripcion }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = alCerrar,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.X, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
        }
        if (!ui.loading) {
            Text(
                stringResource(R.string.cierre_hoy_n_de_m, ui.hechos, ui.total),
                style = BitoTema.tipos.t19.fuerte(),
                color = colores.tinta,
            )
        }
    }
}

/** «Para mañana: A, B y 3 más.»: las tareas no se marcan en el cierre, solo pasan a mañana. */
@Composable
private fun LineaTareas(
    ui: CierreUiState,
    modifier: Modifier = Modifier,
) {
    val mas = if (ui.tareasMas > 0) listOf(stringResource(R.string.cierre_tareas_mas, ui.tareasMas)) else emptyList()
    val bolsa = Bolsa.CIE_TAREAS_MANANA
    val frases = stringArrayResource(BolsasRecursos.array(bolsa, ui.voz)).size
    val indice = if (frases > 0) abs(ui.hoy) % frases else 0
    val frase =
        TextoHabi.Frase(
            listOf(ClaveTrozo(bolsa, ui.voz, indice)),
            mapOf(
                Hueco.LISTA to ValorHueco.Lista(ui.tareasManana + mas),
                Hueco.N to ValorHueco.Numero(ui.tareasManana.size + ui.tareasMas),
            ),
        )
    Text(
        textoHabi(frase),
        style = BitoTema.tipos.t16,
        color = BitoTema.colores.tintaSuave,
        modifier = modifier.fillMaxWidth().testTag("cierre-tareas"),
    )
}
