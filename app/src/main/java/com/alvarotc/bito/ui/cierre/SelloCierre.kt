package com.alvarotc.bito.ui.cierre

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habla.BolsasRecursos
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.premios.Momento
import com.alvarotc.bito.ui.premios.TipoVeta
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.stats.BadgeStrings
import kotlin.math.abs

/** Lo que brota capa a capa en el sello: cada una espera a que termine su celebración de la cadena (G14). */
enum class CapaSello { RACHAS, LOGROS }

/**
 * Lo que dice el sello, fijado al sellar: lo que se celebra después (los logros que pasan a vistos, por ejemplo) no lo
 * cambia. [animo] es 0 bien, 1 regular o 2 duro; [gordo], lo gordo que quedó sin hacer.
 */
data class DatosSello(
    val perfecto: Boolean,
    val hechos: Int,
    val total: Int,
    val bitos: Int,
    val rachas: Int,
    val logros: List<BadgeDef>,
    val gordo: List<String>,
    val voz: Personality,
    val nombre: String,
    val animo: Int?,
    val dia: LogicalDay,
) {
    /** Las capas que tienen algo que decir. */
    val capas: Set<CapaSello>
        get() =
            buildSet {
                if (rachas > 0) add(CapaSello.RACHAS)
                if (logros.isNotEmpty()) add(CapaSello.LOGROS)
            }
}

/** Lo que el sello sabe del cierre ya sellado. */
internal fun datosSello(ui: CierreUiState): DatosSello =
    DatosSello(
        perfecto = ui.perfecto,
        hechos = ui.hechos,
        total = ui.total,
        bitos = ui.bitosHoy,
        rachas = ui.rachasAvanzan,
        logros = ui.logrosNuevos,
        gordo = ui.gordoSinHacer,
        voz = ui.voz,
        nombre = ui.nombre,
        animo = ui.animo,
        dia = ui.hoy,
    )

/**
 * La capa que brota cuando termina [m]: la racha y el logro, la suya; el especial y la etapa (lo último de la cadena),
 * la de las rachas si aún no estaba. El día perfecto ya está en el título y las monedas no son de la cadena.
 */
internal fun capaDe(m: Momento): CapaSello? =
    when (m) {
        is Momento.Veta -> if (m.tipo == TipoVeta.RACHA) CapaSello.RACHAS else null
        is Momento.Logro -> CapaSello.LOGROS
        is Momento.Especial, is Momento.Etapa -> CapaSello.RACHAS
        is Momento.Monedas -> null
    }

// La capa que brota (catálogo 3.8): 260 ms `out` subiendo 18 dp. Las filas del sello miden 60 dp como poco.
private const val CAPA_MS = 260
private val CAPA_SUBE = 18.dp
private val ALTO_LINEA = 60.dp
private const val ALFA_RAYA = 0.22f

/** La coletilla del ánimo: solo con Regular o Duro. */
private val coletillas = mapOf(1 to Bolsa.CIE_ANIMO_REGULAR, 2 to Bolsa.CIE_ANIMO_DURO)

/**
 * El sello del día (§5.9 punto 5, catálogo 3.8): «Día perfecto» o «Día sellado», «N de M · hábitos de hoy», la frase
 * de Habi (con la coletilla del ánimo si fue Regular o Duro), los bitos de hoy y, si lo gordo quedó sin hacer, «Mañana
 * empezamos por…». Las rachas y los logros nuevos brotan cuando entran en [capas] (260 ms, `brote`, `tick`); una capa
 * sin nada que decir no aparece. Con [callar] («Hasta mañana» a mitad de la cadena) lo que falta se apunta sin sonar.
 */
@Composable
internal fun SelloCierre(
    datos: DatosSello,
    capas: Set<CapaSello>,
    modifier: Modifier = Modifier,
    callar: Boolean = false,
) {
    val colores = BitoTema.colores
    val t = BitoTema.tipos
    Column(modifier.fillMaxWidth().testTag("cierre-sello"), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
        Text(
            stringResource(if (datos.perfecto) R.string.cierre_sello_perfecto else R.string.review_sealed_title),
            style = t.t32.fuerte(),
            color = colores.tinta,
            modifier = Modifier.padding(top = Medidas.e18).testTag("cierre-sello-titulo"),
        )
        Text(
            "${stringResource(R.string.ring_of, datos.hechos, datos.total)} · ${stringResource(R.string.ring_caption)}",
            style = t.t19.fuerte(),
            color = colores.tinta,
            modifier = Modifier.testTag("cierre-sello-cuenta"),
        )
        Text(fraseSello(datos), style = t.t16, color = colores.tinta, modifier = Modifier.testTag("cierre-sello-frase"))
        Column(Modifier.fillMaxWidth()) {
            LineaSello(Modifier.testTag("cierre-sello-bitos"), raya = false) {
                Text(
                    stringResource(R.string.cierre_sello_bitos),
                    style = t.t16.fuerte(),
                    color = colores.tinta,
                    modifier = Modifier.weight(1f),
                )
                Text("+${datos.bitos}", style = t.t24.fuerte(), color = colores.oroTexto)
            }
            if (datos.rachas > 0) {
                CapaQueBrota(CapaSello.RACHAS in capas, "cierre-sello-rachas", callar) {
                    Text(
                        pluralStringResource(R.plurals.review_streaks_advanced, datos.rachas, datos.rachas),
                        style = t.t16.fuerte(),
                        color = colores.tinta,
                    )
                }
            }
            if (datos.logros.isNotEmpty()) {
                CapaQueBrota(CapaSello.LOGROS in capas, "cierre-sello-logros", callar) {
                    val nombres = datos.logros.map { stringResource(BadgeStrings.badgeNameRes(it.id)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_new_badges), style = t.t16.fuerte(), color = colores.tinta)
                        Text(nombres.joinToString(", "), style = t.t14, color = colores.tintaSuave)
                    }
                }
            }
            datos.gordo.firstOrNull()?.let { gordo ->
                LineaSello(Modifier.testTag("cierre-sello-gordo")) {
                    Text(stringResource(R.string.cierre_sello_gordo, gordo), style = t.t16, color = colores.tinta)
                }
            }
        }
    }
}

/** La frase de Habi al sellar: `cie_sello_perfecto` o `cie_sello`, y la coletilla del ánimo (la misma que respondió). */
@Composable
private fun fraseSello(datos: DatosSello): String {
    val bolsa = if (datos.perfecto) Bolsa.CIE_SELLO_PERFECTO else Bolsa.CIE_SELLO
    val coletilla = coletillas[datos.animo]
    val trozos =
        listOfNotNull(
            ClaveTrozo(bolsa, datos.voz, indice(bolsa, datos)),
            coletilla?.let { ClaveTrozo(it, datos.voz, indice(it, datos)) },
        )
    val nombre = datos.nombre.ifBlank { stringResource(R.string.habi_name_fallback) }
    return textoHabi(TextoHabi.Frase(trozos, mapOf(Hueco.NOMBRE to ValorHueco.Texto(nombre))))
}

/** El mismo índice que [AnimoCierre]: la frase del mismo día. */
@Composable
private fun indice(
    bolsa: Bolsa,
    datos: DatosSello,
): Int {
    val frases = stringArrayResource(BolsasRecursos.array(bolsa, datos.voz)).size
    return if (frases > 0) abs(datos.dia) % frases else 0
}

/** Una fila del sello: capas en la tierra, separadas por una raya, no tarjetas de métricas. Se lee de una vez. */
@Composable
private fun LineaSello(
    modifier: Modifier = Modifier,
    raya: Boolean = true,
    contenido: @Composable RowScope.() -> Unit,
) {
    val color = BitoTema.colores.contorno.copy(alpha = ALFA_RAYA)
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind { if (raya) drawLine(color, Offset.Zero, Offset(size.width, 0f), Medidas.linea.toPx()) }
            .heightIn(min = ALTO_LINEA)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        content = contenido,
    )
}

/** Una capa que brota al hacerse [visible] (260 ms `out`, 18 dp; reducido, fundido de 150 ms) con `brote` y `tick`. */
@Composable
private fun CapaQueBrota(
    visible: Boolean,
    etiqueta: String,
    callar: Boolean,
    contenido: @Composable RowScope.() -> Unit,
) {
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val brote = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible && brote.value < 1f) {
            if (!callar) gesto(Snd.BROTE, Haptica.TICK)
            brote.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else CAPA_MS, easing = Curvas.out))
        }
    }
    if (!visible) return
    LineaSello(
        Modifier
            .testTag(etiqueta)
            .graphicsLayer {
                alpha = brote.value
                if (!reducido) translationY = (1f - brote.value) * CAPA_SUBE.toPx()
            },
        contenido = contenido,
    )
}
