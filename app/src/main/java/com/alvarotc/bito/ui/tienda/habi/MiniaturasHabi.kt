package com.alvarotc.bito.ui.tienda.habi

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.ui.habi.ColorTint
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.HabiAtlas
import com.alvarotc.bito.ui.habi.HabiPieceImage
import com.alvarotc.bito.ui.habi.HabiTintTable
import com.alvarotc.bito.ui.habi.sampleSizeFor
import kotlin.math.min

// (sin token) `.item .ojos` de tienda-habi.css: dos ojos de 30 dp con 4 dp de hueco, 60 dp de alto.
private val OJO_ANCHO = 30.dp
private val OJOS_HUECO = 4.dp
private val OJOS_ALTO = 60.dp

/**
 * Para las pruebas de geometría: si no es nulo, cada miniatura se sustituye por esta caja y no se decodifica
 * ningún webp. La app nunca lo fija.
 */
internal val LocalMiniaturaFalsa = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** Las piezas del atlas cuyo rectángulo es el del cuerpo entero: para la miniatura hay una capa recortada. */
@DrawableRes
internal fun miniaturaLimpia(id: String): Int? =
    when (id) {
        "head-gorro-lana" -> R.drawable.store_thumb_head_gorro_lana
        "face-gafas-redondas" -> R.drawable.store_thumb_face_gafas_redondas
        "neck-bufanda" -> R.drawable.store_thumb_neck_bufanda
        else -> null
    }

/**
 * La miniatura de una pieza de la tira, a rellenar la caja que le da la hornacina: colores con el
 * cuerpo teñido, ojos con el iris teñido y el resto con su capa (la limpia donde el atlas arrastra el
 * cuerpo). Nada pintado nuevo: salen de las capas de Habi.
 */
@Composable
fun MiniaturaPieza(
    item: CatalogItem,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    when (item.category) {
        CustomizationCategory.BODY_COLOR -> PiezaCapas("cuerpo", HabiTintTable.body[item.id], modifier)
        CustomizationCategory.EYE_COLOR ->
            Row(
                modifier.height(OJOS_ALTO),
                horizontalArrangement = Arrangement.spacedBy(OJOS_HUECO, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PiezaCapas("ojo-abierto-i", HabiTintTable.eyes[item.id], Modifier.size(OJO_ANCHO))
                PiezaCapas("ojo-abierto-d", HabiTintTable.eyes[item.id], Modifier.size(OJO_ANCHO))
            }
        else -> PiezaSuelta(item.id, modifier)
    }
}

/** La pieza [id] sola, encajada en su caja. */
@Composable
internal fun PiezaSuelta(
    id: String,
    modifier: Modifier,
) {
    LocalMiniaturaFalsa.current?.let {
        it(modifier)
        return
    }
    val limpia = miniaturaLimpia(id)
    if (limpia != null) {
        Image(painterResource(limpia), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        HabiPieceImage(id, modifier)
    }
}

/** El dibujo del nudo de cada ranura en la raíz (`RANURAS` del mock): fijo por ranura, no lo que llevas. */
@Composable
fun MiniaturaRiel(ranura: RanuraHabi) {
    val caja = Modifier.size(width = 28.dp, height = 26.dp)
    when (ranura) {
        RanuraHabi.CABEZA -> PiezaSuelta("head-boina", caja)
        RanuraHabi.CARA -> PiezaSuelta("face-gafas-redondas", caja)
        RanuraHabi.CUELLO -> PiezaSuelta("neck-bufanda", caja)
        RanuraHabi.PIES -> PiezaSuelta("feet-botas-agua", caja)
        RanuraHabi.COLORES -> PiezaCapas("cuerpo", HabiTintTable.body["body-lavanda"], caja)
        RanuraHabi.OJOS -> PiezaCapas("ojo-abierto-i", null, caja)
        RanuraHabi.DISFRACES -> PiezaSuelta("costume-calabaza", caja)
    }
}

/**
 * La pieza [clave] del atlas encajada y centrada en la caja. Con [tinte]: el cuerpo entero teñido, o
 * (si la pieza tiene máscara de iris) solo el iris, como los pinta el juego.
 */
@Composable
internal fun PiezaCapas(
    clave: String,
    tinte: ColorTint?,
    modifier: Modifier,
) {
    LocalMiniaturaFalsa.current?.let {
        it(modifier)
        return
    }
    val context = LocalContext.current
    val arte = remember(context) { HabiArt.get(context) }
    val pieza = HabiAtlas.pieces[clave] ?: return
    val altoOrigen = pieza.height * HabiAtlas.SOURCE_CANVAS_PX / HabiAtlas.canvas.height
    Canvas(modifier) {
        val muestreo = sampleSizeFor(size.height, size.height / density, altoOrigen)
        val base = arte.image(pieza.res, muestreo) ?: return@Canvas
        val escala = min(size.width / base.width, size.height / base.height)
        withTransform({
            translate((size.width - base.width * escala) / 2f, (size.height - base.height * escala) / 2f)
            scale(escala, escala, pivot = Offset.Zero)
        }) {
            if (pieza.iris == null) {
                dibuja(base, tinte?.let { arte.filter(listOf(it)) })
            } else {
                dibuja(base, null)
                val iris = arte.image(pieza.iris, muestreo)
                if (iris != null && tinte != null) dibuja(iris, arte.filter(listOf(tinte)))
            }
        }
    }
}

private fun DrawScope.dibuja(
    imagen: ImageBitmap,
    filtro: androidx.compose.ui.graphics.ColorFilter?,
) = drawImage(
    image = imagen,
    srcOffset = IntOffset.Zero,
    srcSize = IntSize(imagen.width, imagen.height),
    dstOffset = IntOffset.Zero,
    dstSize = IntSize(imagen.width, imagen.height),
    colorFilter = filtro,
    filterQuality = FilterQuality.Medium,
)
