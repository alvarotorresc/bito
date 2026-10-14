package com.alvarotc.bito.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Un icono Phosphor Bold (viewBox 256, relleno) como vector. Se tiñe donde se usa con `Icon(tint = …)`.
 * Los Lucide de `BitoIcons` se quedan en las pantallas de otras piezas (decisión 14).
 */
internal fun phosphor(
    name: String,
    path: String,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply { addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)) }.build()
