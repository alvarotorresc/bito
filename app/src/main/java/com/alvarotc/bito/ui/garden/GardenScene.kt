package com.alvarotc.bito.ui.garden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.habi.ColorTint
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.rememberArte
import com.alvarotc.bito.ui.stats.BadgeStrings
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BUBBLE_MS = 2500L
private const val SWAY_KICK_DEGREES = 7f

// El brillo del farolillo de noche, como el del prototipo: una elipse del 22 % del ancho y el 30 % del
// alto, centrada 1,5 puntos a la derecha y 15 por encima del pie del farolillo, en modo pantalla. Es
// luz, no arte.
private const val GLOW_RADIUS_X = 0.22f
private const val GLOW_RADIUS_Y = 0.30f
private const val GLOW_SHIFT_X_PCT = 1.5f
private const val GLOW_RISE_PCT = 15f
private const val GLOW_MID_STOP = 0.45f
private val GlowCore = Color(0xBFFFBE6E)
private val GlowMid = Color(0x38FFA050)

/**
 * El jardín de Habi (pieza 1 §6.3), de atrás adelante: fondo, parcelas, plantas y especiales por
 * profundidad, Habi (con su sombra, que es capa suya), lo que queda delante de Habi, la luz en modo
 * multiplicar y, de noche, el farolillo encima de la luz ([itemsOverLight]) con su brillo en modo
 * pantalla. Mustio y estación son tintes y transformaciones sobre el mismo arte pintado
 * ([GardenLook]). Todos los toques los decide [onSceneTap]: el cuerpo de Habi hace lo de siempre
 * ([onHabiTap], con la reacción de [HabiAvatar] hacia el dedo); una planta se mece; un especial abre
 * un globito con el nombre de su logro. La composición va a una capa propia para que los modos de
 * fusión solo mezclen con la escena.
 */
@Composable
fun GardenScene(
    garden: GardenState,
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    delighted: Boolean = false,
    onHabiTap: (() -> Unit)? = null,
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val art = remember(context) { HabiArt.get(context) }
    val listo = rememberArte(art, remember(garden) { recursosDelJardin(garden) })
    val scope = rememberCoroutineScope()
    val sway = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    var bubble by remember { mutableStateOf<SceneItem.Special?>(null) }
    // Cada toque en un especial vuelve a contar los 2,5 s, aunque sea el mismo.
    var bubbleTick by remember { mutableIntStateOf(0) }
    var habiNudge by remember { mutableIntStateOf(0) }
    var habiTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    LaunchedEffect(bubble, bubbleTick) {
        if (bubble != null) {
            delay(BUBBLE_MS)
            bubble = null
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(GardenArt.SCENE_ASPECT)
            .clip(RoundedCornerShape(24.dp))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .testTag("garden-scene"),
    ) {
        val sceneW = constraints.maxWidth.toFloat()
        val sceneH = constraints.maxHeight.toFloat()
        val items = remember(garden, sceneW, sceneH) { sceneItems(garden, sceneW, sceneH) }
        val habiDepth = GardenArt.habiSpot.yPct
        val plantTints = GardenLook.plantTints(garden.wilt, garden.season)
        val grassTints = GardenLook.grassTints(garden.wilt, garden.season)
        val overLight = itemsOverLight(items, garden.light)
        val habi = habiRect(sceneW, sceneH)
        val habiBody = if (onHabiTap != null) habiBodyRect(sceneW, sceneH) else null
        val currentOnHabiTap by rememberUpdatedState(onHabiTap)

        Canvas(Modifier.fillMaxSize()) {
            if (!listo) return@Canvas
            art.image(GardenArt.background)?.let { drawFill(it, if (grassTints.isEmpty()) null else art.filter(grassTints)) }
            items.filter { it.depth <= habiDepth && it !in overLight }.forEach { drawItem(art, it, garden.wilt, plantTints, sway) }
        }

        val density = LocalDensity.current
        HabiAvatar(
            spec = spec,
            modifier =
                Modifier
                    .offset { IntOffset(habi.left.roundToInt(), habi.top.roundToInt()) }
                    .size(with(density) { habi.width.toDp() }, with(density) { habi.height.toDp() }),
            animated = animated,
            // Los toques de verdad los recoge la capa de delante; esto deja la reacción preparada
            // (y el toque de accesibilidad, que no pasa por la capa).
            onTap = onHabiTap,
            delighted = delighted,
            nudge = habiNudge,
            nudgeTouch = habiTouch,
        )

        // La capa de delante recoge todos los toques: la caja del lienzo de Habi es mucho mayor que su
        // cuerpo y, si los recogiera Habi, taparía el farolillo.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(items, habiBody) {
                    detectTapGestures { tap ->
                        when (val action = onSceneTap(items, tap.x, tap.y, habiBody)) {
                            is SceneTap.Habi -> {
                                habiTouch =
                                    Offset(
                                        ((action.x - habi.left) / habi.width).coerceIn(0f, 1f),
                                        ((action.y - habi.top) / habi.height).coerceIn(0f, 1f),
                                    )
                                habiNudge++
                                currentOnHabiTap?.invoke()
                            }
                            is SceneTap.Sway -> {
                                val anim = sway.getOrPut(action.plot) { Animatable(0f) }
                                scope.launch {
                                    anim.snapTo(SWAY_KICK_DEGREES)
                                    anim.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 200f))
                                }
                            }
                            is SceneTap.Bubble -> {
                                bubble = action.item
                                bubbleTick++
                            }
                            SceneTap.None -> Unit
                        }
                    }
                },
        ) {
            if (!listo) return@Canvas
            items.filter { it.depth > habiDepth && it !in overLight }.forEach { drawItem(art, it, garden.wilt, plantTints, sway) }
            val (top, bottom) = GardenLook.lightGradient(garden.light)
            drawRect(Brush.verticalGradient(listOf(Color(top), Color(bottom))), blendMode = BlendMode.Multiply)
            overLight.forEach { drawItem(art, it, garden.wilt, plantTints, sway) }
            val night = GardenLook.nightness(garden.light)
            val lantern = GardenArt.specialSpots[GardenSpecial.LANTERN.key]
            if (GardenSpecial.LANTERN in garden.specials && lantern != null && night > 0f) drawLanternGlow(lantern, night)
        }

        bubble?.let { SpecialBubble(it, sceneW) }
    }
}

/** Lo que [GardenScene] pinta para [garden]: el fondo y la pieza de cada parcela y de cada especial, a tamaño completo. */
internal fun recursosDelJardin(garden: GardenState): List<Pair<Int, Int>> =
    (listOf(GardenArt.background) + sceneItems(garden, 1f, 1f).map { it.res }).distinct().map { it to 1 }

private fun DrawScope.drawLanternGlow(
    lantern: GardenSpot,
    night: Float,
) {
    val center =
        Offset(
            size.width * (lantern.xPct + GLOW_SHIFT_X_PCT) / 100f,
            size.height * (lantern.yPct - GLOW_RISE_PCT) / 100f,
        )
    val radiusX = size.width * GLOW_RADIUS_X
    val radiusY = size.height * GLOW_RADIUS_Y
    // Un degradado radial es circular: se pinta con el radio horizontal y se estira en vertical.
    withTransform({ scale(1f, radiusY / radiusX, center) }) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to GlowCore,
                    GLOW_MID_STOP to GlowMid,
                    1f to Color.Transparent,
                    center = center,
                    radius = radiusX,
                ),
            radius = radiusX,
            center = center,
            alpha = night.coerceIn(0f, 1f),
            blendMode = BlendMode.Screen,
        )
    }
}

private fun DrawScope.drawFill(
    image: ImageBitmap,
    filter: ColorFilter?,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Una pieza en su rectángulo. Las plantas llevan el mustio del prototipo, `scaleY(..) skewX(..)`
 * con el origen en el pie (la copa se ladea a la derecha), y encima el vaivén del toque.
 */
private fun DrawScope.drawItem(
    art: HabiArt,
    item: SceneItem,
    wilt: Int,
    plantTints: List<ColorTint>,
    sway: Map<Int, Animatable<Float, AnimationVector1D>>,
) {
    val image = art.image(item.res) ?: return
    val r = item.rect
    val plant = item as? SceneItem.Plant
    val pivot = Offset(r.left + r.width / 2f, r.top + r.height)
    withTransform({
        if (plant != null) {
            sway[plant.plot]?.value?.let { rotate(it, pivot) }
            // Orden CSS: el último se aplica primero, así que el punto se cizalla y luego encoge.
            scale(1f, GardenLook.plantScaleY(wilt), pivot)
            val shear = GardenLook.plantShear(wilt)
            if (shear != 0f) {
                translate(pivot.x, pivot.y)
                transform(Matrix().apply { values[Matrix.SkewX] = shear })
                translate(-pivot.x, -pivot.y)
            }
        }
        translate(r.left, r.top)
        scale(r.width / image.width, r.height / image.height, Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width, image.height),
            colorFilter = if (plant != null && plantTints.isNotEmpty()) art.filter(plantTints) else null,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** El globito con el nombre del logro, centrado encima del especial sin salirse de la escena. */
@Composable
private fun SpecialBubble(
    item: SceneItem.Special,
    sceneW: Float,
) {
    Surface(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        val centered = item.rect.left + item.rect.width / 2f - placeable.width / 2f
                        val x = centered.coerceIn(0f, (sceneW - placeable.width).coerceAtLeast(0f))
                        val y = (item.rect.top - placeable.height).coerceAtLeast(0f)
                        placeable.place(x.roundToInt(), y.roundToInt())
                    }
                }
                .testTag("garden-bubble-${item.special.key}"),
        shape = RoundedCornerShape(12.dp),
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Text(
            stringResource(BadgeStrings.badgeNameRes(item.special.badgeId)),
            style = MaterialTheme.typography.labelMedium,
            color = Tinta,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
