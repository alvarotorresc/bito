package com.alvarotc.bito.ui.sistema

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R

@OptIn(ExperimentalTextApi::class)
private fun gabarito(weight: FontWeight) =
    Font(
        resId = R.font.gabarito,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/**
 * Gabarito (OFL), fuente variable 400–900 empaquetada (la app no tiene red). El sistema usa 500, 700 y
 * 900 (catálogo §1.2); 400 y 600 están para el mapeo de Material de las pantallas de otras piezas.
 */
val Gabarito =
    FontFamily(
        gabarito(FontWeight.Normal),
        gabarito(FontWeight.Medium),
        gabarito(FontWeight.SemiBold),
        gabarito(FontWeight.Bold),
        gabarito(FontWeight.Black),
    )

/** Escala con nombre del catálogo §1.2. Nada por debajo de 14. */
@Immutable
data class BitoTipos(
    val t14: TextStyle,
    val t16: TextStyle,
    val t19: TextStyle,
    val t24: TextStyle,
    val t32: TextStyle,
    val t48: TextStyle,
    val t52: TextStyle,
)

private fun estilo(
    size: Int,
    line: Int,
) = TextStyle(fontFamily = Gabarito, fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = line.sp)

val BitoTiposBase =
    BitoTipos(
        t14 = estilo(14, 20),
        t16 = estilo(16, 22),
        t19 = estilo(19, 26),
        t24 = estilo(24, 30),
        t32 = estilo(32, 38),
        t48 = estilo(48, 54),
        t52 = estilo(52, 58),
    )

/** Peso 700: títulos y etiquetas. */
fun TextStyle.fuerte(): TextStyle = copy(fontWeight = FontWeight.Bold)

/** Peso 900 y cifras tabulares: solo cifras protagonistas (racha, saldo, cuenta atrás, objetivo, hora). */
fun TextStyle.cifra(): TextStyle = copy(fontWeight = FontWeight.Black, fontFeatureSettings = "tnum")

val LocalBitoTipos = staticCompositionLocalOf { BitoTiposBase }

/**
 * El interlineado de CSS: el reparto sobrante de `line-height` va mitad arriba y mitad abajo y cuenta también en la primera y la
 * última línea (Compose, por defecto, lo recorta ahí). Para las pantallas que van 1:1 con un mock.
 */
fun TextStyle.comoCss(): TextStyle = copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
