package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Los cinco estados de un día del calendario; se distinguen por la forma, no solo por el color (spec §8.4). */
enum class EstadoGota { HECHO, SIN_HACER, CONGELADO, HOY, NUMERO }

/** La parte pura del calendario: la rejilla de seis filas y a qué estado cae cada día. */
internal object CalendarioGotasLogica {
    const val CELDAS = 42

    /** 42 celdas (6 filas de 7, empezando en lunes): `null` antes del día 1 y después del último. */
    fun celdas(mes: YearMonth): List<LocalDate?> {
        val primero = mes.atDay(1)
        val huecos = primero.dayOfWeek.value - DayOfWeek.MONDAY.value
        return List(CELDAS) { i ->
            val desplazamiento = i - huecos
            if (desplazamiento in 0 until mes.lengthOfMonth()) primero.plusDays(desplazamiento.toLong()) else null
        }
    }

    /**
     * El estado de un día. No recibe el periodo del hábito: vale porque [com.alvarotc.bito.domain.Heatmap.dayDotOf]
     * solo emite `EMPTY` y `ACTIVITY` en hábitos semanales o mensuales, donde «hay registro ese día» es
     * «hecho» y «no lo hay» es «sin hacer». Hoy manda sobre el resto: la gota que toque va dentro de su celda.
     */
    fun estadoDe(d: HeatmapDay): EstadoGota =
        when {
            d.isToday -> EstadoGota.HOY
            d.dot == DayDot.FULFILLED || d.dot == DayDot.ACTIVITY -> EstadoGota.HECHO
            d.dot == DayDot.FAILED || d.dot == DayDot.EMPTY -> EstadoGota.SIN_HACER
            d.dot == DayDot.FROZEN -> EstadoGota.CONGELADO
            else -> EstadoGota.NUMERO
        }

    fun puedeAtras(
        mes: YearMonth,
        primerMes: YearMonth,
    ): Boolean = mes > primerMes

    fun puedeDelante(
        mes: YearMonth,
        ultimoMes: YearMonth,
    ): Boolean = mes < ultimoMes

    /**
     * Si un día responde al toque. Se decide por el punto, no por el dibujo: un día pasado sin sellar (en abstinencia,
     * `PENDING`) se dibuja solo con el número pero se puede tocar para sellarlo; lo que no responde es lo que no existe
     * todavía o no cuenta (`OFF`, por venir o antes de plantarlo) y los días en pausa.
     */
    fun respondeAlToque(d: HeatmapDay): Boolean = d.isToday || (d.dot != DayDot.OFF && d.dot != DayDot.PAUSED)

    /** Si la gota de [dot] va llena: hecho, o la de hoy cuando ya está marcada. */
    fun llena(dot: DayDot): Boolean = dot == DayDot.FULFILLED || dot == DayDot.ACTIVITY
}

// La gota del prototipo (`DROP` de 13-mezcla/index.html), en su caja de 32 x 36.
private const val GOTA_ANCHO = 32
private const val GOTA_ALTO = 36

private val GotaPath: Path =
    Path().apply {
        moveTo(16f, 2.5f)
        cubicTo(16f, 2.5f, 5f, 15.5f, 5f, 23f)
        arcTo(Rect(5f, 12f, 27f, 34f), 180f, -180f, forceMoveTo = false)
        cubicTo(27f, 15.5f, 16f, 2.5f, 16f, 2.5f)
        close()
    }

private val TrazoHecho = Stroke(width = 1.5f)
private val TrazoSeco = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3.2f)))
private val TrazoHoy = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))

private val DESPLAZAMIENTO_ENTRA = 56.dp
private val DESPLAZAMIENTO_TITULO = 24.dp
private val UMBRAL_DESLIZAR = 48.dp
private const val DURACION_CAIDA = 240
private const val RETRASO_CASCADA = 60
private const val ESCALON_COLUMNA = 14
private const val ESCALON_FILA = 8
private const val CAIDA_DP = 6
private const val OPACIDAD_TOPE = 0.32f
private const val PULSO = 1.15f
private const val GIRO_COPITO = 6f

private data class MesDatos(
    val mes: YearMonth,
    val dias: List<HeatmapDay>,
)

/**
 * El calendario de gotas de la ficha (G30, G31): un mes de seis filas, flechas ‹ › y deslizar para ir
 * de mes en mes entre [primerMes] (el de creación del hábito) y [ultimoMes] (el actual). Tocar un día pasado
 * o el de hoy llama a [alTocarDia]; un día congelado se tambalea y no llama a nada. Con [interactivo] en
 * `false` (archivado o lectura) los días no responden, pero las flechas sí.
 */
@Composable
fun CalendarioGotas(
    mes: YearMonth,
    dias: List<HeatmapDay>,
    primerMes: YearMonth,
    ultimoMes: YearMonth,
    alMesAnterior: () -> Unit,
    alMesSiguiente: () -> Unit,
    alTocarDia: (LogicalDay) -> Unit,
    modifier: Modifier = Modifier,
    interactivo: Boolean = true,
) {
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val puedeAtras = CalendarioGotasLogica.puedeAtras(mes, primerMes)
    val puedeDelante = CalendarioGotasLogica.puedeDelante(mes, ultimoMes)
    val atrasActual by rememberUpdatedState(alMesAnterior)
    val delanteActual by rememberUpdatedState(alMesSiguiente)
    val puedeAtrasActual by rememberUpdatedState(puedeAtras)
    val puedeDelanteActual by rememberUpdatedState(puedeDelante)

    fun ir(direccion: Int) {
        if (direccion < 0) {
            if (!puedeAtrasActual) return
            gesto(Snd.PASO_MES_ATRAS, Haptica.TICK)
            atrasActual()
        } else {
            if (!puedeDelanteActual) return
            gesto(Snd.PASO_MES_DELANTE, Haptica.TICK)
            delanteActual()
        }
    }

    val umbral = with(LocalDensity.current) { UMBRAL_DESLIZAR.toPx() }
    val saltoMes = with(LocalDensity.current) { DESPLAZAMIENTO_ENTRA.roundToPx() }
    Column(
        modifier
            .testTag("calendario-gotas")
            .pointerInput(umbral) {
                var acumulado = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acumulado = 0f },
                    onDragEnd = {
                        if (acumulado > umbral) {
                            ir(-1)
                        } else if (acumulado < -umbral) {
                            ir(1)
                        }
                        acumulado = 0f
                    },
                    onDragCancel = { acumulado = 0f },
                    onHorizontalDrag = { _, delta -> acumulado += delta },
                )
            },
    ) {
        CabeceraMes(mes, puedeAtras, puedeDelante, reducido, alAtras = { ir(-1) }, alDelante = { ir(1) })
        FilaSemana()
        AnimatedContent(
            targetState = MesDatos(mes, dias),
            contentKey = { it.mes },
            transitionSpec = {
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    val dir = if (targetState.mes > initialState.mes) 1 else -1
                    val entra =
                        slideInHorizontally(tween(Duraciones.MES_ENTRA, 30, Curvas.out)) { dir * saltoMes } +
                            fadeIn(tween(Duraciones.MES_ENTRA, 30, Curvas.out))
                    val sale =
                        slideOutHorizontally(tween(Duraciones.MES_SALE, easing = Curvas.out)) { -dir * saltoMes } +
                            fadeOut(tween(Duraciones.MES_SALE, easing = Curvas.out))
                    (entra togetherWith sale).using(SizeTransform(clip = false))
                }
            },
            label = "calendario-mes",
        ) { d ->
            val entrando = remember { transition.currentState == EnterExitState.PreEnter }
            MesGotas(d.mes, d.dias, interactivo, alTocarDia, cascada = entrando && !reducido)
        }
        Leyenda()
    }
}

@Composable
private fun CabeceraMes(
    mes: YearMonth,
    puedeAtras: Boolean,
    puedeDelante: Boolean,
    reducido: Boolean,
    alAtras: () -> Unit,
    alDelante: () -> Unit,
) {
    val patron = stringResource(R.string.ficha_cal_mes_pattern)
    val salto = with(LocalDensity.current) { DESPLAZAMIENTO_TITULO.roundToPx() }
    Row(Modifier.fillMaxWidth().padding(bottom = Medidas.e8), verticalAlignment = Alignment.CenterVertically) {
        FlechaMes(IconosSistema.CaretLeft, stringResource(R.string.previous_month), "mes-anterior", puedeAtras, alAtras)
        AnimatedContent(
            targetState = mes,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    val dir = if (targetState > initialState) 1 else -1
                    val entra =
                        slideInHorizontally(tween(Duraciones.ENTRAR - 40, 30, Curvas.out)) { dir * salto } +
                            fadeIn(tween(Duraciones.ENTRAR - 40, 30, Curvas.out))
                    val sale =
                        slideOutHorizontally(tween(160, easing = Curvas.out)) { -dir * salto } +
                            fadeOut(tween(160, easing = Curvas.out))
                    entra togetherWith sale
                }
            },
            label = "calendario-titulo",
        ) { m ->
            val texto =
                remember(m, patron) {
                    m
                        .atDay(1)
                        .format(DateTimeFormatter.ofPattern(patron, Locale.getDefault()))
                        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                }
            Text(
                texto,
                Modifier.fillMaxWidth().testTag("calendario-titulo"),
                style = BitoTema.tipos.t19.fuerte(),
                color = BitoTema.colores.tinta,
                textAlign = TextAlign.Center,
            )
        }
        FlechaMes(IconosSistema.CaretRight, stringResource(R.string.next_month), "mes-siguiente", puedeDelante, alDelante)
    }
}

@Composable
private fun FlechaMes(
    icono: ImageVector,
    descripcion: String,
    etiqueta: String,
    activa: Boolean,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val fuente = remember { MutableInteractionSource() }
    val opacidad by animateFloatAsState(if (activa) 1f else OPACIDAD_TOPE, tween(Duraciones.COLOR), label = "flecha")
    Box(
        Modifier
            .testTag(etiqueta)
            .size(Medidas.toqueMinimo)
            .pulsar(fuente, redondo = true)
            .alpha(opacidad)
            .clip(CircleShape)
            .background(colores.canto)
            .border(Medidas.linea, colores.contorno, CircleShape)
            .clickable(enabled = activa, interactionSource = fuente, indication = null, role = Role.Button, onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icono, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilaSemana() {
    val letras =
        remember {
            val locale = Locale.getDefault()
            DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale).uppercase(locale) }
        }
    Row(Modifier.fillMaxWidth().padding(bottom = Medidas.e4).clearAndSetSemantics { }) {
        letras.forEach { l ->
            Text(
                l,
                Modifier.weight(1f),
                style = BitoTema.tipos.t14.fuerte(),
                color = BitoTema.colores.tintaSobreTAlta2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MesGotas(
    mes: YearMonth,
    dias: List<HeatmapDay>,
    interactivo: Boolean,
    alTocarDia: (LogicalDay) -> Unit,
    cascada: Boolean,
) {
    val celdas = remember(mes) { CalendarioGotasLogica.celdas(mes) }
    val porDia = remember(dias) { dias.associateBy { it.day } }
    val total = RETRASO_CASCADA + 6 * ESCALON_COLUMNA + 5 * ESCALON_FILA + DURACION_CAIDA
    val tiempo = remember { Animatable(if (cascada) 0f else total.toFloat()) }
    LaunchedEffect(Unit) { if (cascada) tiempo.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        for (fila in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (columna in 0 until 7) {
                    val fecha = celdas[fila * 7 + columna]
                    val dia = fecha?.let { porDia[it.toEpochDay().toInt()] }
                    Box(Modifier.weight(1f).height(Medidas.toqueMinimo), contentAlignment = Alignment.Center) {
                        if (fecha != null && dia != null) {
                            val retraso = RETRASO_CASCADA + columna * ESCALON_COLUMNA + fila * ESCALON_FILA
                            CeldaGota(fecha, dia, interactivo, alTocarDia) {
                                Curvas.out.transform(((tiempo.value - retraso) / DURACION_CAIDA).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CeldaGota(
    fecha: LocalDate,
    dia: HeatmapDay,
    interactivo: Boolean,
    alTocarDia: (LogicalDay) -> Unit,
    caida: () -> Float,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val estado = CalendarioGotasLogica.estadoDe(dia)
    val llena = CalendarioGotasLogica.llena(dia.dot)
    val patronDia = stringResource(R.string.heatmap_day_pattern)
    val nombreDia = remember(fecha, patronDia) { fecha.format(DateTimeFormatter.ofPattern(patronDia, Locale.getDefault())) }
    val estadoTexto =
        stringResource(
            when {
                estado == EstadoGota.HECHO -> R.string.ficha_dia_hecho
                estado == EstadoGota.SIN_HACER -> R.string.ficha_dia_sin_hacer
                estado == EstadoGota.CONGELADO -> R.string.ficha_dia_congelado
                estado == EstadoGota.HOY && llena -> R.string.ficha_dia_hoy_hecho
                estado == EstadoGota.HOY -> R.string.ficha_dia_hoy_sin_marcar
                dia.dot == DayDot.PAUSED -> R.string.ficha_dia_pausa
                dia.dot == DayDot.PENDING -> R.string.ficha_dia_pendiente
                else -> R.string.ficha_dia_sin_registro
            },
        )
    val descripcion = stringResource(R.string.heatmap_day_cd, nombreDia, estadoTexto)

    // G31: la gota que cambia de estado hace un pulso (1 a 1,15 a 1). Con reducido, ninguno.
    val escala = remember { Animatable(1f) }
    var anterior by remember { mutableStateOf(dia.dot) }
    LaunchedEffect(dia.dot) {
        if (anterior != dia.dot) {
            anterior = dia.dot
            if (!reducido) {
                escala.animateTo(PULSO, Muelles.marcar())
                escala.animateTo(1f, Muelles.marcar())
            }
        }
    }
    // El copito congelado se tambalea (±6°) y no cambia nada.
    val giro = remember { Animatable(0f) }
    var temblores by remember { mutableIntStateOf(0) }
    LaunchedEffect(temblores) {
        if (temblores > 0 && !reducido) {
            giro.snapTo(-GIRO_COPITO)
            giro.animateTo(0f, Muelles.nodulo())
        }
    }

    val hoy = estado == EstadoGota.HOY
    val forma = RoundedCornerShape(14.dp)
    var celda = Modifier.fillMaxWidth().height(Medidas.toqueMinimo).testTag("dia-${dia.day}")
    if (hoy) celda = celda.padding(horizontal = 3.dp).clip(forma).background(colores.canto).border(Medidas.linea, colores.contorno, forma)
    if (interactivo && CalendarioGotasLogica.respondeAlToque(dia)) {
        celda =
            celda.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                if (estado == EstadoGota.CONGELADO) {
                    gesto(h = Haptica.LOW)
                    temblores++
                } else {
                    alTocarDia(dia.day)
                }
            }
    }
    Box(celda.semantics(mergeDescendants = true) { contentDescription = descripcion }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(GOTA_ANCHO.dp, GOTA_ALTO.dp)
                .offset(y = (-1).dp)
                .graphicsLayer {
                    val c = caida()
                    translationY = -(1f - c) * CAIDA_DP.dp.toPx()
                    alpha = c
                    scaleX = escala.value
                    scaleY = escala.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            when {
                estado == EstadoGota.HECHO || (hoy && llena) -> GotaLlena()
                estado == EstadoGota.SIN_HACER -> GotaSeca(colores.raiz, TrazoSeco)
                hoy -> GotaSeca(colores.contorno, TrazoHoy)
                estado == EstadoGota.CONGELADO -> Copito(Modifier.graphicsLayer { rotationZ = giro.value })
            }
        }
        Text(
            fecha.dayOfMonth.toString(),
            Modifier.offset(y = 2.dp),
            style =
                BitoTema.tipos.t14.cifra().copy(
                    fontWeight = if (estado == EstadoGota.NUMERO) FontWeight.Medium else FontWeight.Bold,
                ),
            color =
                when (estado) {
                    EstadoGota.HECHO, EstadoGota.HOY, EstadoGota.CONGELADO -> colores.tinta
                    else -> colores.tintaSobreTAlta2
                },
        )
    }
}

/** Dibuja en la caja de 32 x 36 del prototipo, a la escala real del lienzo. */
private fun DrawScope.enCaja(bloque: DrawScope.() -> Unit) {
    val s = size.width / GOTA_ANCHO
    withTransform({ scale(s, s, pivot = Offset.Zero) }) { bloque() }
}

@Composable
private fun GotaLlena(modifier: Modifier = Modifier) {
    val colores = BitoTema.colores
    Canvas(modifier.fillMaxSize()) {
        enCaja {
            drawPath(GotaPath, colores.agua)
            drawPath(GotaPath, colores.contorno, style = TrazoHecho)
            drawOval(colores.campo.copy(alpha = 0.75f), topLeft = Offset(9.2f, 17f), size = Size(3.6f, 6f))
        }
    }
}

@Composable
private fun GotaSeca(
    color: Color,
    trazo: Stroke,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) { enCaja { drawPath(GotaPath, color, style = trazo) } }
}

/** El congelador: un copito de nieve sobre un círculo de hielo, el mismo de la tienda. */
@Composable
private fun Copito(modifier: Modifier = Modifier) {
    val colores = BitoTema.colores
    val copo = rememberVectorPainter(IconosSistema.Snowflake)
    val filtro = remember(colores.aguaHonda) { ColorFilter.tint(colores.aguaHonda) }
    Canvas(modifier.fillMaxSize()) {
        enCaja {
            drawCircle(colores.hielo, radius = 13f, center = Offset(16f, 19f))
            drawCircle(colores.contorno, radius = 13f, center = Offset(16f, 19f), style = TrazoHecho)
            translate(5f, 8f) {
                with(copo) { draw(Size(22f, 22f), alpha = 0.5f, colorFilter = filtro) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Leyenda() {
    val colores = BitoTema.colores
    FlowRow(
        Modifier.fillMaxWidth().padding(top = Medidas.e12, start = 6.dp, end = 6.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EntradaLeyenda(stringResource(R.string.day_done)) { GotaLlena() }
        EntradaLeyenda(stringResource(R.string.ficha_leyenda_sin_hacer)) { GotaSeca(colores.raiz, TrazoSeco) }
        EntradaLeyenda(stringResource(R.string.ficha_leyenda_congelador)) { Copito() }
        EntradaLeyenda(stringResource(R.string.ficha_leyenda_hoy)) { GotaSeca(colores.contorno, TrazoHoy) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("14", style = BitoTema.tipos.t14.cifra().copy(fontWeight = FontWeight.Medium), color = colores.tintaSobreTAlta2)
            Text(stringResource(R.string.ficha_leyenda_por_venir), style = BitoTema.tipos.t14, color = colores.tintaSobreTAlta2)
        }
    }
}

@Composable
private fun EntradaLeyenda(
    texto: String,
    icono: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(16.dp, 18.dp)) { icono() }
        Text(texto, style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSobreTAlta2)
    }
}
