package com.alvarotc.bito.ui.camino

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeFamily
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Emblema
import com.alvarotc.bito.ui.sistema.componentes.FamiliaLogro
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.stats.BadgeStrings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val POR_FILA = 4
private val EMBLEMA_REJILLA = 78.dp
private const val ESCALA_TOQUE = 0.96f

internal fun familiaDe(familia: BadgeFamily): FamiliaLogro =
    when (familia) {
        BadgeFamily.STREAK -> FamiliaLogro.RACHAS
        BadgeFamily.CONSTANCY -> FamiliaLogro.CONSTANCIA
        BadgeFamily.MOMENT -> FamiliaLogro.MOMENTOS
    }

/** El arte del especial del jardín que da un logro (el mismo de la escena). */
@DrawableRes
internal fun arteDeEspecial(especial: GardenSpecial): Int =
    when (especial) {
        GardenSpecial.LANTERN -> R.drawable.garden_farolillo
        GardenSpecial.STONE_PATH -> R.drawable.garden_caminito
        GardenSpecial.POND -> R.drawable.garden_estanque
        GardenSpecial.MUSHROOMS -> R.drawable.garden_setas
        GardenSpecial.BIG_CHERRY -> R.drawable.garden_cerezo_grande
        GardenSpecial.GOLDEN_TREE -> R.drawable.garden_arbol_dorado
    }

/** El nombre del especial con su artículo («el farolillo»), para «Plantó el farolillo en tu jardín.». */
@StringRes
internal fun nombreDeEspecial(especial: GardenSpecial): Int =
    when (especial) {
        GardenSpecial.LANTERN -> R.string.camino_especial_farolillo
        GardenSpecial.STONE_PATH -> R.string.camino_especial_caminito
        GardenSpecial.POND -> R.string.camino_especial_estanque
        GardenSpecial.MUSHROOMS -> R.string.camino_especial_setas
        GardenSpecial.BIG_CHERRY -> R.string.camino_especial_cerezo_grande
        GardenSpecial.GOLDEN_TREE -> R.string.camino_especial_arbol_dorado
    }

/** Una fecha del día a mes en el idioma de la app («8 de julio», «July 8»). */
@Composable
internal fun fechaDelDia(fecha: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(stringResource(R.string.heatmap_day_pattern), locale).format(fecha)
}

/**
 * Lo que le falta a un logro todavía sin conseguir: «13 de 100 días», o, si es Mes redondo y este mes ya no puede
 * salir ([mesImposible]), «El 1 de noviembre empieza otro mes entero.» a partir de [hoy]. Null si ya está conseguido o
 * no tiene cuenta (los de sí o no).
 */
@Composable
internal fun faltaTexto(
    logro: LogroUi,
    hoy: LocalDate,
    mesImposible: Boolean = false,
): String? {
    if (logro.conseguido != null) return null
    if (logro.def.id == "perfect-month" && mesImposible) {
        return stringResource(R.string.camino_otro_mes, fechaDelDia(hoy.withDayOfMonth(1).plusMonths(1)))
    }
    val progreso = logro.progreso ?: return null
    return if (progreso.objetivo > 1) stringResource(R.string.camino_falta_dias, progreso.actual, progreso.objetivo) else null
}

/** El emblema de un logro con su arte pintado dentro y su anillo de lo que lleva. */
@Composable
internal fun EmblemaDeLogro(
    logro: LogroUi,
    tamano: Dp,
    modifier: Modifier = Modifier,
    celebrar: Boolean = false,
    encendido: Boolean = logro.conseguido != null,
) {
    val progreso = logro.progreso
    val objetivo = progreso?.objetivo ?: 0
    val anillo = if (progreso == null || objetivo <= 0) 0f else progreso.actual.toFloat() / objetivo
    // Al encenderse, el anillo parte de lo que faltaba un paso antes y se cierra; en reposo, lleno o a medias.
    val antes = if (objetivo > 1) (objetivo - 1f) / objetivo else 0f
    Emblema(
        familia = familiaDe(logro.def.family),
        progreso =
            if (celebrar) {
                antes
            } else if (encendido) {
                1f
            } else {
                anillo
            },
        encendido = encendido,
        modifier = modifier,
        tamano = tamano,
        celebrar = celebrar,
    ) {
        LogrosArte.de(logro.def.id)?.let { Image(painterResource(it), contentDescription = null, Modifier.fillMaxSize()) }
    }
}

/** Los catorce logros en cuatro columnas por familia, sobre la tierra alta 2 (§7.4). */
@Composable
internal fun LogrosCamino(
    estado: CaminoUiState,
    alTocar: (LogroUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Column(
        modifier
            .fillMaxWidth()
            .background(colores.tAlta2)
            .drawBehind { drawLine(colores.contorno.copy(alpha = 0.22f), Offset(0f, 0f), Offset(size.width, 0f), Medidas.linea.toPx()) }
            .padding(bottom = 120.dp)
            .testTag("camino-logros"),
    ) {
        Text(
            stringResource(R.string.camino_logros, estado.conseguidos, estado.logros.size),
            Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
            style = BitoTema.tipos.t24.fuerte(),
            color = colores.tinta,
        )
        listOf(BadgeFamily.STREAK, BadgeFamily.CONSTANCY, BadgeFamily.MOMENT).forEach { familia ->
            val deLaFamilia = estado.logros.filter { it.def.family == familia }
            if (deLaFamilia.isEmpty()) return@forEach
            Text(
                stringResource(BadgeStrings.badgeFamilyRes(familia)),
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = Medidas.e8),
                style = BitoTema.tipos.t16.fuerte(),
                color = colores.tintaSobreTAlta2,
            )
            Column(Modifier.padding(horizontal = Medidas.e12), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
                deLaFamilia.chunked(POR_FILA).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila.forEach {
                                logro ->
                            LogroEnRejilla(logro, estado.hoy, mesImposible(estado), { alTocar(logro) }, Modifier.weight(1f))
                        }
                        repeat(POR_FILA - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogroEnRejilla(
    logro: LogroUi,
    hoy: LocalDate,
    mesImposible: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val encendido = logro.conseguido != null
    // Solo anima el que se enciende mientras se mira (§11.4): el resto nace ya en su estado.
    val estabaEncendido = remember { encendido }
    val falta = faltaTexto(logro, hoy, mesImposible) ?: if (encendido) null else stringResource(BadgeStrings.badgeHowRes(logro.def.id))
    val nombre = stringResource(BadgeStrings.badgeNameRes(logro.def.id))
    val descripcion =
        if (encendido) {
            stringResource(R.string.camino_logro_conseguido_cd, nombre)
        } else {
            stringResource(R.string.camino_logro_pendiente_cd, nombre, falta.orEmpty())
        }
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pulsado && !reducido) ESCALA_TOQUE else 1f, tween(Duraciones.PULSAR), label = "logro-toque")
    Column(
        modifier
            .testTag("logro-${logro.def.id}")
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }.clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alPulsar)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Medidas.e4),
    ) {
        EmblemaDeLogro(logro, EMBLEMA_REJILLA, celebrar = encendido && !estabaEncendido)
        Text(
            nombre,
            style = BitoTema.tipos.t14.fuerte(),
            color = if (encendido) colores.tinta else colores.tintaSobreTAlta2,
            textAlign = TextAlign.Center,
        )
        if (falta != null) {
            Text(
                falta,
                style = BitoTema.tipos.t14,
                color = colores.tintaSobreTAlta2,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
