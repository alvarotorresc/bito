package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

// `.ttabs` de tienda-habi.css. La raya es rgba(74,44,34,.22), sin token en el sistema.
private val RayaPestanas = Color(0x384A2C22)
private val ALTO_PESTANA = 46.dp
private val ALTO_INDICADOR = 3.dp
private val AIRE_INDICADOR = 12.dp
private const val UMBRAL_INDICADOR = 0.001f

/**
 * Las tres pestañas de la tienda (Habi, Jardín, Congeladores): 46 dp de alto, una raya debajo y un indicador
 * de 3 dp que se desliza con el muelle `indicador` (en modo reducido, un fundido de 150 ms, sin muelle).
 * El contenido que entra lo monta la pantalla.
 */
@Composable
fun PestanasTienda(
    pestana: PestanaTienda,
    alElegir: (PestanaTienda) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    var ancho by remember { mutableIntStateOf(0) }
    val posicion = remember { Animatable(pestana.ordinal.toFloat()) }
    LaunchedEffect(pestana, reducido) {
        val espec =
            if (reducido) {
                tween<Float>(Duraciones.REDUCIDO)
            } else {
                Muelles.indicador<Float>(umbral = UMBRAL_INDICADOR)
            }
        posicion.animateTo(pestana.ordinal.toFloat(), espec)
    }
    val etiquetas =
        listOf(
            stringResource(R.string.store_tab_habi),
            stringResource(R.string.store_tab_garden),
            stringResource(R.string.store_tab_freezers),
        )
    val tags = listOf("ttab-habi", "ttab-jardin", "ttab-congeladores")
    val densidad = LocalDensity.current
    val anchoPestana = with(densidad) { (ancho / PestanaTienda.entries.size).toDp() }

    Box(modifier.padding(horizontal = Medidas.margen).fillMaxWidth().onSizeChanged { ancho = it.width }) {
        Column {
            Row(Modifier.selectableGroup().fillMaxWidth()) {
                PestanaTienda.entries.forEachIndexed { i, p ->
                    val activa = p == pestana
                    val color by animateColorAsState(
                        if (activa) colores.tinta else colores.tintaSuave,
                        tween(Duraciones.COLOR),
                        label = "pestana-color",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(ALTO_PESTANA)
                            .testTag(tags[i])
                            .selectable(
                                selected = activa,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (!activa) {
                                    gesto(Snd.INTERRUPTOR_ON, Haptica.TICK)
                                    alElegir(p)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(text = etiquetas[i], style = tipos.t16.copy(fontWeight = FontWeight.Bold, color = color))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(Medidas.linea).background(RayaPestanas))
        }
        Box(
            Modifier
                .offset(x = AIRE_INDICADOR, y = ALTO_PESTANA + Medidas.linea - ALTO_INDICADOR)
                .width((anchoPestana - AIRE_INDICADOR * 2).coerceAtLeast(0.dp))
                .height(ALTO_INDICADOR)
                .graphicsLayer { translationX = posicion.value * (ancho / PestanaTienda.entries.size.toFloat()) }
                .testTag("ttind")
                .clip(RoundedCornerShape(ALTO_INDICADOR))
                .background(colores.tinta),
        )
    }
}
