package com.alvarotc.bito.ui.sistema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.alvarotc.bito.ui.theme.BitoTypography

/** Acceso a los tokens, al estilo de `MaterialTheme`. */
object BitoTema {
    val colores: BitoColores
        @Composable @ReadOnlyComposable
        get() = LocalBitoColores.current
    val tipos: BitoTipos
        @Composable @ReadOnlyComposable
        get() = LocalBitoTipos.current
}

/** Material mapeado a la paleta (spec §4.2.5): las pantallas de otras piezas cambian de color sin tocarlas. */
fun esquemaDe(c: BitoColores): ColorScheme =
    lightColorScheme(
        primary = c.agua,
        onPrimary = c.tinta,
        primaryContainer = c.hielo,
        onPrimaryContainer = c.tinta,
        secondary = c.piedra,
        onSecondary = c.tinta,
        secondaryContainer = c.piedraClara,
        onSecondaryContainer = c.tinta,
        tertiary = Escena.rachas.anillo,
        onTertiary = c.canto,
        tertiaryContainer = Escena.rachas.disco,
        onTertiaryContainer = Escena.rachas.anillo,
        background = c.tAlta,
        onBackground = c.tinta,
        surface = c.canto,
        onSurface = c.tinta,
        surfaceVariant = c.tAlta2,
        onSurfaceVariant = c.tintaSuave,
        surfaceTint = c.canto,
        surfaceBright = c.canto,
        surfaceDim = c.tAlta2,
        surfaceContainerLowest = c.campo,
        surfaceContainerLow = c.canto,
        surfaceContainer = c.canto,
        surfaceContainerHigh = c.canto,
        surfaceContainerHighest = c.tAlta2,
        outline = c.contorno,
        outlineVariant = c.hornacina,
        error = c.peligro,
        onError = c.canto,
        errorContainer = c.peligroFondo,
        onErrorContainer = c.peligroTexto,
        scrim = c.velo.copy(alpha = 1f),
    )

/** El sistema de la Bito 2: provee los tokens y el `MaterialTheme` mapeado. Las Tareas 8 y 9 le añaden reducido, sonido y tacto. */
@Composable
fun BitoSistema(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBitoColores provides BitoClaro,
        LocalBitoTipos provides BitoTiposBase,
    ) {
        MaterialTheme(colorScheme = esquemaDe(BitoClaro), typography = BitoTypography, content = content)
    }
}
