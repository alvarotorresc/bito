package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.habi.itemNameRes
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas

// (sin token) `.cseg h4` de tienda-habi.css; la raya es rgba(74,44,34,.22).
private val RayaCabecera = Color(0x384A2C22)
private val AIRE_ARRIBA = 14.dp
private val AIRE_DERECHA = 64.dp
private val AIRE_ABAJO = 8.dp
private val AIRE_IZQUIERDA = 20.dp
private val HUECO_META = 12.dp
private val LETRA_META = 14.sp
private val ALTO_NOMBRE = 22.8.sp // 19 sp a 1,2 de interlineado
private val ALTO_META = 16.8.sp

/**
 * La cabecera anclada de una ranura: el nombre a la izquierda y la meta a la derecha («Puesto: botas
 * de agua», «3 piezas» o «Tapado por el disfraz»), en una línea con elipsis. El `testTag` es
 * `ranura-<id>`.
 */
@Composable
fun CabeceraRanura(
    ranura: RanuraHabi,
    meta: MetaRanura,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val tapada = meta == MetaRanura.Tapada
    val colorMeta by animateColorAsState(
        targetValue = if (tapada) colores.tinta else colores.tintaSuave,
        animationSpec = tween(Duraciones.COLOR),
        label = "cabecera-meta",
    )
    val textoMeta =
        when (meta) {
            MetaRanura.Tapada -> stringResource(R.string.store_slot_covered)
            is MetaRanura.Puesta ->
                stringResource(R.string.store_slot_worn, stringResource(itemNameRes(meta.itemId)).lowercase(Locale.current.platformLocale))
            is MetaRanura.Piezas -> stringResource(R.string.store_slot_count, meta.cuantas)
        }
    val linea = Medidas.linea
    Row(
        modifier
            .testTag("ranura-${ranura.id}")
            .fillMaxWidth()
            .background(colores.tAlta)
            .drawBehind {
                drawRect(
                    RayaCabecera,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - linea.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, linea.toPx()),
                )
            }
            .padding(start = AIRE_IZQUIERDA, top = AIRE_ARRIBA, end = AIRE_DERECHA, bottom = AIRE_ABAJO + linea)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(HUECO_META),
    ) {
        // `align-items: baseline` de `.cseg h4`: nombre y meta comparten línea base.
        Text(
            stringResource(ranura.nombre),
            Modifier.alignByBaseline(),
            style = tipos.t19.copy(fontWeight = FontWeight.Bold, lineHeight = ALTO_NOMBRE),
            color = colores.tinta,
            maxLines = 1,
        )
        Text(
            textoMeta,
            Modifier.weight(1f).alignByBaseline().testTag("meta-${ranura.id}"),
            style =
                tipos.t14.copy(
                    fontSize = LETRA_META,
                    lineHeight = ALTO_META,
                    fontWeight = if (tapada) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.End,
                ),
            color = colorMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
