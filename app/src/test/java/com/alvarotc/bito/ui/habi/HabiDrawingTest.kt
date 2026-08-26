package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.HabiSalvia
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * Pixel-level smoke tests for the Habi bean renderer. Not snapshot tests — they only assert the
 * handful of invariants that matter for a vector renderer: the equipped body color paints, unknown
 * catalog ids never crash, and blink actually closes the eyes. Fidelity against the mockup is a
 * human visual call via the @Preview grid in HabiAvatar.kt, not something these tests can capture.
 *
 * [GraphicsMode.Mode.NATIVE] is required: Robolectric's default (LEGACY) mode fakes drawing without
 * touching a real pixel buffer, and Compose's `ImageBitmap(w, h)` factory crashes under LEGACY
 * entirely (it calls an unshadowed internal `Bitmap.createBitmap` overload).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabiDrawingTest {
    private val size = 96

    /** Mirrors the body-fill normalized center from the drawing spec (cx=0.5, cy=0.55). */
    private fun bodyCenterPixel(sizePx: Int) = px(0.5f, sizePx) to px(0.55f, sizePx)

    /** Mirrors the left-eye normalized center from the drawing spec (y=0.50, x=0.5-0.14 — art pass 2026-08-25). */
    private fun leftEyePixel(sizePx: Int) = px(0.5f - 0.14f, sizePx) to px(0.5f, sizePx)

    /**
     * Mirrors the first sparkle's offset from the drawing spec, relative to the left eye.
     * `eyeRy = eyeRx * 1.0` mirrors EYE_HEIGHT_MULT (round eyes per architect review, was 1.4/oval).
     *
     * Uses `.toInt()` (truncate toward zero, i.e. floor for these always-positive coordinates),
     * not `.roundToInt()`: a bitmap pixel index `i` represents the half-open continuous interval
     * `[i, i+1)`, so the pixel containing a continuous point is `floor(point)`, not
     * `round(point)`. This tiny sparkle glyph (~1.3px radius) is small enough that the two
     * conventions disagree — verified empirically with a direct pixel-neighborhood probe (temp
     * test, deleted) after the round-eyes change shifted this offset: `round()` landed one pixel
     * off the fully-opaque sparkle pixel; `toInt()` lands exactly on it.
     */
    private fun leftEyeSparklePixel(
        sizePx: Int,
        eyeScale: Float,
    ): Pair<Int, Int> {
        val eyeCenterX = (0.5f - 0.14f) * sizePx
        val eyeCenterY = 0.5f * sizePx
        val eyeRx = 0.048f * eyeScale * sizePx
        val eyeRy = eyeRx * 1.0f
        val x = eyeCenterX + eyeRx * 0.55f
        val y = eyeCenterY - eyeRy * 0.75f
        return x.toInt() to y.toInt()
    }

    private fun px(
        fraction: Float,
        sizePx: Int,
    ) = (fraction * sizePx).roundToInt()

    /** Renders with an explicit [blink], unlike the public [renderHabiBitmap] (widget-facing, no blink axis). */
    private fun renderWithBlink(
        spec: HabiSpec,
        sizePx: Int,
        blink: Float,
    ): Bitmap {
        val imageBitmap = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(imageBitmap),
            Size(sizePx.toFloat(), sizePx.toFloat()),
        ) {
            drawHabi(spec, blink = blink)
        }
        return imageBitmap.asAndroidBitmap()
    }

    @Test
    fun `bitmap render paints body pixels with the equipped body color`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val bitmap = renderHabiBitmap(spec, size)
        val (x, y) = bodyCenterPixel(size)

        assertEquals(HabiSalvia.toArgb(), bitmap.getPixel(x, y))
    }

    @Test
    fun `distinct body items render distinct pixels`() {
        val salvia = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(bodyColor = "body-salvia"))
        val carbon = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(bodyColor = "body-carbon"))
        val (x, y) = bodyCenterPixel(size)

        val salviaPixel = renderHabiBitmap(salvia, size).getPixel(x, y)
        val carbonPixel = renderHabiBitmap(carbon, size).getPixel(x, y)

        assertNotEquals(salviaPixel, carbonPixel)
    }

    @Test
    fun `unknown equipped ids fall back to defaults without crashing`() {
        val spec =
            HabiSpec(
                Mood.DRAMATIC,
                Personality.SARGENTO,
                EquippedSet(bodyColor = "body-nope", eyeColor = "eyes-nope"),
            )
        val (x, y) = bodyCenterPixel(size)

        val bitmap = renderHabiBitmap(spec, size)

        assertEquals(HabiSalvia.toArgb(), bitmap.getPixel(x, y))
    }

    @Test
    fun `blink closes the eyes`() {
        // NEUTRA draws no brows, keeping the eye pixel free of any other layer's stroke.
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = leftEyePixel(size)

        val open = renderWithBlink(spec, size, blink = 0f).getPixel(x, y)
        val closed = renderWithBlink(spec, size, blink = 1f).getPixel(x, y)

        assertNotEquals(open, closed)
        assertEquals(HabiSalvia.toArgb(), closed)
    }

    @Test
    fun `sparkles do not render over a blinked-shut eye`() {
        // CHEERLEADER RADIANT: eyeScale 1.2 (1.15 base + 0.05 radiant), sparkles 3 — a sparkle is
        // guaranteed to land at the offset leftEyeSparklePixel computes.
        val spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet())
        val (x, y) = leftEyeSparklePixel(size, eyeScale = 1.2f)

        val open = renderWithBlink(spec, size, blink = 0f).getPixel(x, y)
        val closed = renderWithBlink(spec, size, blink = 1f).getPixel(x, y)

        assertEquals(Tarjeta.toArgb(), open)
        assertEquals(HabiSalvia.toArgb(), closed)
    }

    // --- Scene opts (mockup 7b): body tone override + closed-lid eyes --------------------------

    /** Onboarding 7b's muted sage, the override's one real consumer — any tone would exercise the axis. */
    private val mutedTone = Color(0xFFA3AF9C)

    /**
     * Mirrors the closed-lid arc's mid-arc centerline from the drawing spec: x at the left eye
     * (0.5 - 0.14), y = EYE_Y 0.5 + CLOSED_EYE_DROP 0.05 + CLOSED_EYE_SAG 0.018. `.toInt()` for
     * the same floor-not-round reason as [leftEyeSparklePixel]: at 96px the centerline sits at
     * y=54.5, and round() picks the row on the stroke's antialiased bottom boundary while floor
     * lands mid-stroke.
     */
    private fun leftClosedLidPixel(sizePx: Int): Pair<Int, Int> {
        val x = (0.5f - 0.14f) * sizePx
        val y = (0.5f + 0.05f + 0.018f) * sizePx
        return x.toInt() to y.toInt()
    }

    /** Mirrors the lash mark's mid-arc centerline: LASH_DROP 0.07 under the lid line, sagging LASH_SAG 0.01. */
    private fun leftLashPixel(sizePx: Int): Pair<Int, Int> {
        val x = (0.5f - 0.14f) * sizePx
        val y = (0.5f + 0.05f + 0.07f + 0.01f) * sizePx
        return x.toInt() to y.toInt()
    }

    @Test
    fun `body tone override replaces the catalog body color`() {
        val spec = HabiSpec(Mood.WILTED, Personality.NEUTRA, EquippedSet(), bodyToneOverride = mutedTone)
        val (x, y) = bodyCenterPixel(size)

        val overridden = renderHabiBitmap(spec, size).getPixel(x, y)
        val canon = renderHabiBitmap(spec.copy(bodyToneOverride = null), size).getPixel(x, y)

        assertEquals(mutedTone.toArgb(), overridden)
        assertEquals(HabiSalvia.toArgb(), canon)
    }

    @Test
    fun `body tone override drives the pattern tint`() {
        val spec =
            HabiSpec(
                Mood.NORMAL,
                Personality.NEUTRA,
                EquippedSet(pattern = "pattern-motas"),
                bodyToneOverride = mutedTone,
            )
        // Mirrors the pattern tint rule: the resolved body tone darkened by PATTERN_TINT_FACTOR
        // 0.82 — if the tint still derived from the catalog color, no pixel would match this.
        val expectedTint =
            Color(red = mutedTone.red * 0.82f, green = mutedTone.green * 0.82f, blue = mutedTone.blue * 0.82f).toArgb()

        val bitmap = renderHabiBitmap(spec, size)

        assertTrue(bodyBoundingBoxPixels(size).any { (x, y) -> bitmap.getPixel(x, y) == expectedTint })
    }

    @Test
    fun `closed eyes swap the drooped open eye for the sagging lid arc`() {
        val open = HabiSpec(Mood.WILTED, Personality.NEUTRA, EquippedSet())
        val closed = open.copy(closedEyes = true)
        val (eyeX, eyeY) = leftEyePixel(size)
        val (lidX, lidY) = leftClosedLidPixel(size)

        val openBitmap = renderHabiBitmap(open, size)
        val closedBitmap = renderHabiBitmap(closed, size)

        // WILTED's resting droop still leaves ink on the eye line; the closed variant clears it...
        assertEquals(Tinta.toArgb(), openBitmap.getPixel(eyeX, eyeY))
        assertEquals(HabiSalvia.toArgb(), closedBitmap.getPixel(eyeX, eyeY))
        // ...and paints the lid arc below it, where the open face keeps clean body.
        assertEquals(Tinta.toArgb(), closedBitmap.getPixel(lidX, lidY))
        assertEquals(HabiSalvia.toArgb(), openBitmap.getPixel(lidX, lidY))
    }

    @Test
    fun `closed eyes carry their lash marks`() {
        val closed = HabiSpec(Mood.WILTED, Personality.NEUTRA, EquippedSet(), closedEyes = true)
        val (x, y) = leftLashPixel(size)

        val lashPixel = renderHabiBitmap(closed, size).getPixel(x, y)
        val barePixel = renderHabiBitmap(closed.copy(closedEyes = false), size).getPixel(x, y)

        // Translucent ink: darker than the bare body there, never the full-strength eye stroke.
        assertNotEquals(barePixel, lashPixel)
        assertNotEquals(Tinta.toArgb(), lashPixel)
    }

    @Test
    fun `closed eyes draw no sparkles`() {
        // WILTED CHEERLEADER keeps one sparkle on its drooped open eye — closed lids must not
        // carry it over. Scans the whole upper left-eye socket instead of one offset: every
        // sparkle spot lands above/beside the eye center, well clear of the arcs at y 0.55+.
        val spec = HabiSpec(Mood.WILTED, Personality.CHEERLEADER, EquippedSet(), closedEyes = true)
        val socket = (28..42).flatMap { x -> (40..52).map { y -> x to y } }

        val bitmap = renderHabiBitmap(spec, size)

        assertTrue(socket.none { (x, y) -> bitmap.getPixel(x, y) == Tarjeta.toArgb() })
    }

    // --- T10: patterns and accessories ---------------------------------------------------------

    /** Larger canvas than [size]: upper/lower probes sit close to the body edge, where antialiasing
     * at 96px can flip a pixel's alpha. 256px keeps the transparent-vs-opaque margin unambiguous. */
    private val accessorySize = 256

    /** Every pixel in the body's bounding box, for a scan that survives pattern geometry retuning. */
    private fun bodyBoundingBoxPixels(sizePx: Int): List<Pair<Int, Int>> {
        val minX = px(0.5f - 0.34f, sizePx)
        val maxX = px(0.5f + 0.34f, sizePx)
        val minY = px(0.55f - 0.42f, sizePx)
        val maxY = px(0.55f + 0.42f, sizePx)
        return (minX..maxX).flatMap { x -> (minY..maxY).map { y -> x to y } }
    }

    private fun anyPixelDiffers(
        a: Bitmap,
        b: Bitmap,
        points: List<Pair<Int, Int>>,
    ) = points.any { (x, y) -> a.getPixel(x, y) != b.getPixel(x, y) }

    @Test
    fun `each pattern renders distinct pixels`() {
        // All 7 catalog patterns, not just motas/rayitas — a pairwise scan catches a swapped
        // positions-list or glyph-function between any two (e.g. corazones/estrellas), which
        // compiles cleanly either way and would otherwise go unverified.
        val patternIds =
            listOf(
                "pattern-motas",
                "pattern-rayitas",
                "pattern-corazones",
                "pattern-estrellas",
                "pattern-flores",
                "pattern-chispas",
                "pattern-llamas",
            )
        val points = bodyBoundingBoxPixels(size)
        val bareBitmap = renderHabiBitmap(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()), size)
        val bitmaps =
            patternIds.associateWith { id ->
                renderHabiBitmap(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(pattern = id)), size)
            }

        for (id in patternIds) {
            assertTrue("$id should differ from no pattern", anyPixelDiffers(bareBitmap, bitmaps.getValue(id), points))
        }
        for (i in patternIds.indices) {
            for (j in i + 1 until patternIds.size) {
                val (a, b) = patternIds[i] to patternIds[j]
                assertTrue("$a should differ from $b", anyPixelDiffers(bitmaps.getValue(a), bitmaps.getValue(b), points))
            }
        }
    }

    @Test
    fun `upper items draw above the body apex`() {
        // Apex is BODY_CY - BODY_RY = 0.55 - 0.42 = 0.13; probe just above it, inside the beanie dome.
        val withUpper = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(upper = "upper-gorro-lana"))
        val withoutUpper = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = px(0.5f, accessorySize) to px(0.10f, accessorySize)

        val equippedAlpha = android.graphics.Color.alpha(renderHabiBitmap(withUpper, accessorySize).getPixel(x, y))
        val bareAlpha = android.graphics.Color.alpha(renderHabiBitmap(withoutUpper, accessorySize).getPixel(x, y))

        assertNotEquals(0, equippedAlpha)
        assertEquals(0, bareAlpha)
    }

    @Test
    fun `lower items draw below the body`() {
        // LOWER_DX = 0.20 (both lower items' shared anchor); probe at 0.22 clears the bare egg's
        // antialiased edge at this y (measured: alpha 11 at 0.20, 0 at 0.22) while staying inside
        // both the sock (half-width 0.05) and sneaker (half-width 0.065) shapes drawn at the anchor.
        val withLower = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(lower = "lower-calcetines"))
        val withoutLower = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = px(0.5f + 0.22f, accessorySize) to px(0.90f, accessorySize)

        val equippedAlpha = android.graphics.Color.alpha(renderHabiBitmap(withLower, accessorySize).getPixel(x, y))
        val bareAlpha = android.graphics.Color.alpha(renderHabiBitmap(withoutLower, accessorySize).getPixel(x, y))

        assertNotEquals(0, equippedAlpha)
        assertEquals(0, bareAlpha)
    }

    @Test
    fun `unknown pattern or accessory ids draw nothing and do not crash`() {
        val spec =
            HabiSpec(
                Mood.NORMAL,
                Personality.NEUTRA,
                EquippedSet(pattern = "pattern-nope", upper = "upper-nope", lower = "lower-nope"),
            )
        val bare = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())

        val unknownBitmap = renderHabiBitmap(spec, size)
        val bareBitmap = renderHabiBitmap(bare, size)
        val (x, y) = bodyCenterPixel(size)

        assertEquals(bareBitmap.getPixel(x, y), unknownBitmap.getPixel(x, y))
    }
}
