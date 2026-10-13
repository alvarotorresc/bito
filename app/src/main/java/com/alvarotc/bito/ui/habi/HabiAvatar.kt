package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Mofletes
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Parpadeo por pieza (pieza 1 §3.5): los ojos cerrados pintados durante unos 130 ms. Un doble
// parpadeo de vez en cuando evita que el reposo parezca un metrónomo.
private const val BLINK_HOLD_MS = 130L
private const val BLINK_MIN_DELAY_MS = 3000L
private const val BLINK_MAX_DELAY_MS = 5000L
private const val DOUBLE_BLINK_CHANCE = 4 // 1 de cada N parpadeos es doble.
private const val DOUBLE_BLINK_GAP_MS = 90L

// Fundido entre la cara anterior y la nueva (§3.5), en lugar de deformar parámetros.
private const val EXPRESSION_FADE_MS = 150

private const val TAP_SCALE_X = 1.06f
private const val TAP_SCALE_Y = 0.94f

// En modo expresivo (animated && onTap != null) el dedo hunde un poco más que en la esquina de Hoy:
// es la precarga del toque; la reacción de verdad llega al soltar.
private const val EXPRESSIVE_TAP_SCALE_X = 1.07f
private const val EXPRESSIVE_TAP_SCALE_Y = 0.93f

// Todo se ancla en los PIES (origen abajo en el centro) para que aplastar, estirar y aterrizar
// tengan peso; la respiración se ve y va al ritmo del ánimo.
private const val BREATH_SCALE = 0.022f
private const val BREATH_LIFT_DP = 1.2f
private const val BREATH_PERIOD_RADIANT_MS = 2000
private const val BREATH_PERIOD_NORMAL_MS = 2700
private const val BREATH_PERIOD_LOW_MS = 3500

// Al acabar la guía, los ojos cerrados se funden con los abiertos en este tiempo: alguien que
// despierta, no un pestañeo.
private const val LID_RELEASE_MS = 450

/** Lo que la respiración aplica al graphicsLayer: escala desde los pies y elevación (en unidades de BREATH_LIFT_DP). */
internal data class BreathScales(val scaleX: Float, val scaleY: Float, val liftFactor: Float)

/**
 * La fórmula de siempre con [amplitude] multiplicando el efecto: con 1 reproduce la respiración en
 * reposo; con [GUIDED_BREATH_AMPLITUDE] queda en +6,6 % de alto, -3,3 % de ancho y 3,6 dp de elevación.
 */
internal fun breathScales(
    breathValue: Float,
    amplitude: Float,
): BreathScales =
    BreathScales(
        scaleX = 1f - BREATH_SCALE * 0.5f * amplitude * breathValue,
        scaleY = 1f + BREATH_SCALE * amplitude * breathValue,
        liftFactor = amplitude * breathValue,
    )

private const val IDLE_GESTURE_MIN_DELAY_MS = 5000L
private const val IDLE_GESTURE_MAX_DELAY_MS = 11000L
private const val SQUASH_SCALE_X = 0.10f
private const val SQUASH_SCALE_Y = 0.14f
private const val TAP_HOP_DP = 8f

// Un toque se contesta con materia blanda, no con una coreografía: el cuerpo se comprime bajo el
// dedo (más si el toque es en la cabeza), se inclina en contra y vuelve con un muelle poco
// amortiguado. Las amplitudes varían en cada toque y de vez en cuando hay un saltito.
private const val POKE_PRESS_MS = 70
private const val POKE_SQUASH_BASE = 0.4f
private const val POKE_SQUASH_HEAD_GAIN = 0.35f
private const val POKE_TILT_MAX_DEG = 10f
private const val POKE_JITTER = 0.3f
private const val HOP_CHANCE = 3 // 1 de cada N toques salta, salvo si el anterior ya saltó.
private const val GAZE_HOLD_MS = 420L
private const val IDLE_GLANCE_HOLD_MS = 600L

private val JellySpring = spring<Float>(dampingRatio = 0.32f, stiffness = 380f)
private val SettleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
private val GazeSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)

/** Lo que decide la cara: si cambia, la cara vieja se funde con la nueva. */
private data class FaceKey(
    val mood: Mood,
    val personality: Personality,
    val delighted: Boolean,
    val closedEyes: Boolean,
)

/**
 * Habi con vida, en piezas pintadas (pieza 1 §3): respiración al ritmo del ánimo, parpadeo con la pieza
 * de ojos cerrados, gestos en reposo (inclinarse, estirarse, mirar a un lado), fundido de 150 ms al
 * cambiar de cara y, con [onTap], la reacción blanda al toque con la mirada hacia el dedo. Solo
 * presenta: [spec] ya trae ánimo, personalidad y lo puesto.
 *
 * [animated] congela todo en el fotograma de reposo cuando es false (los tests de Compose y la
 * esquina de Hoy). [nudge] repite la reacción al toque desde fuera (el foco, el jardín), con la
 * mirada hacia [nudgeTouch] (0..1 en el lienzo) si viene; si no, hacia el último toque. [breath], si no es
 * null, sustituye la respiración de reposo por la guiada y cierra los ojos con la pieza pintada; al
 * volver a null se abren con un fundido de LID_RELEASE_MS.
 */
@Composable
fun HabiAvatar(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    onTap: (() -> Unit)? = null,
    delighted: Boolean = false,
    nudge: Int = 0,
    breath: HabiBreath? = null,
    nudgeTouch: Offset? = null,
) {
    val context = LocalContext.current
    val art = remember(context) { HabiArt.get(context) }
    val density = LocalDensity.current
    val hopPx = with(density) { TAP_HOP_DP.dp.toPx() }
    val breathLiftPx = with(density) { BREATH_LIFT_DP.dp.toPx() }
    val faceKey = FaceKey(spec.mood, spec.personality, delighted, spec.closedEyes)
    val blinkValue: Float
    var tapBlinkPulse: (() -> Unit)? = null
    var tapReactionPulse: (() -> Unit)? = null
    var breathValue = 0f
    // Canales de respuesta, en reposo a 0: squash >0 aplasta / <0 estira (desde los pies), hop
    // eleva, tilt balancea sobre los pies, gaze mueve la cara. Gestos y toques los comparten: el
    // último animateTo gana, que es como debe interrumpirse una mascota.
    var squashValue = 0f
    var hopValue = 0f
    var tiltValue = 0f
    var gazeValue = Offset.Zero
    var heartsPhaseValue = 0f
    var fadeFrom: FaceKey? = null
    var fadeProgress = 1f
    // Dónde cayó el último dedo (0..1 dentro del lienzo), observado sin consumir el toque.
    var lastTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    // Los bucles de reposo leen la guía en vivo a través de esto y se saltan su turno mientras dure.
    val guided by rememberUpdatedState(breath != null)
    if (animated) {
        val breathPeriod =
            when (spec.mood) {
                Mood.RADIANT -> BREATH_PERIOD_RADIANT_MS
                Mood.WILTED, Mood.DRAMATIC -> BREATH_PERIOD_LOW_MS
                else -> BREATH_PERIOD_NORMAL_MS
            }
        val infiniteTransition = rememberInfiniteTransition(label = "habi-breath")
        val breathPhase by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(breathPeriod, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
                label = "habi-breath-phase",
            )
        breathValue = guidedBreathValue(breath, breathPhase)

        // Fundido de expresión. `shownFace` es la cara que ya se está enseñando; hasta que el efecto
        // corre, el fotograma de la recomposición sigue pintando la vieja entera (progreso 0), así
        // que nunca hay un fotograma con la cara nueva de golpe.
        var shownFace by remember { mutableStateOf(faceKey) }
        var previousFace by remember { mutableStateOf(faceKey) }
        val fade = remember { Animatable(1f) }
        LaunchedEffect(faceKey) {
            if (faceKey != shownFace) {
                previousFace = shownFace
                shownFace = faceKey
                fade.snapTo(0f)
                fade.animateTo(1f, tween(EXPRESSION_FADE_MS, easing = LinearEasing))
            }
        }
        if (faceKey != shownFace) {
            fadeFrom = shownFace
            fadeProgress = 0f
        } else {
            fadeFrom = previousFace
            fadeProgress = fade.value
        }

        if (delighted) {
            val heartsTransition = rememberInfiniteTransition(label = "habi-hearts")
            val heartsPhase by
                heartsTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                    label = "habi-hearts-phase",
                )
            heartsPhaseValue = heartsPhase
        }

        val idleBlink = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
                if (guided) continue
                val blinks = if (Random.nextInt(DOUBLE_BLINK_CHANCE) == 0) 2 else 1
                repeat(blinks) { index ->
                    if (index > 0) delay(DOUBLE_BLINK_GAP_MS)
                    idleBlink.snapTo(1f)
                    delay(BLINK_HOLD_MS)
                    idleBlink.snapTo(0f)
                }
            }
        }

        // Al pasar de guiado a sin guía, los ojos cerrados se funden con los abiertos. Mientras dura
        // la guía, lidRelease queda aparcado en el cierre: así el fotograma en que `breath` pasa a
        // null ya sale con los ojos cerrados y el fundido empieza desde ahí. `wasGuided` evita que
        // un avatar que nunca tuvo guía anime nada al aparecer.
        val lidRelease = remember { Animatable(0f) }
        var wasGuided by remember { mutableStateOf(breath != null) }
        LaunchedEffect(breath != null) {
            val nowGuided = breath != null
            if (nowGuided) {
                lidRelease.snapTo(breath?.eyeClosure ?: GUIDED_EYE_CLOSURE)
            } else if (wasGuided) {
                lidRelease.animateTo(0f, tween(LID_RELEASE_MS, easing = LinearOutSlowInEasing))
            }
            wasGuided = nowGuided
        }

        val squash = remember { Animatable(0f) }
        val hop = remember { Animatable(0f) }
        val tilt = remember { Animatable(0f) }
        val gazeX = remember { Animatable(0f) }
        val gazeY = remember { Animatable(0f) }
        squashValue = squash.value
        hopValue = hop.value
        tiltValue = tilt.value
        gazeValue = Offset(gazeX.value, gazeY.value)

        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(IDLE_GESTURE_MIN_DELAY_MS, IDLE_GESTURE_MAX_DELAY_MS))
                if (guided) continue
                when (Random.nextInt(3)) {
                    0 -> {
                        tilt.animateTo(-5f, tween(240, easing = EaseInOut))
                        delay(320)
                        tilt.animateTo(0f, SettleSpring)
                    }
                    1 -> {
                        squash.animateTo(-0.4f, tween(300, easing = EaseInOut))
                        delay(260)
                        squash.animateTo(0f, SettleSpring)
                    }
                    else -> {
                        val side = if (Random.nextBoolean()) 1f else -1f
                        gazeX.animateTo(side * 0.6f, tween(140, easing = LinearOutSlowInEasing))
                        delay(IDLE_GLANCE_HOLD_MS)
                        gazeX.animateTo(0f, GazeSpring)
                    }
                }
            }
        }

        if (onTap != null) {
            val tapBlink = remember { Animatable(0f) }
            val tapScope = rememberCoroutineScope()
            val tapJob = remember { arrayOfNulls<Job>(1) }
            val gazeJob = remember { arrayOfNulls<Job>(1) }
            val lastPokeHopped = remember { booleanArrayOf(false) }
            tapBlinkPulse = {
                tapScope.launch {
                    tapBlink.snapTo(1f)
                    delay(BLINK_HOLD_MS)
                    tapBlink.snapTo(0f)
                }
            }
            tapReactionPulse = {
                val touch = lastTouch
                gazeJob[0]?.cancel()
                gazeJob[0] =
                    tapScope.launch {
                        val targetX = ((touch.x - 0.5f) * 2f).coerceIn(-1f, 1f)
                        val targetY = ((touch.y - 0.5f) * 2f).coerceIn(-1f, 1f)
                        coroutineScope {
                            launch { gazeX.animateTo(targetX, tween(90, easing = LinearOutSlowInEasing)) }
                            launch { gazeY.animateTo(targetY, tween(90, easing = LinearOutSlowInEasing)) }
                        }
                        delay(GAZE_HOLD_MS)
                        coroutineScope {
                            launch { gazeX.animateTo(0f, GazeSpring) }
                            launch { gazeY.animateTo(0f, GazeSpring) }
                        }
                    }
                tapJob[0]?.cancel()
                tapJob[0] =
                    tapScope.launch {
                        val jitter = 1f + POKE_JITTER * (Random.nextFloat() * 2f - 1f)
                        val squashTarget = (POKE_SQUASH_BASE + POKE_SQUASH_HEAD_GAIN * (1f - touch.y)) * jitter
                        val tiltTarget = -(touch.x - 0.5f) * 2f * POKE_TILT_MAX_DEG * jitter
                        coroutineScope {
                            launch { squash.animateTo(squashTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
                            launch { tilt.animateTo(tiltTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
                        }
                        val hops = !lastPokeHopped[0] && Random.nextInt(HOP_CHANCE) == 0
                        lastPokeHopped[0] = hops
                        if (hops) {
                            val height = 0.6f + Random.nextFloat() * 0.3f
                            coroutineScope {
                                launch { squash.animateTo(-0.45f * height, tween(150, easing = LinearOutSlowInEasing)) }
                                launch { hop.animateTo(height, tween(150, easing = LinearOutSlowInEasing)) }
                                launch { tilt.animateTo(0f, tween(150, easing = LinearOutSlowInEasing)) }
                            }
                            coroutineScope {
                                launch { hop.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
                                launch { squash.animateTo(0.35f, tween(140, easing = FastOutLinearInEasing)) }
                            }
                            squash.animateTo(0f, JellySpring)
                        } else {
                            coroutineScope {
                                launch { squash.animateTo(0f, JellySpring) }
                                launch { tilt.animateTo(0f, JellySpring) }
                            }
                        }
                    }
            }
            blinkValue = guidedBlinkValue(breath, idleBlink.value, tapBlink.value, lidRelease.value)
        } else {
            blinkValue = guidedBlinkValue(breath, idleBlink.value, 0f, lidRelease.value)
        }

        // Recuerda el valor ya contestado, sembrado con el que traiga `nudge` al componer: una
        // recreación de la actividad (rotación) que conserve el ViewModel no repite la reacción.
        var lastAnsweredNudge by remember { mutableIntStateOf(nudge) }
        LaunchedEffect(nudge) {
            if (nudge > lastAnsweredNudge) {
                nudgeTouch?.let { lastTouch = it }
                tapBlinkPulse?.invoke()
                tapReactionPulse?.invoke()
            }
            lastAnsweredNudge = nudge
        }
    } else {
        breathValue = guidedBreathValue(breath, 0f)
        blinkValue = guidedBlinkValue(breath, 0f, 0f, 0f)
    }

    val expressiveTap = tapBlinkPulse != null
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleX by
        animateFloatAsState(
            targetValue =
                if (!pressed) {
                    1f
                } else if (expressiveTap) {
                    EXPRESSIVE_TAP_SCALE_X
                } else {
                    TAP_SCALE_X
                },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-x",
        )
    val scaleY by
        animateFloatAsState(
            targetValue =
                if (!pressed) {
                    1f
                } else if (expressiveTap) {
                    EXPRESSIVE_TAP_SCALE_Y
                } else {
                    TAP_SCALE_Y
                },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-y",
        )

    // Una sola descripción para todo el lienzo, con el ánimo; las piezas no llevan semántica propia.
    val contentDescription =
        stringResource(
            R.string.habi_avatar_cd_mood,
            stringResource(R.string.habi_avatar_cd),
            stringResource(HabiVoice.moodLabelRes(spec.mood)),
        )

    val breathing = breathScales(breathValue, breath?.amplitude ?: 1f)
    val look = HabiLook(delighted = delighted, eyeClosure = blinkValue, gazeX = gazeValue.x, gazeY = gazeValue.y)
    val current = habiLayers(spec, look)
    val from = fadeFrom
    val layers =
        if (from != null && fadeProgress < 1f) {
            val previous = spec.copy(mood = from.mood, personality = from.personality, closedEyes = from.closedEyes)
            crossfadeFace(habiLayers(previous, look.copy(delighted = from.delighted)), current, fadeProgress)
        } else {
            current
        }

    val canvasModifier =
        modifier
            .semantics { this.contentDescription = contentDescription }
            .let { base ->
                if (onTap != null) {
                    base
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                lastTouch =
                                    Offset(
                                        (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                        (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    )
                            }
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                onTap()
                                tapBlinkPulse?.invoke()
                                tapReactionPulse?.invoke()
                            },
                        )
                } else {
                    base
                }
            }
            .graphicsLayer {
                // Anclado en los pies: una criatura con peso, no un globo que escala desde el centro.
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -breathLiftPx * breathing.liftFactor - hopPx * hopValue
                rotationZ = tiltValue
                this.scaleX = scaleX * (1f + SQUASH_SCALE_X * squashValue) * breathing.scaleX
                this.scaleY = scaleY * (1f - SQUASH_SCALE_Y * squashValue) * breathing.scaleY
            }

    Canvas(canvasModifier) {
        drawHabiLayers(art, layers, sampleSizeFor(size.height, size.height / this.density))
        if (delighted) drawHearts(heartsPhaseValue)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarMoodGridPreview() {
    BitoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            for (personality in Personality.entries) {
                Row {
                    for (mood in Mood.entries) {
                        HabiAvatar(
                            spec = HabiSpec(mood, personality, EquippedSet()),
                            modifier = Modifier.size(72.dp).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarDoradoPreview() {
    BitoTheme {
        HabiAvatar(
            spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet(bodyColor = "body-dorado")),
            modifier = Modifier.size(150.dp),
        )
    }
}

/** El catálogo puesto, para revisarlo a ojo: un artículo de cada eje y los disfraces. */
@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarCatalogPreview() {
    val sets =
        listOf(
            EquippedSet(head = "head-copa"),
            EquippedSet(face = "face-gafas-redondas"),
            EquippedSet(neck = "neck-bufanda"),
            EquippedSet(feet = "feet-botas-agua"),
            EquippedSet(costume = "costume-calabaza"),
            EquippedSet(costume = "costume-reno"),
        )
    BitoTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            for (set in sets) {
                HabiAvatar(spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, set), modifier = Modifier.size(72.dp).padding(4.dp))
            }
        }
    }
}

// Corazones (racha de caricias): tres carriles (x, desfase de fase, inclinación) puestos a mano.
private val HEART_LANES = listOf(Triple(-0.26f, 0f, -12f), Triple(0.05f, 0.38f, 6f), Triple(0.28f, 0.72f, 14f))
private const val HEART_RISE_START_Y = 0.42f
private const val HEART_RISE_TRAVEL = 0.3f
private const val HEART_HALF_BASE = 0.026f
private const val HEART_HALF_GROWTH = 0.012f
private const val HEART_SWAY = 0.015f
private const val HEART_FADE_IN_END = 0.12f
private const val HEART_PEAK_ALPHA = 0.85f

/** Tres corazones que suben desde la cara mientras dura `delighted`. Son un efecto, no arte de Habi. */
private fun DrawScope.drawHearts(phase: Float) {
    for ((xFactor, offset, tiltDeg) in HEART_LANES) {
        val p = (phase + offset) % 1f
        val rise = 1f - (1f - p) * (1f - p)
        val alpha = (p / HEART_FADE_IN_END).coerceAtMost(1f) * (1f - rise) * HEART_PEAK_ALPHA
        if (alpha <= 0.01f) continue
        val sway = size.width * HEART_SWAY * sin((p * 1.5f + xFactor) * 2f * PI.toFloat())
        val cx = size.width * (0.5f + xFactor) + sway
        val cy = size.height * (HEART_RISE_START_Y - HEART_RISE_TRAVEL * rise)
        val half = size.width * (HEART_HALF_BASE + HEART_HALF_GROWTH * (p / 0.25f).coerceAtMost(1f))
        val heart =
            Path().apply {
                moveTo(cx, cy + half)
                cubicTo(cx - 1.6f * half, cy, cx - 0.9f * half, cy - 1.2f * half, cx, cy - 0.4f * half)
                cubicTo(cx + 0.9f * half, cy - 1.2f * half, cx + 1.6f * half, cy, cx, cy + half)
            }
        rotate(degrees = tiltDeg, pivot = Offset(cx, cy)) {
            drawPath(heart, color = Mofletes, alpha = alpha)
        }
    }
}
