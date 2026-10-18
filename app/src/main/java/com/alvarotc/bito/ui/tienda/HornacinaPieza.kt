package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Monedita

/** Lo que dice la línea de debajo del nombre. [PRECIO] pinta la moneda y el número. */
enum class EtiquetaHornacina { PUESTO, TUYO, PROBANDO, TAPADO, EN_TU_JARDIN, RACHA, TEMPORADA, LOGRO, PRECIO }

/** El aro interior de la hornacina: lo puesto en `agua-honda`, lo que se prueba en `contorno`. */
enum class AroHornacina { NINGUNO, PUESTO, PROBANDO }

// (sin token) `.item .niche` de tienda-habi.css.
private val ALTO_HORNACINA = 104.dp
private val ARTE_MAX_ANCHO = 84.dp
private val ARTE_MAX_ALTO = 78.dp
private val ARTE_AIRE_ABAJO = 12.dp
private val GROSOR_ARO = 2.dp
private val AIRE_NOMBRE = 6.dp
private val AIRE_PRECIO = 2.dp
private val ALTO_PRECIO = 20.dp
private val MONEDA = 18.dp
private val LETRA = 14.sp
private val ALTO_LETRA = 16.8.sp // 14 sp a 1,2 de interlineado
private const val ALFA_BLOQUEADA = 0.55f
private const val SATURACION_BLOQUEADA = 0.5f
private const val ALFA_TAPADA = 0.5f

/**
 * Una pieza de la tira: hueco de 104 dp con el arte abajo, el nombre y la línea de debajo. Toda la
 * columna es la celda y va alineada desde arriba: el hueco nunca se centra, así las hornacinas de una
 * fila arrancan a la misma altura aunque un nombre ocupe dos líneas. [imagen] se coloca en una caja de
 * como mucho 84 por 78 dp, a 12 dp del suelo del hueco; que no intercepte toques.
 * [textoEtiqueta] (ya traducido) manda sobre el texto fijo de [etiqueta]. El `testTag("item-<id>")` lo
 * pone quien la usa en [modifier].
 */
@Composable
fun HornacinaPieza(
    imagen: @Composable BoxScope.() -> Unit,
    nombre: String,
    etiqueta: EtiquetaHornacina,
    textoEtiqueta: String?,
    precio: Int?,
    aro: AroHornacina,
    bloqueada: Boolean,
    tapada: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val reducido = BitoTema.reducido
    val interaccion = remember { MutableInteractionSource() }
    val pulsada by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pulsada && !reducido) Duraciones.ESCALA_PULSAR else 1f,
        animationSpec = tween(Duraciones.PULSAR, easing = Curvas.out),
        label = "hornacina-pulsar",
    )
    val colorAro by animateColorAsState(
        targetValue =
            when (aro) {
                AroHornacina.NINGUNO -> Color.Transparent
                AroHornacina.PUESTO -> colores.aguaHonda
                AroHornacina.PROBANDO -> colores.contorno
            },
        animationSpec = tween(Duraciones.COLOR),
        label = "hornacina-aro",
    )
    val alfaArte by animateFloatAsState(
        targetValue =
            when {
                bloqueada -> ALFA_BLOQUEADA
                tapada -> ALFA_TAPADA
                else -> 1f
            },
        animationSpec = tween(Duraciones.COLOR),
        label = "hornacina-arte",
    )
    val filtroGris = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(SATURACION_BLOQUEADA) }) }

    val texto = textoEtiqueta ?: textoFijo(etiqueta)
    val conPrecio = etiqueta == EtiquetaHornacina.PRECIO
    val descripcionPrecio = if (conPrecio && precio != null) pluralStringResource(R.plurals.sistema_saldo, precio, precio) else null
    val descripcion = listOfNotNull(nombre, if (conPrecio) descripcionPrecio else texto).joinToString(", ")
    val estado =
        when {
            aro == AroHornacina.PUESTO -> stringResource(R.string.store_label_worn)
            aro == AroHornacina.PROBANDO -> stringResource(R.string.store_label_trying)
            bloqueada -> texto
            else -> null
        }

    Column(
        modifier
            .defaultMinSize(minWidth = Medidas.toqueMinimo, minHeight = Medidas.toqueMinimo)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alTocar)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                if (estado != null) stateDescription = estado
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            Modifier
                .testTag("hornacina-hueco")
                .fillMaxWidth()
                .height(ALTO_HORNACINA)
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                }.clip(BitoFormas.hornacina)
                .background(colores.hornacina)
                .drawWithContent {
                    // el aro es una sombra interior: pinta sobre el fondo y bajo el arte
                    val g = GROSOR_ARO.toPx()
                    if (colorAro.alpha > 0f) {
                        drawRect(colorAro)
                        drawRect(colores.hornacina, topLeft = Offset(g, g), size = Size(size.width - 2 * g, size.height - 2 * g))
                    }
                    drawContent()
                }.clearAndSetSemantics { },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ARTE_AIRE_ABAJO)
                    .sizeIn(maxWidth = ARTE_MAX_ANCHO, maxHeight = ARTE_MAX_ALTO)
                    .drawWithContent {
                        // opacidad y gris de la capa entera: `opacity` y `grayscale` de `.niche img`
                        drawIntoCanvas { lienzo ->
                            val pincel = Paint().apply { alpha = alfaArte }
                            if (bloqueada) pincel.colorFilter = filtroGris
                            lienzo.saveLayer(size.toRect(), pincel)
                            this@drawWithContent.drawContent()
                            lienzo.restore()
                        }
                    },
                contentAlignment = Alignment.Center,
                content = imagen,
            )
        }
        Text(
            nombre,
            Modifier.padding(top = AIRE_NOMBRE).clearAndSetSemantics { },
            style = tipos.t14.copy(fontSize = LETRA, lineHeight = ALTO_LETRA, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            color = colores.tinta,
        )
        Row(
            Modifier.padding(top = AIRE_PRECIO).heightIn(min = ALTO_PRECIO).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(Medidas.e4, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (etiqueta == EtiquetaHornacina.PRECIO) {
                Monedita(MONEDA, Modifier.testTag("hornacina-moneda"))
                if (precio != null) {
                    Text(
                        precio.toString(),
                        style = tipos.t14.copy(lineHeight = ALTO_LETRA, fontWeight = FontWeight.Bold),
                        color = colores.tinta,
                    )
                }
            } else {
                val fuerte = etiqueta == EtiquetaHornacina.PROBANDO
                Text(
                    texto.orEmpty(),
                    style = tipos.t14.copy(lineHeight = ALTO_LETRA, fontWeight = if (fuerte) FontWeight.Bold else FontWeight.Medium),
                    color = if (fuerte) colores.tinta else colores.tintaSuave,
                )
            }
        }
    }
}

@Composable
private fun textoFijo(etiqueta: EtiquetaHornacina): String? =
    when (etiqueta) {
        EtiquetaHornacina.PUESTO -> stringResource(R.string.store_label_worn)
        EtiquetaHornacina.TUYO -> stringResource(R.string.store_label_owned)
        EtiquetaHornacina.PROBANDO -> stringResource(R.string.store_label_trying)
        EtiquetaHornacina.TAPADO -> stringResource(R.string.store_label_covered)
        EtiquetaHornacina.EN_TU_JARDIN -> stringResource(R.string.store_garden_in)
        EtiquetaHornacina.RACHA, EtiquetaHornacina.TEMPORADA, EtiquetaHornacina.LOGRO, EtiquetaHornacina.PRECIO -> null
    }
