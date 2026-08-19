package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    /** Mirrors the left-eye normalized center from the drawing spec (y=0.45, x=0.5-0.145). */
    private fun leftEyePixel(sizePx: Int) = px(0.5f - 0.145f, sizePx) to px(0.45f, sizePx)

    /** Mirrors the first sparkle's offset from the drawing spec, relative to the left eye. */
    private fun leftEyeSparklePixel(
        sizePx: Int,
        eyeScale: Float,
    ): Pair<Int, Int> {
        val eyeCenterX = (0.5f - 0.145f) * sizePx
        val eyeCenterY = 0.45f * sizePx
        val eyeRx = 0.052f * eyeScale * sizePx
        val eyeRy = eyeRx * 1.4f
        val x = eyeCenterX + eyeRx * 0.55f
        val y = eyeCenterY - eyeRy * 0.75f
        return x.roundToInt() to y.roundToInt()
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
}
