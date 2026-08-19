package com.alvarotc.bito.ui.habi

import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.theme.HabiSalvia
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tinta

/**
 * Maps catalog item ids (`domain/model/Customization.kt`) to Habi's paint colors. An unknown id
 * (future catalog entry, corrupt data) always falls back to the axis default — never crashes.
 *
 * Only `body-salvia` and `eyes-tinta` are theme tokens ([HabiSalvia], [Tinta], [Hoja]); every other
 * hex here is Habi-specific and art-phase-tunable against the mockup
 * (design/mockups/m6/4-habi-pantalla.png) — they live only in this object, never promoted to
 * ui/theme/Color.kt.
 */
object HabiPalette {
    // Body tones — afinables contra mockup.
    private val bodyColors =
        mapOf(
            "body-salvia" to HabiSalvia,
            "body-vainilla" to Color(0xFFE8DCC8),
            "body-melocoton" to Color(0xFFECC3A4),
            "body-cielo" to Color(0xFFAFC8D8),
            "body-lavanda" to Color(0xFFC5B4D4),
            "body-rosa" to Color(0xFFE0B4C4),
            "body-oliva" to Color(0xFFB8BC85),
            "body-terracota" to Color(0xFFD6957D),
            "body-carbon" to Color(0xFF8D8478),
            "body-dorado" to Color(0xFFE3C169),
        )

    // Eye tones — afinables contra mockup.
    private val eyeColors =
        mapOf(
            "eyes-tinta" to Tinta,
            "eyes-avellana" to Color(0xFF7A5C3E),
            "eyes-verde" to Hoja,
            "eyes-azul" to Color(0xFF5E8CA7),
            "eyes-ambar" to Color(0xFFC98A3D),
            "eyes-violeta" to Color(0xFF7E6A9E),
            "eyes-granate" to Color(0xFFA05252),
        )

    fun bodyColor(itemId: String): Color = bodyColors[itemId] ?: HabiSalvia

    fun eyeColor(itemId: String): Color = eyeColors[itemId] ?: Tinta
}
