package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val BOB_HALF_RANGE_DP = 2f
private const val BOB_PERIOD_MS = 2400
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

/**
 * The living Habi bean: idle bob, periodic blink, and (when [onTap] is given) a squash-and-stretch
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
    val bobPx: Float
    val blinkValue: Float
    var tapBlinkPulse: (() -> Unit)? = null
    if (animated) {
        val infiniteTransition = rememberInfiniteTransition(label = "habi-bob")
        val bobPhase by
            infiniteTransition.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(BOB_PERIOD_MS, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "habi-bob-phase",
            )
        bobPx = with(density) { (bobPhase * BOB_HALF_RANGE_DP).dp.toPx() }

        val idleBlink = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
                idleBlink.animateTo(1f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
                idleBlink.animateTo(0f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
            }
        }

        if (onTap != null) {
            val tapBlink = remember { Animatable(0f) }
            val tapScope = rememberCoroutineScope()
            tapBlinkPulse = {
                tapScope.launch {
                    tapBlink.animateTo(1f, tween(TAP_BLINK_HALF_DURATION_MS, easing = LinearEasing))
                    tapBlink.animateTo(0f, tween(TAP_BLINK_HALF_DURATION_MS, easing = LinearEasing))
                }
            }
            blinkValue = maxOf(idleBlink.value, tapBlink.value)
        } else {
            blinkValue = idleBlink.value
        }
    } else {
        bobPx = 0f
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
                        },
                    )
                } else {
                    base
                }
            }
            .graphicsLayer {
                translationY = bobPx
                this.scaleX = scaleX
                this.scaleY = scaleY
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
