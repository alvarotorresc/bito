package com.alvarotc.bito.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Bito tiene UN solo tema (decisión de diseño): base crema, sin modo oscuro.
private val BitoColorScheme =
    lightColorScheme(
        primary = Hoja,
        onPrimary = Tarjeta,
        primaryContainer = HojaTinte,
        onPrimaryContainer = Tinta,
        secondary = HabiSalvia,
        onSecondary = Tinta,
        tertiary = Brasa,
        onTertiary = Tarjeta,
        tertiaryContainer = BrasaTinte,
        onTertiaryContainer = Brasa,
        background = Papel,
        onBackground = Tinta,
        surface = Tarjeta,
        onSurface = Tinta,
        surfaceVariant = HojaTinte,
        onSurfaceVariant = TintaSuave,
        outline = Borde,
        error = Brasa,
    )

@Composable
fun BitoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BitoColorScheme,
        typography = BitoTypography,
        content = content,
    )
}
