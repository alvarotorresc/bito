package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.alvarotc.bito.domain.model.CheekStyle
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.FaceParams
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.delightedParamsOf
import com.alvarotc.bito.domain.model.faceParamsOf
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
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

// Architect review (post-T10 grids): round eyes match the mockup — was 1.4 (oval); 1.0 makes
// rx == ry, a true circle when fully open. Blink still reads fine: ryOpen shrinks toward 0 as
// blink -> 1, same "closing eyelid" motion, just starting from a circle instead of an oval.
private const val EYE_HEIGHT_MULT = 1.0f

private const val CHEEK_Y = 0.58f
private const val CHEEK_DX = 0.19f
private const val BLUSH_RADIUS = 0.055f

// War paint sits right under the eyes (mockup 4a), not down at BLUSH's cheek height. Clear of the
// eye oval's own bottom edge (EYE_Y + eye ry) so it reads as a separate mark, not camouflaged
// against the eye's outline.
private const val WAR_PAINT_Y = 0.55f
private const val WAR_PAINT_DX = 0.145f
private const val WAR_PAINT_LENGTH = 0.09f
private const val WAR_PAINT_WIDTH = 0.03f
private const val WAR_PAINT_ANGLE_DEG = 20f
private const val WAR_PAINT_ALPHA = 0.95f

private const val BROW_Y = 0.34f
private const val BROW_LENGTH = 0.11f
private const val BROW_WIDTH = 0.022f

private const val MOUTH_Y = 0.66f
private const val MOUTH_HALF_WIDTH = 0.08f
private const val MOUTH_STROKE_WIDTH = 0.02f
private const val MOUTH_CURVE_DEPTH = 0.06f
private const val MOUTH_OPEN_BASE_HEIGHT = 0.03f
private const val MOUTH_OPEN_HEIGHT_SCALE = 0.15f

// The "return" curve bows to the OPPOSITE side of the corner line from the main bulge, at this
// fraction of openHeight — straddling the corner line gives the shape real thickness through the
// middle instead of tapering to a razor-thin sliver (a single-sided lens read as a thin stroke).
private const val MOUTH_OPEN_NEAR_FACTOR = 0.22f
private const val SMIRK_SHIFT_X = 0.04f
private const val SMIRK_TILT_DEG = 6f

private const val DORADO_BODY_ITEM = "body-dorado"

// --- Pattern layer (T10) — clipped to the body silhouette. Tint = darkened body tone unless noted
// (llamas). Every glyph list below is a fixed, hand-placed scatter — no randomness at runtime.
// Numbers are art-phase-tunable against design/mockups/m6/4-habi-pantalla.png.
private const val PATTERN_TINT_FACTOR = 0.82f // multiply body RGB for the pattern tint.

private const val MOTAS_RADIUS = 0.03f
private const val MOTAS_SPACING = 0.14f
private const val MOTAS_ROW_OFFSET = MOTAS_SPACING / 2f

private const val RAYITAS_STROKE = 0.02f
private const val RAYITAS_SPACING = 0.12f
private const val RAYITAS_ANGLE_DEG = 45f

private const val HEART_SIZE = 0.05f
private const val STAR_OUTER_RADIUS = 0.045f
private const val STAR_INNER_RATIO = 0.5f
private const val FLOWER_PETAL_RADIUS = 0.018f
private const val FLOWER_PETAL_ORBIT = 0.022f
private const val FLOWER_CENTER_RADIUS = 0.012f
private const val SPARKLE_OUTER_RADIUS = 0.05f
private const val SPARKLE_INNER_RATIO = 0.35f

// Fixed scatter positions (fraction of viewport), one list per glyph pattern — hand-placed inside
// the body's bounding box (cx=0.5±0.34, cy=0.55±0.42); clipPath trims whatever falls outside the
// egg so these don't need to hug the silhouette precisely.
private val CORAZONES_POSITIONS =
    listOf(0.38f to 0.30f, 0.63f to 0.28f, 0.30f to 0.50f, 0.70f to 0.50f, 0.50f to 0.40f, 0.40f to 0.70f, 0.60f to 0.72f)
private val ESTRELLAS_POSITIONS =
    listOf(0.35f to 0.24f, 0.65f to 0.26f, 0.25f to 0.46f, 0.75f to 0.46f, 0.50f to 0.36f, 0.38f to 0.66f, 0.62f to 0.68f)
private val FLORES_POSITIONS =
    listOf(0.36f to 0.28f, 0.64f to 0.30f, 0.28f to 0.52f, 0.72f to 0.52f, 0.50f to 0.44f, 0.50f to 0.72f)
private val CHISPAS_POSITIONS =
    listOf(
        0.34f to 0.26f,
        0.66f to 0.24f,
        0.24f to 0.48f,
        0.76f to 0.48f,
        0.50f to 0.34f,
        0.38f to 0.68f,
        0.62f to 0.70f,
        0.50f to 0.82f,
    )

private const val LLAMA_TINT_BLEND = 0.2f // 0 = pure Brasa; blends toward body color so it still reads as "this body's fire".
private const val LLAMA_BASE_Y = 0.90f // where the body is still wide (not the tapering bottom tip at 0.97).

// Fixed tongues: (base x offset from BODY_CX, height, width) — heights within the 0.15-0.25 spec range.
private val LLAMA_TONGUES =
    listOf(
        Triple(-0.20f, 0.18f, 0.10f),
        Triple(-0.09f, 0.24f, 0.09f),
        Triple(0.01f, 0.20f, 0.11f),
        Triple(0.11f, 0.25f, 0.09f),
        Triple(0.21f, 0.17f, 0.10f),
    )

// --- Upper slot (T10) — anchored at the head top, center x=BODY_CX. Half-widths are checked
// against the egg's own half-width at that y (see eggPath) so hats hug the head instead of
// floating past it; the top hat's brim is the one deliberate exception (brims overhang).
private const val GORRO_DOME_TOP_Y = 0.035f
private const val GORRO_DOME_BOTTOM_Y = 0.185f
private const val GORRO_DOME_RX = 0.15f
private const val GORRO_BAND_TOP_Y = 0.175f
private const val GORRO_BAND_BOTTOM_Y = 0.215f
private const val GORRO_BAND_RX = 0.165f
private const val GORRO_POMPOM_Y = 0.025f
private const val GORRO_POMPOM_RADIUS = 0.028f

// Knot is centered on the body highlight (HIGHLIGHT_CY=0.16) with radius > highlight's own rx/ry
// (0.05/0.035) so it fully covers the highlight — a circle centered on an ellipse's center always
// contains it once its radius exceeds the ellipse's larger semi-axis. Wings pinch to a point at the
// neck, so they can't help with that; the knot alone has to do it.
private const val LAZO_CENTER_Y = HIGHLIGHT_CY
private const val LAZO_WING_HALF_WIDTH = 0.11f
private const val LAZO_WING_HALF_HEIGHT = 0.065f
private const val LAZO_KNOT_RADIUS = 0.06f

// Brim's bottom edge (COPA_BRIM_Y + COPA_BRIM_RY) must reach past the highlight's bottom
// (HIGHLIGHT_CY + HIGHLIGHT_RY = 0.195), and the cylinder must reach the brim's top with no gap.
private const val COPA_CYLINDER_TOP_Y = 0.02f
private const val COPA_CYLINDER_BOTTOM_Y = 0.16f
private const val COPA_CYLINDER_RX = 0.09f
private const val COPA_BAND_TOP_Y = 0.105f
private const val COPA_BAND_BOTTOM_Y = 0.125f
private const val COPA_BRIM_Y = 0.178f
private const val COPA_BRIM_RX = 0.17f
private const val COPA_BRIM_RY = 0.02f

private const val CORONA_BASE_Y = 0.205f
private const val CORONA_BAND_TOP_Y = 0.165f
private const val CORONA_RX = 0.165f
private const val CORONA_PEAK_CENTER_Y = 0.035f
private const val CORONA_PEAK_SIDE_Y = 0.095f
private const val CORONA_VALLEY_Y = 0.14f

// --- Lower slot (T10) — anchored below the body (drawn last, so it always paints over the egg's
// tapering bottom rather than being occluded by it). Both items share the same DX/anchor so a
// single probe point covers either.
private const val LOWER_DX = 0.20f
private const val SOCK_TOP_Y = 0.845f
private const val SOCK_BOTTOM_Y = 0.965f
private const val SOCK_HALF_WIDTH = 0.05f
private const val SOCK_STRIPE_TOP_Y = 0.875f
private const val SOCK_STRIPE_BOTTOM_Y = 0.905f

private const val SNEAKER_TOP_Y = 0.86f
private const val SNEAKER_BOTTOM_Y = 0.95f
private const val SNEAKER_HALF_WIDTH = 0.065f
private const val SNEAKER_SOLE_HEIGHT = 0.022f

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
 * lower (tech doc §7.1, layer order is LAW) — this is the single call site every layer hangs off.
 */
fun DrawScope.drawHabi(
    spec: HabiSpec,
    blink: Float = 0f,
    delighted: Boolean = false,
) {
    val face = if (delighted) delightedParamsOf(spec.personality) else faceParamsOf(spec.mood, spec.personality)
    val vp = HabiViewport(size)
    val eyeColor = HabiPalette.eyeColor(spec.equipped.eyeColor)

    drawBody(vp, spec.equipped.bodyColor)
    drawPattern(vp, spec.equipped.pattern, spec.equipped.bodyColor)
    drawCheeks(vp, face)
    drawEyes(vp, face, blink, eyeColor)
    drawBrows(vp, face)
    drawMouth(vp, face)
    drawUpper(vp, spec.equipped.upper)
    drawLower(vp, spec.equipped.lower)
}

/**
 * Offscreen render for consumers that need a plain bitmap (the widget, T15). Transparent background.
 *
 * Real Android handles the internal `Bitmap.createBitmap(DisplayMetrics, ...)` overload Compose's
 * `ImageBitmap(w, h)` calls just fine. Robolectric does not, under its default LEGACY graphics mode:
 * that overload isn't shadowed there and falls through to a real native call that fails. Any
 * Robolectric test that pixel-checks this function's output needs
 * `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (see HabiDrawingTest) — without it, this call crashes.
 */
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

/** The body/pattern silhouette — extracted so drawBody and drawPattern can't drift apart. */
private fun bodyPath(vp: HabiViewport): Path = eggPath(vp, BODY_CX, BODY_CY, BODY_RX, BODY_RY, BODY_BULGE)

private fun DrawScope.drawBody(
    vp: HabiViewport,
    bodyItemId: String,
) {
    val bodyPath = bodyPath(vp)
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

    // Always on (no upper-slot gating — a hat simply draws over it later, by layer order alone).
    // Clipped to bodyPath: at HIGHLIGHT_CY - HIGHLIGHT_RY it would otherwise poke past the egg's
    // own apex (BODY_CY - BODY_RY) by a visible sliver at real sizes.
    clipPath(bodyPath) {
        val highlight = vp.point(HIGHLIGHT_CX, HIGHLIGHT_CY)
        drawOval(
            color = Tarjeta.copy(alpha = 0.8f),
            topLeft = Offset(highlight.x - vp.len(HIGHLIGHT_RX), highlight.y - vp.len(HIGHLIGHT_RY)),
            size = Size(vp.len(HIGHLIGHT_RX * 2f), vp.len(HIGHLIGHT_RY * 2f)),
        )
    }
}

/** Patterned overlay clipped to the body silhouette; an unknown or null id draws nothing. */
private fun DrawScope.drawPattern(
    vp: HabiViewport,
    patternId: String?,
    bodyItemId: String,
) {
    if (patternId == null) return
    val bodyColor = HabiPalette.bodyColor(bodyItemId)
    val tint = bodyColor.darken()

    clipPath(bodyPath(vp)) {
        when (patternId) {
            "pattern-motas" -> drawPatternMotas(vp, tint)
            "pattern-rayitas" -> drawPatternRayitas(vp, tint)
            "pattern-corazones" -> drawPatternGlyphs(vp, CORAZONES_POSITIONS, tint, ::heartPath)
            "pattern-estrellas" -> drawPatternGlyphs(vp, ESTRELLAS_POSITIONS, tint, ::starPath)
            "pattern-flores" -> drawPatternFlores(vp, tint)
            "pattern-chispas" -> drawPatternGlyphs(vp, CHISPAS_POSITIONS, tint, ::sparklePath)
            "pattern-llamas" -> drawPatternLlamas(vp, lerp(Brasa, bodyColor, LLAMA_TINT_BLEND))
            else -> Unit // unknown catalog id (forward compat): no-op, never crash.
        }
    }
}

/** Staggered grid of dots, alternate rows offset by half the spacing — deterministic, no randomness. */
private fun DrawScope.drawPatternMotas(
    vp: HabiViewport,
    tint: Color,
) {
    val radius = vp.len(MOTAS_RADIUS)
    val rows = (BODY_RY * 2f / MOTAS_SPACING).toInt() + 2
    val cols = (BODY_RX * 2f / MOTAS_SPACING).toInt() + 2
    for (row in 0..rows) {
        val y = BODY_CY - BODY_RY + row * MOTAS_SPACING
        val rowOffset = if (row % 2 == 1) MOTAS_ROW_OFFSET else 0f
        for (col in 0..cols) {
            val x = BODY_CX - BODY_RX - MOTAS_SPACING + rowOffset + col * MOTAS_SPACING
            drawCircle(color = tint, radius = radius, center = vp.point(x, y))
        }
    }
}

/** Diagonal pinstripes: horizontal lines drawn inside a rotated frame, spaced evenly. */
private fun DrawScope.drawPatternRayitas(
    vp: HabiViewport,
    tint: Color,
) {
    val strokeWidth = vp.len(RAYITAS_STROKE)
    val span = BODY_RX.coerceAtLeast(BODY_RY) * 3f // long enough that rotation never leaves a corner uncovered.
    rotate(degrees = RAYITAS_ANGLE_DEG, pivot = vp.point(BODY_CX, BODY_CY)) {
        val lineCount = (BODY_RY * 4f / RAYITAS_SPACING).toInt() + 2
        for (i in 0..lineCount) {
            val y = BODY_CY - BODY_RY * 2f + i * RAYITAS_SPACING
            drawLine(
                color = tint,
                start = vp.point(BODY_CX - span, y),
                end = vp.point(BODY_CX + span, y),
                strokeWidth = strokeWidth,
            )
        }
    }
}

/** Shared driver for the fixed-position glyph patterns (corazones/estrellas/chispas). */
private fun DrawScope.drawPatternGlyphs(
    vp: HabiViewport,
    positions: List<Pair<Float, Float>>,
    tint: Color,
    glyph: (HabiViewport, Float, Float) -> Path,
) {
    for ((x, y) in positions) {
        drawPath(glyph(vp, x, y), color = tint)
    }
}

private fun DrawScope.drawPatternFlores(
    vp: HabiViewport,
    tint: Color,
) {
    for ((x, y) in FLORES_POSITIONS) {
        val orbit = vp.len(FLOWER_PETAL_ORBIT)
        val petalRadius = vp.len(FLOWER_PETAL_RADIUS)
        val center = vp.point(x, y)
        for (i in 0 until 5) {
            val angle = Math.toRadians(-90.0 + 72.0 * i)
            val petalCenter =
                Offset(center.x + orbit * cos(angle).toFloat(), center.y + orbit * sin(angle).toFloat())
            drawCircle(color = tint, radius = petalRadius, center = petalCenter)
        }
        drawCircle(color = tint, radius = vp.len(FLOWER_CENTER_RADIUS), center = center)
    }
}

/** Wavy flame tongues rising from where the body is still wide, not the tapering bottom tip. */
private fun DrawScope.drawPatternLlamas(
    vp: HabiViewport,
    tint: Color,
) {
    for ((dx, height, width) in LLAMA_TONGUES) {
        drawPath(flamePath(vp, BODY_CX + dx, height, width), color = tint)
    }
}

/**
 * The tip leans sideways off-center and each side bulges then pinches on the way up — a straight
 * symmetric triangle reads as a spike, not a flame; the lean + pinch is what makes it flicker.
 */
private fun flamePath(
    vp: HabiViewport,
    baseX: Float,
    height: Float,
    width: Float,
): Path {
    val lean = width * 0.35f
    val leftBase = vp.point(baseX - width / 2f, LLAMA_BASE_Y)
    val rightBase = vp.point(baseX + width / 2f, LLAMA_BASE_Y)
    val tip = vp.point(baseX + lean, LLAMA_BASE_Y - height)
    val leftBulge = vp.point(baseX - width * 0.62f, LLAMA_BASE_Y - height * 0.35f)
    val leftPinch = vp.point(baseX + lean * 0.3f, LLAMA_BASE_Y - height * 0.72f)
    val rightBulge = vp.point(baseX + width * 0.7f, LLAMA_BASE_Y - height * 0.28f)
    val rightPinch = vp.point(baseX + lean * 0.6f, LLAMA_BASE_Y - height * 0.6f)
    return Path().apply {
        moveTo(leftBase.x, leftBase.y)
        cubicTo(leftBulge.x, leftBulge.y, leftPinch.x, leftPinch.y, tip.x, tip.y)
        cubicTo(rightPinch.x, rightPinch.y, rightBulge.x, rightBulge.y, rightBase.x, rightBase.y)
        close()
    }
}

/**
 * A two-lobe heart via cubic Beziers: a center top notch, two rounded lobes bulging out and up,
 * meeting at a bottom tip. Points expressed as (dx, dy) multiples of [HEART_SIZE] from center.
 */
private fun heartPath(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
): Path {
    fun pt(
        dx: Float,
        dy: Float,
    ) = vp.point(cx + dx * HEART_SIZE, cy + dy * HEART_SIZE)

    val notch = pt(0f, -0.35f)
    val tip = pt(0f, 0.9f)
    return Path().apply {
        moveTo(notch.x, notch.y)
        // Left lobe: up and out, around the bulge, down to where it meets the tip curve.
        cubicTo(pt(-0.6f, -1.0f).x, pt(-0.6f, -1.0f).y, pt(-1.3f, -0.15f).x, pt(-1.3f, -0.15f).y, pt(-0.8f, 0.35f).x, pt(-0.8f, 0.35f).y)
        cubicTo(pt(-0.5f, 0.75f).x, pt(-0.5f, 0.75f).y, pt(-0.2f, 1.0f).x, pt(-0.2f, 1.0f).y, tip.x, tip.y)
        // Mirror for the right lobe, back to the notch.
        cubicTo(pt(0.2f, 1.0f).x, pt(0.2f, 1.0f).y, pt(0.5f, 0.75f).x, pt(0.5f, 0.75f).y, pt(0.8f, 0.35f).x, pt(0.8f, 0.35f).y)
        cubicTo(pt(1.3f, -0.15f).x, pt(1.3f, -0.15f).y, pt(0.6f, -1.0f).x, pt(0.6f, -1.0f).y, notch.x, notch.y)
        close()
    }
}

/** N-point star polygon via alternating outer/inner radii — used for estrellas (5pt) and chispas (4pt, spikier). */
private fun starPolygon(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
    points: Int,
    outerRadius: Float,
    innerRatio: Float,
): Path {
    val center = vp.point(cx, cy)
    val innerRadius = outerRadius * innerRatio
    val step = Math.PI / points
    val path = Path()
    for (i in 0 until points * 2) {
        val radius = vp.len(if (i % 2 == 0) outerRadius else innerRadius)
        val angle = -Math.PI / 2 + i * step
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun starPath(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
): Path = starPolygon(vp, cx, cy, points = 5, outerRadius = STAR_OUTER_RADIUS, innerRatio = STAR_INNER_RATIO)

private fun sparklePath(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
): Path = starPolygon(vp, cx, cy, points = 4, outerRadius = SPARKLE_OUTER_RADIUS, innerRatio = SPARKLE_INNER_RATIO)

/** Darkened variant of a body tone for pattern tinting — multiply RGB, alpha untouched. */
private fun Color.darken(factor: Float = PATTERN_TINT_FACTOR): Color =
    copy(red = red * factor, green = green * factor, blue = blue * factor)

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
                    color = Tinta.copy(alpha = WAR_PAINT_ALPHA),
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
            // Sparkles ride on the open eye only — a mid-blink eye is covered by body color, so a
            // sparkle floating over it would read as a stray dot with nothing under it.
            drawSparkles(vp, center, rx, ry, face.sparkles)
        }
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
            // The lens bulges toward mouthCurve's sign: down for a happy open smile (radiant), up
            // for a distressed wail (dramatic cheerleader's mouthOpen=0.8, mouthCurve=-0.8). The
            // return curve bows to the OPPOSITE side (MOUTH_OPEN_NEAR_FACTOR) so the shape straddles
            // the corner line and reads as a genuine open hole, not a thin one-sided sliver.
            val direction = if (face.mouthCurve < 0f) -1f else 1f
            val farY = y + direction * vp.len(MOUTH_OPEN_BASE_HEIGHT + MOUTH_OPEN_HEIGHT_SCALE * face.mouthOpen)
            val nearY = y - direction * vp.len(MOUTH_OPEN_BASE_HEIGHT + MOUTH_OPEN_HEIGHT_SCALE * face.mouthOpen) * MOUTH_OPEN_NEAR_FACTOR
            val path =
                Path().apply {
                    moveTo(centerX - halfWidth, y)
                    quadraticTo(centerX, farY, centerX + halfWidth, y)
                    quadraticTo(centerX, nearY, centerX - halfWidth, y)
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

/** Equipped headwear, drawn over the body's top so it occludes the highlight naturally. */
private fun DrawScope.drawUpper(
    vp: HabiViewport,
    upperId: String?,
) {
    when (upperId) {
        "upper-gorro-lana" -> drawGorroLana(vp)
        "upper-lazo" -> drawLazo(vp)
        "upper-copa" -> drawCopa(vp)
        "upper-corona" -> drawCorona(vp)
        else -> Unit // null or unknown catalog id: no-op, never crash.
    }
}

/** Knitted beanie: dome cap + folded brim band + pompom (mockup 4a: brasa-toned wool, cream pompom). */
private fun DrawScope.drawGorroLana(vp: HabiViewport) {
    val kappa = 0.5522847498f
    val top = vp.point(BODY_CX, GORRO_DOME_TOP_Y)
    val left = vp.point(BODY_CX - GORRO_DOME_RX, GORRO_DOME_BOTTOM_Y)
    val right = vp.point(BODY_CX + GORRO_DOME_RX, GORRO_DOME_BOTTOM_Y)
    val kx = vp.len(GORRO_DOME_RX) * kappa
    val ky = vp.len(GORRO_DOME_BOTTOM_Y - GORRO_DOME_TOP_Y) * kappa
    val dome =
        Path().apply {
            moveTo(left.x, left.y)
            cubicTo(left.x, left.y - ky, top.x - kx, top.y, top.x, top.y)
            cubicTo(top.x + kx, top.y, right.x, right.y - ky, right.x, right.y)
            lineTo(left.x, left.y)
            close()
        }
    drawPath(dome, color = Brasa)

    drawRoundRect(
        color = HabiPalette.GorroLanaBand,
        topLeft = vp.point(BODY_CX - GORRO_BAND_RX, GORRO_BAND_TOP_Y),
        size = Size(vp.len(GORRO_BAND_RX * 2f), vp.len(GORRO_BAND_BOTTOM_Y - GORRO_BAND_TOP_Y)),
        cornerRadius = CornerRadius(vp.len((GORRO_BAND_BOTTOM_Y - GORRO_BAND_TOP_Y) / 2f)),
    )

    drawCircle(color = Tarjeta, radius = vp.len(GORRO_POMPOM_RADIUS), center = vp.point(BODY_CX, GORRO_POMPOM_Y))
}

/** Bow: two rounded "wing" petals pinched to a point at the center, plus a contrasting knot. */
private fun DrawScope.drawLazo(vp: HabiViewport) {
    val wingColor = HabiPalette.LazoTono
    for (side in SIDES) {
        drawPath(bowWingPath(vp, side), color = wingColor)
    }
    drawCircle(color = Brasa, radius = vp.len(LAZO_KNOT_RADIUS), center = vp.point(BODY_CX, LAZO_CENTER_Y))
}

/**
 * A petal shape pinched to a point at the center (neck) and bulging outward past the nominal tip
 * x — that outward overshoot on the belly control point is what reads as a rounded loop rather than
 * a thin triangular sliver.
 */
private fun bowWingPath(
    vp: HabiViewport,
    side: Float,
): Path {
    val neck = vp.point(BODY_CX, LAZO_CENTER_Y)
    val tipTop = vp.point(BODY_CX + side * LAZO_WING_HALF_WIDTH, LAZO_CENTER_Y - LAZO_WING_HALF_HEIGHT)
    val tipBottom = vp.point(BODY_CX + side * LAZO_WING_HALF_WIDTH, LAZO_CENTER_Y + LAZO_WING_HALF_HEIGHT)
    val ctrlUpper = vp.point(BODY_CX + side * LAZO_WING_HALF_WIDTH * 0.5f, LAZO_CENTER_Y - LAZO_WING_HALF_HEIGHT * 1.3f)
    val ctrlBelly = vp.point(BODY_CX + side * LAZO_WING_HALF_WIDTH * 1.35f, LAZO_CENTER_Y)
    val ctrlLower = vp.point(BODY_CX + side * LAZO_WING_HALF_WIDTH * 0.5f, LAZO_CENTER_Y + LAZO_WING_HALF_HEIGHT * 1.3f)
    return Path().apply {
        moveTo(neck.x, neck.y)
        quadraticTo(ctrlUpper.x, ctrlUpper.y, tipTop.x, tipTop.y)
        quadraticTo(ctrlBelly.x, ctrlBelly.y, tipBottom.x, tipBottom.y)
        quadraticTo(ctrlLower.x, ctrlLower.y, neck.x, neck.y)
        close()
    }
}

/** Top hat: cylinder + band + brim (the one upper allowed to overhang the head — brims do). */
private fun DrawScope.drawCopa(vp: HabiViewport) {
    val cylinderSize = Size(vp.len(COPA_CYLINDER_RX * 2f), vp.len(COPA_CYLINDER_BOTTOM_Y - COPA_CYLINDER_TOP_Y))
    drawRect(color = Tinta, topLeft = vp.point(BODY_CX - COPA_CYLINDER_RX, COPA_CYLINDER_TOP_Y), size = cylinderSize)

    drawRect(
        color = Tarjeta,
        topLeft = vp.point(BODY_CX - COPA_CYLINDER_RX, COPA_BAND_TOP_Y),
        size = Size(vp.len(COPA_CYLINDER_RX * 2f), vp.len(COPA_BAND_BOTTOM_Y - COPA_BAND_TOP_Y)),
    )

    val brimCenter = vp.point(BODY_CX, COPA_BRIM_Y)
    drawOval(
        color = Tinta,
        topLeft = Offset(brimCenter.x - vp.len(COPA_BRIM_RX), brimCenter.y - vp.len(COPA_BRIM_RY)),
        size = Size(vp.len(COPA_BRIM_RX * 2f), vp.len(COPA_BRIM_RY * 2f)),
    )
}

/** Exclusive: 3-point zigzag crown on a base band, gold (same family as body-dorado). */
private fun DrawScope.drawCorona(vp: HabiViewport) {
    val baseLeft = vp.point(BODY_CX - CORONA_RX, CORONA_BASE_Y)
    val baseRight = vp.point(BODY_CX + CORONA_RX, CORONA_BASE_Y)
    val bandLeft = vp.point(BODY_CX - CORONA_RX, CORONA_BAND_TOP_Y)
    val bandRight = vp.point(BODY_CX + CORONA_RX, CORONA_BAND_TOP_Y)
    val peakLeft = vp.point(BODY_CX - CORONA_RX * 0.6f, CORONA_PEAK_SIDE_Y)
    val peakCenter = vp.point(BODY_CX, CORONA_PEAK_CENTER_Y)
    val peakRight = vp.point(BODY_CX + CORONA_RX * 0.6f, CORONA_PEAK_SIDE_Y)
    val valleyLeft = vp.point(BODY_CX - CORONA_RX * 0.3f, CORONA_VALLEY_Y)
    val valleyRight = vp.point(BODY_CX + CORONA_RX * 0.3f, CORONA_VALLEY_Y)

    val path =
        Path().apply {
            moveTo(baseLeft.x, baseLeft.y)
            lineTo(bandLeft.x, bandLeft.y)
            lineTo(peakLeft.x, peakLeft.y)
            lineTo(valleyLeft.x, valleyLeft.y)
            lineTo(peakCenter.x, peakCenter.y)
            lineTo(valleyRight.x, valleyRight.y)
            lineTo(peakRight.x, peakRight.y)
            lineTo(bandRight.x, bandRight.y)
            lineTo(baseRight.x, baseRight.y)
            close()
        }
    drawPath(path, color = HabiPalette.CoronaGold)
}

/**
 * Equipped footwear, drawn last: it always paints over the egg's tapering bottom rather than being
 * occluded by it, so the pair reads as peeking out from under the body.
 */
private fun DrawScope.drawLower(
    vp: HabiViewport,
    lowerId: String?,
) {
    when (lowerId) {
        "lower-calcetines" -> drawCalcetines(vp)
        "lower-zapatillas" -> drawZapatillas(vp)
        else -> Unit // null or unknown catalog id: no-op, never crash.
    }
}

/** Two half-capsule socks, Tarjeta with a Hoja stripe. */
private fun DrawScope.drawCalcetines(vp: HabiViewport) {
    val size = Size(vp.len(SOCK_HALF_WIDTH * 2f), vp.len(SOCK_BOTTOM_Y - SOCK_TOP_Y))
    val cornerRadius = CornerRadius(vp.len(SOCK_HALF_WIDTH))
    for (side in SIDES) {
        val centerX = BODY_CX + side * LOWER_DX
        drawRoundRect(color = Tarjeta, topLeft = vp.point(centerX - SOCK_HALF_WIDTH, SOCK_TOP_Y), size = size, cornerRadius = cornerRadius)
        drawRoundRect(
            color = Hoja,
            topLeft = vp.point(centerX - SOCK_HALF_WIDTH, SOCK_STRIPE_TOP_Y),
            size = Size(vp.len(SOCK_HALF_WIDTH * 2f), vp.len(SOCK_STRIPE_BOTTOM_Y - SOCK_STRIPE_TOP_Y)),
            cornerRadius = CornerRadius(vp.len(SOCK_HALF_WIDTH * 0.25f)),
        )
    }
}

/** Two capsule sneakers: Tinta upper, Tarjeta sole. */
private fun DrawScope.drawZapatillas(vp: HabiViewport) {
    val upperSize = Size(vp.len(SNEAKER_HALF_WIDTH * 2f), vp.len(SNEAKER_BOTTOM_Y - SNEAKER_TOP_Y))
    val upperCorner = CornerRadius(vp.len(SNEAKER_HALF_WIDTH * 0.6f))
    for (side in SIDES) {
        val centerX = BODY_CX + side * LOWER_DX
        drawRoundRect(
            color = Tinta,
            topLeft = vp.point(centerX - SNEAKER_HALF_WIDTH, SNEAKER_TOP_Y),
            size = upperSize,
            cornerRadius = upperCorner,
        )
        drawRoundRect(
            color = Tarjeta,
            topLeft = vp.point(centerX - SNEAKER_HALF_WIDTH, SNEAKER_BOTTOM_Y - SNEAKER_SOLE_HEIGHT),
            size = Size(vp.len(SNEAKER_HALF_WIDTH * 2f), vp.len(SNEAKER_SOLE_HEIGHT)),
            cornerRadius = CornerRadius(vp.len(SNEAKER_SOLE_HEIGHT / 2f)),
        )
    }
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
