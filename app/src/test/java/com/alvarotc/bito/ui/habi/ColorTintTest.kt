package com.alvarotc.bito.ui.habi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Las matrices reproducen los filtros CSS que usa la galería: lo aprobado allí es lo que pinta la app. */
class ColorTintTest {
    private val delta = 1e-4f

    private fun apply(
        m: FloatArray,
        r: Float,
        g: Float,
        b: Float,
    ): FloatArray = FloatArray(3) { row -> m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b }

    @Test
    fun `the identity tint is the identity matrix`() {
        val expected = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertArrayEquals(expected, ColorTint.IDENTITY.matrix(), delta)
    }

    @Test
    fun `a hue rotation leaves grays untouched`() {
        val out = apply(ColorTint(hueDegrees = 137f).matrix(), 0.4f, 0.4f, 0.4f)
        out.forEach { assertEquals(0.4f, it, delta) }
    }

    @Test
    fun `zero saturation turns any color into its luminance gray`() {
        val out = apply(ColorTint(saturation = 0f).matrix(), 0.2f, 0.7f, 0.1f)
        val luminance = 0.213f * 0.2f + 0.715f * 0.7f + 0.072f * 0.1f
        out.forEach { assertEquals(luminance, it, delta) }
    }

    @Test
    fun `brightness scales every channel and leaves alpha alone`() {
        val m = ColorTint(brightness = 0.5f).matrix()
        assertEquals(0.5f, apply(m, 1f, 0f, 0f)[0], delta)
        assertEquals(1f, m[18], delta)
    }

    @Test
    fun `full sepia on white matches the CSS formula`() {
        val out = apply(ColorTint(sepia = 1f).matrix(), 1f, 1f, 1f)
        assertEquals(0.393f + 0.769f + 0.189f, out[0], delta)
        assertEquals(0.349f + 0.686f + 0.168f, out[1], delta)
        assertEquals(0.272f + 0.534f + 0.131f, out[2], delta)
    }

    @Test
    fun `saturation happens before sepia like the CSS filter list`() {
        // Desaturar primero deja un gris (la luminancia del verde) y la sepia lo tiñe; al revés saldría gris.
        val out = apply(ColorTint(saturation = 0f, sepia = 1f).matrix(), 0f, 1f, 0f)
        assertEquals(0.715f * (0.393f + 0.769f + 0.189f), out[0], delta)
        assertEquals(0.715f * (0.272f + 0.534f + 0.131f), out[2], delta)
    }

    @Test
    fun `combining with the identity changes nothing and the order is the list order`() {
        val cielo = ColorTint(hueDegrees = 75f, saturation = 1.2f)
        assertArrayEquals(cielo.matrix(), combinedMatrix(listOf(ColorTint.IDENTITY, cielo)), delta)
        val dim = ColorTint(brightness = 0.5f)
        val both = apply(combinedMatrix(listOf(cielo, dim)), 0.3f, 0.6f, 0.2f)
        val first = apply(cielo.matrix(), 0.3f, 0.6f, 0.2f)
        val stepwise = apply(dim.matrix(), first[0], first[1], first[2])
        for (i in 0..2) assertEquals(stepwise[i], both[i], delta)
    }
}
