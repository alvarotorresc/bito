package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val RECORRIDO_MES = 56.dp
private val RECORRIDO_TITULO = 24.dp
private val UMBRAL_DESLIZAR = 48.dp

/** G30, G31 hacia delante: de hoy a +11 meses; días pasados apagados; el elegido con su gota. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalendarioElegir(
    hoy: LocalDate,
    elegido: LocalDate?,
    alElegir: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val locale: Locale = LocalConfiguration.current.locales[0]
    val meses = remember(hoy) { CalendarioLogica.meses(hoy) }
    var indice by rememberSaveable(hoy) {
        mutableIntStateOf(elegido?.let { meses.indexOf(YearMonth.from(it)) }?.takeIf { it >= 0 } ?: 0)
    }
    var direccion by remember { mutableIntStateOf(1) }
    val densidad = LocalDensity.current

    fun ir(delta: Int) {
        val destino = (indice + delta).coerceIn(0, meses.lastIndex)
        if (destino == indice) return
        direccion = delta
        indice = destino
        gesto(if (delta > 0) Snd.PASO_MES_DELANTE else Snd.PASO_MES_ATRAS, Haptica.TICK)
    }

    var arrastre by remember { mutableFloatStateOf(0f) }
    val umbralPx = with(densidad) { UMBRAL_DESLIZAR.toPx() }
    val recorridoMes = with(densidad) { RECORRIDO_MES.roundToPx() }
    val recorridoTitulo = with(densidad) { RECORRIDO_TITULO.roundToPx() }

    Column(
        modifier.draggable(
            state = rememberDraggableState { arrastre += it },
            orientation = Orientation.Horizontal,
            onDragStarted = { arrastre = 0f },
            onDragStopped = {
                when {
                    arrastre <= -umbralPx -> ir(1)
                    arrastre >= umbralPx -> ir(-1)
                }
                arrastre = 0f
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlechaMes(IconosSistema.CaretLeft, stringResource(R.string.sistema_b5b_mes_anterior), "mes-anterior", indice > 0) { ir(-1) }
            AnimatedContent(
                targetState = indice,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val signo = if (targetState >= initialState) 1 else -1
                    if (reducido) {
                        fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
                    } else {
                        (
                            slideInHorizontally(tween(Duraciones.MES_ENTRA, easing = Curvas.out)) { signo * recorridoTitulo } +
                                fadeIn(tween(Duraciones.MES_ENTRA, easing = Curvas.out))
                        ) togetherWith
                            (
                                slideOutHorizontally(tween(Duraciones.MES_SALE)) { -signo * recorridoTitulo } +
                                    fadeOut(tween(Duraciones.MES_SALE))
                            )
                    }
                },
                label = "titulo-mes",
            ) { i ->
                val mes = meses[i]
                Text(
                    "${mes.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }} ${mes.year}",
                    Modifier.fillMaxWidth(),
                    style = tipos.t19.fuerte(),
                    color = colores.tinta,
                    textAlign = TextAlign.Center,
                )
            }
            FlechaMes(
                IconosSistema.CaretRight,
                stringResource(R.string.sistema_b5b_mes_siguiente),
                "mes-siguiente",
                indice < meses.lastIndex,
            ) { ir(1) }
        }
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f),
                    style = tipos.t14.fuerte(),
                    color = colores.tintaSuave,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AnimatedContent(
            targetState = indice,
            transitionSpec = {
                val signo = if (targetState >= initialState) 1 else -1
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    (
                        slideInHorizontally(tween(Duraciones.MES_ENTRA, easing = Curvas.out)) { signo * recorridoMes } +
                            fadeIn(tween(Duraciones.MES_ENTRA, easing = Curvas.out))
                    ) togetherWith
                        (
                            slideOutHorizontally(tween(Duraciones.MES_SALE)) { -signo * recorridoMes } +
                                fadeOut(tween(Duraciones.MES_SALE))
                        )
                }
            },
            label = "cuadricula-mes",
        ) { i ->
            Column {
                CalendarioLogica.celdas(meses[i]).chunked(7).forEach { semana ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        semana.forEach { dia ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (dia != null) {
                                    DiaCelda(dia, hoy, dia == elegido) {
                                        gesto(Snd.TIC, Haptica.SEG)
                                        alElegir(dia)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlechaMes(
    icono: ImageVector,
    descripcion: String,
    etiqueta: String,
    habilitada: Boolean,
    alPulsar: () -> Unit,
) {
    Box(
        Modifier
            .size(Medidas.toqueMinimo)
            .testTag(etiqueta)
            .alpha(if (habilitada) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(enabled = habilitada, role = Role.Button, onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, Modifier.size(20.dp), tint = BitoTema.colores.tinta)
    }
}

@Composable
private fun DiaCelda(
    dia: LocalDate,
    hoy: LocalDate,
    elegido: Boolean,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val elegible = CalendarioLogica.elegible(dia, hoy)
    val escala = remember { Animatable(if (elegido) 1f else 0.6f) }
    LaunchedEffect(elegido) {
        if (!elegido) {
            escala.snapTo(0.6f)
        } else if (reducido) {
            escala.snapTo(1f)
        } else {
            escala.snapTo(0.6f)
            escala.animateTo(1.08f, tween(180, easing = Curvas.gota))
            escala.animateTo(1f, tween(120, easing = Curvas.out))
        }
    }
    var base =
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(Medidas.e4)
            .clip(CircleShape)
    if (dia == hoy) base = base.border(Medidas.linea, colores.contorno, CircleShape)
    base = if (elegible) base.clickable(role = Role.Button, onClick = alPulsar) else base.alpha(0.35f)
    Box(base, contentAlignment = Alignment.Center) {
        if (elegido) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f * escala.value
                drawCircle(colores.agua, radius = r)
            }
        }
        Text(
            dia.dayOfMonth.toString(),
            style = BitoTema.tipos.t16.fuerte(),
            color = colores.tinta,
        )
    }
}
