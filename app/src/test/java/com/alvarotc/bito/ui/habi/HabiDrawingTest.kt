package com.alvarotc.bito.ui.habi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
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

    /** Lee las constantes del renderer en vez de duplicarlas: T8 mueve la geometría y esto la sigue sola. */
    private fun bodyCenterPixel(sizePx: Int) = px(BODY_CX, sizePx) to px(BODY_CY, sizePx)

    private fun leftEyePixel(sizePx: Int) = px(BODY_CX - EYE_DX, sizePx) to px(EYE_Y, sizePx)

    private fun rightEyePixel(sizePx: Int) = px(BODY_CX + EYE_DX, sizePx) to px(EYE_Y, sizePx)

    /** Mirrors SHADOW_CY plus a couple of hundredths below it: suelo puro, fuera de la silueta. */
    private fun groundPixel(sizePx: Int) = px(BODY_CX, sizePx) to px(SHADOW_CY + 0.02f, sizePx)

    /**
     * Mirrors the first sparkle's offset from the drawing spec, relative to the left eye.
     * `eyeRy = eyeRx * EYE_HEIGHT_MULT` (round eyes per architect review, was 1.4/oval).
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
        val eyeCenterX = (BODY_CX - EYE_DX) * sizePx
        val eyeCenterY = EYE_Y * sizePx
        val eyeRx = EYE_BASE_RX * eyeScale * sizePx
        val eyeRy = eyeRx * EYE_HEIGHT_MULT
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
    ): Bitmap = renderInto(sizePx) { drawHabi(spec, blink = blink) }

    private fun renderWithBody(
        spec: HabiSpec,
        sizePx: Int,
        body: HabiBodyMotion,
    ): Bitmap = renderInto(sizePx) { drawHabi(spec, body = body) }

    private fun renderWithoutShadow(
        spec: HabiSpec,
        sizePx: Int,
    ): Bitmap = renderInto(sizePx) { drawHabi(spec, groundShadow = false) }

    private fun renderInto(
        sizePx: Int,
        block: DrawScope.() -> Unit,
    ): Bitmap {
        val imageBitmap = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(imageBitmap),
            Size(sizePx.toFloat(), sizePx.toFloat()),
            block,
        )
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

    // --- T8: the ground shadow and the foot pivot -----------------------------------------------

    @Test
    fun `the ground shadow paints under her, and the body still covers its own center`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val bitmap = renderHabiBitmap(spec, size)
        val (gx, gy) = groundPixel(size)
        val (bx, by) = bodyCenterPixel(size)

        assertNotEquals(0, bitmap.getPixel(gx, gy)) // deja de ser transparente
        assertEquals(HabiSalvia.toArgb(), bitmap.getPixel(bx, by))
    }

    @Test
    fun `groundShadow off leaves the floor untouched`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (gx, gy) = groundPixel(size)

        assertEquals(0, renderWithoutShadow(spec, size).getPixel(gx, gy))
    }

    @Test
    fun `a tipped body moves the eye out of its resting pixel`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = leftEyePixel(size)

        val upright = renderHabiBitmap(spec, size).getPixel(x, y)
        val tipped = renderWithBody(spec, size, HabiBodyMotion(tiltDeg = 78f)).getPixel(x, y)

        assertNotEquals(upright, tipped)
    }

    @Test
    fun `the widget bitmap carries the same shadow as the canvas`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (gx, gy) = groundPixel(size)

        assertEquals(
            renderWithBody(spec, size, HabiBodyMotion.Rest).getPixel(gx, gy),
            renderHabiBitmap(spec, size).getPixel(gx, gy),
        )
    }

    /**
     * `groundPixel` (the test above) is painted BEFORE the body's `withTransform`, so it can't
     * tell an identity transform from a bug inside `withTransform` itself. This checks two points
     * that ARE inside the transform (body center, left eye): passing `HabiBodyMotion.Rest`
     * explicitly must render pixel-identical to not passing `body` at all.
     */
    @Test
    fun `HabiBodyMotion Rest renders identically to no body param at all`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (bx, by) = bodyCenterPixel(size)
        val (ex, ey) = leftEyePixel(size)

        val withRest = renderWithBody(spec, size, HabiBodyMotion.Rest)
        val withoutBody = renderHabiBitmap(spec, size)

        assertEquals(withoutBody.getPixel(bx, by), withRest.getPixel(bx, by))
        assertEquals(withoutBody.getPixel(ex, ey), withRest.getPixel(ex, ey))
    }

    /**
     * El paso mas propenso a error del pase de T8: bajar la silueta 0,03 en el viewport
     * normalizado es una resta que toca decenas de constantes, y una sola olvidada produce una
     * Habi sutilmente mal puesta que ningun otro test caza (las coordenadas de los demas tests se
     * movieron en bloque con las constantes que ahora leen).
     *
     * El delta boca-cuerpo (+0,11 respecto a BODY_CY) es el del pase de arte, fijado aqui como
     * literal INDEPENDIENTE de MOUTH_Y a proposito: si esa constante se hubiera quedado sin
     * desplazar, su propio simbolo seguiria coincidiendo consigo mismo (una prueba inutil que no
     * puede fallar nunca), pero la distancia real al centro del cuerpo la delataria — la boca
     * dejaria de caer donde este test la busca (verificado: revertir MOUTH_Y a 0.66f pone este
     * test en rojo).
     *
     * El ojo no puede verificarse igual: su propio ovalo (radio ~4,6px a 96px) es mas ancho que
     * el desplazamiento de 0,03 (~2,9px), asi que un pixel en su centro sigue cayendo dentro del
     * ovalo aunque EYE_Y se hubiera quedado sin mover — no discrimina (verificado). El brillo si:
     * es un glifo diminuto (~1,3px de radio), pero SOLO si su posicion esperada tambien se calcula
     * con el delta ojo-cuerpo (-0,05 respecto a BODY_CY) en vez de leer EYE_Y — leer EYE_Y aqui
     * repetiria el mismo fallo (el propio simbolo se mueve con el bug y deja de discriminar).
     * [leftEyeSparklePixel] SI lee EYE_Y a proposito (prueba el comportamiento del brillo, no el
     * desplazamiento), por eso este test no lo reutiliza.
     */
    @Test
    fun `the shift moved the origin, not the anatomy`() {
        val bodySpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val bodyBitmap = renderHabiBitmap(bodySpec, size)
        val (bx, by) = bodyCenterPixel(size)
        val mouthY = px(BODY_CY + 0.11f, size)
        val mouthXs = px(BODY_CX - 0.1f, size)..px(BODY_CX + 0.1f, size)

        assertEquals(HabiSalvia.toArgb(), bodyBitmap.getPixel(bx, by))
        // La boca es una curva, no una linea recta: escanea un tramo horizontal a la altura
        // esperada en vez de un unico pixel exacto.
        assertTrue(mouthXs.any { x -> bodyBitmap.getPixel(x, mouthY) == Tinta.toArgb() })

        // CHEERLEADER RADIANT: eyeScale 1.2 (1.15 base + 0.05 radiant). Mirrors drawSparkles'
        // first spot (eyeCenter.x + eyeRx*0.55, eyeCenter.y - eyeRy*0.75) but with eyeCenterY
        // computed from BODY_CY - 0.05, not from EYE_Y.
        val sparkleSpec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet())
        val eyeRx = EYE_BASE_RX * 1.2f * size
        val eyeRy = eyeRx * EYE_HEIGHT_MULT
        val eyeCenterX = (BODY_CX - EYE_DX) * size
        val eyeCenterY = (BODY_CY - 0.05f) * size
        val sx = (eyeCenterX + eyeRx * 0.55f).toInt()
        val sy = (eyeCenterY - eyeRy * 0.75f).toInt()

        assertEquals(Tarjeta.toArgb(), renderHabiBitmap(sparkleSpec, size).getPixel(sx, sy))
    }

    // --- T8 fix round 1: the pattern position lists, and the upper slot's top clip ---------------

    /**
     * Mirrors `wrapOnBody`'s own projection for FLORES_POSITIONS' 5th entry (index 4): it sits at
     * `x = BODY_CX` exactly, so its radial vector from the body center is purely vertical (no x
     * displacement to predict). Its `y` is expressed as `BODY_CY - 0.11f` — BODY_CY-relative, not
     * a raw viewport literal — because that is EXACTLY the bug this test exists to catch: the four
     * fixed pattern-position lists (corazones/estrellas/flores/chispas) are viewport-absolute
     * literals that T8's original pass forgot to shift by -0.03 along with BODY_CY, so the
     * printed pattern silently sat 0.03 lower relative to the body than intended.
     *
     * Returns the TOP EDGE of the wrapped glyph's petal ring, not its center: the ring (petal
     * orbit + radius, both scaled by the wrap) is ~6-7px across at this size, wider than the 0.03
     * shift (~3px) it needs to catch, so a center probe sits inside BOTH the correct and the
     * pre-fix wrapped position — verified empirically (temp diagnostic, deleted) by dumping a
     * pixel window around the predicted center with the bug reintroduced: the center pixel was
     * tinted either way. 3px above the center is inside the correct glyph but still bare body with
     * the bug (measured), so that is the offset returned here — an empirically tuned pixel offset,
     * not a formula, same convention as the antialiasing margins measured elsewhere in this file.
     *
     * `BODY_BULGE`/`BODY_RY`/`WRAP_EDGE_INSET` are private in HabiDrawing.kt, so their values are
     * re-declared here at their published constants.
     */
    private fun wrappedFloresTopEdgePixel(sizePx: Int): Pair<Int, Int> {
        val listY = BODY_CY - 0.11f
        val bodyBulge = 0.05f
        val bodyRy = 0.42f
        val wrapEdgeInset = 0.94f
        val bulgeCy = BODY_CY + bodyBulge
        val ny = (listY - bulgeCy) / bodyRy
        val d = kotlin.math.abs(ny)
        val theta = d * (Math.PI / 2.0).toFloat()
        val factor = kotlin.math.sin(theta.toDouble()).toFloat() * wrapEdgeInset / d
        val wrappedCenterY = bulgeCy + ny * bodyRy * factor
        return px(BODY_CX, sizePx) to ((wrappedCenterY * sizePx).toInt() - 3)
    }

    @Test
    fun `flores keeps its glyphs pinned to the body, not a stale viewport offset`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(pattern = "pattern-flores"))
        val bitmap = renderHabiBitmap(spec, size)
        val (x, y) = wrappedFloresTopEdgePixel(size)

        // Not a color equality check (the tint depends on the equipped body color): just that
        // SOMETHING paints there instead of the bare body tone.
        assertNotEquals(HabiSalvia.toArgb(), bitmap.getPixel(x, y))
    }

    /**
     * GORRO_POMPOM_Y and COPA_CYLINDER_TOP_Y do NOT take T8's -0.03 shift (controller ruling):
     * with it, their top edges land past y=0 and renderHabiBitmap (the widget/notification path,
     * which does not clip like a Compose Canvas inset within a larger layout) permanently loses
     * that part of the accessory.
     *
     * Reverting GORRO_POMPOM_Y to the pre-T8 literal (0.025f) alone is not enough: with
     * GORRO_POMPOM_RADIUS 0.028f its top edge still sits at -0.003 — a tiny overflow that
     * predates this milestone entirely (present before T8 ever touched this constant), but a real
     * one: measured at accessorySize (256px), it produced a fully opaque (alpha 255) pompom pixel
     * in row 0, not just antialiasing. GORRO_POMPOM_Y moved to 0.035f instead — 0.01 past the
     * historic 0.025f, flagged here and in the fix report for the controller to confirm — clears
     * row 0 with margin (measured: maxAlpha 0).
     *
     * Row 1 is sampled too, but the pass/fail line differs by accessory: COPA_CYLINDER_TOP_Y
     * (0.02f, a rect's flat top, no radius overshoot) clears both rows cleanly. GORRO's row 1 does
     * carry ink — but it is GORRO_DOME_TOP_Y's (0.005f) own rounded peak, confirmed by color
     * (measured: the row-1 pixel is a blend of Brasa, the dome's fill; the row-2 pixel is exactly
     * Brasa). GORRO_DOME_TOP_Y is NOT negative, so nothing is clipped there — it is the dome
     * legitimately sitting close to the canvas top, unrelated to T8's shift and out of this
     * ruling's scope ("asoma por encima de 0" applies to negative constants, not merely small
     * positive ones). Row 1 is therefore checked for what this fix DOES own: no leftover Tarjeta
     * (the pompom's own solid fill, distinct from the dome's Brasa/GorroLanaBand) — isolating the
     * pompom's contribution from the dome's.
     */
    @Test
    fun `the beanie and the top hat do not lose ink off the top of the canvas`() {
        val bare = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val withGorro = bare.copy(equipped = EquippedSet(upper = "upper-gorro-lana"))
        val withCopa = bare.copy(equipped = EquippedSet(upper = "upper-copa"))
        val bareBitmap = renderHabiBitmap(bare, accessorySize)
        val gorroBitmap = renderHabiBitmap(withGorro, accessorySize)
        val copaBitmap = renderHabiBitmap(withCopa, accessorySize)

        for (x in 0 until accessorySize) {
            assertEquals(
                "upper-gorro-lana row=0 col=$x should match the bare render",
                bareBitmap.getPixel(x, 0),
                gorroBitmap.getPixel(x, 0),
            )
            assertEquals("upper-copa row=0 col=$x should match the bare render", bareBitmap.getPixel(x, 0), copaBitmap.getPixel(x, 0))
            assertEquals("upper-copa row=1 col=$x should match the bare render", bareBitmap.getPixel(x, 1), copaBitmap.getPixel(x, 1))
            assertNotEquals(
                "upper-gorro-lana row=1 col=$x should not carry the pompom's own fill color",
                Tarjeta.toArgb(),
                gorroBitmap.getPixel(x, 1),
            )
        }
    }

    // --- T7: the eye ritual (HabiSpec.eyesPainted) ----------------------------------------------

    @Test
    fun `both eyes are painted by default`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val bitmap = renderHabiBitmap(spec, size)
        val (lx, ly) = leftEyePixel(size)
        val (rx, ry) = rightEyePixel(size)

        assertEquals(Tinta.toArgb(), bitmap.getPixel(lx, ly))
        assertEquals(Tinta.toArgb(), bitmap.getPixel(rx, ry))
    }

    @Test
    fun `one painted eye is the left one - the daruma convention`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), eyesPainted = 1)
        val bitmap = renderHabiBitmap(spec, size)
        val (lx, ly) = leftEyePixel(size)
        val (rx, ry) = rightEyePixel(size)

        assertEquals(Tinta.toArgb(), bitmap.getPixel(lx, ly))
        assertNotEquals(Tinta.toArgb(), bitmap.getPixel(rx, ry))
    }

    @Test
    fun `a newborn Habi has neither eye painted, and no sparkles either`() {
        // CHEERLEADER RADIANT: eyeScale 1.2, sparkles 3 — con los ojos pintados ese píxel es Tarjeta.
        val painted = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet())
        val newborn = painted.copy(eyesPainted = 0)
        val (lx, ly) = leftEyePixel(size)
        val (sx, sy) = leftEyeSparklePixel(size, eyeScale = 1.2f)

        assertNotEquals(Tinta.toArgb(), renderHabiBitmap(newborn, size).getPixel(lx, ly))
        assertEquals(Tarjeta.toArgb(), renderHabiBitmap(painted, size).getPixel(sx, sy))
        assertNotEquals(Tarjeta.toArgb(), renderHabiBitmap(newborn, size).getPixel(sx, sy))
    }

    @Test
    fun `an unpainted eye still blinks - it is geometry, not fill`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), eyesPainted = 0)
        val (x, y) = leftEyePixel(size)

        val open = renderWithBlink(spec, size, blink = 0f).getPixel(x, y)
        val closed = renderWithBlink(spec, size, blink = 1f).getPixel(x, y)

        assertNotEquals(open, closed)
    }

    /**
     * Pins the axiom T8/T9/T10 build on: an unpainted eye is the SAME oval, drawn with a lightened
     * fill — not skipped. `assertNotEquals(Tinta, ...)` alone (the four tests above) can't tell
     * "draws a lightened oval" apart from "skips the eye entirely and leaves bare body" — both give
     * a non-Tinta pixel. An exact match against `bodyTone.lighten(UNPAINTED_EYE_LIGHTEN)` at the
     * eye's own center rules the skip out: a skip would leave the RAW (un-lightened) body tone there.
     */
    @Test
    fun `an unpainted eye's fill is the body tone lightened - not skipped, not the bare body`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), eyesPainted = 0)
        val (x, y) = leftEyePixel(size)

        val pixel = renderHabiBitmap(spec, size).getPixel(x, y)

        assertEquals(HabiSalvia.lighten(UNPAINTED_EYE_LIGHTEN).toArgb(), pixel)
    }

    /** Mirrors the unpainted eye's ink ring: centered on the oval's own edge, EYE_BASE_RX from the
     * eye center. Uses [accessorySize] (defined below, T10) instead of [size]: at 96px the ring's
     * antialiased edge and the fill's own antialiased edge overlap at this radius; 256px keeps a
     * clean pixel inside the stroke band. */
    private fun leftEyeRingPixel(
        sizePx: Int,
        eyeScale: Float,
    ): Pair<Int, Int> {
        val eyeCenterX = (BODY_CX - EYE_DX) * sizePx
        val eyeCenterY = EYE_Y * sizePx
        val eyeRx = EYE_BASE_RX * eyeScale * sizePx
        return (eyeCenterX + eyeRx).toInt() to eyeCenterY.toInt()
    }

    /** Second half of the same axiom: the unpainted eye also carries the ink ring — a distinct
     * stroke over the lightened fill, not a bare filled circle. */
    @Test
    fun `an unpainted eye carries an ink ring over the lightened fill`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), eyesPainted = 0)
        val (x, y) = leftEyeRingPixel(accessorySize, eyeScale = 1f)
        val fillOnly = HabiSalvia.lighten(UNPAINTED_EYE_LIGHTEN).toArgb()

        val ringPixel = renderHabiBitmap(spec, accessorySize).getPixel(x, y)

        assertNotEquals(fillOnly, ringPixel)
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
        val x = (BODY_CX - EYE_DX) * sizePx
        val y = (EYE_Y + 0.05f + 0.018f) * sizePx
        return x.toInt() to y.toInt()
    }

    /** Mirrors the lash mark's mid-arc centerline: LASH_DROP 0.07 under the lid line, sagging LASH_SAG 0.01. */
    private fun leftLashPixel(sizePx: Int): Pair<Int, Int> {
        val x = (BODY_CX - EYE_DX) * sizePx
        val y = (EYE_Y + 0.05f + 0.07f + 0.01f) * sizePx
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
        // sparkle spot lands above/beside the eye center, well clear of the arcs below EYE_Y.
        // Built off leftEyePixel (not hardcoded) so it tracks EYE_Y/EYE_DX on its own.
        val spec = HabiSpec(Mood.WILTED, Personality.CHEERLEADER, EquippedSet(), closedEyes = true)
        val (eyeCenterX, eyeCenterY) = leftEyePixel(size)
        val socket = (eyeCenterX - 7..eyeCenterX + 7).flatMap { x -> (eyeCenterY - 8..eyeCenterY + 4).map { y -> x to y } }

        val bitmap = renderHabiBitmap(spec, size)

        assertTrue(socket.none { (x, y) -> bitmap.getPixel(x, y) == Tarjeta.toArgb() })
    }

    // --- T10: patterns and accessories ---------------------------------------------------------

    /** Larger canvas than [size]: upper/lower probes sit close to the body edge, where antialiasing
     * at 96px can flip a pixel's alpha. 256px keeps the transparent-vs-opaque margin unambiguous. */
    private val accessorySize = 256

    /**
     * Every pixel in the body's bounding box, for a scan that survives pattern geometry retuning.
     * BODY_CX/BODY_CY are read from the renderer (T8 moved BODY_CY); the 0.34/0.42 half-extents
     * mirror BODY_RX/BODY_RY, which stayed private — T8 only moves the silueta, never its radii.
     */
    private fun bodyBoundingBoxPixels(sizePx: Int): List<Pair<Int, Int>> {
        val minX = px(BODY_CX - 0.34f, sizePx)
        val maxX = px(BODY_CX + 0.34f, sizePx)
        val minY = px(BODY_CY - 0.42f, sizePx)
        val maxY = px(BODY_CY + 0.42f, sizePx)
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
        // Apex is BODY_CY - BODY_RY = 0.52 - 0.42 = 0.10 (T8 moved BODY_CY, the -0.03 shift);
        // probe just above it, inside the beanie dome.
        val withUpper = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(upper = "upper-gorro-lana"))
        val withoutUpper = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = px(0.5f, accessorySize) to px(0.07f, accessorySize)

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
        // y 0.87 (was 0.90, T8's -0.03 shift): SOCK_TOP_Y..SOCK_BOTTOM_Y is 0.815-0.935 and
        // SNEAKER_TOP_Y..SNEAKER_BOTTOM_Y is 0.83-0.92 post-shift, both still cover it.
        val withLower = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(lower = "lower-calcetines"))
        val withoutLower = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = px(0.5f + 0.22f, accessorySize) to px(0.87f, accessorySize)

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

    // --- T9: the grain -------------------------------------------------------------------------

    /**
     * El grano multiplica cada píxel del cuerpo por un gris irregular (~0,78-1,00), así que las
     * comparaciones de tono dejan de ser igualdad exacta. La tolerancia cubre ese rango con
     * margen; lo que estos tests siguen probando es el TONO, no el valor exacto.
     */
    private fun assertArgbCloseTo(
        expected: Color,
        actualArgb: Int,
        tolerance: Int = 64,
    ) {
        val expectedArgb = expected.toArgb()
        for (shift in listOf(16, 8, 0)) {
            val e = (expectedArgb shr shift) and 0xFF
            val a = (actualArgb shr shift) and 0xFF
            assertTrue("canal $shift: esperaba ~$e, vino $a", kotlin.math.abs(e - a) <= tolerance)
        }
        assertEquals(0xFF, (actualArgb shr 24) and 0xFF)
    }

    /** Luminancia perceptual (Rec. 709) de un ARGB. */
    private fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * El grano multiplica RGB de forma pareja, así que las proporciones entre canales (el tinte)
     * se conservan dentro de tolerancia, aunque el valor absoluto de cada canal baje.
     */
    private fun assertRatioCloseTo(
        a: Int,
        b: Int,
        tolerance: Double = 0.06,
    ) {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF

        fun assertRatio(
            aNum: Int,
            aDen: Int,
            bNum: Int,
            bDen: Int,
            label: String,
        ) {
            if (aDen == 0 || bDen == 0) {
                assertEquals("$label: un denominador es 0", aNum == 0, bNum == 0)
                return
            }
            val ratioA = aNum.toDouble() / aDen
            val ratioB = bNum.toDouble() / bDen
            assertTrue(
                "$label: esperaba ~$ratioA, vino $ratioB",
                kotlin.math.abs(ratioA - ratioB) <= tolerance,
            )
        }

        assertRatio(ar, ag, br, bg, "R/G")
        assertRatio(ag, ab, bg, bb, "G/B")
    }

    @Test
    fun `the grain darkens without shifting the hue`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val (x, y) = bodyCenterPixel(size)
        val grain = HabiGrain.brush(ApplicationProvider.getApplicationContext())

        val plain = renderHabiBitmap(spec, size).getPixel(x, y)
        val grained = renderInto(size) { drawHabi(spec, grain = grain) }.getPixel(x, y)

        // Más oscuro en luminancia…
        assertTrue(luminance(grained) <= luminance(plain))
        // …y con el mismo tinte: las proporciones R:G:B se conservan dentro de tolerancia.
        assertRatioCloseTo(plain, grained)
    }

    @Test
    fun `the grain never leaks outside the body silhouette`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val grain = HabiGrain.brush(ApplicationProvider.getApplicationContext())
        // Esquina superior izquierda: fuera de la silueta y fuera de la sombra.
        val corner = 2 to 2

        assertEquals(0, renderInto(size) { drawHabi(spec, grain = grain) }.getPixel(corner.first, corner.second))
    }

    /**
     * Varios puntos claramente fuera de la elipse del cuerpo (radios ~0,34/0,42 alrededor de
     * (BODY_CX, BODY_CY)) Y fuera de la franja de la sombra (SHADOW_CY 0,95 +/- ~0,045, x en
     * 0,24-0,76): dos esquinas superiores, los bordes izquierdo/derecho a media altura y el borde
     * superior central. Ninguno cae dentro del bounding box del cuerpo ni de la sombra, así que el
     * grano (enmascarado a `bodyPath`) no debería tocarlos bajo ninguna circunstancia.
     */
    private fun outsideBodyPixels(sizePx: Int): List<Pair<Int, Int>> =
        listOf(0.02f to 0.02f, 0.98f to 0.02f, 0.02f to 0.5f, 0.98f to 0.5f, 0.5f to 0.02f)
            .map { (nx, ny) -> px(nx, sizePx) to px(ny, sizePx) }

    /**
     * Los dos tests anteriores (del brief, verbatim) pasan igual con `grain` completamente inerte:
     * la comparación de luminancia en el centro usa `<=` no estricto, y la esquina (2,2) es
     * transparente con o sin grano. Ninguno detecta que el bloque `grain?.let { clipPath(...) {
     * drawRect(...) } }` desaparezca por completo (verificado manualmente comentándolo: ambos
     * siguen en verde). Este test agrega sobre toda la caja del cuerpo para cazar exactamente esa
     * regresión: cuenta cuántos píxeles cambian, exige que la luminancia MEDIA de la caja baje de
     * forma estricta y acotada, y confirma que fuera de la silueta nada se mueve.
     */
    @Test
    fun `the grain measurably darkens the body box and nothing outside it`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val grain = HabiGrain.brush(ApplicationProvider.getApplicationContext())
        val boxPoints = bodyBoundingBoxPixels(size)

        val plain = renderHabiBitmap(spec, size)
        val grained = renderInto(size) { drawHabi(spec, grain = grain) }

        // (a) Una fraccion sustancial de la caja del cuerpo cambia. Medido con un test diagnostico
        // (temporal, no commiteado) sobre esta misma caja: 4049 pixeles distintos de ~5427 — el
        // umbral de 500 deja margen de sobra sin acercarse a lo que un no-op silencioso daria (0).
        val diffCount = boxPoints.count { (x, y) -> plain.getPixel(x, y) != grained.getPixel(x, y) }
        assertTrue("esperaba >= 500 pixeles distintos en la caja del cuerpo, vinieron $diffCount", diffCount >= 500)

        // (b) La luminancia MEDIA de la caja baja de forma estricta, y no mas del techo teorico:
        // con ALPHA 0.10 y un grano 0.78-1.00, el factor de multiplicacion efectivo va de 0.978 a
        // 1.00 (como mucho ~2.2% de oscurecimiento en el interior del cuerpo); promediar sobre
        // toda la caja (incluye esquinas fuera de la silueta, identicas en ambos renders) solo
        // puede diluir esa caida, nunca superarla — 3% deja margen sin dejar pasar un no-op (que
        // daria caida 0, fallando el estricto <).
        val plainMean = boxPoints.map { (x, y) -> luminance(plain.getPixel(x, y)) }.average()
        val grainedMean = boxPoints.map { (x, y) -> luminance(grained.getPixel(x, y)) }.average()
        assertTrue(
            "la luminancia media deberia bajar con el grano: plano=$plainMean, con grano=$grainedMean",
            grainedMean < plainMean,
        )
        val relativeDrop = (plainMean - grainedMean) / plainMean
        assertTrue("la caida relativa de luminancia ($relativeDrop) supera el techo del 3%", relativeDrop <= 0.03)

        // (c) Fuera de la silueta (y lejos de la sombra), nada cambia: el grano esta enmascarado a
        // bodyPath, no pintado sobre todo el lienzo.
        for ((x, y) in outsideBodyPixels(size)) {
            assertEquals(
                "($x,$y), fuera de la silueta, no deberia cambiar con el grano",
                plain.getPixel(x, y),
                grained.getPixel(x, y),
            )
        }
    }
}
