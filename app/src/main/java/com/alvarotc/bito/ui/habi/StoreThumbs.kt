package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.HabiSalvia
import com.alvarotc.bito.ui.theme.Tinta
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 36dp store-grid thumbnail per catalog item (GUIA Fidelidad M6: "icono línea trazo-2 del ítem
 * ~36dp" — NOT a mini bean render). BODY_COLOR/EYE_COLOR get a filled swatch of the color itself;
 * PATTERN gets a salvia disc with a small line-art glyph of the pattern; UPPER/LOWER get a simple
 * trazo-2 line icon of the accessory. Exact shapes are art-phase-tunable, like [HabiPalette]'s hexes
 * — this is deliberately simple geometry, not hand-tuned vector art.
 */
@Composable
fun StoreItemThumb(
    item: CatalogItem,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(36.dp)) {
        when (item.category) {
            CustomizationCategory.BODY_COLOR -> drawColorSwatch(HabiPalette.bodyColor(item.id))
            CustomizationCategory.EYE_COLOR -> drawColorSwatch(HabiPalette.eyeColor(item.id))
            CustomizationCategory.PATTERN -> drawPatternSwatch(item.id)
            CustomizationCategory.UPPER -> drawUpperGlyph(item.id)
            CustomizationCategory.LOWER -> drawLowerGlyph(item.id)
        }
    }
}

private fun DrawScope.drawColorSwatch(color: Color) {
    val radius = size.minDimension / 2f * 0.86f
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(color = Borde, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
}

private fun DrawScope.drawPatternSwatch(id: String) {
    drawColorSwatch(HabiSalvia)
    val r = size.minDimension / 2f * 0.55f
    when (id) {
        "pattern-motas" -> {
            val dotR = size.minDimension * 0.05f
            listOf(
                Offset(center.x - r * 0.45f, center.y - r * 0.35f),
                Offset(center.x + r * 0.4f, center.y - r * 0.05f),
                Offset(center.x - r * 0.1f, center.y + r * 0.5f),
            ).forEach { drawCircle(color = Tinta, radius = dotR, center = it) }
        }
        "pattern-rayitas" -> {
            val step = size.minDimension * 0.2f
            for (i in -1..1) {
                val x = center.x + i * step
                drawLine(
                    Tinta,
                    Offset(x - r * 0.35f, center.y - r * 0.75f),
                    Offset(x + r * 0.35f, center.y + r * 0.75f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        "pattern-corazones" -> drawHeart(center, r, Tinta)
        "pattern-estrellas" -> drawFourPointStar(center, r, r * 0.35f, Tinta)
        "pattern-flores" -> drawFlower(center, r, Tinta)
        "pattern-chispas" -> {
            drawFourPointStar(Offset(center.x - r * 0.3f, center.y - r * 0.2f), r * 0.55f, r * 0.2f, Tinta)
            drawFourPointStar(Offset(center.x + r * 0.4f, center.y + r * 0.35f), r * 0.32f, r * 0.12f, Tinta)
        }
        "pattern-llamas" -> drawFlame(center, r, Tinta)
        else -> drawCircle(color = Tinta, radius = r * 0.2f, center = center)
    }
}

private fun DrawScope.drawUpperGlyph(id: String) {
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = size.minDimension / 2f * 0.75f
    when (id) {
        "upper-gorro-lana" -> {
            val path =
                Path().apply {
                    moveTo(center.x - r, center.y + r * 0.5f)
                    cubicTo(center.x - r, center.y - r * 0.9f, center.x + r, center.y - r * 0.9f, center.x + r, center.y + r * 0.5f)
                }
            drawPath(path, color = Tinta, style = stroke)
            drawLine(
                Tinta,
                Offset(center.x - r, center.y + r * 0.5f),
                Offset(center.x + r, center.y + r * 0.5f),
                strokeWidth = stroke.width,
            )
            drawCircle(Tinta, radius = r * 0.14f, center = Offset(center.x, center.y - r * 0.9f))
        }
        "upper-lazo" -> {
            val left =
                Path().apply {
                    moveTo(center.x - r * 0.15f, center.y)
                    lineTo(center.x - r, center.y - r * 0.55f)
                    lineTo(center.x - r, center.y + r * 0.55f)
                    close()
                }
            val right =
                Path().apply {
                    moveTo(center.x + r * 0.15f, center.y)
                    lineTo(center.x + r, center.y - r * 0.55f)
                    lineTo(center.x + r, center.y + r * 0.55f)
                    close()
                }
            drawPath(left, color = Tinta, style = stroke)
            drawPath(right, color = Tinta, style = stroke)
            drawCircle(Tinta, radius = r * 0.18f, center = center)
        }
        "upper-copa" -> {
            val brimY = center.y + r * 0.5f
            drawLine(Tinta, Offset(center.x - r, brimY), Offset(center.x + r, brimY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawRect(
                color = Tinta,
                topLeft = Offset(center.x - r * 0.55f, center.y - r * 0.9f),
                size = Size(r * 1.1f, r * 1.4f),
                style = stroke,
            )
        }
        "upper-corona" -> {
            val baseY = center.y + r * 0.5f
            val path =
                Path().apply {
                    moveTo(center.x - r, baseY)
                    lineTo(center.x - r, center.y - r * 0.1f)
                    lineTo(center.x - r * 0.5f, center.y + r * 0.2f)
                    lineTo(center.x, center.y - r * 0.9f)
                    lineTo(center.x + r * 0.5f, center.y + r * 0.2f)
                    lineTo(center.x + r, center.y - r * 0.1f)
                    lineTo(center.x + r, baseY)
                    close()
                }
            drawPath(path, color = Tinta, style = stroke)
        }
        else -> drawCircle(color = Tinta, radius = r * 0.5f, center = center, style = stroke)
    }
}

private fun DrawScope.drawLowerGlyph(id: String) {
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = size.minDimension / 2f * 0.75f
    when (id) {
        "lower-calcetines" -> {
            val path =
                Path().apply {
                    moveTo(center.x - r * 0.35f, center.y - r)
                    lineTo(center.x - r * 0.35f, center.y + r * 0.3f)
                    cubicTo(
                        center.x - r * 0.35f,
                        center.y + r * 0.9f,
                        center.x + r * 0.4f,
                        center.y + r * 0.9f,
                        center.x + r,
                        center.y + r * 0.6f,
                    )
                    lineTo(center.x + r, center.y + r * 0.15f)
                    lineTo(center.x + r * 0.35f, center.y + r * 0.15f)
                    lineTo(center.x + r * 0.35f, center.y - r)
                    close()
                }
            drawPath(path, color = Tinta, style = stroke)
            drawLine(
                Tinta,
                Offset(center.x - r * 0.35f, center.y - r * 0.55f),
                Offset(center.x + r * 0.35f, center.y - r * 0.55f),
                strokeWidth = stroke.width,
            )
        }
        "lower-zapatillas" -> {
            val path =
                Path().apply {
                    moveTo(center.x - r, center.y + r * 0.3f)
                    lineTo(center.x - r, center.y - r * 0.1f)
                    cubicTo(
                        center.x - r * 0.6f,
                        center.y - r * 0.5f,
                        center.x - r * 0.1f,
                        center.y - r * 0.5f,
                        center.x + r * 0.1f,
                        center.y - r * 0.15f,
                    )
                    lineTo(center.x + r * 0.9f, center.y - r * 0.15f)
                    cubicTo(
                        center.x + r * 1.1f,
                        center.y - r * 0.15f,
                        center.x + r * 1.1f,
                        center.y + r * 0.3f,
                        center.x + r * 0.8f,
                        center.y + r * 0.3f,
                    )
                    close()
                }
            drawPath(path, color = Tinta, style = stroke)
            drawLine(
                Tinta,
                Offset(center.x - r, center.y + r * 0.3f),
                Offset(center.x + r * 0.8f, center.y + r * 0.3f),
                strokeWidth = stroke.width,
            )
        }
        else -> drawCircle(color = Tinta, radius = r * 0.5f, center = center, style = stroke)
    }
}

private fun DrawScope.drawHeart(
    center: Offset,
    r: Float,
    color: Color,
) {
    val topY = center.y - r * 0.3f
    val path =
        Path().apply {
            moveTo(center.x, center.y + r * 0.9f)
            cubicTo(center.x - r * 1.3f, center.y + r * 0.1f, center.x - r * 0.8f, topY - r * 0.9f, center.x, topY - r * 0.1f)
            cubicTo(center.x + r * 0.8f, topY - r * 0.9f, center.x + r * 1.3f, center.y + r * 0.1f, center.x, center.y + r * 0.9f)
            close()
        }
    drawPath(path, color = color)
}

private fun DrawScope.drawFlower(
    center: Offset,
    r: Float,
    color: Color,
) {
    val petalR = r * 0.4f
    val petalDist = r * 0.55f
    for (i in 0 until 5) {
        val angle = Math.toRadians((i * 72 - 90).toDouble())
        val petalCenter = Offset(center.x + (petalDist * cos(angle)).toFloat(), center.y + (petalDist * sin(angle)).toFloat())
        drawCircle(color = color, radius = petalR, center = petalCenter)
    }
    drawCircle(color = color, radius = petalR * 0.6f, center = center)
}

private fun DrawScope.drawFlame(
    center: Offset,
    r: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(center.x, center.y - r)
            cubicTo(center.x + r * 0.9f, center.y - r * 0.2f, center.x + r * 0.5f, center.y + r * 0.6f, center.x, center.y + r)
            cubicTo(center.x - r * 0.5f, center.y + r * 0.6f, center.x - r * 0.9f, center.y - r * 0.2f, center.x, center.y - r)
            close()
        }
    drawPath(path, color = color)
}

/** A simple 4-point sparkle star (same silhouette family as [com.alvarotc.bito.ui.icons.BitoIcons.Sparkle]). */
private fun DrawScope.drawFourPointStar(
    center: Offset,
    outerR: Float,
    innerR: Float,
    color: Color,
) {
    val path = Path()
    val degrees = listOf(-90.0, -45.0, 0.0, 45.0, 90.0, 135.0, 180.0, 225.0)
    degrees.forEachIndexed { i, deg ->
        val rad = Math.toRadians(deg)
        val r = if (i % 2 == 0) outerR else innerR
        val x = center.x + (r * cos(rad)).toFloat()
        val y = center.y + (r * sin(rad)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}
