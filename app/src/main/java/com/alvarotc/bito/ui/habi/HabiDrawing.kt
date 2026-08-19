package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.alvarotc.bito.domain.model.CheekStyle
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.FaceParams
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.faceParamsOf
import com.alvarotc.bito.ui.theme.Mofletes
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import kotlin.math.cos
import kotlin.math.sin

/** What Habi looks like right now: mood x personality resolves the face, [equipped] tints/dresses the body. */
data class HabiSpec(val mood: Mood, val personality: Personality, val equipped: EquippedSet)

// Body — egg/bean path, center (0.5, 0.55), slightly wider at the bottom (BODY_BULGE shifts the
// widest point below center). Numbers are art-phase-tunable against design/mockups/m6/4-habi-pantalla.png.
private const val BODY_CX = 0.5f
private const val BODY_CY = 0.55f
private const val BODY_RX = 0.34f
private const val BODY_RY = 0.42f
private const val BODY_BULGE = 0.05f

private const val HIGHLIGHT_CX = 0.5f
private const val HIGHLIGHT_CY = 0.16f
private const val HIGHLIGHT_RX = 0.05f
private const val HIGHLIGHT_RY = 0.035f

private const val EYE_Y = 0.45f
private const val EYE_DX = 0.145f
private const val EYE_BASE_RX = 0.052f
private const val EYE_HEIGHT_MULT = 1.4f

private const val CHEEK_Y = 0.58f
private const val CHEEK_DX = 0.19f
private const val BLUSH_RADIUS = 0.055f

// War paint sits right under the eyes (mockup 4a), not down at BLUSH's cheek height.
private const val WAR_PAINT_Y = 0.505f
private const val WAR_PAINT_DX = 0.195f
private const val WAR_PAINT_LENGTH = 0.085f
private const val WAR_PAINT_WIDTH = 0.022f
private const val WAR_PAINT_ANGLE_DEG = 20f

private const val BROW_Y = 0.34f
private const val BROW_LENGTH = 0.11f
private const val BROW_WIDTH = 0.022f

private const val MOUTH_Y = 0.66f
private const val MOUTH_HALF_WIDTH = 0.08f
private const val MOUTH_STROKE_WIDTH = 0.02f
private const val MOUTH_CURVE_DEPTH = 0.06f
private const val MOUTH_OPEN_BASE_HEIGHT = 0.03f
private const val MOUTH_OPEN_HEIGHT_SCALE = 0.09f
private const val SMIRK_SHIFT_X = 0.04f
private const val SMIRK_TILT_DEG = 6f

private const val DORADO_BODY_ITEM = "body-dorado"

/**
 * Maps the normalized 0..1 viewport (of `min(size.width, size.height)`) used by every drawing
 * helper below onto the actual [DrawScope] pixels, centering the square viewport in a non-square
 * canvas.
 */
private class HabiViewport(size: Size) {
    val side = size.minDimension
    private val originX = (size.width - side) / 2f
    private val originY = (size.height - side) / 2f

    fun x(nx: Float) = originX + nx * side

    fun y(ny: Float) = originY + ny * side

    fun len(n: Float) = n * side

    fun point(
        nx: Float,
        ny: Float,
    ) = Offset(x(nx), y(ny))
}

/**
 * Paints Habi: body (tinted) -> pattern -> cheeks -> eyes+highlights -> brows -> mouth -> upper ->
 * lower (tech doc §7.1, layer order is LAW). Pattern/upper/lower are not implemented yet (T10) —
 * they are named no-op stubs below so this stays the single call site later tasks extend.
 */
fun DrawScope.drawHabi(
    spec: HabiSpec,
    blink: Float = 0f,
) {
    val face = faceParamsOf(spec.mood, spec.personality)
    val vp = HabiViewport(size)
    val eyeColor = HabiPalette.eyeColor(spec.equipped.eyeColor)

    drawBody(vp, spec.equipped.bodyColor)
    drawPattern(vp, spec.equipped.pattern)
    drawCheeks(vp, face)
    drawEyes(vp, face, blink, eyeColor)
    drawBrows(vp, face)
    drawMouth(vp, face)
    drawUpper(vp, spec.equipped.upper)
    drawLower(vp, spec.equipped.lower)
}

/** Offscreen render for consumers that need a plain bitmap (the widget, T15). Transparent background. */
fun renderHabiBitmap(
    spec: HabiSpec,
    sizePx: Int,
): Bitmap {
    val imageBitmap = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(imageBitmap),
        Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        drawHabi(spec)
    }
    return imageBitmap.asAndroidBitmap()
}

private fun DrawScope.drawBody(
    vp: HabiViewport,
    bodyItemId: String,
) {
    val bodyPath = eggPath(vp, BODY_CX, BODY_CY, BODY_RX, BODY_RY, BODY_BULGE)
    drawPath(bodyPath, color = HabiPalette.bodyColor(bodyItemId))

    if (bodyItemId == DORADO_BODY_ITEM) {
        clipPath(bodyPath) {
            rotate(degrees = -25f, pivot = vp.point(BODY_CX, BODY_CY)) {
                drawRect(
                    color = Tarjeta.copy(alpha = 0.3f),
                    topLeft = vp.point(0.05f, 0.28f),
                    size = Size(vp.len(1.1f), vp.len(0.12f)),
                )
            }
        }
    }

    val highlight = vp.point(HIGHLIGHT_CX, HIGHLIGHT_CY)
    drawOval(
        color = Tarjeta.copy(alpha = 0.8f),
        topLeft = Offset(highlight.x - vp.len(HIGHLIGHT_RX), highlight.y - vp.len(HIGHLIGHT_RY)),
        size = Size(vp.len(HIGHLIGHT_RX * 2f), vp.len(HIGHLIGHT_RY * 2f)),
    )
}

/** T10 adds patterned overlays (motas/rayitas/...); intentionally a no-op until then. */
@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawPattern(
    vp: HabiViewport,
    patternId: String?,
) {
    // no-op: pattern layer arrives in T10.
}

private fun DrawScope.drawCheeks(
    vp: HabiViewport,
    face: FaceParams,
) {
    when (face.cheeks) {
        CheekStyle.NONE -> Unit
        CheekStyle.BLUSH -> {
            for (side in SIDES) {
                val center = vp.point(BODY_CX + side * CHEEK_DX, CHEEK_Y)
                drawCircle(color = Mofletes.copy(alpha = 0.75f), radius = vp.len(BLUSH_RADIUS), center = center)
            }
        }
        CheekStyle.WAR_PAINT -> {
            val length = vp.len(WAR_PAINT_LENGTH)
            val strokeWidth = vp.len(WAR_PAINT_WIDTH)
            for (side in SIDES) {
                val center = vp.point(BODY_CX + side * WAR_PAINT_DX, WAR_PAINT_Y)
                val (dx, dy) = angleOffset(side * -WAR_PAINT_ANGLE_DEG, length / 2f)
                drawLine(
                    color = Tinta.copy(alpha = 0.85f),
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawEyes(
    vp: HabiViewport,
    face: FaceParams,
    blink: Float,
    eyeColor: Color,
) {
    val rx = vp.len(EYE_BASE_RX * face.eyeScale)
    val ry = rx * EYE_HEIGHT_MULT
    val openAmount = (1f - blink).coerceIn(0f, 1f)
    val ryOpen = ry * openAmount

    for (side in SIDES) {
        val center = vp.point(BODY_CX + side * EYE_DX, EYE_Y)
        if (ryOpen > 0f) {
            drawOval(
                color = eyeColor,
                topLeft = Offset(center.x - rx, center.y - ryOpen),
                size = Size(rx * 2f, ryOpen * 2f),
            )
        }
        drawSparkles(vp, center, rx, ry, face.sparkles)
    }
}

private fun DrawScope.drawSparkles(
    vp: HabiViewport,
    eyeCenter: Offset,
    eyeRx: Float,
    eyeRy: Float,
    count: Int,
) {
    if (count <= 0) return
    val spots =
        listOf(
            Offset(eyeCenter.x + eyeRx * 0.55f, eyeCenter.y - eyeRy * 0.75f) to vp.len(0.014f),
            Offset(eyeCenter.x + eyeRx * 0.95f, eyeCenter.y - eyeRy * 0.15f) to vp.len(0.008f),
            Offset(eyeCenter.x + eyeRx * 0.15f, eyeCenter.y - eyeRy * 1.05f) to vp.len(0.006f),
        )
    for ((offset, radius) in spots.take(count.coerceAtMost(spots.size))) {
        drawCircle(color = Tarjeta, radius = radius, center = offset)
    }
}

private fun DrawScope.drawBrows(
    vp: HabiViewport,
    face: FaceParams,
) {
    val angleDeg = face.browAngleDeg ?: return
    val length = vp.len(BROW_LENGTH)
    val strokeWidth = vp.len(BROW_WIDTH)

    for (side in SIDES) {
        val center = vp.point(BODY_CX + side * EYE_DX, BROW_Y)
        // Mirrored so the inner end (toward the nose) dips for a frown: left = -angle, right = +angle.
        val theta = if (side < 0f) -angleDeg else angleDeg
        val (dx, dy) = angleOffset(theta, length / 2f)
        drawLine(
            color = Tinta,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMouth(
    vp: HabiViewport,
    face: FaceParams,
) {
    val shiftX = if (face.smirk) SMIRK_SHIFT_X else 0f
    val tiltDeg = if (face.smirk) SMIRK_TILT_DEG else 0f
    val centerX = vp.x(BODY_CX + shiftX)
    val y = vp.y(MOUTH_Y)
    val halfWidth = vp.len(MOUTH_HALF_WIDTH)

    rotate(degrees = tiltDeg, pivot = Offset(centerX, y)) {
        if (face.mouthOpen > 0.01f) {
            val openHeight = vp.len(MOUTH_OPEN_BASE_HEIGHT + MOUTH_OPEN_HEIGHT_SCALE * face.mouthOpen)
            val path =
                Path().apply {
                    moveTo(centerX - halfWidth, y)
                    quadraticTo(centerX, y + openHeight, centerX + halfWidth, y)
                    quadraticTo(centerX, y + openHeight * 0.35f, centerX - halfWidth, y)
                    close()
                }
            drawPath(path, color = Tinta)
        } else {
            val controlY = y + vp.len(face.mouthCurve * MOUTH_CURVE_DEPTH)
            val path =
                Path().apply {
                    moveTo(centerX - halfWidth, y)
                    quadraticTo(centerX, controlY, centerX + halfWidth, y)
                }
            drawPath(
                path,
                color = Tinta,
                style = Stroke(width = vp.len(MOUTH_STROKE_WIDTH), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** T10 adds the equipped headwear; intentionally a no-op until then. */
@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawUpper(
    vp: HabiViewport,
    upperId: String?,
) {
    // no-op: upper slot arrives in T10.
}

/** T10 adds the equipped footwear; intentionally a no-op until then. */
@Suppress("UNUSED_PARAMETER")
private fun DrawScope.drawLower(
    vp: HabiViewport,
    lowerId: String?,
) {
    // no-op: lower slot arrives in T10.
}

/** -1f (left) / +1f (right) — every paired feature (eyes, cheeks, brows) mirrors across these. */
private val SIDES = listOf(-1f, 1f)

private fun angleOffset(
    angleDeg: Float,
    magnitude: Float,
): Pair<Float, Float> {
    val rad = Math.toRadians(angleDeg.toDouble())
    return (cos(rad).toFloat() * magnitude) to (sin(rad).toFloat() * magnitude)
}

/**
 * A closed egg/bean shape via 4 cubic Beziers: a top point, a bottom point and two side points
 * shifted down by [bulge] from the vertical center — the shorter, rounder curve below the side
 * points reads as "wider at the bottom" than the longer, more tapered curve above them.
 */
private fun eggPath(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    bulge: Float,
): Path {
    val kappa = 0.5522847498f
    val top = vp.point(cx, cy - ry)
    val right = vp.point(cx + rx, cy + bulge)
    val bottom = vp.point(cx, cy + ry)
    val left = vp.point(cx - rx, cy + bulge)
    val rxPx = vp.len(rx)
    val upperSpan = vp.len(ry + bulge) * kappa
    val lowerSpan = vp.len(ry - bulge) * kappa
    val kx = rxPx * kappa

    return Path().apply {
        moveTo(top.x, top.y)
        cubicTo(top.x + kx, top.y, right.x, right.y - upperSpan, right.x, right.y)
        cubicTo(right.x, right.y + lowerSpan, bottom.x + kx, bottom.y, bottom.x, bottom.y)
        cubicTo(bottom.x - kx, bottom.y, left.x, left.y + lowerSpan, left.x, left.y)
        cubicTo(left.x, left.y - upperSpan, top.x - kx, top.y, top.x, top.y)
        close()
    }
}
