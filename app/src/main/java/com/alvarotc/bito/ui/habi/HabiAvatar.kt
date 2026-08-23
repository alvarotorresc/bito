package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val BLINK_HALF_DURATION_MS = 90
private const val BLINK_MIN_DELAY_MS = 3000L
private const val BLINK_MAX_DELAY_MS = 5000L
private const val TAP_SCALE_X = 1.06f
private const val TAP_SCALE_Y = 0.94f

// Architect request: the plain press-squash above wasn't a perceptible enough tap reaction on the
// HabiScreen stage avatar. Expressive mode (animated && onTap != null) exaggerates the squash and
// pulses a full blink on click — the corner avatar (animated = false) never opts into this.
private const val EXPRESSIVE_TAP_SCALE_X = 1.10f
private const val EXPRESSIVE_TAP_SCALE_Y = 0.90f
private const val TAP_BLINK_HALF_DURATION_MS = 100

// QA 2026-08-23 bis («mascota, no robot»): everything anchors at the FEET (bottom-center
// transform origin) so squash, stretch and landings read as weight; breathing is visible and
// mood-paced; idle micro-gestures fire every few seconds; a tap plays one of three full
// choreographies (hop / happy wiggle / double bounce) with anticipation and overshoot.
private const val BREATH_SCALE = 0.022f
private const val BREATH_LIFT_DP = 1.2f
private const val BREATH_PERIOD_RADIANT_MS = 2000
private const val BREATH_PERIOD_NORMAL_MS = 2700
private const val BREATH_PERIOD_LOW_MS = 3500
private const val IDLE_GESTURE_MIN_DELAY_MS = 5000L
private const val IDLE_GESTURE_MAX_DELAY_MS = 11000L
private const val SQUASH_SCALE_X = 0.10f
private const val SQUASH_SCALE_Y = 0.14f
private const val TAP_HOP_DP = 10f

/**
 * The living Habi bean: breathing idle (mood-paced), periodic blink, idle micro-gestures, and (when [onTap] is given) a squash-and-stretch
 * tap response. Purely presentational — [spec] already carries mood, personality and the equipped
 * set; this composable owns no state about what Habi wears or feels.
 *
 * [animated] gates BOTH infinite loops (bob + blink) at once, frozen to their rest frame when
 * false — a constant per call site (never toggled mid-lifetime here), so branching composable
 * calls on it is safe. Exists for compose tests: `captureToImage`/`waitUntil` never settle against
 * an infinite transition, so a test that needs a stable frame passes `animated = false` instead of
 * fighting the clock. Every real screen keeps the default `true`.
 *
 * When both [animated] and [onTap] apply, a tap also pulses a full blink (open -> closed -> open,
 * independent Animatable from the idle blink loop so neither interrupts the other) and exaggerates
 * the press squash — the HabiScreen stage avatar's tap reaction. Callers with `animated = false`
 * (the Today corner avatar) keep the plain press squash only, no blink pulse.
 */
@Composable
fun HabiAvatar(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val hopPx = with(density) { TAP_HOP_DP.dp.toPx() }
    val breathLiftPx = with(density) { BREATH_LIFT_DP.dp.toPx() }
    val blinkValue: Float
    var tapBlinkPulse: (() -> Unit)? = null
    var tapReactionPulse: (() -> Unit)? = null
    var breathValue = 0f
    // Choreography channels, all resting at 0: squash >0 flattens / <0 stretches (from the feet),
    // hop lifts, tilt rocks on the feet. Idle gestures and tap reactions share them — the newest
    // animateTo wins, which is exactly the interruption behavior a pet should have.
    var squashValue = 0f
    var hopValue = 0f
    var tiltValue = 0f
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
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(breathPeriod, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "habi-breath-phase",
            )
        breathValue = breathPhase

        val idleBlink = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
                idleBlink.animateTo(1f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
                idleBlink.animateTo(0f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
            }
        }

        val squash = remember { Animatable(0f) }
        val hop = remember { Animatable(0f) }
        val tilt = remember { Animatable(0f) }
        squashValue = squash.value
        hopValue = hop.value
        tiltValue = tilt.value

        // Idle micro-gestures: a curious tilt or a tall stretch, every few seconds.
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(IDLE_GESTURE_MIN_DELAY_MS, IDLE_GESTURE_MAX_DELAY_MS))
                when (Random.nextInt(2)) {
                    0 -> {
                        tilt.animateTo(-5f, tween(240, easing = EaseInOut))
                        delay(320)
                        tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    else -> {
                        squash.animateTo(-0.5f, tween(300, easing = EaseInOut))
                        delay(260)
                        squash.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
            }
        }

        if (onTap != null) {
            val tapBlink = remember { Animatable(0f) }
            val tapScope = rememberCoroutineScope()
            val tapJob = remember { arrayOfNulls<Job>(1) }
            tapBlinkPulse = {
                tapScope.launch {
                    tapBlink.animateTo(1f, tween(TAP_BLINK_HALF_DURATION_MS, easing = LinearEasing))
                    tapBlink.animateTo(0f, tween(TAP_BLINK_HALF_DURATION_MS, easing = LinearEasing))
                }
            }
            tapReactionPulse = {
                tapJob[0]?.cancel()
                tapJob[0] =
                    tapScope.launch {
                        squash.snapTo(0f)
                        hop.snapTo(0f)
                        tilt.snapTo(0f)
                        when (Random.nextInt(3)) {
                            0 -> {
                                // Brinco: anticipación (se agacha), salto estirado, aterrizaje
                                // aplastado y asentarse con rebote.
                                squash.animateTo(1f, tween(80, easing = LinearEasing))
                                coroutineScope {
                                    launch { squash.animateTo(-0.8f, tween(140, easing = LinearOutSlowInEasing)) }
                                    launch { hop.animateTo(1f, tween(140, easing = LinearOutSlowInEasing)) }
                                }
                                coroutineScope {
                                    launch { hop.animateTo(0f, tween(150, easing = FastOutLinearInEasing)) }
                                    launch { squash.animateTo(0.7f, tween(150, easing = FastOutLinearInEasing)) }
                                }
                                squash.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                )
                            }
                            1 -> {
                                // Meneo feliz: balanceo sobre los pies con un puntito de squash.
                                squash.snapTo(0.35f)
                                tilt.animateTo(-9f, tween(80, easing = LinearEasing))
                                tilt.animateTo(8f, tween(90, easing = LinearEasing))
                                tilt.animateTo(-5f, tween(80, easing = LinearEasing))
                                coroutineScope {
                                    launch { tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                    launch { squash.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                }
                            }
                            else -> {
                                // Doble botecito, estirándose al subir y aplastándose al caer.
                                repeat(2) {
                                    coroutineScope {
                                        launch { hop.animateTo(0.45f, tween(110, easing = LinearOutSlowInEasing)) }
                                        launch { squash.animateTo(-0.4f, tween(110, easing = LinearOutSlowInEasing)) }
                                    }
                                    coroutineScope {
                                        launch { hop.animateTo(0f, tween(110, easing = FastOutLinearInEasing)) }
                                        launch { squash.animateTo(0.5f, tween(110, easing = FastOutLinearInEasing)) }
                                    }
                                }
                                squash.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    }
            }
            blinkValue = maxOf(idleBlink.value, tapBlink.value)
        } else {
            blinkValue = idleBlink.value
        }
    } else {
        blinkValue = 0f
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

    // [D]/[E]: was a single static "Habi" regardless of mood/personality/equipped — now folds in
    // the mood, the one enrichment that's cheap AND honest (see HabiVoice.moodLabelRes' kdoc for
    // why personality-flavored copy stays out of scope here). Still ONE aggregated description on
    // the Canvas container, never per drawn path — drawHabi()'s ~30 private drawX helpers below
    // correctly carry zero semantics of their own.
    val contentDescription =
        stringResource(
            R.string.habi_avatar_cd_mood,
            stringResource(R.string.habi_avatar_cd),
            stringResource(HabiVoice.moodLabelRes(spec.mood)),
        )

    val canvasModifier =
        modifier
            .semantics { this.contentDescription = contentDescription }
            .let { base ->
                if (onTap != null) {
                    base.clickable(
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
                // Anchored at the feet: a creature with weight, not a balloon scaling around
                // its middle.
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -breathLiftPx * breathValue - hopPx * hopValue
                rotationZ = tiltValue
                this.scaleX = scaleX * (1f + SQUASH_SCALE_X * squashValue) * (1f - BREATH_SCALE * 0.5f * breathValue)
                this.scaleY = scaleY * (1f - SQUASH_SCALE_Y * squashValue) * (1f + BREATH_SCALE * breathValue)
            }

    Canvas(canvasModifier) {
        drawHabi(spec, blink = blinkValue)
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

/** T10 catalog: every pattern, human-checked here — fidelity against the mockup is a visual call, not a test. */
@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarPatternsPreview() {
    val patterns =
        listOf(
            "pattern-motas",
            "pattern-rayitas",
            "pattern-corazones",
            "pattern-estrellas",
            "pattern-flores",
            "pattern-chispas",
            "pattern-llamas",
        )
    BitoTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            for (pattern in patterns) {
                HabiAvatar(
                    spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(pattern = pattern)),
                    modifier = Modifier.size(72.dp).padding(4.dp),
                )
            }
        }
    }
}

/** T10 catalog: every upper + lower item. */
@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarAccessoriesPreview() {
    val uppers = listOf("upper-gorro-lana", "upper-lazo", "upper-copa", "upper-corona")
    val lowers = listOf("lower-calcetines", "lower-zapatillas")
    BitoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                for (upper in uppers) {
                    HabiAvatar(
                        spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(upper = upper)),
                        modifier = Modifier.size(72.dp).padding(4.dp),
                    )
                }
            }
            Row {
                for (lower in lowers) {
                    HabiAvatar(
                        spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(lower = lower)),
                        modifier = Modifier.size(72.dp).padding(4.dp),
                    )
                }
            }
        }
    }
}
