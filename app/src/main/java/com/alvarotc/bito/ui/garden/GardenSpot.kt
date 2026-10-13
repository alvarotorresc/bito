package com.alvarotc.bito.ui.garden

/** Un sitio de la escena en % de su ancho y alto: [xPct] es el centro, [yPct] la base y [heightPct] el alto. */
data class GardenSpot(
    val xPct: Float,
    val yPct: Float,
    val heightPct: Float,
)

/** Una pieza del jardín. [aspect] = ancho / alto; [heightRatio] = su alto respecto al alto de su sitio. */
data class GardenPiece(
    val res: Int,
    val aspect: Float,
    val heightRatio: Float = 1f,
)
