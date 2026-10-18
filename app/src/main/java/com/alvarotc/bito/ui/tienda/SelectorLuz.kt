package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Fila de la luz (`#tluz`, `#luzriel` de tienda-habi.css): todo en dp del mock.
private val ALTO_FILA = 52.dp
private val RELLENO_INICIO = 20.dp
private val RELLENO_FIN = 12.dp
private val ENTRE_ETIQUETA_Y_RIEL = 6.dp
private val MARGEN_SURCO = 36.dp
private val SURCO_ARRIBA = 15.dp
private val SURCO_ALTO = 2.dp
private val ASTRO = 22.dp
private val ASTRO_ARRIBA = 5.dp
private val ASTRO_LINEA = 1.5.dp
private val ASTRO_ANILLO = 3.dp
private val BOTON_ANCHO = 72.dp
private val BOTON_TEXTO_ARRIBA = 26.dp
private const val PARADAS_MAX = 2f
private const val MUESTRAS = 5
private const val MILIS_POR_SEGUNDO = 1000f

/**
 * El mando de la luz: etiqueta «Luz», un surco con su astro y tres botones (Día, Tarde, Noche). Tocar un botón
 * lleva el astro a su parada con el muelle `luz`; arrastrarlo lo sigue sin muelle y al soltar se proyecta
 * [LuzArmario.PROYECCION_S] de velocidad antes de ajustar. [estado] lleva el valor vivo; [luz] es la parada
 * que dice el estado de la pantalla y [alElegir] la avisa de una nueva.
 */
@Composable
fun SelectorLuz(
    luz: LuzTienda,
    estado: LuzEstado,
    alElegir: (LuzTienda) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val alElegirVivo by rememberUpdatedState(alElegir)
    val luzVivo by rememberUpdatedState(luz)
    val reducidoVivo by rememberUpdatedState(reducido)
    val parada by remember { derivedStateOf { (estado.valor * PARADAS_MAX).roundToInt().coerceIn(0, LuzTienda.entries.lastIndex) } }
    var ancho by remember { mutableIntStateOf(0) }
    var enfocado by remember { mutableStateOf(false) }
    var botonConFoco by remember { mutableIntStateOf(0) }
    val focos = remember { List(LuzTienda.entries.size) { FocusRequester() } }

    LaunchedEffect(luz, reducido) { if (estado.destino != luz) estado.ir(luz, reducido) }

    fun ir(
        destino: LuzTienda,
        sonar: Boolean,
    ) {
        if (sonar) gesto(Snd.TIC, Haptica.SEG)
        if (destino != luzVivo) alElegirVivo(destino)
        scope.launch { estado.ir(destino, reducidoVivo) }
    }

    fun elegirConTeclado(destino: LuzTienda) {
        if (destino != estado.destino) ir(destino, sonar = true)
        scope.launch {
            // El foco viaja con el astro, pero solo el botón activo es enfocable: espera a que la recomposición lo ponga.
            withFrameNanos { }
            focos[destino.ordinal].requestFocus()
        }
    }

    val etiqueta = stringResource(R.string.store_light)
    val nombres =
        listOf(
            stringResource(R.string.store_light_day),
            stringResource(R.string.store_light_afternoon),
            stringResource(R.string.store_light_night),
        )

    Row(
        modifier
            .testTag("tluz")
            .fillMaxWidth()
            .height(ALTO_FILA)
            .padding(start = RELLENO_INICIO, end = RELLENO_FIN),
        horizontalArrangement = Arrangement.spacedBy(ENTRE_ETIQUETA_Y_RIEL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = etiqueta,
            style = tipos.t14.copy(fontWeight = FontWeight.Bold, color = colores.tintaSuave),
        )
        Box(
            Modifier
                .weight(1f)
                .height(ALTO_FILA)
                .testTag("luzriel")
                .onSizeChanged { ancho = it.width }
                .selectableGroup()
                .onFocusChanged { enfocado = it.hasFocus }
                .onKeyEvent { evento ->
                    if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val paso =
                        when (evento.key) {
                            Key.DirectionRight, Key.DirectionUp -> 1
                            Key.DirectionLeft, Key.DirectionDown -> -1
                            else -> 0
                        }
                    when {
                        paso != 0 -> {
                            val i = (estado.destino.ordinal + paso).coerceIn(0, LuzTienda.entries.lastIndex)
                            elegirConTeclado(LuzTienda.entries[i])
                            true
                        }
                        evento.key == Key.Enter || evento.key == Key.NumPadEnter || evento.key == Key.Spacebar -> {
                            elegirConTeclado(LuzTienda.entries[botonConFoco])
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    val margen = MARGEN_SURCO.toPx()
                    val agarre = viewConfiguration.touchSlop

                    fun deLaPosicion(x: Float): Float {
                        val largo = size.width - 2f * margen
                        return if (largo <= 0f) 0f else ((x - margen) / largo * PARADAS_MAX).coerceIn(0f, PARADAS_MAX)
                    }
                    awaitEachGesture {
                        val abajo = awaitFirstDown(requireUnconsumed = false)
                        gesto(null, Haptica.TICK)
                        val previa = estado.destino
                        var ultima = previa.ordinal
                        var arrastrando = false
                        var posicion = abajo.position
                        val muestras = ArrayDeque<Pair<Long, Float>>()
                        while (true) {
                            val evento = awaitPointerEvent()
                            val cambio = evento.changes.firstOrNull { it.id == abajo.id } ?: break
                            posicion = cambio.position
                            if (!cambio.pressed) break
                            if (!arrastrando && (posicion - abajo.position).getDistance() > agarre) arrastrando = true
                            if (arrastrando) {
                                cambio.consume()
                                val v = deLaPosicion(posicion.x)
                                scope.launch { estado.seguir(v / PARADAS_MAX) }
                                muestras.addLast(cambio.uptimeMillis to v)
                                if (muestras.size > MUESTRAS) muestras.removeFirst()
                                val q = v.roundToInt()
                                if (q != ultima) {
                                    ultima = q
                                    gesto(Snd.TIC, Haptica.SEG)
                                }
                            }
                        }
                        if (arrastrando && muestras.isNotEmpty()) {
                            val a = muestras.first()
                            val b = muestras.last()
                            val segundos = (b.first - a.first) / MILIS_POR_SEGUNDO
                            val velocidad = if (segundos > 0f) (b.second - a.second) / segundos / PARADAS_MAX else 0f
                            ir(LuzArmario.alSoltar(b.second / PARADAS_MAX, velocidad, reducidoVivo), sonar = false)
                        } else {
                            val destino = LuzArmario.masCercana(deLaPosicion(posicion.x) / PARADAS_MAX)
                            ir(destino, sonar = destino != previa)
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .padding(start = MARGEN_SURCO, end = MARGEN_SURCO, top = SURCO_ARRIBA)
                    .fillMaxWidth()
                    .height(SURCO_ALTO)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colores.raiz),
            )
            val oro = colores.oro
            val contorno = colores.contorno
            val agua = colores.agua
            Box(
                Modifier
                    .offset(x = MARGEN_SURCO - ASTRO / 2, y = ASTRO_ARRIBA)
                    .size(ASTRO)
                    .graphicsLayer {
                        val recorrido = (ancho - 2f * MARGEN_SURCO.toPx()).coerceAtLeast(0f)
                        translationX = estado.valor * recorrido
                    }
                    .drawBehind {
                        val r = ASTRO.toPx() / 2f
                        if (enfocado) drawCircle(agua, radius = r + ASTRO_ANILLO.toPx())
                        drawCircle(contorno, radius = r)
                        drawCircle(LuzArmario.astro(estado.valor, oro), radius = r - ASTRO_LINEA.toPx())
                    }
                    .semantics {
                        contentDescription = "$etiqueta: ${nombres[parada]}"
                        progressBarRangeInfo = ProgressBarRangeInfo(LuzTienda.entries[parada].parada, 0f..1f, steps = 1)
                        setProgress { valor ->
                            val destino = LuzArmario.masCercana(valor)
                            if (destino != estado.destino) ir(destino, sonar = true)
                            true
                        }
                    },
            )
            LuzTienda.entries.forEachIndexed { i, parada1 ->
                val activo = i == parada
                val color by animateColorAsState(
                    if (activo) colores.tinta else colores.tintaSuave,
                    tween(Duraciones.COLOR),
                    label = "luz-boton",
                )
                Box(
                    Modifier
                        .offset {
                            val recorrido = (ancho - 2f * MARGEN_SURCO.toPx()).coerceAtLeast(0f)
                            IntOffset((i / PARADAS_MAX * recorrido).roundToInt(), 0)
                        }
                        .width(BOTON_ANCHO)
                        .height(ALTO_FILA)
                        .focusRequester(focos[i])
                        .focusProperties { canFocus = i == estado.destino.ordinal }
                        .onFocusChanged { if (it.isFocused) botonConFoco = i }
                        .focusable()
                        .semantics(mergeDescendants = true) {
                            role = Role.RadioButton
                            selected = activo
                            onClick {
                                ir(parada1, sonar = parada1 != estado.destino)
                                true
                            }
                        },
                ) {
                    // Un `<button>` centra su texto en lo que deja `padding-top: 26px`.
                    BasicText(
                        text = nombres[i],
                        modifier = Modifier.fillMaxSize().padding(top = BOTON_TEXTO_ARRIBA).wrapContentHeight(Alignment.CenterVertically),
                        style =
                            tipos.t14.copy(
                                color = color,
                                fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                    )
                }
            }
        }
    }
}
