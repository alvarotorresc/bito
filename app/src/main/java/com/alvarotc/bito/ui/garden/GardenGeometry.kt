package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.habi.HabiAtlas
import com.alvarotc.bito.ui.habi.RigCanvas

/** Un rectángulo en píxeles de la escena. */
data class SceneRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x >= left && x <= left + width && y >= top && y <= top + height
}

/** Lo que se pinta del jardín. [depth] es la línea del suelo en % de la escena: más abajo, más delante. */
sealed interface SceneItem {
    val res: Int
    val rect: SceneRect
    val depth: Float

    data class Soil(val plot: Int, override val res: Int, override val rect: SceneRect, override val depth: Float) : SceneItem

    data class Plant(val plot: Int, override val res: Int, override val rect: SceneRect, override val depth: Float) : SceneItem

    data class Special(
        val special: GardenSpecial,
        override val res: Int,
        override val rect: SceneRect,
        override val depth: Float,
    ) : SceneItem
}

/** Lo que pide un toque en la escena (fuera de Habi, que tiene su propio toque). */
sealed interface SceneTap {
    /** Mecer la planta de la parcela [plot]. */
    data class Sway(val plot: Int) : SceneTap

    /** Abrir el globito con el logro de [item]. */
    data class Bubble(val item: SceneItem.Special) : SceneTap

    data object None : SceneTap
}

/** La tierra va justo detrás de su planta: medio punto de profundidad, como en el prototipo. */
private const val SOIL_BEHIND = 0.5f

/** El rectángulo de una pieza de aspecto [aspect] y [heightPct] % de alto, apoyada abajo en el centro de [spot]. */
internal fun spotRect(
    spot: GardenSpot,
    heightPct: Float,
    aspect: Float,
    sceneW: Float,
    sceneH: Float,
): SceneRect {
    val height = sceneH * heightPct / 100f
    val width = height * aspect
    val centerX = sceneW * spot.xPct / 100f
    val bottom = sceneH * spot.yPct / 100f
    return SceneRect(centerX - width / 2f, bottom - height, width, height)
}

/**
 * Todo lo que hay que pintar, de atrás adelante (§6.3, capas 2 y 3). Cada parcela abierta pone su
 * tierra justo detrás de su planta, bajada entera [GardenLookTable.SOIL_SINK] de su propio alto
 * (arriba y abajo, sin encoger) para que asome por debajo del pie, como en el prototipo.
 */
internal fun sceneItems(
    garden: GardenState,
    sceneW: Float,
    sceneH: Float,
): List<SceneItem> {
    val items = mutableListOf<SceneItem>()
    garden.plots.forEachIndexed { index, plot ->
        val spot = GardenArt.plotSpots[index]
        val soil = GardenArt.plots[index]
        val soilRect = spotRect(spot, spot.heightPct * soil.heightRatio, soil.aspect, sceneW, sceneH)
        val sunk = soilRect.copy(top = soilRect.top + soilRect.height * GardenLookTable.SOIL_SINK)
        items += SceneItem.Soil(index, soil.res, sunk, spot.yPct - SOIL_BEHIND)
        val plant = GardenArt.plants[plot.species.ordinal][plot.stage.ordinal]
        val plantRect = spotRect(spot, spot.heightPct * plant.heightRatio, plant.aspect, sceneW, sceneH)
        items += SceneItem.Plant(index, plant.res, plantRect, spot.yPct)
    }
    for (special in garden.specials) {
        val spot = GardenArt.specialSpots[special.key] ?: continue
        val piece = GardenArt.specials[special.key] ?: continue
        val rect = spotRect(spot, spot.heightPct * piece.heightRatio, piece.aspect, sceneW, sceneH)
        items += SceneItem.Special(special, piece.res, rect, spot.yPct)
    }
    return items.sortedBy { it.depth }
}

/** La caja de [com.alvarotc.bito.ui.habi.HabiAvatar] en la escena: los pies en [spot] y el cuerpo con [spot]`.heightPct` de alto. */
internal fun habiRect(
    sceneW: Float,
    sceneH: Float,
    spot: GardenSpot = GardenArt.habiSpot,
    canvas: RigCanvas = HabiAtlas.canvas,
    bodyWidth: Float = HabiAtlas.BODY_WIDTH,
    bodyHeight: Float = HabiAtlas.BODY_HEIGHT,
): SceneRect {
    val k = sceneH * spot.heightPct / 100f / bodyHeight
    val feetY = sceneH * spot.yPct / 100f
    val centerX = sceneW * spot.xPct / 100f
    return SceneRect(
        left = centerX + (canvas.left - bodyWidth / 2f) * k,
        top = feetY + (canvas.top - bodyHeight) * k,
        width = canvas.width * k,
        height = canvas.height * k,
    )
}

/** La planta o el especial más al frente bajo el dedo. La tierra no responde. */
internal fun hitTest(
    items: List<SceneItem>,
    x: Float,
    y: Float,
): SceneItem? = items.lastOrNull { it !is SceneItem.Soil && it.rect.contains(x, y) }

/** Qué hace un toque en ([x], [y]): una planta se mece, un especial abre su globito, lo demás nada. */
internal fun onSceneTap(
    items: List<SceneItem>,
    x: Float,
    y: Float,
): SceneTap =
    when (val hit = hitTest(items, x, y)) {
        is SceneItem.Plant -> SceneTap.Sway(hit.plot)
        is SceneItem.Special -> SceneTap.Bubble(hit)
        else -> SceneTap.None
    }
