package com.alvarotc.bito.ui.habi

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Un tinte de color como los filtros CSS de la galería, aplicados en este orden: `hue-rotate`,
 * `saturate`, `sepia` y `brightness` (fórmulas del estándar Filter Effects). Kotlin puro: la
 * matriz 4x5 sirve igual a `android.graphics.ColorMatrix` que a la de Compose. El alfa no cambia.
 */
data class ColorTint(
    val hueDegrees: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val sepia: Float = 0f,
) {
    fun matrix(): FloatArray = toColorMatrix(rgb3())

    internal fun rgb3(): FloatArray {
        var m = hueRotate(hueDegrees)
        m = multiply(saturate(saturation), m)
        m = multiply(sepiaOf(sepia), m)
        return multiply(scale(brightness), m)
    }

    companion object {
        val IDENTITY = ColorTint()
    }
}

/** La matriz de aplicar [tints] uno detrás de otro (el primero, primero). */
fun combinedMatrix(tints: List<ColorTint>): FloatArray {
    var m = scale(1f)
    for (tint in tints) m = multiply(tint.rgb3(), m)
    return toColorMatrix(m)
}

private fun toColorMatrix(m: FloatArray): FloatArray =
    floatArrayOf(
        m[0], m[1], m[2], 0f, 0f,
        m[3], m[4], m[5], 0f, 0f,
        m[6], m[7], m[8], 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

private fun multiply(
    a: FloatArray,
    b: FloatArray,
): FloatArray =
    FloatArray(9) { i ->
        val r = i / 3
        val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }

private fun scale(k: Float): FloatArray = floatArrayOf(k, 0f, 0f, 0f, k, 0f, 0f, 0f, k)

private fun hueRotate(degrees: Float): FloatArray {
    val rad = degrees * PI.toFloat() / 180f
    val c = cos(rad)
    val s = sin(rad)
    return floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f,
        0.715f - c * 0.715f - s * 0.715f,
        0.072f - c * 0.072f + s * 0.928f,
        0.213f - c * 0.213f + s * 0.143f,
        0.715f + c * 0.285f + s * 0.140f,
        0.072f - c * 0.072f - s * 0.283f,
        0.213f - c * 0.213f - s * 0.787f,
        0.715f - c * 0.715f + s * 0.715f,
        0.072f + c * 0.928f + s * 0.072f,
    )
}

private fun saturate(s: Float): FloatArray =
    floatArrayOf(
        0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s,
        0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s,
        0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s,
    )

private fun sepiaOf(amount: Float): FloatArray {
    val k = 1f - amount.coerceIn(0f, 1f)
    return floatArrayOf(
        0.393f + 0.607f * k, 0.769f - 0.769f * k, 0.189f - 0.189f * k,
        0.349f - 0.349f * k, 0.686f + 0.314f * k, 0.168f - 0.168f * k,
        0.272f - 0.272f * k, 0.534f - 0.534f * k, 0.131f + 0.869f * k,
    )
}
