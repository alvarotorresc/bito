package com.alvarotc.bito.ui.habi

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

// La capa aparte de la cara con disfraz: solo se lee, así que una para todos los fotogramas.
private val MaskLayerPaint = Paint()

/**
 * Pinta [layers] en este DrawScope: el lienzo del rig encajado con [rigFit] (centrado y apoyado
 * abajo), cada pieza en su rectángulo del atlas más su desplazamiento, con su tinte y su opacidad.
 * Lo usan los dos pintores (spec §3.2): el de Compose y el bitmap de los widgets. Una clave que no
 * esté en el atlas, o una pieza que no se pueda decodificar, se salta sin romper nada.
 *
 * Cada tramo seguido de capas `masked` (la cara con disfraz) se pinta en una capa aparte que se
 * cierra con el hueco del disfraz en `DstIn`: la cara solo se ve por el hueco, que no se mueve con
 * ella. Si el hueco falta o no se decodifica, la cara se pinta sin recortar.
 */
internal fun DrawScope.drawHabiLayers(
    art: HabiArt,
    layers: List<HabiLayer>,
    sample: Int,
) {
    val fit = rigFit(size.width, size.height)
    val hole = layers.firstNotNullOfOrNull { HabiAtlas.costumeFaces[it.key] }?.mask
    val holeImage = hole?.let { art.image(it.res, sample) }
    var i = 0
    while (i < layers.size) {
        val layer = layers[i]
        if (!layer.masked || hole == null || holeImage == null) {
            drawLayer(art, fit, layer, sample)
            i++
            continue
        }
        // DstIn solo actúa donde se pinta el hueco: fuera de su rectángulo lo recorta el clip.
        val holeRect =
            Rect(
                fit.originX + hole.left * fit.scale,
                fit.originY + hole.top * fit.scale,
                fit.originX + (hole.left + hole.width) * fit.scale,
                fit.originY + (hole.top + hole.height) * fit.scale,
            )
        val canvas = drawContext.canvas
        canvas.saveLayer(holeRect, MaskLayerPaint)
        canvas.clipRect(holeRect)
        while (i < layers.size && layers[i].masked) {
            drawLayer(art, fit, layers[i], sample)
            i++
        }
        drawPiece(holeImage, fit, hole, 0f, 0f, 1f, null, BlendMode.DstIn)
        canvas.restore()
    }
}

private fun DrawScope.drawLayer(
    art: HabiArt,
    fit: RigFit,
    layer: HabiLayer,
    sample: Int,
) {
    val piece = HabiAtlas.pieces[layer.key] ?: return
    val res = if (layer.iris) piece.iris ?: return else piece.res
    val image = art.image(res, sample) ?: return
    val filter = layer.tint?.let { art.filter(listOf(it)) }
    drawPiece(image, fit, piece, layer.dx, layer.dy, layer.alpha, filter, BlendMode.SrcOver)
}

/** [image] estirada sobre el rectángulo de [piece] más ([dx], [dy]), en coordenadas del cuerpo. */
private fun DrawScope.drawPiece(
    image: ImageBitmap,
    fit: RigFit,
    piece: AtlasPiece,
    dx: Float,
    dy: Float,
    alpha: Float,
    colorFilter: ColorFilter?,
    blendMode: BlendMode,
) {
    val left = fit.originX + (piece.left + dx) * fit.scale
    val top = fit.originY + (piece.top + dy) * fit.scale
    val scaleX = piece.width * fit.scale / image.width
    val scaleY = piece.height * fit.scale / image.height
    withTransform({
        translate(left, top)
        scale(scaleX, scaleY, pivot = Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width, image.height),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = colorFilter,
            blendMode = blendMode,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * Habi sin animación en un bitmap transparente de [sizePx] × [sizePx], para los widgets. Mismas capas y
 * mismo pintor ([drawHabiLayers]) que la app, sobre un `android.graphics.Canvas`. En Robolectric
 * necesita `@GraphicsMode(NATIVE)` (ver HabiPaintingTest).
 */
fun renderHabiBitmap(
    context: Context,
    spec: HabiSpec,
    sizePx: Int,
): Bitmap {
    val art = HabiArt.get(context)
    val imageBitmap = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(imageBitmap),
        Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        drawHabiLayers(art, habiLayers(spec), sampleSizeFor(sizePx.toFloat(), drawHeightDp = 0f))
    }
    return imageBitmap.asAndroidBitmap()
}
