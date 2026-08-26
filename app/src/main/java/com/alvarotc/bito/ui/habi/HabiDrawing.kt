package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sqrt

/**
 * What Habi looks like right now: mood x personality resolves the face, [equipped] tints/dresses
 * the body.
 *
 * [bodyToneOverride] and [closedEyes] are SCENE-ONLY opts, both null/false everywhere in the app
 * proper: the canon (design/habi/habi-moods.html) keeps the body at the same full-saturation fill
 * in every mood ("la cara ES el mood") and renders WILTED with heavy but open lids
 * ([WILTED_EYELID_DROOP]) — so no mood ever maps to either. A static illustration that needs the
 * desaturated body or the closed-lid face of its mockup (onboarding 7b's slumped sofa Habi) opts
 * in explicitly, keeping its sampled tone as a scene-local constant.
 */
data class HabiSpec(
    val mood: Mood,
    val personality: Personality,
    val equipped: EquippedSet,
    /** Replaces the equipped catalog body color (pattern tint and shading follow it); null = catalog color. */
    val bodyToneOverride: Color? = null,
    /** Draws each eye as a sagging closed arc with a faint lash mark below, instead of the open oval. */
    val closedEyes: Boolean = false,
)

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

// Soft ambient-occlusion gradient toward the egg's underside (art pass 2026-08-25): a single flat
// fill read as a sticker, and the flatness was worst with a pattern equipped. The shade is the
// body's own tone darkened — never a gray — and it paints OVER the pattern so print dims into the
// shadow exactly like the body does. Starts below the face cluster so the mouth stays on clean
// ground and the body-center test pixel (0.5, 0.55) is untouched.
private const val SHADE_TOP_Y = 0.78f
private const val SHADE_ALPHA = 0.11f
private const val SHADE_TONE_FACTOR = 0.7f

// Art pass (2026-08-25, remeasured against design/habi/habi-moods.html): the first draft floated
// the eyes high and wide, which read scribbled. The mockup's face cluster sits LOWER and tighter —
// a taller forehead over closer-set, slightly smaller eyes is what makes the bean read young and
// tender instead of startled.
private const val EYE_Y = 0.5f
private const val EYE_DX = 0.14f
private const val EYE_BASE_RX = 0.048f

// Architect review (post-T10 grids): round eyes match the mockup — was 1.4 (oval); 1.0 makes
// rx == ry, a true circle when fully open. Blink still reads fine: ryOpen shrinks toward 0 as
// blink -> 1, same "closing eyelid" motion, just starting from a circle instead of an oval.
private const val EYE_HEIGHT_MULT = 1.0f

// A closing eye shrinks toward its BOTTOM edge (the oval's center rides down as it closes), which
// reads as an upper lid descending; the previous symmetric shrink read as a robotic squint.
private const val EYELID_BIAS = 0.55f

// WILTED keeps its eyes heavy-lidded (mockup: relaxed, nearly-closed arcs) — a resting droop on
// the same closing axis a blink uses, so the two compose instead of fighting.
private const val WILTED_EYELID_DROOP = 0.42f

// Closed-lid variant (HabiSpec.closedEyes, onboarding 7b): each eye is a round-capped arc that
// SAGS below its endpoints — the tips are the high points, the opposite bow of the canon mustia
// card's upward arcs — with a fainter, narrower echo arc (the lash mark) under it. Measured on
// design/mockups/m9-7b-onboarding-historia-1-sofa.png at body scale (avatar box ~106px): arcs
// rest ~5px under the open eye's center line, 13px wide, sagging ~2px with a ~3.6px stroke;
// lashes sit 7.5px further down, 9px wide, at ~28% ink over the body.
private const val CLOSED_EYE_DROP = 0.05f
private const val CLOSED_EYE_HALF_WIDTH = 0.06f
private const val CLOSED_EYE_SAG = 0.018f
private const val CLOSED_EYE_STROKE = 0.034f
private const val LASH_DROP = 0.07f
private const val LASH_HALF_WIDTH = 0.04f
private const val LASH_SAG = 0.01f
private const val LASH_STROKE = 0.024f
private const val LASH_ALPHA = 0.28f

// Sparkle radii scale WITH the eye (fractions of its rx) so a big radiant eye and a small corner
// avatar keep the same glint proportions — they were fixed lengths before and drifted at extremes.
private val SPARKLE_RADII = listOf(0.27f, 0.155f, 0.115f)

// Maximum eye shift for a full gaze (-1..1 per axis) — a dart of the eyes toward the finger, small
// enough that the eye never leaves its socket area.
private const val GAZE_SHIFT_X = 0.014f
private const val GAZE_SHIFT_Y = 0.01f

private const val CHEEK_Y = 0.615f
private const val CHEEK_DX = 0.2f
private const val BLUSH_RADIUS = 0.05f

// Blush warms with the smile (mockup alphas: .55 mustia, .7 normal, .8 radiante) — tying it to
// mouthCurve makes the fade ride the same morph the mouth animates on, for free.
private const val BLUSH_ALPHA_BASE = 0.55f
private const val BLUSH_ALPHA_SMILE_GAIN = 0.2f

// War paint sits right under the eyes (mockup 4a), not down at BLUSH's cheek height. Clear of the
// eye oval's own bottom edge (EYE_Y + eye ry = 0.548) so it reads as a separate mark, not
// camouflaged against the eye's outline.
private const val WAR_PAINT_Y = 0.6f
private const val WAR_PAINT_DX = 0.14f
private const val WAR_PAINT_LENGTH = 0.09f
private const val WAR_PAINT_WIDTH = 0.03f
private const val WAR_PAINT_ANGLE_DEG = 20f
private const val WAR_PAINT_ALPHA = 0.95f

// Brows hover just over the eyes (mockup: almost touching), not up on the forehead — distance
// from the eye is what turned "angry" into "surprised" in the first draft.
private const val BROW_Y = 0.42f
private const val BROW_LENGTH = 0.12f
private const val BROW_WIDTH = 0.026f

// Mouth strokes match the mockup's chunky trazo (4/120 ≈ 0.033; a touch under so the big stage
// avatar doesn't turn cartoonish), and the mouth WIDENS with joy: rest width plus gains for an
// open mouth and a positive curve, like the mockup's narrow "normal" vs wide "radiante".
private const val MOUTH_Y = 0.66f
private const val MOUTH_HALF_WIDTH = 0.065f
private const val MOUTH_WIDTH_OPEN_GAIN = 0.022f
private const val MOUTH_WIDTH_SMILE_GAIN = 0.02f
private const val MOUTH_STROKE_WIDTH = 0.028f
private const val MOUTH_CURVE_DEPTH = 0.085f

// DRAMATIC's closed mouth is the mockup's worry wave (an S through the corner line), morphed in by
// HabiFaceMotion.mouthWobble so a mood change bends the line instead of swapping shapes.
private const val MOUTH_WOBBLE_AMP = 0.016f
private const val MOUTH_SAMPLES = 20
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

// --- Volume wrap (art pass 2026-08-25). The patterns used to be flat prints pasted over the egg
// and chopped mid-glyph by the clip — a sticker look. Hand-placed positions are now read as
// coordinates ON the surface and projected to screen like print on a curved toy: screen radius
// follows sin (cells crowd toward the limb), glyph scale follows cos (they shrink there), and the
// inset keeps every glyph whole, just shy of the silhouette.
private const val WRAP_EDGE_INSET = 0.94f
private const val WRAP_MIN_SCALE = 0.45f

private const val MOTAS_RADIUS = 0.028f
private const val MOTAS_SPACING = 0.13f
private const val MOTAS_ROW_OFFSET = MOTAS_SPACING / 2f

private const val RAYITAS_STROKE = 0.02f
private const val RAYITAS_SPACING = 0.12f
private const val RAYITAS_ANGLE_DEG = 45f
private const val RAYITAS_SAMPLES = 24

private const val HEART_SIZE = 0.044f
private const val STAR_OUTER_RADIUS = 0.042f
private const val STAR_INNER_RATIO = 0.5f
private const val FLOWER_PETAL_RADIUS = 0.016f
private const val FLOWER_PETAL_ORBIT = 0.02f
private const val FLOWER_CENTER_RADIUS = 0.011f
private const val SPARKLE_OUTER_RADIUS = 0.045f
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
 * Continuous face channels [HabiAvatar] animates between moods, so an expression MORPHS (lids,
 * brow, mouth curve) instead of snapping frame to frame. [face] is the resolved [FaceParams],
 * possibly mid-interpolation; the floats are 0..1 progress for traits [FaceParams] keeps discrete:
 * [smirkProgress] slides the Sargento's mouth off-center, [eyelidDroop] half-closes WILTED's heavy
 * lids, [mouthWobble] bends DRAMATIC's worry wave into the mouth line. Resting values (what a
 * static render shows) come from [restingFaceMotion].
 */
data class HabiFaceMotion(
    val face: FaceParams,
    val smirkProgress: Float,
    val eyelidDroop: Float,
    val mouthWobble: Float,
)

/** The settled face for a spec — every channel at its target, no interpolation. */
fun restingFaceMotion(
    spec: HabiSpec,
    delighted: Boolean = false,
): HabiFaceMotion {
    val face = if (delighted) delightedParamsOf(spec.personality) else faceParamsOf(spec.mood, spec.personality)
    return HabiFaceMotion(
        face = face,
        smirkProgress = if (face.smirk) 1f else 0f,
        eyelidDroop = if (!delighted && spec.mood == Mood.WILTED) WILTED_EYELID_DROOP else 0f,
        mouthWobble = if (!delighted && spec.mood == Mood.DRAMATIC) 1f else 0f,
    )
}

/**
 * Paints Habi: body (tinted) -> pattern -> shading -> cheeks -> eyes+highlights -> brows -> mouth
 * -> upper -> lower (tech doc §7.1, layer order is LAW; the shade/highlight pass is part of the
 * body layer, lifted above the pattern so light sits ON the printed body, not under it) — this is
 * the single call site every layer hangs off.
 *
 * [motion] and [gaze] are the avatar's live animation channels; static consumers (widget bitmap,
 * frozen test frames) leave the defaults and get the resting expression.
 */
fun DrawScope.drawHabi(
    spec: HabiSpec,
    blink: Float = 0f,
    delighted: Boolean = false,
    motion: HabiFaceMotion? = null,
    gaze: Offset = Offset.Zero,
) {
    val resolved = motion ?: restingFaceMotion(spec, delighted)
    val face = resolved.face
    val vp = HabiViewport(size)
    val eyeColor = HabiPalette.eyeColor(spec.equipped.eyeColor)
    // One resolved tone feeds body, pattern tint and shading, so a scene override mutes all three
    // together — a desaturated body under a full-saturation pattern would give the sticker look
    // right back.
    val bodyTone = spec.bodyToneOverride ?: HabiPalette.bodyColor(spec.equipped.bodyColor)

    drawBody(vp, bodyTone, spec.equipped.bodyColor)
    drawPattern(vp, spec.equipped.pattern, bodyTone)
    drawBodyShading(vp, bodyTone)
    drawCheeks(vp, face)
    // Closed lids are a whole-eye replacement, not a closure amount: blink, droop, gaze and
    // sparkles all describe an OPEN eye, so none of them apply over the arcs.
    if (spec.closedEyes) {
        drawClosedEyes(vp, eyeColor)
    } else {
        drawEyes(vp, face, blink, resolved.eyelidDroop, gaze, eyeColor)
    }
    drawBrows(vp, face)
    drawMouth(vp, face, resolved.smirkProgress, resolved.mouthWobble)
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
    bodyTone: Color,
    bodyItemId: String,
) {
    val bodyPath = bodyPath(vp)
    drawPath(bodyPath, color = bodyTone)

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
}

/**
 * The body layer's light: a soft underside shade (vertical gradient of the darkened body tone)
 * plus the top highlight. Drawn AFTER the pattern so both the body and its print dim into the
 * shadow and the highlight reads as light ON the printed surface — with it underneath, a pattern
 * flattened the whole bean back into a sticker.
 */
private fun DrawScope.drawBodyShading(
    vp: HabiViewport,
    bodyTone: Color,
) {
    val bodyPath = bodyPath(vp)
    val shadeTone = bodyTone.darken(SHADE_TONE_FACTOR)

    clipPath(bodyPath) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    0f to shadeTone.copy(alpha = 0f),
                    1f to shadeTone.copy(alpha = SHADE_ALPHA),
                    startY = vp.y(SHADE_TOP_Y),
                    endY = vp.y(BODY_CY + BODY_RY),
                ),
            topLeft = vp.point(BODY_CX - BODY_RX, SHADE_TOP_Y),
            size = Size(vp.len(BODY_RX * 2f), vp.len(BODY_CY + BODY_RY - SHADE_TOP_Y)),
        )

        // Always on (no upper-slot gating — a hat simply draws over it later, by layer order
        // alone). Clipped: at HIGHLIGHT_CY - HIGHLIGHT_RY it would otherwise poke past the egg's
        // own apex (BODY_CY - BODY_RY) by a visible sliver at real sizes.
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
    bodyTone: Color,
) {
    if (patternId == null) return
    val tint = bodyTone.darken()

    clipPath(bodyPath(vp)) {
        when (patternId) {
            "pattern-motas" -> drawPatternMotas(vp, tint)
            "pattern-rayitas" -> drawPatternRayitas(vp, tint)
            "pattern-corazones" -> drawPatternGlyphs(vp, CORAZONES_POSITIONS, tint, ::heartPath)
            "pattern-estrellas" -> drawPatternGlyphs(vp, ESTRELLAS_POSITIONS, tint, ::starPath)
            "pattern-flores" -> drawPatternFlores(vp, tint)
            "pattern-chispas" -> drawPatternGlyphs(vp, CHISPAS_POSITIONS, tint, ::sparklePath)
            "pattern-llamas" -> drawPatternLlamas(vp, lerp(Brasa, bodyTone, LLAMA_TINT_BLEND))
            else -> Unit // unknown catalog id (forward compat): no-op, never crash.
        }
    }
}

/** A hand-scattered point projected onto the egg's curved surface: screen position plus the local glyph scale. */
private class WrappedPoint(val x: Float, val y: Float, val scale: Float)

/**
 * Treats (x, y) as a coordinate ON the surface and foreshortens it: radial distance from the body
 * center maps through sin (cells crowd toward the limb), glyph scale through cos (they shrink
 * there) — the same projection print undergoes on a real curved toy. The inset keeps everything
 * whole, just shy of the silhouette, so the clip never chops a glyph in half.
 */
private fun wrapOnBody(
    x: Float,
    y: Float,
): WrappedPoint {
    val nx = (x - BODY_CX) / BODY_RX
    val ny = (y - (BODY_CY + BODY_BULGE)) / BODY_RY
    val d = sqrt(nx * nx + ny * ny)
    if (d < 1e-4f) return WrappedPoint(x, y, 1f)
    val theta = d.coerceAtMost(1f) * (Math.PI / 2.0).toFloat()
    val factor = sin(theta) * WRAP_EDGE_INSET / d
    val scale = WRAP_MIN_SCALE + (1f - WRAP_MIN_SCALE) * cos(theta)
    return WrappedPoint(
        BODY_CX + nx * BODY_RX * factor,
        BODY_CY + BODY_BULGE + ny * BODY_RY * factor,
        scale,
    )
}

/** Staggered grid of dots wrapped onto the volume — deterministic, no randomness. Grid cells past the silhouette are skipped, not clamped, so no ring of dots piles up at the rim. */
private fun DrawScope.drawPatternMotas(
    vp: HabiViewport,
    tint: Color,
) {
    val rows = (BODY_RY * 2f / MOTAS_SPACING).toInt() + 2
    val cols = (BODY_RX * 2f / MOTAS_SPACING).toInt() + 2
    for (row in 0..rows) {
        val y = BODY_CY - BODY_RY + row * MOTAS_SPACING
        val rowOffset = if (row % 2 == 1) MOTAS_ROW_OFFSET else 0f
        for (col in 0..cols) {
            val x = BODY_CX - BODY_RX - MOTAS_SPACING + rowOffset + col * MOTAS_SPACING
            val nx = (x - BODY_CX) / BODY_RX
            val ny = (y - (BODY_CY + BODY_BULGE)) / BODY_RY
            if (nx * nx + ny * ny > 1f) continue
            val w = wrapOnBody(x, y)
            drawCircle(color = tint, radius = vp.len(MOTAS_RADIUS) * w.scale, center = vp.point(w.x, w.y))
        }
    }
}

/**
 * Diagonal pinstripes that bow with the body: each stripe is sampled along its straight print line
 * and every sample wrapped onto the surface, so lines curve around the volume and taper off just
 * shy of the silhouette instead of running past it flat.
 */
private fun DrawScope.drawPatternRayitas(
    vp: HabiViewport,
    tint: Color,
) {
    val strokeWidth = vp.len(RAYITAS_STROKE)
    val rad = Math.toRadians(RAYITAS_ANGLE_DEG.toDouble())
    val dirX = cos(rad).toFloat()
    val dirY = sin(rad).toFloat()
    val span = BODY_RX.coerceAtLeast(BODY_RY) * 1.6f
    val lineCount = (BODY_RY * 4f / RAYITAS_SPACING).toInt() + 2
    for (i in 0..lineCount) {
        val offset = -BODY_RY * 2f + i * RAYITAS_SPACING
        val path = Path()
        var started = false
        for (s in 0..RAYITAS_SAMPLES) {
            val t = -span + 2f * span * s / RAYITAS_SAMPLES
            val x = BODY_CX - dirY * offset + dirX * t
            val y = BODY_CY + BODY_BULGE + dirX * offset + dirY * t
            val nx = (x - BODY_CX) / BODY_RX
            val ny = (y - (BODY_CY + BODY_BULGE)) / BODY_RY
            if (nx * nx + ny * ny > 1f) {
                started = false
                continue
            }
            val w = wrapOnBody(x, y)
            val p = vp.point(w.x, w.y)
            if (!started) {
                path.moveTo(p.x, p.y)
                started = true
            } else {
                path.lineTo(p.x, p.y)
            }
        }
        drawPath(path, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

/** Shared driver for the fixed-position glyph patterns (corazones/estrellas/chispas), wrap applied per glyph. */
private fun DrawScope.drawPatternGlyphs(
    vp: HabiViewport,
    positions: List<Pair<Float, Float>>,
    tint: Color,
    glyph: (HabiViewport, Float, Float, Float) -> Path,
) {
    for ((x, y) in positions) {
        val w = wrapOnBody(x, y)
        drawPath(glyph(vp, w.x, w.y, w.scale), color = tint)
    }
}

private fun DrawScope.drawPatternFlores(
    vp: HabiViewport,
    tint: Color,
) {
    for ((x, y) in FLORES_POSITIONS) {
        val w = wrapOnBody(x, y)
        val orbit = vp.len(FLOWER_PETAL_ORBIT) * w.scale
        val petalRadius = vp.len(FLOWER_PETAL_RADIUS) * w.scale
        val center = vp.point(w.x, w.y)
        for (i in 0 until 5) {
            val angle = Math.toRadians(-90.0 + 72.0 * i)
            val petalCenter =
                Offset(center.x + orbit * cos(angle).toFloat(), center.y + orbit * sin(angle).toFloat())
            drawCircle(color = tint, radius = petalRadius, center = petalCenter)
        }
        drawCircle(color = tint, radius = vp.len(FLOWER_CENTER_RADIUS) * w.scale, center = center)
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
    scale: Float = 1f,
): Path {
    fun pt(
        dx: Float,
        dy: Float,
    ) = vp.point(cx + dx * HEART_SIZE * scale, cy + dy * HEART_SIZE * scale)

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
    scale: Float = 1f,
): Path = starPolygon(vp, cx, cy, points = 5, outerRadius = STAR_OUTER_RADIUS * scale, innerRatio = STAR_INNER_RATIO)

private fun sparklePath(
    vp: HabiViewport,
    cx: Float,
    cy: Float,
    scale: Float = 1f,
): Path = starPolygon(vp, cx, cy, points = 4, outerRadius = SPARKLE_OUTER_RADIUS * scale, innerRatio = SPARKLE_INNER_RATIO)

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
            // Blush warms with the smile and cools when it drops (mockup: .55 mustia -> .8
            // radiante) — riding mouthCurve means it animates with the same morph the mouth does.
            val alpha = BLUSH_ALPHA_BASE + BLUSH_ALPHA_SMILE_GAIN * face.mouthCurve.coerceIn(0f, 1f)
            for (side in SIDES) {
                val center = vp.point(BODY_CX + side * CHEEK_DX, CHEEK_Y)
                drawCircle(color = Mofletes.copy(alpha = alpha), radius = vp.len(BLUSH_RADIUS), center = center)
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
    droop: Float,
    gaze: Offset,
    eyeColor: Color,
) {
    val rx = vp.len(EYE_BASE_RX * face.eyeScale)
    val ry = rx * EYE_HEIGHT_MULT
    // Blink and WILTED's resting droop share the closing axis, so a blink on heavy lids just
    // closes the remaining slit instead of popping the eye back open first.
    val closure = maxOf(blink, droop).coerceIn(0f, 1f)
    val ryOpen = ry * (1f - closure)
    // The UPPER lid does the closing: the oval's center rides down as it shrinks, so the bottom
    // edge holds still — a symmetric shrink read as a robotic squint, not a lid.
    val lidShift = (ry - ryOpen) * EYELID_BIAS
    // A glance, not a stare: both eyes shift together a tiny fraction toward the gaze target.
    val gazeDx = vp.len(GAZE_SHIFT_X) * gaze.x.coerceIn(-1f, 1f)
    val gazeDy = vp.len(GAZE_SHIFT_Y) * gaze.y.coerceIn(-1f, 1f)

    for (side in SIDES) {
        val base = vp.point(BODY_CX + side * EYE_DX, EYE_Y)
        val center = Offset(base.x + gazeDx, base.y + lidShift + gazeDy)
        if (ryOpen > 0f) {
            drawOval(
                color = eyeColor,
                topLeft = Offset(center.x - rx, center.y - ryOpen),
                size = Size(rx * 2f, ryOpen * 2f),
            )
            // Sparkles ride on the mostly-open eye only — over a nearly-shut slit they would read
            // as stray dots with nothing under them.
            if (closure < 0.5f) drawSparkles(center, rx, ryOpen, face.sparkles)
        }
    }
}

/** Radii are fractions of the eye's own rx so the glint keeps its proportions from the big radiant eye down to the 40dp corner avatar. */
private fun DrawScope.drawSparkles(
    eyeCenter: Offset,
    eyeRx: Float,
    eyeRy: Float,
    count: Int,
) {
    if (count <= 0) return
    val spots =
        listOf(
            Offset(eyeCenter.x + eyeRx * 0.55f, eyeCenter.y - eyeRy * 0.75f) to eyeRx * SPARKLE_RADII[0],
            Offset(eyeCenter.x + eyeRx * 0.95f, eyeCenter.y - eyeRy * 0.15f) to eyeRx * SPARKLE_RADII[1],
            Offset(eyeCenter.x + eyeRx * 0.15f, eyeCenter.y - eyeRy * 1.05f) to eyeRx * SPARKLE_RADII[2],
        )
    for ((offset, radius) in spots.take(count.coerceAtMost(spots.size))) {
        drawCircle(color = Tarjeta, radius = radius, center = offset)
    }
}

/**
 * The closed-lid face (HabiSpec.closedEyes): per eye, the sagging lid arc in the eye's own ink
 * plus the lash mark echoing it below at [LASH_ALPHA] — mirrored on the published eye anchors
 * (EYE_DX/EYE_Y), so overlays keyed to that geometry (7c's scene brows) stay valid.
 */
private fun DrawScope.drawClosedEyes(
    vp: HabiViewport,
    eyeColor: Color,
) {
    for (side in SIDES) {
        val cx = BODY_CX + side * EYE_DX
        val lidY = EYE_Y + CLOSED_EYE_DROP
        drawSaggingArc(vp, cx, lidY, CLOSED_EYE_HALF_WIDTH, CLOSED_EYE_SAG, CLOSED_EYE_STROKE, eyeColor)
        drawSaggingArc(vp, cx, lidY + LASH_DROP, LASH_HALF_WIDTH, LASH_SAG, LASH_STROKE, eyeColor.copy(alpha = LASH_ALPHA))
    }
}

/** A round-capped quadratic bowing DOWN from its endpoints; its midpoint sits halfway to the control point, so the control doubles [sag]. */
private fun DrawScope.drawSaggingArc(
    vp: HabiViewport,
    cx: Float,
    y: Float,
    halfWidth: Float,
    sag: Float,
    strokeWidth: Float,
    color: Color,
) {
    val start = vp.point(cx - halfWidth, y)
    val end = vp.point(cx + halfWidth, y)
    val control = vp.point(cx, y + sag * 2f)
    val path =
        Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, end.x, end.y)
        }
    drawPath(path, color = color, style = Stroke(width = vp.len(strokeWidth), cap = StrokeCap.Round))
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
    smirkProgress: Float,
    wobble: Float,
) {
    // The smirk slides in by progress (0..1) so the Sargento's mouth glides back to center when he
    // drops the act (delight), instead of teleporting.
    val shiftX = SMIRK_SHIFT_X * smirkProgress
    val tiltDeg = SMIRK_TILT_DEG * smirkProgress
    val centerX = vp.x(BODY_CX + shiftX)
    val y = vp.y(MOUTH_Y)
    val halfWidth =
        vp.len(
            MOUTH_HALF_WIDTH +
                MOUTH_WIDTH_OPEN_GAIN * face.mouthOpen +
                MOUTH_WIDTH_SMILE_GAIN * face.mouthCurve.coerceAtLeast(0f),
        )

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
            // Sampled polyline instead of a single quadraticTo: x runs linear (the control point
            // shares the mouth's center x), y carries the parabola — identical to the old Bezier —
            // PLUS the dramatic worry wave (an S over one full sine period, the mockup's wavy
            // mouth), morphed in by wobble so mood changes bend the line rather than swap shapes.
            val depth = vp.len(face.mouthCurve * MOUTH_CURVE_DEPTH)
            val wobbleAmp = vp.len(MOUTH_WOBBLE_AMP) * wobble.coerceIn(0f, 1f)
            val path = Path()
            for (i in 0..MOUTH_SAMPLES) {
                val t = i / MOUTH_SAMPLES.toFloat()
                val px = centerX + halfWidth * (2f * t - 1f)
                val py = y + 2f * t * (1f - t) * depth + wobbleAmp * sin(2.0 * Math.PI * t).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
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
