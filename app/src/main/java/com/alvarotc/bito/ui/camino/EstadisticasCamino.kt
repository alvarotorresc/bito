package com.alvarotc.bito.ui.camino

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HabitRecord
import com.alvarotc.bito.domain.WeekRow
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.today.TarjetaHabi
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val ANCHO_CELDA = 26.dp
private val ALTO_CELDA = 30.dp
private val HUECO_CELDA = 3.dp
private val ANCHO_GOTAS = ANCHO_CELDA * 7 + HUECO_CELDA * 6
private val GOTA_ANCHO = 22.dp
private val GOTA_ALTO = 25.dp

/**
 * Las estadísticas de Camino (§7.3): comentario de Habi, días perfectos, Tu semana, rachas activas y los
 * cantos de Récords y Tus números. Sin tarjetas de panel, gráficas ni sombras: tierra alta con las piezas de Hoy.
 */
@Composable
internal fun EstadisticasCamino(
    estado: CaminoUiState,
    alRecords: () -> Unit,
    alNumeros: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = estado.stats
    Column(modifier.fillMaxWidth().padding(horizontal = Medidas.margen).padding(top = 18.dp, bottom = 22.dp)) {
        val comentario =
            remember(stats.mood, stats.personality) {
                Propuesta("comentario", TextoHabi(HabiVoice.bubbleRes(stats.mood, stats.personality)), emptySet())
            }
        TarjetaHabi(comentario, alResponder = {}, modifier = Modifier.fillMaxWidth().testTag("camino-comentario"))
        DiasPerfectos(stats.perfectDays.thisMonth, stats.perfectDays.thisYear)
        TuSemana(estado)
        RachasActivas(estado)
        Cantos(stats.bestRecord, stats.totalEntries, alRecords, alNumeros)
    }
}

@Composable
private fun Encabezado(
    titulo: String,
    modifier: Modifier = Modifier,
    derecha: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Medidas.e4, end = Medidas.e4, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(titulo, style = BitoTema.tipos.t19.fuerte(), color = BitoTema.colores.tinta)
        derecha?.invoke()
    }
}

@Composable
private fun DiasPerfectos(
    esteMes: Int,
    esteAno: Int,
) {
    Encabezado(stringResource(R.string.stats_perfect_days_label))
    Row(Modifier.fillMaxWidth().padding(horizontal = Medidas.e4).testTag("camino-dias-perfectos"), verticalAlignment = Alignment.Bottom) {
        CifraConEtiqueta(esteMes, stringResource(R.string.camino_este_mes), Modifier.weight(1f))
        Spacer(Modifier.width(Medidas.e4))
        Box(Modifier.width(2.dp).height(44.dp).background(BitoTema.colores.raiz))
        Spacer(Modifier.width(Medidas.e16))
        CifraConEtiqueta(esteAno, stringResource(R.string.camino_este_ano), Modifier.weight(1f))
    }
}

@Composable
private fun CifraConEtiqueta(
    valor: Int,
    etiqueta: String,
    modifier: Modifier = Modifier,
) {
    val cifra = BitoTema.tipos.t48.cifra().copy(fontSize = 44.sp, lineHeight = 44.sp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalAlignment = Alignment.Bottom) {
        CifraRodillo(valor, estilo = cifra, color = BitoTema.colores.tinta)
        Text(etiqueta, Modifier.padding(bottom = Medidas.e4), style = BitoTema.tipos.t16, color = BitoTema.colores.tintaSuave)
    }
}

@Composable
private fun TuSemana(estado: CaminoUiState) {
    val semana = estado.stats.week
    val delta = semana.deltaVsLastWeek
    Encabezado(stringResource(R.string.camino_tu_semana)) {
        if (delta != null && delta >= 0) {
            Text(
                stringResource(R.string.camino_frente_semana, delta),
                style = BitoTema.tipos.t14.fuerte(),
                color = BitoTema.colores.okTexto,
            )
        }
    }
    val colores = BitoTema.colores
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BitoFormas.tarjeta)
            .background(colores.canto)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(start = Medidas.e12, end = Medidas.e12, top = 10.dp, bottom = Medidas.e12)
            .testTag("camino-semana"),
    ) {
        if (semana.rows.isEmpty()) {
            Text(stringResource(R.string.stats_week_empty), style = BitoTema.tipos.t16, color = colores.tintaSuave)
        } else {
            CabeceraSemana(estado.hoy.dayOfWeek)
            semana.rows.forEach { fila -> FilaSemana(fila) }
            LeyendaSemana()
        }
    }
}

/** L M X J V S D (o los del idioma), sobre las gotas y no sobre el nombre; el de hoy va más fuerte. */
@Composable
private fun CabeceraSemana(hoy: DayOfWeek) {
    val locale = Locale.getDefault()
    val colores = BitoTema.colores
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(ANCHO_GOTAS), horizontalArrangement = Arrangement.spacedBy(HUECO_CELDA)) {
            for (ordinal in 1..7) {
                val dia = DayOfWeek.of(ordinal)
                Box(Modifier.width(ANCHO_CELDA), contentAlignment = Alignment.Center) {
                    Text(
                        dia.getDisplayName(TextStyle.NARROW, locale),
                        style = BitoTema.tipos.t14.fuerte(),
                        color = if (dia == hoy) colores.tinta else colores.tintaSuave,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaSemana(fila: WeekRow) {
    val colores = BitoTema.colores
    // [D]: el total hecho/objetivo no dice QUÉ días se hicieron: una descripción agregada sobre las gotas, nunca por gota.
    val dias = weekRowDaysDescription(fila.dots)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(colores.contorno.copy(alpha = 0.12f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(end = Medidas.e4), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fila.name,
                Modifier.weight(1f, fill = false),
                style = BitoTema.tipos.t16,
                color = colores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${fila.done}/${fila.target}",
                Modifier.padding(start = Medidas.e4),
                style = BitoTema.tipos.t14,
                color = colores.tintaSuave,
                maxLines = 1,
            )
        }
        Row(
            Modifier
                .width(ANCHO_GOTAS)
                .semantics { contentDescription = dias }
                .testTag("week-dots-${fila.habitId}"),
            horizontalArrangement = Arrangement.spacedBy(HUECO_CELDA),
        ) {
            fila.dots.forEach { punto ->
                Box(Modifier.size(ANCHO_CELDA, ALTO_CELDA), contentAlignment = Alignment.Center) { GotaDia(punto) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeyendaSemana() {
    val colores = BitoTema.colores
    FlowRow(
        Modifier.fillMaxWidth().padding(top = Medidas.e8),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(Medidas.e4),
    ) {
        ElementoLeyenda(DayDot.FULFILLED, stringResource(R.string.camino_ley_cumplido), colores.tintaSuave)
        ElementoLeyenda(DayDot.FAILED, stringResource(R.string.camino_ley_fallado), colores.tintaSuave)
        ElementoLeyenda(DayDot.FROZEN, stringResource(R.string.camino_ley_congelado), colores.tintaSuave)
        ElementoLeyenda(DayDot.PAUSED, stringResource(R.string.camino_ley_pausa), colores.tintaSuave)
        ElementoLeyenda(DayDot.PENDING, stringResource(R.string.camino_ley_pendiente), colores.tintaSuave)
    }
}

@Composable
private fun ElementoLeyenda(
    punto: DayDot,
    texto: String,
    color: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e4), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 16.dp), contentAlignment = Alignment.Center) { GotaDia(punto, escala = 14f / 22f) }
        Text(texto, style = BitoTema.tipos.t14, color = color)
    }
}

/**
 * La gota de un día: hecho = gota de agua, fallado = su contorno a trazos, copito = congelado, en pausa = círculo con
 * raya, pendiente = un punto. EMPTY (día sin registro de un hábito no diario) y OFF (aún sin llegar) también son punto.
 */
@Composable
private fun GotaDia(
    punto: DayDot,
    escala: Float = 1f,
) {
    val colores = BitoTema.colores
    val ancho = GOTA_ANCHO * escala
    val alto = GOTA_ALTO * escala
    Canvas(Modifier.size(ancho, alto)) {
        when (punto) {
            DayDot.FULFILLED, DayDot.ACTIVITY -> gotaLlena(colores.agua, colores.contorno)
            DayDot.FAILED -> gotaTrazos(colores.tintaSuave)
            DayDot.FROZEN -> copito(colores.hielo, colores.copo)
            DayDot.PAUSED -> pausa(colores.tintaSuave)
            DayDot.PENDING -> drawCircle(colores.tintaSuave.copy(alpha = 0.55f), radius = 2.5.dp.toPx() * (size.width / 22.dp.toPx()))
            DayDot.EMPTY, DayDot.OFF ->
                drawCircle(colores.piedra.copy(alpha = 0.7f), radius = 2.dp.toPx() * (size.width / 22.dp.toPx()))
        }
    }
}

private fun DrawScope.perfilGota(): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, h * 0.02f)
        cubicTo(w * 0.62f, h * 0.25f, w * 0.92f, h * 0.42f, w * 0.92f, h * 0.65f)
        cubicTo(w * 0.92f, h * 0.86f, w * 0.73f, h * 0.98f, w * 0.5f, h * 0.98f)
        cubicTo(w * 0.27f, h * 0.98f, w * 0.08f, h * 0.86f, w * 0.08f, h * 0.65f)
        cubicTo(w * 0.08f, h * 0.42f, w * 0.38f, h * 0.25f, w * 0.5f, h * 0.02f)
        close()
    }
}

private fun DrawScope.gotaLlena(
    relleno: Color,
    contorno: Color,
) {
    val trazo = 1.5.dp.toPx() * (size.width / 22.dp.toPx())
    val perfil = perfilGota()
    drawPath(perfil, relleno)
    drawPath(perfil, contorno, style = Stroke(trazo))
    drawCircle(Color.White.copy(alpha = 0.7f), radius = size.width * 0.06f, center = Offset(size.width * 0.38f, size.height * 0.62f))
}

private fun DrawScope.gotaTrazos(color: Color) {
    val trazo = 1.2.dp.toPx() * (size.width / 22.dp.toPx())
    drawPath(
        perfilGota(),
        color,
        style = Stroke(trazo, pathEffect = PathEffect.dashPathEffect(floatArrayOf(trazo * 2.2f, trazo * 2.2f))),
    )
}

private fun DrawScope.copito(
    relleno: Color,
    color: Color,
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width * 0.42f
    drawCircle(relleno, r, c)
    drawCircle(color, r, c, style = Stroke(1.4.dp.toPx() * (size.width / 22.dp.toPx())))
    val brazo = r * 0.6f
    val trazo = 1.2.dp.toPx() * (size.width / 22.dp.toPx())
    for (grados in listOf(0f, 60f, 120f)) {
        val a = Math.toRadians(grados.toDouble())
        val dx = (kotlin.math.cos(a) * brazo).toFloat()
        val dy = (kotlin.math.sin(a) * brazo).toFloat()
        drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), trazo, StrokeCap.Round)
    }
}

private fun DrawScope.pausa(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width * 0.42f
    val trazo = 1.4.dp.toPx() * (size.width / 22.dp.toPx())
    drawCircle(color, r, c, style = Stroke(trazo))
    drawLine(color, Offset(c.x - r * 0.5f, c.y), Offset(c.x + r * 0.5f, c.y), trazo, StrokeCap.Round)
}

/** El nombre del día y su estado, como lo dice TalkBack en cada fila: la misma palabra que el resto de la app. */
@Composable
internal fun weekRowDaysDescription(dots: List<DayDot>): String {
    val locale = Locale.getDefault()
    return dots
        .mapIndexedNotNull { indice, punto ->
            val estado = estadoDe(punto) ?: return@mapIndexedNotNull null
            val dia = DayOfWeek.of(indice + 1).getDisplayName(TextStyle.SHORT, locale)
            stringResource(R.string.stats_week_day_state, dia, estado)
        }.joinToString(", ")
}

@Composable
private fun estadoDe(punto: DayDot): String? =
    when (punto) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> stringResource(R.string.day_done)
        DayDot.FAILED, DayDot.EMPTY -> stringResource(R.string.day_not_done)
        DayDot.FROZEN -> stringResource(R.string.heatmap_day_frozen)
        DayDot.PAUSED -> stringResource(R.string.paused_section_title)
        DayDot.PENDING -> stringResource(R.string.heatmap_day_pending)
        DayDot.OFF -> null
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RachasActivas(estado: CaminoUiState) {
    Encabezado(stringResource(R.string.stats_streaks_title))
    val rachas = estado.stats.activeStreaks.sortedByDescending { it.length }
    if (rachas.isEmpty()) {
        Text(
            stringResource(R.string.stats_streaks_empty),
            Modifier.padding(horizontal = Medidas.e4),
            style = BitoTema.tipos.t16,
            color = BitoTema.colores.tintaSuave,
        )
        return
    }
    val colores = BitoTema.colores
    FlowRow(
        Modifier.fillMaxWidth().testTag("camino-rachas"),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        verticalArrangement = Arrangement.spacedBy(Medidas.e8),
    ) {
        rachas.forEach { racha ->
            val descripcion =
                stringResource(R.string.stats_streak_card_cd, racha.name, racha.length, stringResource(unidadDe(racha.period)))
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colores.canto)
                    .border(Medidas.linea, colores.contorno, RoundedCornerShape(20.dp))
                    .padding(start = Medidas.e8, end = 14.dp)
                    .semantics(mergeDescendants = true) { contentDescription = descripcion }
                    .testTag("streak-${racha.habitId}"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogrosArte.de("streak-7")?.let { Image(painterResource(it), contentDescription = null, Modifier.size(24.dp)) }
                Text("${racha.length}", style = BitoTema.tipos.t16.cifra(), color = colores.tinta)
                Text(racha.name, style = BitoTema.tipos.t16, color = colores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Cantos(
    mejor: HabitRecord?,
    totalRegistros: Int,
    alRecords: () -> Unit,
    alNumeros: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formato = NumberFormat.getIntegerInstance(locale)
    Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canto(
            titulo = stringResource(R.string.stats_teaser_records),
            subtitulo =
                if (mejor != null) {
                    stringResource(R.string.camino_mejor_racha, mejor.best)
                } else {
                    stringResource(R.string.records_empty)
                },
            cifra = mejor?.best,
            marcador = stringResource(R.string.no_data_placeholder),
            alPulsar = alRecords,
            formato = formato::format,
            modifier = Modifier.testTag("canto-records"),
        )
        Canto(
            titulo = stringResource(R.string.stats_teaser_numbers),
            subtitulo = stringResource(R.string.camino_registros_totales),
            cifra = totalRegistros,
            marcador = stringResource(R.string.no_data_placeholder),
            alPulsar = alNumeros,
            formato = formato::format,
            modifier = Modifier.testTag("canto-numeros"),
        )
    }
}

/** Un canto (como los de Hoy): título, línea pequeña y la cifra a la derecha. Toda la fila abre su hoja. */
@Composable
private fun Canto(
    titulo: String,
    subtitulo: String,
    cifra: Int?,
    marcador: String,
    alPulsar: () -> Unit,
    formato: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val interaccion = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pulsar(interaccion)
            .heightIn(min = 64.dp)
            .clip(BitoFormas.canto)
            .background(colores.canto)
            .border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alPulsar)
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
            Text(subtitulo, style = BitoTema.tipos.t14, color = colores.tintaSuave)
        }
        if (cifra != null) {
            CifraRodillo(
                cifra,
                estilo = BitoTema.tipos.t24.cifra().copy(fontWeight = FontWeight.Black),
                color = colores.tinta,
                formato = formato,
            )
        } else {
            Text(marcador, style = BitoTema.tipos.t24.cifra(), color = colores.tintaSuave)
        }
        Icon(IconosSistema.CaretRight, contentDescription = null, Modifier.size(16.dp), tint = colores.tintaSuave)
    }
}

internal fun unidadDe(periodo: Period): Int =
    when (periodo) {
        Period.DAY -> R.string.unit_days
        Period.WEEK -> R.string.unit_weeks
        Period.MONTH -> R.string.unit_months
    }
